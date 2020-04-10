package org.omnirom.omnijaws;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.omnirom.omnijaws.WeatherInfo.DayForecast;

public class AerisWeatherProvider extends AbstractWeatherProvider {
    private static final String TAG = "AerisWeatherProvider";

    private static final int FORECAST_DAYS = 5;
    private static final String SELECTION_LOCATION = "%s,%s";
    private static final String API_KEY = "YOUR_AERIES_API_KEY";
    private static final String API_KEY_PREFERENCE = "custom_api_key";


    private static final String URL_LOCATION =
            "https://api.aerisapi.com/places/search?query=name:%s&limit=10&%s";
    private static final String URL_WEATHER =
            "https://api.aerisapi.com/observations/%s?&format=json&filter=allstations&limit=1&%s";
    private static final String URL_FORECAST =
            "https://api.aerisapi.com/forecasts/%s?&format=json&filter=day&limit=5&%s";

    public AerisWeatherProvider(Context context) {
        super(context);
    }

    @Override
    public List<WeatherInfo.WeatherLocation> getLocations(String input) {

        String url = String.format(URL_LOCATION, Uri.encode(input), getAPIKey());
        String response = retrieve(url);
        if (response == null) {
            return null;
        }

        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject jsonObject = new JSONObject(response);
            ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<>();
            JSONArray jsonArray = jsonObject.getJSONArray("response");
            int count = jsonArray.length();
            for (int i = 0; i < count; i++) {
                JSONObject result = jsonArray.getJSONObject(i);
                WeatherInfo.WeatherLocation location = new WeatherInfo.WeatherLocation();

                // No specific ID seems to exist, will concat location coordinates to get one
                location.id = String.format(Locale.US, SELECTION_LOCATION, result.getJSONObject("loc").getString("lat"), result.getJSONObject("loc").getString("long"));
                location.city = result.getJSONObject("place").getString("name");
                location.countryId = result.getJSONObject("place").getString("countryFull");
                results.add(location);
            }
            return results;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed location data (input=" + input + ")", e);
        }

        return null;
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        return handleWeatherRequest(id, metric);
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_LOCATION,
                location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(selection, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection, boolean metric) {
        String conditionUrl = String.format(Locale.US, URL_WEATHER, selection, getAPIKey());
        String conditionResponse = retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }
        log(TAG, "Condition URL = " + conditionUrl + " returning a response of " + conditionResponse);

