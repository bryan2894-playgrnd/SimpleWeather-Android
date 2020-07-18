package com.thewizrd.simpleweather.controls;

import androidx.core.util.ObjectsCompat;

import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.controls.ImageDataViewModel;
import com.thewizrd.shared_resources.locationdata.LocationData;
import com.thewizrd.shared_resources.utils.NumberUtils;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.WeatherUtils;
import com.thewizrd.shared_resources.weatherdata.LocationType;
import com.thewizrd.shared_resources.weatherdata.Weather;
import com.thewizrd.shared_resources.weatherdata.WeatherAPI;
import com.thewizrd.shared_resources.weatherdata.WeatherIcons;
import com.thewizrd.shared_resources.weatherdata.WeatherManager;

import java.util.Locale;
import java.util.concurrent.Callable;

public class LocationPanelViewModel {
    private WeatherManager wm;
    private Weather weather;

    private String locationName;
    private String currTemp;
    private String currWeather;
    private String weatherIcon;
    private String hiTemp;
    private String loTemp;
    private String pop;
    private String popIcon;
    private int windDir;
    private String windSpeed;
    private ImageDataViewModel imageData;
    private LocationData locationData;
    private int locationType = LocationType.SEARCH.getValue();
    private String weatherSource;

    private boolean editMode = false;

    public String getLocationName() {
        return locationName;
    }

    public String getCurrTemp() {
        return currTemp;
    }

    public String getCurrWeather() {
        return currWeather;
    }

    public String getWeatherIcon() {
        return weatherIcon;
    }

    public String getHiTemp() {
        return hiTemp;
    }

    public String getLoTemp() {
        return loTemp;
    }

    public String getPop() {
        return pop;
    }

    public String getPopIcon() {
        return popIcon;
    }

    public int getWindDir() {
        return windDir;
    }

    public String getWindSpeed() {
        return windSpeed;
    }

    public ImageDataViewModel getImageData() {
        return imageData;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public String getWeatherSource() {
        return weatherSource;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public int getLocationType() {
        if (locationData != null)
            return locationData.getLocationType().getValue();
        return locationType;
    }

    public LocationPanelViewModel() {
        wm = WeatherManager.getInstance();
    }

    public LocationPanelViewModel(Weather weather) {
        wm = WeatherManager.getInstance();
        setWeather(weather);
    }

    public void setWeather(Weather weather) {
        if (weather != null && weather.isValid() && !ObjectsCompat.equals(this.weather, weather)) {
            this.weather = weather;

            imageData = null;

            locationName = weather.getLocation().getName();

            if (weather.getCondition().getTempF() != null && !ObjectsCompat.equals(weather.getCondition().getTempF(), weather.getCondition().getTempC())) {
                int temp = Settings.isFahrenheit() ? Math.round(weather.getCondition().getTempF()) : Math.round(weather.getCondition().getTempC());
                String unitTemp = Settings.isFahrenheit() ? WeatherIcons.FAHRENHEIT : WeatherIcons.CELSIUS;

                currTemp = String.format(Locale.getDefault(), "%d%s", temp, unitTemp);
            } else {
                currTemp = "--";
            }

            currWeather = weather.getCondition().getWeather();

            if (weather.getCondition().getHighF() != null && !ObjectsCompat.equals(weather.getCondition().getHighF(), weather.getCondition().getHighC())) {
                int temp = Settings.isFahrenheit() ? Math.round(weather.getCondition().getHighF()) : Math.round(weather.getCondition().getHighC());
                hiTemp = String.format(Locale.getDefault(), "%dº", temp);
            } else {
                hiTemp = "--";
            }

            if (weather.getCondition().getLowF() != null && !ObjectsCompat.equals(weather.getCondition().getLowF(), weather.getCondition().getLowC())) {
                int temp = Settings.isFahrenheit() ? Math.round(weather.getCondition().getLowF()) : Math.round(weather.getCondition().getLowC());
                loTemp = String.format(Locale.getDefault(), "%dº", temp);
            } else {
                loTemp = "--";
            }

            if (weather.getCondition().getWindMph() != null && weather.getCondition().getWindMph() >= 0 &&
                    weather.getCondition().getWindDegrees() != null && weather.getCondition().getWindDegrees() >= 0) {
                int speedVal = Settings.isFahrenheit() ? Math.round(weather.getCondition().getWindMph()) : Math.round(weather.getCondition().getWindKph());
                String speedUnit = Settings.isFahrenheit() ? "mph" : "kph";

                windSpeed = String.format(Locale.getDefault(), "%d %s", speedVal, speedUnit);
                windDir = weather.getCondition().getWindDegrees() + 180;
            } else {
                windSpeed = "--";
                windDir = 0;
            }

            if (weather.getPrecipitation() != null) {
                pop = weather.getPrecipitation().getPop() != null ? weather.getPrecipitation().getPop() + "%" : null;

                if (WeatherAPI.OPENWEATHERMAP.equals(Settings.getAPI()) || WeatherAPI.METNO.equals(Settings.getAPI())) {
                    popIcon = WeatherIcons.CLOUDY;
                } else {
                    popIcon = WeatherIcons.UMBRELLA;
                }
            }

            weatherIcon = weather.getCondition().getIcon();
            weatherSource = weather.getSource();

            if (locationData == null) {
                locationData = new LocationData();
                locationData.setQuery(weather.getQuery());
                locationData.setName(weather.getLocation().getName());
                locationData.setLatitude(NumberUtils.getValueOrDefault(weather.getLocation().getLatitude(), 0));
                locationData.setLongitude(NumberUtils.getValueOrDefault(weather.getLocation().getLongitude(), 0));
                locationData.setTzLong(weather.getLocation().getTzLong());
            }
        }
    }

    public void updateBackground() {
        new AsyncTask<Void>().await(new Callable<Void>() {
            @Override
            public Void call() {
                if (weather != null) {
                    ImageDataViewModel imageVM = WeatherUtils.getImageData(weather);

                    if (imageVM != null) {
                        imageData = imageVM;
                    } else {
                        imageData = null;
                    }
                }

                return null;
            }
        });
    }
}
