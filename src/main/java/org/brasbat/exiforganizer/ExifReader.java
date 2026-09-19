package org.brasbat.exiforganizer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.lang.GeoLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExifReader {
    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private ExifReader() {
    }

    public static ExifData read(Path file) throws IOException {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException ex) {
            throw new IOException("Could not parse image metadata", ex);
        }
        ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        Date date = firstDate(subIfd, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
        if (date == null) {
            date = firstDate(ifd0, ExifIFD0Directory.TAG_DATETIME);
        }
        LocalDateTime dateTime = date == null ? null : LocalDateTime.ofInstant(
                date.toInstant(), java.time.ZoneId.systemDefault());
        String make = text(ifd0, ExifIFD0Directory.TAG_MAKE);
        String model = text(ifd0, ExifIFD0Directory.TAG_MODEL);
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        GeoLocation geoLocation = gps == null ? null : gps.getGeoLocation();
        Double latitude = geoLocation == null ? null : geoLocation.getLatitude();
        Double longitude = geoLocation == null ? null : geoLocation.getLongitude();
        if (!validCoordinate(latitude, longitude)) {
            latitude = null;
            longitude = null;
        }
        String embeddedLocation = gpsLocation(gps);
        String location = latitude == null || longitude == null
                ? (embeddedLocation == null ? "No GPS location" : embeddedLocation)
                : LocationDatabase.findNearest(latitude, longitude);
        if (isUnavailable(location) && embeddedLocation != null) {
            location = embeddedLocation;
        }
        Map<String, String> properties = new LinkedHashMap<>();
        addProperties(metadata, properties);
        properties.put("Year", dateTime == null ? null : String.format("%04d", dateTime.getYear()));
        properties.put("Month", dateTime == null ? null : String.format("%02d", dateTime.getMonthValue()));
        properties.put("Day", dateTime == null ? null : String.format("%02d", dateTime.getDayOfMonth()));
        properties.put("Hour", dateTime == null ? null : String.format("%02d", dateTime.getHour()));
        properties.put("Minute", dateTime == null ? null : String.format("%02d", dateTime.getMinute()));
        properties.put("Second", dateTime == null ? null : String.format("%02d", dateTime.getSecond()));
        properties.put("Make", make);
        properties.put("Model", model);
        properties.put("Location", location);
        return new ExifData(dateTime, make, model, location, latitude, longitude, properties);
    }

    private static Date firstDate(Directory directory, int... tags) {
        if (directory == null) {
            return null;
        }
        for (int tag : tags) {
            if (directory.containsTag(tag)) {
                try {
                    return directory.getDate(tag);
                } catch (RuntimeException ignored) {
                    String value = directory.getString(tag);
                    try {
                        return Date.from(LocalDateTime.parse(value, EXIF_DATE)
                                .atZone(java.time.ZoneId.systemDefault()).toInstant());
                    } catch (DateTimeParseException ignoredAgain) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static String text(Directory directory, int tag) {
        return directory == null ? null : directory.getString(tag);
    }

    private static void addProperties(Metadata metadata, Map<String, String> properties) {
        for (Directory directory : metadata.getDirectories()) {
            for (com.drew.metadata.Tag tag : directory.getTags()) {
                String value = directory.getString(tag.getTagType());
                if (value != null && !value.trim().isEmpty()) {
                    properties.putIfAbsent(tag.getTagName(), value.trim());
                    properties.putIfAbsent(directory.getName() + "." + tag.getTagName(), value.trim());
                }
            }
        }
    }

    private static String gpsLocation(GpsDirectory gps) {
        if (gps == null || !gps.containsTag(GpsDirectory.TAG_AREA_INFORMATION)) {
            return null;
        }
        String area = gps.getDescription(GpsDirectory.TAG_AREA_INFORMATION);
        if (area == null || area.trim().isEmpty()) {
            area = gps.getString(GpsDirectory.TAG_AREA_INFORMATION);
        }
        return area == null || area.trim().isEmpty() ? null : area.trim();
    }

    private static boolean isUnavailable(String location) {
        return location == null
                || location.equals("Location unavailable")
                || location.equals("Region unavailable")
                || location.equals("Invalid GPS location");
    }

    private static boolean validCoordinate(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180
                && (latitude != 0 || longitude != 0);
    }
}
