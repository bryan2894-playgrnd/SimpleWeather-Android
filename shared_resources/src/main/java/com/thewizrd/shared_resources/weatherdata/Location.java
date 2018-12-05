package com.thewizrd.shared_resources.weatherdata;

import android.util.Log;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.thewizrd.shared_resources.utils.DateTimeUtils;
import com.thewizrd.shared_resources.utils.Logger;

import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;

public class Location {

    @SerializedName("name")
    private String name;
    @SerializedName("latitude")
    private String latitude;
    @SerializedName("longitude")
    private String longitude;
    @SerializedName("tz_offset")
    private ZoneOffset tzOffset;
    @SerializedName("tz_short")
    private String tzShort;
    @SerializedName("tz_long")
    private String tzLong;

    private Location() {
        tzOffset = ZoneOffset.UTC;
    }

    public Location(com.thewizrd.shared_resources.weatherdata.weatherunderground.CurrentObservation condition) {
        name = condition.getDisplayLocation().getFull();
        latitude = condition.getDisplayLocation().getLatitude();
        longitude = condition.getDisplayLocation().getLongitude();
        //if (condition.getLocalTzOffset().startsWith("-"))
        tzOffset = ZoneOffset.of(condition.getLocalTzOffset());
        //else
        //tzOffset = ZoneOffset.of(condition.getLocalTzOffset());
        tzShort = condition.getLocalTzShort();
        tzLong = condition.getLocalTzLong();
    }

    public Location(com.thewizrd.shared_resources.weatherdata.weatheryahoo.Query query) {
        name = query.getResults().getChannel().getLocation().getCity() + "," + query.getResults().getChannel().getLocation().getRegion();
        latitude = query.getResults().getChannel().getItem().getLat();
        longitude = query.getResults().getChannel().getItem().getLong();
        saveTimeZone(query);
    }

    public Location(com.thewizrd.shared_resources.weatherdata.openweather.ForecastRootobject root) {
        // Use location name from location provider
        name = null;
        latitude = Float.toString(root.getCity().getCoord().getLat());
        longitude = Float.toString(root.getCity().getCoord().getLon());
        tzOffset = ZoneOffset.UTC;
        tzShort = "UTC";
    }

    public Location(com.thewizrd.shared_resources.weatherdata.metno.Weatherdata foreRoot) {
        // API doesn't provide location name (at all)
        name = null;
        latitude = foreRoot.getProduct().getTime().get(0).getLocation().getLatitude().toString();
        longitude = foreRoot.getProduct().getTime().get(0).getLocation().getLongitude().toString();
        tzOffset = ZoneOffset.UTC;
        tzShort = "UTC";
    }

    public Location(com.thewizrd.shared_resources.weatherdata.here.LocationItem location) {
        // Use location name from location provider
        name = null;
        latitude = Float.toString(location.getLatitude());
        longitude = Float.toString(location.getLongitude());
        tzOffset = ZoneOffset.UTC;
        tzShort = "UTC";
    }

    private void saveTimeZone(com.thewizrd.shared_resources.weatherdata.weatheryahoo.Query query) {
        /* Get TimeZone info by using UTC and local build time */
        // Now
        LocalDateTime utc = LocalDateTime.ofInstant(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(query.getCreated())), ZoneOffset.UTC);

        // There
        int AMPMidx = query.getResults().getChannel().getLastBuildDate().lastIndexOf(" AM ");
        if (AMPMidx < 0)
            AMPMidx = query.getResults().getChannel().getLastBuildDate().lastIndexOf(" PM ");

        LocalDateTime there = LocalDateTime.parse(query.getResults().getChannel().getLastBuildDate().substring(0, AMPMidx + 4),
                DateTimeFormatter.ofPattern("eee, dd MMM yyyy hh:mm a ", Locale.ROOT));
        Duration offset = Duration.between(there, utc);

        tzOffset = ZoneOffset.of(String.format(Locale.ROOT, "%s", DateTimeUtils.durationToHMFormat(offset)));
        tzShort = query.getResults().getChannel().getLastBuildDate().substring(AMPMidx + 4);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public ZoneOffset getTzOffset() {
        return tzOffset;
    }

    public void setTzOffset(ZoneOffset tzOffset) {
        this.tzOffset = tzOffset;
    }

    public String getTzShort() {
        return tzShort;
    }

    public void setTzShort(String tzShort) {
        this.tzShort = tzShort;
    }

    public String getTzLong() {
        return tzLong;
    }

    public void setTzLong(String tzLong) {
        this.tzLong = tzLong;
    }

    public static Location fromJson(JsonReader extReader) {
        Location obj = null;

        try {
            obj = new Location();
            JsonReader reader;
            String jsonValue;

            if (extReader.peek() == JsonToken.STRING) {
                jsonValue = extReader.nextString();
            } else {
                jsonValue = null;
            }

            if (jsonValue == null)
                reader = extReader;
            else {
                reader = new JsonReader(new StringReader(jsonValue));
                reader.beginObject(); // StartObject
            }

            while (reader.hasNext() && reader.peek() != JsonToken.END_OBJECT) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT)
                    reader.beginObject(); // StartObject

                String property = reader.nextName();

                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }

                switch (property) {
                    case "name":
                        obj.name = reader.nextString();
                        break;
                    case "latitude":
                        obj.latitude = reader.nextString();
                        break;
                    case "longitude":
                        obj.longitude = reader.nextString();
                        break;
                    case "tz_offset":
                        obj.tzOffset = ZoneOffset.of(reader.nextString());
                        break;
                    case "tz_short":
                        obj.tzShort = reader.nextString();
                        break;
                    case "tz_long":
                        obj.tzLong = reader.nextString();
                        break;
                    default:
                        break;
                }
            }

            if (reader.peek() == JsonToken.END_OBJECT)
                reader.endObject();

        } catch (Exception ex) {
            obj = null;
        }

        return obj;
    }

    public String toJson() {
        StringWriter sw = new StringWriter();
        JsonWriter writer = new JsonWriter(sw);
        writer.setSerializeNulls(true);

        try {
            // {
            writer.beginObject();

            // "name" : ""
            writer.name("name");
            writer.value(name);

            // "latitude" : ""
            writer.name("latitude");
            writer.value(latitude);

            // "longitude" : ""
            writer.name("longitude");
            writer.value(longitude);

            // "tz_offset" : ""
            writer.name("tz_offset");
            writer.value(DateTimeUtils.offsetToHMSFormat(tzOffset));

            // "tz_short" : ""
            writer.name("tz_short");
            writer.value(tzShort);

            // "tz_long" : ""
            writer.name("tz_long");
            writer.value(tzLong);

            // }
            writer.endObject();
        } catch (IOException e) {
            Logger.writeLine(Log.ERROR, e, "Location: error writing json string");
        }

        return sw.toString();
    }
}