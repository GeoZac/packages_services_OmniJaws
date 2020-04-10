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

    private static final int FORECAST_DAYS = 11;    // Forecast is send as parts now , day and night
    private static final String SELECTION_LOCATION = "geocode=%f,%f";
    private static final String SELECTION_ID = "placeid=%s";
    private static final String API_KEY = "YOUR_WUG_API_KEY";
    private static final String API_KEY_PREFERENCE = "custom_api_key";


    private static final String URL_LOCATION =
            "https://api.weather.com/v3/location/search?query=%3$s&language=%2$s&format=json&apiKey=%1$s";
    private static final String URL_WEATHER =
            "https://api.weather.com/v3/wx/observations/current?%3$s&units=m&language=%2$s&format=json&apiKey=%1$s";
    private static final String URL_FORECAST =
            "https://api.weather.com/v3/wx/forecast/daily/5day?%3$s&format=json&units=m&language=%2$s&apiKey=%1$s";

    public WeatherUndergroundProvider(Context context) {
        super(context);
    }

    @Override
    public List<WeatherInfo.WeatherLocation> getLocations(String input) {

        String url = String.format(URL_LOCATION,getAPIKey(),getLanguageCode(), Uri.encode(input));
        String response = retrieve(url);
        if (response == null) {
            return null;
        }

        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject jsonObject = new JSONObject(response).getJSONObject("location");
            ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<>();
                int count = jsonObject.getJSONArray("address").length();
                for (int i = 0; i < count; i++) {
                    WeatherInfo.WeatherLocation location = new WeatherInfo.WeatherLocation();

                    location.id = jsonObject.getJSONArray("placeId").getString(i);
                    location.city = jsonObject.getJSONArray("city").getString(i);
                    location.countryId = jsonObject.getJSONArray("country").getString(i);
                    results.add(location);
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
        String conditionUrl = String.format(Locale.US, URL_WEATHER,getAPIKey(), getLanguageCode(),selection);
        String conditionResponse = retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }
        log(TAG, "Condition URL = " + conditionUrl + " returning a response of " + conditionResponse);

        String forecastUrl = String.format(Locale.US, URL_FORECAST,getAPIKey(),getLanguageCode(), selection);
        String forecastResponse = retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }
        log(TAG, "Forcast URL = " + forecastUrl + " returning a response of " + forecastResponse);

        try {
            JSONObject conditions = new JSONObject(conditionResponse);

            ArrayList<WeatherInfo.DayForecast> forecasts =
                    parseForecasts(new JSONObject(forecastResponse), metric);

            WeatherInfo w = new WeatherInfo(mContext,
                                            "N/A", // Not supported on current version
                                            "N/A", // Not supported on current version
                                            conditions.getString("wxPhraseLong"),
                                            Integer.parseInt(conditions.getString("iconCode")),
                                            Float.parseFloat(conditions.getString("temperature")),
                                            Float.parseFloat(conditions.getString("relativeHumidity")),
                                            Float.parseFloat(conditions.getString("windSpeed")),
                                            Integer.parseInt(conditions.getString("windDirection")),
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

    private ArrayList<DayForecast> parseForecasts(JSONObject forecasts, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<>();
        int count = forecasts.length();
        String units = metric ? "celsius" : "fahrenheit";

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        JSONObject dayparts = forecasts.getJSONArray("daypart").getJSONObject(0);

        // Add tonight's forecast
        result.add(new DayForecast(
                Float.parseFloat(forecasts.getJSONArray("temperatureMin").get(0).toString()),
                Float.parseFloat(forecasts.getJSONArray("temperatureMin").get(0).toString()),
                dayparts.getJSONArray("wxPhraseLong").get(1).toString(),
                Integer.parseInt(dayparts.getJSONArray("iconCode").get(1).toString()),
                "NaN",
                metric));

        // Now deal with days skipping the night data
        for (int i = 2; i < FORECAST_DAYS; i = i + 2) {
            DayForecast item;
            try {
                item = new DayForecast(
                        Float.parseFloat(forecasts.getJSONArray("temperatureMin").getString(i / 2)),
                        Float.parseFloat(forecasts.getJSONArray("temperatureMax").getString(i / 2)),
                        dayparts.getJSONArray("wxPhraseLong").get(i).toString(),
                        Integer.parseInt(dayparts.getJSONArray("iconCode").getString(i)),
                        "NaN",
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
    
    private static final HashMap<String, String> LANGUAGE_CODE_MAPPING = new HashMap<String, String>();
    static {
        LANGUAGE_CODE_MAPPING.put("bg-", "BU");
        LANGUAGE_CODE_MAPPING.put("de-", "DL");
        LANGUAGE_CODE_MAPPING.put("es-", "SP");
        LANGUAGE_CODE_MAPPING.put("fi-", "FI");
        LANGUAGE_CODE_MAPPING.put("fr-", "FR");
        LANGUAGE_CODE_MAPPING.put("it-", "IT");
        LANGUAGE_CODE_MAPPING.put("nl-", "NL");
        LANGUAGE_CODE_MAPPING.put("pl-", "PL");
        LANGUAGE_CODE_MAPPING.put("pt-", "BR");
        LANGUAGE_CODE_MAPPING.put("ro-", "RO");
        LANGUAGE_CODE_MAPPING.put("ru-", "RU");
        LANGUAGE_CODE_MAPPING.put("se-", "SW");
        LANGUAGE_CODE_MAPPING.put("tr-", "TR");
        LANGUAGE_CODE_MAPPING.put("uk-", "UA");
        LANGUAGE_CODE_MAPPING.put("zh-CN", "CN");
        LANGUAGE_CODE_MAPPING.put("zh-TW", "TW");
    }
    
    private String getLanguageCode() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String selector = locale.getLanguage() + "-" + locale.getCountry();

        for (Map.Entry<String, String> entry : LANGUAGE_CODE_MAPPING.entrySet()) {
            if (selector.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "EN";
    }

    private int mapConditionIconToCode(String icon) {

        switch (icon) {

            case "tstorms":
                return 4;     // thunderstorms
            case "nt_tstorms":
                return 45;    // thundershowers (night)

            case "chancetstorms":
                return 37;    // isolated thunderstorms (day)
            case "nt_chancetstorms":
                return 47;    // isolated thundershowers (night)

            case "chanceflurries":
                return 10;     // freezing rain
            case "nt_chanceflurries":
                return 13;    // snow flurries

            case "chancerain":
                return 40;     // scattered showers (day)
            case "nt_chancerain":
                return 9;     // drizzle

            case "chancesleet":
                return 5;      // mixed rain and snow
            case "nt_chancesleet":
                return 8;    //  freezing drizzle

            case "chancesnow":
                return 14;    // light snow showers    
            case "nt_chancesnow":
                return 42;    // scattered snow showers (night)

            case "rain":
            case "nt_rain":
                return 12;    // showers 

            case "snow":
            case "nt_snow":
                return 16;    // snow

            case "sleet":
                return 18;    // sleet

            case "fog":
                return 19;    // dust
            case "nt_fog":
                return 20;    // foggy

            case "sunny":
                return 32;    // sunny (day)
                                // Well that's just great,can't show a bright 
            case "nt_sunny":    // sun icon in the night  
                return 33;    // fair (night) 

            case "hazy":
                return 21;    // haze
            case "nt_hazy":
                return 22;    // smoky

            case "clear":
                return 34;    // fair (day)
            case "nt_clear":
                return 31;    // clear (night)

            case "mostlysunny":
                return 34;    // fair (day)
            case "nt_mostlysunny":
                return 33;    // fair (night)
                
            case "mostlycloudy":
                return 28;    // mostly cloudy (day)
            case "nt_mostlycloudy":
                return 27;    // mostly cloudy (night) 

            case "partlycloudy": //Lets just say 50-50
            case "partlysunny":
                return 30;    // partly cloudy (day)
            case "nt_partlycloudy":
            case "nt_partlysunny":
                return 29;    // partly cloudy (night)

            case "cloudy":
                return 26;    // cloudy
            case "nt_cloudy":
                return 25;    // cold
            
            case "flurries":
            case "nt_flurries":
                return 13;    // snow flurries

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
