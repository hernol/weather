package ar.com.hernol.weather.providers.openweathermap;

import android.util.Log;

import com.google.android.gms.maps.model.LatLngBounds;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import ar.com.hernol.weather.DebugLog;
import ar.com.hernol.weather.LenientDoubleTypeAdapter;
import ar.com.hernol.weather.model.WeatherInfo;
import ar.com.hernol.weather.providers.Provider;
import ar.com.hernol.weather.providers.wunderground.WundergroundResponse;

/**
 * Created by hernol on 11/6/16.
 */

public class OpenWeatherMapProvider extends Provider {
    private static final String TAG = "codeka.weather";
    private static final String API_KEY = "e4b707016af58444ffdda5a1bf254e82";

    @Override
    public void fetchWeather(WeatherInfo.Builder builder) {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(Double.class, new LenientDoubleTypeAdapter())
                .create();

        Log.d(TAG, "Querying Wunderground for weather info for: " + builder.getLat() + "," + builder.getLng());
        try {
            URL url = new URL(String.format(
                "http://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s",
                builder.getLat(),
                builder.getLng(),
                API_KEY));
        Log.d(TAG, "Connecting to: " + url);
        URLConnection conn = url.openConnection();
        InputStream ins = new BufferedInputStream(conn.getInputStream());

        JsonReader json = new JsonReader(new InputStreamReader(ins, "UTF-8"));
        OpenWeatherMapResponse response = gson.fromJson(json, OpenWeatherMapResponse.class);
        Log.d(TAG, "Response parsed successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Error fetching weather information.", e);
            DebugLog.current().log("Error fetching weather: " + e.getMessage());
        }

    }

    @Override
    public InputStream fetchMapOverlay(LatLngBounds latLngBounds, int width, int height) {
        return null;
    }
}
