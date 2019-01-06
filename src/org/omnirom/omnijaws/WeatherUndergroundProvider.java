package org.omnirom.omnijaws;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.omnirom.omnijaws.WeatherInfo.DayForecast;


public class WeatherUndergroundProvider extends AbstractWeatherProvider {
    private static final String TAG = "WeatherUndergroundProvider";

    private static final int FORECAST_DAYS = 5;
    private static final String SELECTION_LOCATION = "/q/%f,%f";
    private static final String SELECTION_ID = "%s";
    private static final String API_KEY = "YOUR_WUG_API_KEY";
    private static final String API_KEY_PREFERENCE = "custom_api_key";


    private static final String URL_LOCATION =
            "http://api.wunderground.com/api/%s/geolookup/q/%s.json";
    private static final String URL_WEATHER =
            "http://api.wunderground.com/api/%s/conditions%s.json";
    private static final String URL_FORECAST =
            "http://api.wunderground.com/api/%s/forecast10day%s.json";

    public WeatherUndergroundProvider(Context context) {
        super(context);
    }

    @Override
    public List<WeatherInfo.WeatherLocation> getLocations(String input) {

        String url = String.format(URL_LOCATION,getAPIKey(), Uri.encode(input));
        String response = retrieve(url);
        if (response == null) {
            return null;
        }

        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject jsonObject = new JSONObject(response);
            ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<>();
            if (jsonObject.optJSONObject("location")!= null) { //this means only one result
                WeatherInfo.WeatherLocation location = new WeatherInfo.WeatherLocation();
                JSONObject jsonLocation = new JSONObject(response).getJSONObject("location");
                location.id = jsonLocation.getString("l");
                location.city = jsonLocation.getString("city");
                location.countryId = jsonLocation.getString("country_name");
                results.add(location);

            }else {
                JSONArray jsonArray = jsonObject.getJSONObject("response").getJSONArray("results");
                int count = jsonArray.length();
                for (int i = 0; i < count; i++) {
                    JSONObject result = jsonArray.getJSONObject(i);
                    WeatherInfo.WeatherLocation location = new WeatherInfo.WeatherLocation();

                    location.id = result.getString("l");
                    location.city = result.getString("name");
                    location.countryId = result.getString("country");
                    results.add(location);
                }
            }
            return results;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed location data (input=" + input + ")", e);
        }

        return null;
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_ID, id);
        return handleWeatherRequest(selection, metric);
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_LOCATION,
                location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(selection, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection, boolean metric) {
        String units = metric ? "metric" : "imperial";
        String conditionUrl = String.format(Locale.US, URL_WEATHER,getAPIKey(), selection);
        String conditionResponse = retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }
        log(TAG, "Condition URL = " + conditionUrl + " returning a response of " + conditionResponse);

        String forecastUrl = String.format(Locale.US, URL_FORECAST,getAPIKey(), selection);
        String forecastResponse = retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }
        log(TAG, "Forcast URL = " + forecastUrl + " returning a response of " + forecastResponse);

        try {
            JSONObject conditions = new JSONObject(conditionResponse).getJSONObject("current_observation");
            
            JSONObject weather = conditions.getJSONObject("display_location");

            ArrayList<WeatherInfo.DayForecast> forecasts =
                    parseForecasts(new JSONObject(forecastResponse).getJSONObject("forecast").getJSONObject("simpleforecast").getJSONArray("forecastday"), metric);

            WeatherInfo w = new WeatherInfo(mContext,
                                            weather.getString("wmo"),
                                            weather.getString("city"),
                                            conditions.getString("weather"),
                                            mapConditionIconToCode(conditions.getString("icon")),
                                            conditions.getInt("temp_c") * 1.0f,
                                            cleanStrings(conditions.getString("relative_humidity")),
                                            conditions.getInt("wind_kph")*1.0f,
                                            conditions.getInt("wind_degrees"),
                                            metric,
                                            forecasts,
                                            System.currentTimeMillis());

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + conditionUrl
                    + ")", e);
        }

        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray forecasts, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < FORECAST_DAYS; i++) {
            DayForecast item;
            try {
                JSONObject forecast = forecasts.getJSONObject(i);
                item = new DayForecast(
                        Float.valueOf(forecast.getJSONObject("low").getString("celsius")),
                        Float.valueOf(forecast.getJSONObject("high").getString("celsius")),
                        forecast.getString("conditions"),
                        mapConditionIconToCode(forecast.getString("icon")),
                        forecast.getJSONObject("date").getString("epoch"),
                        metric);
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i + " creating dummy", e);
                item = new DayForecast(
                        0,
                        0,
                        " ",
                        -1,
                        "NaN",
                        metric);
            }
            result.add(item);
        }
        return result;
    }


    @Override
    public boolean shouldRetry() {
        return false;
    }
    

    private int mapConditionIconToCode(String icon) {

        switch (icon) {


            case "tstorms":
            case "nt_tstorms":
                return 4;   // thunderstorm

            case "chancetstorms":
            case "nt_chancetstorms":   // chance of thunderstorm
                return 37;

            case "chanceflurries":
            case "nt_chanceflurries":
                return 15;  //  blowing snow

            case "chancerain":
            case "nt_chancerain":
                return 40;  //  blowing snow

            case "chancesleet":
            case "nt_chancesleet":
                return 8;   //  freezing drizzle

            case "chancesnow":
            case "nt_chancesnow":
                return 14;  //  light snow showers

            case "rain":
                return 11;  //  rain

            case "snow":
                return 16;  // snow

            case "sleet":
                return 18;  // sleet

            case "fog":
                return 20;  // foggy

            case "sunny":
                return 36;  //  hot

            case "hazy":
                return 21;  //  haze

            case "clear":
                return 34;  // fair

            case "mostlysunny":
                return 32;  //  sunny
                
            case "mostlycloudy":
                return 28;

            case "partlycloudy":
                return 30;

            case "cloudy":
                return 26;  //

        }

        return -1;
    }
    // Humidity is passed as X%,so strip the percent symbol and and returns the number
    private float cleanStrings(String string) {
        float fl= 1.0f;
        try {
             fl = Float.valueOf(string.replaceAll("[^\\d.]", ""));
        }catch (Exception e){
            Log.w(TAG, "Received malformed data  for humidity" + string , e);
        }
        return fl;
    }

    private String getAPIKey() {
        String customKey = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(API_KEY_PREFERENCE, "");
        if (TextUtils.isEmpty(customKey) && customKey.length()!= 16) {
            return mContext.getResources().getString(R.string.wug_api_key, API_KEY);
        } else {
            return customKey;
        }
    }

}
