package org.brasbat.exiforganizer;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

public class ExifData {
    private final LocalDateTime dateTime;
    private final String make;
    private final String model;
    private final String location;
    private final Double latitude;
    private final Double longitude;
    private final Map<String, String> properties;

    public ExifData(LocalDateTime dateTime, String make, String model, String location,
                    Double latitude, Double longitude, Map<String, String> properties) {
        this.dateTime = dateTime;
        this.make = valueOrUnknown(make);
        this.model = valueOrUnknown(model);
        this.location = location == null || location.trim().isEmpty()
                ? "No GPS location" : location.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.properties = Collections.unmodifiableMap(properties);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.trim().isEmpty() ? "Unknown" : value.trim();
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getLocation() {
        return location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getProperty(String name) {
        return properties.get(name);
    }
}
