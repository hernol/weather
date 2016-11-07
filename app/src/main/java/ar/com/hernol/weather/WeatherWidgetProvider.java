package ar.com.hernol.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.GregorianCalendar;

import ar.com.hernol.weather.location.GeocodeInfo;
import ar.com.hernol.weather.model.CurrentCondition;
import ar.com.hernol.weather.model.Forecast;
import ar.com.hernol.weather.model.WeatherIcon;
import ar.com.hernol.weather.model.WeatherInfo;

/**
 * This is the widget provider which renders the actual widget.
 */
public class WeatherWidgetProvider extends AppWidgetProvider {
  private static final String TAG = WeatherWidgetProvider.class.getSimpleName();
  private RemoteViews remoteViews;
  private ComponentName componentName;

  public static final String CUSTOM_REFRESH_ACTION = "ar.com.hernol.weather.UpdateAction";

  /**
   * You can call this to send a notification to the graph widget to update itself.
   */
  public static void notifyRefresh(Context context) {
    Intent i = new Intent(context, WeatherWidgetProvider.class);
    i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    i.putExtra(CUSTOM_REFRESH_ACTION, 1);
    context.sendBroadcast(i);
  }

  /**
   * Called when we receive a notification, either from the widget subsystem directly, or from our
   * custom refresh code.
   */
  @Override
  public void onReceive(Context context, Intent intent) {
    WeatherAlarmReceiver.schedule(context);

    try {
      remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
      componentName = new ComponentName(context, WeatherWidgetProvider.class);

      if (intent.getIntExtra(CUSTOM_REFRESH_ACTION, 0) == 1) {
        refreshWidget(context);
      } else {
        super.onReceive(context, intent);
      }

      AppWidgetManager.getInstance(context).updateAppWidget(componentName, remoteViews);
    } catch (Exception e) {
      Log.e(TAG, "Unhandled exception!", e);
    }
  }

  /**
   * This is called when the "options" of the widget change. We'll just refresh the widget.
   */
  @Override
  public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
      int appWidgetId, Bundle newOptions) {

    WeatherAlarmReceiver.schedule(context);
    refreshWidget(context);
  }

  /**
   * This is called when the widget is updated (usually when it starts up, but also gets called
   * ~every 30 minutes.
   */
  @Override
  public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
    super.onUpdate(context, mgr, appWidgetIds);

    WeatherAlarmReceiver.schedule(context);

    Intent intent = new Intent(context, WeatherActivity.class);
    PendingIntent pendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    remoteViews.setOnClickPendingIntent(R.id.weather_btn, pendingIntent);

    refreshWidget(context);
  }

  private void refreshWidget(Context context) {
    SharedPreferences prefs = context.getSharedPreferences("ar.com.hernol.weather",
        Context.MODE_PRIVATE);
    WeatherInfo weatherInfo = WeatherInfo.Builder.load(prefs);
    if (weatherInfo == null) {
      return;
    }

    CurrentCondition currentCondition = weatherInfo.getCurrentCondition();
    if (currentCondition == null) {
      // oops!
      return;
    }
    remoteViews.setTextViewText(R.id.weather_temp, String.format("%d °C",
        (int) Math.round(currentCondition.getTemperature())));
    remoteViews.setTextViewText(R.id.weather_text, currentCondition.getDescription());

    GeocodeInfo geocodeInfo = weatherInfo.getGeocodeInfo();
    remoteViews.setTextViewText(R.id.geocode_location, geocodeInfo.getShortName());

    // a bit hacky...
    int hour = new GregorianCalendar().get(Calendar.HOUR_OF_DAY);
    boolean isNight = hour < 6 || hour > 20;

    WeatherIcon icon = currentCondition.getIcon();
    remoteViews.setImageViewResource(R.id.current_icon, icon.getLargeIconId(isNight));

    int offset;
    if (hour < 12) {
      // still morning, show today's forecast
      offset = 0;
      remoteViews.setTextViewText(R.id.tomorrow_text, "Today");
    } else {
      offset = 1;
      remoteViews.setTextViewText(R.id.tomorrow_text, "Tomorrow");
    }

    Forecast forecast = weatherInfo.getForecasts().get(offset);
    remoteViews.setTextViewText(R.id.tomorrow_weather, String.format(
        "%d °C %s", Math.round(forecast.getHighTemperature()), forecast.getShortDescription()));
    remoteViews.setImageViewResource(R.id.tomorrow_icon, forecast.getIcon().getSmallIconId(false));
  }
}
