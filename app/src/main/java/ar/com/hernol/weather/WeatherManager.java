package ar.com.hernol.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLngBounds;

import java.io.InputStream;
import java.util.ArrayList;

import ar.com.hernol.weather.location.GeocodeInfo;
import ar.com.hernol.weather.location.GeocodeProvider;
import ar.com.hernol.weather.location.LocationProvider;
import ar.com.hernol.weather.model.WeatherInfo;
import ar.com.hernol.weather.providers.wunderground.WundergroundProvider;
import ar.com.hernol.weather.providers.openweathermap.OpenWeatherMapProvider;

public class WeatherManager {
  private static final String TAG = WeatherManager.class.getSimpleName();
  public static WeatherManager i = new WeatherManager();

  private static final long STATIONARY_QUERY_TIME_MS = 3 * 60 * 60 * 1000; // every three hours
  private static final long MOVING_QUERY_TIME_MS = 60 * 1000; // every minute

  private boolean queryInProgress;

  private ArrayList<Runnable> onUpdateRunnables = new ArrayList<>();

  private WeatherManager() {
  }

  /** Adds a runnable to be called when the weather is updated (may not happen on UI thread) */
  public void addUpdateRunnable(Runnable runnable) {
    onUpdateRunnables.add(runnable);
  }

  public void removeUpdateRunnable(Runnable runnable) {
    onUpdateRunnables.remove(runnable);
  }

  public void refreshWeather(final Context context, final boolean force) {
    if (queryInProgress) {
      return;
    }
    queryInProgress = true;

    final SharedPreferences prefs =
        context.getSharedPreferences("ar.com.hernol.weather", Context.MODE_PRIVATE);

    Log.d(TAG, "Doing a location query.");
    LocationProvider locationProvider = new LocationProvider(context);
    locationProvider.getLocation(force, new LocationProvider.LocationFetchedListener() {
      @Override
      public void onLocationFetched(Location loc) {
        Log.i(TAG, "Got location: " + loc.getLatitude() + "," + loc.getLongitude());
        DebugLog.current().setLocation(loc.getLatitude(), loc.getLongitude());
        DebugLog.current().log("Got location fix: " + loc.getLatitude() + "," + loc.getLongitude());

        boolean needWeatherQuery = force || checkNeedWeatherQuery(loc, prefs);
        if (needWeatherQuery) {
          queryWeather(context, loc, prefs);
        } else {
          DebugLog.current().log("No weather query required.");
          queryInProgress = false;
        }
      }
    });
  }

  public WeatherInfo getCurrentWeather(Context context) {
    final SharedPreferences prefs = context.getSharedPreferences("ar.com.hernol.weather",
        Context.MODE_PRIVATE);
    return WeatherInfo.Builder.load(prefs);
  }

  /** Fetches an image to overlay over a map. Must be called on a background thread. */
  public InputStream fetchMapOverlay(LatLngBounds latLngBounds, int width, int height) {
    return new WundergroundProvider().fetchMapOverlay(latLngBounds, width, height);
  }

  /**
   * Checks whether we need to do a new weather query.
   *
   * We only do a weather query once every three hours by default, unless we've moved > 5km since
   * the last weather query, in which case we do it every 30 minutes.
   *
   * @param loc Current location, used to determine if we've moved since the last weather query.
   * @param prefs A SharedPreferences which holds our saved data.
   * @return A value which indicates whether we need to do a new weather query.
   */
  private boolean checkNeedWeatherQuery(Location loc, SharedPreferences prefs) {
    long timeOfLastWeatherQuery = prefs.getLong("TimeOfLastWeatherQuery", 0);
    if (timeOfLastWeatherQuery == 0) {
      DebugLog.current().log("No previous weather queries, doing first one.");
      return true;
    }

    double lastQueryLat = prefs.getFloat("LastQueryLat", 0.0f);
    double lastQueryLng = prefs.getFloat("LastQueryLng", 0.0f);

    long timeBetweenQueries = STATIONARY_QUERY_TIME_MS;
    if (lastQueryLat != 0.0f && lastQueryLng != 0.0f) {
      float[] results = new float[1];
      Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), lastQueryLat, lastQueryLng,
          results);
      DebugLog.current().log("We've moved " + results[0] + " metres since the last query.");
      if (results[0] > 5000.0f) {
        timeBetweenQueries = MOVING_QUERY_TIME_MS;
      }
    }

    long timeSinceLastWeatherQuery = System.currentTimeMillis() - timeOfLastWeatherQuery;
    if (timeSinceLastWeatherQuery > timeBetweenQueries) {
      DebugLog.current().log(timeSinceLastWeatherQuery
          + "ms has elapsed since last weather query. Performing new query now.");
      return true;
    }
    return false;
  }

  /** Fires off a thread to perform the actual weather query. */
  private void queryWeather(
      final Context context,
      final Location loc,
      final SharedPreferences prefs) {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        GeocodeInfo geocodeInfo =
            new GeocodeProvider().getGeocodeInfo(loc.getLatitude(), loc.getLongitude());

        WeatherInfo.Builder builder = new WeatherInfo.Builder(loc.getLatitude(), loc.getLongitude());
        builder.setGeocodeInfo(geocodeInfo);
        //new WundergroundProvider().fetchWeather(builder);
        new OpenWeatherMapProvider().fetchWeather(builder);

        WeatherInfo weatherInfo = builder.build();
        if (weatherInfo != null) {
          SharedPreferences.Editor editor = prefs.edit();
          weatherInfo.save(editor);
  
          editor.putLong("TimeOfLastWeatherQuery", System.currentTimeMillis());
          editor.putFloat("LastQueryLat", (float) loc.getLatitude());
          editor.putFloat("LastQueryLng", (float) loc.getLongitude());
          editor.apply();
        }

        try {
          DebugLog.saveCurrent(context);
        } catch (Exception e) {
          // ignore errors.
        }

        WeatherWidgetProvider.notifyRefresh(context);
        for (Runnable runnable : onUpdateRunnables) {
          runnable.run();
        }
        queryInProgress = false;
      }
    });
    t.start();
  }
}

