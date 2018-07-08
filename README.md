About

This is just a fork of AICP's implementation of OmniROMs's OmniJaws which 
is a minimized service to fetch weather data from OpenWeatherMap or Yahoo.
This work just extends it to WeatherUnderground service too.

Thanks to http://openweathermap.org/ for providing an unrestricted API key

The WeatherUnderground API is fairly accurate (for me,atleast) but the free key has rate limits.

OpenWeatherMap API
http://openweathermap.org/current



IMPORTANT: please request your own API key from OpenWeatherMap at
https://openweathermap.org/appid#get
and enter it via [AicpExtras->Weather->Service settings->API key]

Yahoo weather API
https://developer.yahoo.com/weather/

WeatherUnderground API
http://api.wunderground.com/weather/api

Very Important
-----
This repo doesnt have a WeatherUnderground API key included,so the make sure to get a API key and replace the dummy here
https://github.com/GeoZac/packages_services_OmniJaws/blob/o8.1/res/values/config.xml

The WeatherUnderground provider is entirely based on the already existing weather providers in orignial project

Current Status
-----
The mapping of condition to the conditionCode is fairly complete

No multi-language support

No support for imprial units

Thanks to the original creators and contributors of the LockClock app
https://github.com/CyanogenMod/android_packages_apps_LockClock

It provided a lot of insights and knowledge how to do this
Some parts of the code like the provider access have been taken
from it with minor modifications. Please check the file copyright
headers for the origins of the files.

If you already include LockClock with your ROM you should
consider if you really need this. The provider API is similiar
and you dont need two different providers.

The default condition icon pack has also been extracted from
LockClock

Client access
-----
Here is a client code example using the content provider
to access weather data
https://github.com/omnirom/android_packages_services_OmniJaws/blob/android-8.1/src/org/omnirom/omnijaws/client/OmniJawsClient.java

Broadcasts and content observers
-----
There are two ways to register for changes on weather data

Using ContentObserver on URI
```java
content://org.omnirom.omnijaws.provider/weather
```

Using broadcasts
```java
private static final String ACTION_BROADCAST = "org.omnirom.omnijaws.WEATHER_UPDATE";
private static final String ACTION_ERROR = "org.omnirom.omnijaws.WEATHER_ERROR";
```

Units
-----
Depending on the value of the metric setting the following units are used to display the weather

```code
Temperature:
metric = "C"
imperial = "F"

Wind speed:
metric = "km/h"
imperial = "mph"
```

Condition icon packs support
-----
add activity with action "org.omnirom.WeatherIconPack"
the name is used to defined the prefix for the image names
default should be .weather.

```xml
		<activity
			android:name=".weather_vclouds"
			android:label="LockClock (vclouds)" >
			<intent-filter>
				<action android:name="org.omnirom.WeatherIconPack" />

				<category android:name="android.intent.category.DEFAULT" />
			</intent-filter>
		</activity>
```

An example can be found here https://github.com/maxwen/WeatherIconSample


Icons:
Outline weather icon set is used with permission from  http://emske.com/25-outline-weather-icons/