        String forecastUrl = String.format(Locale.US, URL_FORECAST, selection, getAPIKey());
        String forecastResponse = retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }
        log(TAG, "Forcast URL = " + forecastUrl + " returning a response of " + forecastResponse);


        try {
            JSONObject conditions = new JSONObject(conditionResponse).optJSONObject("response");

            if (conditions == null)
                return null;

            JSONObject observation = conditions.getJSONObject("ob");

            ArrayList<DayForecast> forecasts = parseForecasts(new JSONObject(forecastResponse).getJSONArray("response").getJSONObject(0).getJSONArray("periods"), metric);

            WeatherInfo w = new WeatherInfo(mContext,
                    conditions.getJSONObject("place").getString("name"),
                    conditions.getJSONObject("place").getString("name"),
                    observation.getString("weatherShort"),
                    mapConditionIconToCode(observation.getString("icon")),
                    Float.parseFloat(observation.getString(metric ? "tempC" : "tempF")),
                    Float.parseFloat(observation.getString("humidity")),
                    Float.parseFloat(observation.getString(metric ? "windMPH" : "windKPH")),
                    Integer.parseInt(observation.getString("windDirDEG")),
                    metric,
                    forecasts,
                    System.currentTimeMillis());

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + conditionUrl
                    + ")", e);
        } catch (NumberFormatException e) {
            // Weather provider sends a response with random values equal to null,
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
                        Float.parseFloat(forecast.getString(metric ? "minTempC" : "minTempF")),
                        Float.parseFloat(forecast.getString(metric ? "maxTempC" : "maxTempF")),
                        forecast.getString("weatherPrimary"),
                        mapConditionIconToCode(forecast.getString("icon")),
                        forecast.getString("timestamp"),
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

    private int mapConditionIconToCode(String icon) {

        switch (icon) {
            case "blizzard.png":
                return 0;

            case "blizzardn.png":
                return 1;

            case "rainandsnow.png":
            case "rainandsnown.png":


            case "snowshowers.png":
            case "snowshowersn.png":
                return 5;

            case "sleetsnow.png":
                return 6;

            case "sleetsnown.png":
                return 7;

            case "smoke.png":
            case "smoken.png":
                return 22;

            case "blowingsnow.png":
            case "blowingsnown.png":
                return 15;

            case "clearw.png":
            case "clearwn.png":
                return 24;

            case "cloudy.png":
            case "cloudyn.png":
            case "cloudywn.png":
                return 26;

            case "cloudyw.png":
                return 40;

            case "cold.png":
            case "coldn.png":
                return 25;

            case "drizzle.png":
                return 9;

            case "drizzlef.png":
                return 8;

            case "raintosnow.png":
            case "raintosnown.png":
                return 10;

            case "rainw.png":
            case "showers.png":
            case "showersn.png":
                return 11;

            case "rain.png":
            case "rainn.png":
                return 12;

            case "flurriesw.png":
            case "flurrieswn.png":
                return 13;

            case "flurries.png":
            case "flurriesn.png":
                return 14;

            case "showersw.png":
            case "showerswn.png":
                return 17;

            case "sleet.png":
            case "sleetn.png":
                return 18;

            case "dust.png":
            case "dustn.png":
                return 19;

            case "fog.png":
            case "fogn.png":
                return 20;

            case "fdrizzle.png":
            case "fdrizzlen.png":
                return 23;

            case "wind.png":
                return 24;

            case "clearn.png":
            case "fairn.png":
                return 33;

            case "clear.png":
            case "fair.png":
                return 34;

            case "freezingrain.png":
            case "freezingrainn.png":
                return 35;

            case "drizzlen.png":
                return 46;

            case "hazy.png":
            case "hazyn.png":
                return 21;

            case "hot.png":
                return 36;

            case "mcloudy.png":
            case "mcloudys.png":
            case "mcloudysf.png":
            case "mcloudysfw.png":
            case "mcloudysw.png":
                return 28;

            case "mcloudyn.png":
            case "pcloudyswn.png":
            case "pcloudytwn.png":
            case "pcloudywn.png":
            case "pcloudytn.png":
                return 29;

            case "pcloudy.png":
            case "pcloudyrw.png":
            case "pcloudys.png":
            case "pcloudysf.png":
            case "pcloudysw.png":
            case "pcloudysfw.png":
            case "pcloudyr.png":
                return 30;

            case "mcloudyr.png":
            case "mcloudyrw.png":
                return 40;

            case "wintrymix.png":
            case "wintrymixn.png":
                return 41;

            case "mcloudyrn.png":
            case "mcloudyrwn.png":
            case "mcloudysfn.png":
            case "mcloudysfwn.png":
            case "mcloudysn.png":
            case "mcloudyswn.png":
                return 27;

            case "mcloudyt.png":
            case "mcloudytw.png":
            case "mcloudyw.png":
                return 37;

            case "pcloudyt.png":
            case "pcloudytw.png":
            case "pcloudyw.png":
                return 38;

            case "snowshowersw.png":
            case "snowshowerswn.png":
                return 42;

            case "snowtorain.png":
            case "snowtorainn.png":
                return 43;

            case "pcloudyn.png":
            case "pcloudyrn.png":
            case "pcloudyrwn.png":
            case "pcloudysfn.png":
            case "pcloudysfwn.png":
            case "pcloudysn.png":
                return 44;

            case "snown.png":
                return 46;

            case "mcloudytn.png":
            case "mcloudytwn.png":
            case "mcloudywn.png":
                return 47;

            case "snow.png":
                return 16;

            case "snoww.png":
            case "snowwn.png":
                return 15;

            case "sunnyn.png":
                return 31;


            case "sunnywn.png":
                return 33;

            case "sunnyw.png":
            case "sunny.png":
                return 34;

            case "tstorm.png":
                return 39;

            case "tstorms.png":
                return 3;

            case "tstormn.png":
            case "tstormsn.png":
            case "tstormswn.png":
                return 45;

            case "tstormsw.png":
                return 4;
            default:
                log(TAG, "got " + icon);
                return 0;

        }
    }

    @Override
    public boolean shouldRetry() {
        return false;
    }

    private String getAPIKey() {
        String customKey = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(API_KEY_PREFERENCE, "");
        if (TextUtils.isEmpty(customKey) && customKey.length()!= 86) {
            return mContext.getResources().getString(R.string.wug_api_key, API_KEY);
        } else {
            return customKey;
        }
    }

}
