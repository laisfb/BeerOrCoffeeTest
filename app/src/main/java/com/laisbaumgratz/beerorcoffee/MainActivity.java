package com.laisbaumgratz.beerorcoffee;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.laisbaumgratz.beerorcoffee.adapters.PlacesAutoCompleteAdapter;
import com.laisbaumgratz.beerorcoffee.domain.Service;
import com.laisbaumgratz.beerorcoffee.listeners.RecyclerItemClickListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
                            implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final LatLngBounds BOUNDS = new LatLngBounds(new LatLng(-0, 0), new LatLng(0, 0));
    private static final int REQUEST_LOCATION = 1;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean permissionGranted;
    private boolean fromSettings = false;
    double latitude, longitude;

    protected GoogleApiClient googleApiClient;
    private PlacesAutoCompleteAdapter paAdapter;

    private ScrollView scrollView;
    private EditText etName;
    private EditText etAddress;
    private RecyclerView recyclerView;
    private TextView tvLatitude;
    private EditText etLatitude;
    private TextView tvLongitude;
    private EditText etLongitude;
    private TextView tvBeverage;
    private Spinner spBeverage;
    private Button btnSubmit;

    private String name;
    private String address;
    private int beverage;

    private RegisterTask registerTask;
    private GetAddressTask getAddressTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buildGoogleApiClient();
        findViewsById();
        setupVariables();
        setupSpinner();

        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Localização")
                .setMessage("Deseja usar sua localização para preencher os campos do formulário?")
                .setPositiveButton("Sim", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        if (!isLocationEnabled())
                            showAlert();
                        else {
                            toggleGPSUpdates();
                        }
                    }
                })
                .setNegativeButton("Não", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    }
                });
        dialog.show();
    }

    protected synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .build();
    }

    private void findViewsById() {
        scrollView = (ScrollView)findViewById(R.id.outer_scrollview);
        etName = (EditText) findViewById(R.id.et_name);
        etAddress = (EditText) findViewById(R.id.et_address);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        tvLatitude = (TextView) findViewById(R.id.tv_latitude);
        etLatitude = (EditText) findViewById(R.id.et_latitude);
        tvLongitude = (TextView) findViewById(R.id.tv_longitude);
        etLongitude = (EditText) findViewById(R.id.et_longitude);
        tvBeverage = (TextView) findViewById(R.id.tv_beverage);
        spBeverage = (Spinner) findViewById(R.id.sp_beverage);
        btnSubmit = (Button) findViewById(R.id.btn_submit);
    }

    private void setupVariables() {
        locationListener = new LocationListener() {
            public void onLocationChanged(final Location location) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        updateLatLongAddress();
                    }
                });
            }
            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {
                Toast.makeText(MainActivity.this, "Network status changed", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onProviderEnabled(String s) {
                Toast.makeText(MainActivity.this, "Provider was enabled", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onProviderDisabled(String s) {
                Toast.makeText(MainActivity.this, "Provider was disabled", Toast.LENGTH_SHORT).show();
            }
        };

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        paAdapter = new PlacesAutoCompleteAdapter(this, R.layout.search_row, googleApiClient, BOUNDS, null);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(paAdapter);

        // What happens when the user chooses one of the suggestions
        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(this, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        final PlacesAutoCompleteAdapter.PlaceAutocomplete item = paAdapter.getItem(position);
                        final String placeId = String.valueOf(item.placeId);
                        Log.i("TAG", "Autocomplete item selected: " + item);

                        PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(googleApiClient, placeId);
                        placeResult.setResultCallback(new ResultCallback<PlaceBuffer>() {
                            @Override
                            public void onResult(@NonNull PlaceBuffer places) {
                                if (places.getCount() == 1) {
                                    latitude = places.get(0).getLatLng().latitude;
                                    longitude = places.get(0).getLatLng().longitude;
                                    etName.setText(item.description1);

                                    updateLayout();
                                    updateLatLongAddress();
                                }
                                else {
                                    Toast.makeText(getApplicationContext(), "Error. Please try again later", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        Log.i("TAG", "Clicked: " + item);
                        Log.i("TAG", "Called getPlaceById to get Place details for " + item.placeId);
                    }
                })
        );

        etAddress.setOnTouchListener(new EditText.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                // Scroll down
                if (scrollView.getScrollY() != 330) {
                    scrollView.scrollTo(0, 330);
                }

                // Show RecyclerView
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
                params.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                recyclerView.setLayoutParams(params);

                // Hide the other views
                tvLatitude.setVisibility(View.INVISIBLE);
                etLatitude.setVisibility(View.INVISIBLE);
                tvLongitude.setVisibility(View.INVISIBLE);
                etLongitude.setVisibility(View.INVISIBLE);
                tvBeverage.setVisibility(View.INVISIBLE);
                spBeverage.setVisibility(View.INVISIBLE);
                btnSubmit.setVisibility(View.INVISIBLE);

                // Request focus to EditText and show keyboard
                etAddress.setFocusable(true);
                if(etAddress.requestFocus()) {
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }

                return false;
            }
        });

        etAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!s.toString().equals("") && googleApiClient.isConnected()) {
                    // Show suggestions only if etAddress is on focus
                    if (etAddress.isFocused())
                        paAdapter.getFilter().filter(s.toString());
                    else
                        paAdapter.getFilter().filter("");
                }
                else if (!googleApiClient.isConnected()) {
                    Toast.makeText(getApplicationContext(), "Google API Client not connected", Toast.LENGTH_SHORT).show();
                    googleApiClient.connect();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etAddress.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // If user pressed the 'enter' key
                if (actionId == KeyEvent.KEYCODE_ENDCALL) {
                    updateLayout();
                    setLatLongFromAddress();
                }

                return true;
            }
        });
    }

    private void updateLayout() {
        // Scroll up
        scrollView.fullScroll(ScrollView.FOCUS_UP);

        // Hide RecyclerView
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
        params.height = 0;
        recyclerView.setLayoutParams(params);

        // Show the other views
        tvLatitude.setVisibility(View.VISIBLE);
        etLatitude.setVisibility(View.VISIBLE);
        tvLongitude.setVisibility(View.VISIBLE);
        etLongitude.setVisibility(View.VISIBLE);
        tvBeverage.setVisibility(View.VISIBLE);
        spBeverage.setVisibility(View.VISIBLE);
        btnSubmit.setVisibility(View.VISIBLE);

        // Hide keyboard
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void setLatLongFromAddress() {
        address = etAddress.getText().toString();

        getAddressTask = new GetAddressTask();
        getAddressTask.execute((Void) null);
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.beer_or_coffee, R.layout.spinner_item);

        if (spBeverage != null) {
            spBeverage.setAdapter(adapter);

            spBeverage.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    beverage = position;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void showAlert() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Ativar Localização")
                .setMessage("Sua localização está desativada. " +
                        "Deseja ativar?")
                .setPositiveButton("Sim", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        Intent settings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(settings);
                        fromSettings = true;
                    }
                })
                .setNegativeButton("Não", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    }
                });
        dialog.show();
    }

    public void toggleGPSUpdates() {
        Toast.makeText(getApplicationContext(), "Por favor, aguarde alguns instantes...", Toast.LENGTH_SHORT).show();

        permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (permissionGranted) {
            // Try to get the user last known location
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                updateLatLongAddress();
            }

            // Get user location every 5 minutes
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5 * 60 * 1000, 10, locationListener);
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            toggleGPSUpdates();
        }
    }

    private void updateLatLongAddress() {
        etLatitude.setText(latitude + "");
        etLongitude.setText(longitude + "");

        try {
            Geocoder geocoder;
            List<Address> addresses;
            geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
            addresses = geocoder.getFromLocation(latitude, longitude, 1);

            String city = addresses.get(0).getLocality();
            String state = addresses.get(0).getAdminArea();
            String country = addresses.get(0).getCountryName();

            address = addresses.get(0).getAddressLine(0) + ", " + city + " - " + state + ", " + country;
            etAddress.setText(address);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendData(View view) {

        boolean cancel = false;
        View focusView = null;

        if (spBeverage.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Por favor, selecione o tipo de bebida disponível", Toast.LENGTH_SHORT).show();
            focusView = spBeverage;
            cancel = true;
        }

        if (etLatitude.getText() == null || etLatitude.getText().toString().isEmpty()) {
            etLatitude.setError(getString(R.string.error_field_required));
            focusView = etLatitude;
            cancel = true;
        }

        if (etLongitude.getText() == null || etLongitude.getText().toString().isEmpty()) {
            etLongitude.setError(getString(R.string.error_field_required));
            focusView = etLongitude;
            cancel = true;
        }

        if (etAddress.getText() == null || etAddress.getText().toString().isEmpty()) {
            etAddress.setError(getString(R.string.error_field_required));
            focusView = etAddress;
            cancel = true;
        }

        if (etName.getText() == null || etName.getText().toString().isEmpty()) {
            etName.setError(getString(R.string.error_field_required));
            focusView = etName;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        }
        else {
            name = etName.getText().toString();
            address = etAddress.getText().toString();
            latitude = Double.parseDouble(etLatitude.getText().toString());
            longitude = Double.parseDouble(etLongitude.getText().toString());
            beverage = (int) spBeverage.getSelectedItemId();

            registerTask = new RegisterTask();
            registerTask.execute((Void) null);
        }
    }

    private class GetAddressTask extends AsyncTask<Void, Void, LatLng> {
        @Override
        protected LatLng doInBackground(Void... params) {
            return Service.getLatLngFromAddress(address);
        }

        @Override
        protected void onPostExecute(final LatLng result) {
            getAddressTask = null;

            if (result != null) {
                etLatitude.setText(result.latitude + "");
                etLongitude.setText(result.longitude + "");
            }
        }
    }

    private class RegisterTask extends AsyncTask<Void, Void, Boolean> {

        private void clearForm() {
            etName.setText("");
            etAddress.setText("");
            etLatitude.setText("");
            etLongitude.setText("");
            spBeverage.setSelection(0);

            scrollView.fullScroll(ScrollView.FOCUS_UP);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            JSONObject request = new JSONObject();
            try {
                request.put("name", name);
                request.put("address", address);
                request.put("latitude", latitude);
                request.put("longitude", longitude);
                request.put("beverage", beverage);
            }
            catch (JSONException e) {
                e.printStackTrace();
            }

            return Service.savePlaceData(request);
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            registerTask = null;

            if (success) {
                Toast.makeText(getApplicationContext(), "Local cadastrado com sucesso!", Toast.LENGTH_SHORT).show();
                clearForm();
            }
            else {
                Toast.makeText(getApplicationContext(), "Houve um erro ao salvar os dados. Por favor, tente novamente dentro de alguns minutos.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
            googleApiClient.connect();
        }

        if (fromSettings) {
            if(isLocationEnabled())
                toggleGPSUpdates();
            else
                Toast.makeText(getApplicationContext(), "Localização não ativada", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (googleApiClient.isConnected())
            googleApiClient.disconnect();
    }
}
