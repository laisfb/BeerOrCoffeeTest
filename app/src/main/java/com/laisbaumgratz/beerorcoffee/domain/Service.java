package com.laisbaumgratz.beerorcoffee.domain;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Service {

    private static final String TAG = "Service";
    private static final String API_ADDRESS = "https://c7q5vyiew7.execute-api.us-east-1.amazonaws.com";

    private static final String
            URL = API_ADDRESS + "/prod/places";

    public static Boolean savePlaceData(JSONObject request) {
        HttpRequest response = HttpRequest.post(URL)
                .header("x-api-key", "IfXJnQVdjo1fI4z6OQTWB6RPJ8Qs4JbcaDOZ83vt")
                .contentType("application/json")
                .send(request.toString());

        Log.d(TAG, "response = " + response.body());

        return response.code() == 200;
    }

    public static LatLng getLatLngFromAddress(String address) {
        double lat = 0, lng = 0;

        try {
            String URL = "http://maps.googleapis.com/maps/api/geocode/json?address=" +  URLEncoder.encode(address, "UTF-8");
            String response = HttpRequest.get(URL).body();
            Log.d(TAG, "getLatLngFromAddress response = " + response);

            JSONObject jsonObject = new JSONObject(response);
            JSONObject firstResult = ((JSONArray) jsonObject.get("results")).getJSONObject(0);
            JSONObject location = firstResult.getJSONObject("geometry").getJSONObject("location");
            lat = location.getDouble("lat");
            lng = location.getDouble("lng");

        }
        catch (UnsupportedEncodingException | JSONException e) {
            e.printStackTrace();
        }

        return new LatLng(lat, lng);
    }
}
