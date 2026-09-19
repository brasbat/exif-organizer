package org.brasbat.exiforganizer;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PathTemplate {
    private static final Pattern TOKEN = Pattern.compile("\\{([^}:]+)(?::([^}]+))?}");

    private PathTemplate() {
    }

    public static String resolve(String template, PhotoFile photo) {
        ExifData exif = photo.getMetadata();
        LocalDateTime date = exif.getDateTime();
        Matcher matcher = TOKEN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String format = matcher.group(2);
            String replacement = value(name, format, date, exif, photo.getFileName());
            matcher.appendReplacement(result, Matcher.quoteReplacement(sanitize(replacement)));
        }
        matcher.appendTail(result);
        return result.toString().replace('/', java.io.File.separatorChar)
                .replace('\\', java.io.File.separatorChar);
    }

    private static String value(String name, String format, LocalDateTime date, ExifData exif, String fileName) {
        if ("FileName".equalsIgnoreCase(name)) {
            return fileName;
        }
        if ("Make".equalsIgnoreCase(name) || "CameraMake".equalsIgnoreCase(name)) {
            return exif.getMake();
        }
        if ("Model".equalsIgnoreCase(name) || "CameraModel".equalsIgnoreCase(name)) {
            return exif.getModel();
        }
        if ("Location".equalsIgnoreCase(name) || "Region".equalsIgnoreCase(name)) {
            return exif.getLocation();
        }
        String property = exif.getProperty(name);
        if (property != null && !property.trim().isEmpty()) {
            return property;
        }
        if (date == null) {
            return "Unknown";
        }
        if (format != null) {
            return date.format(DateTimeFormatter.ofPattern(format));
        }
        if ("Year".equalsIgnoreCase(name)) return String.format("%04d", date.getYear());
        if ("Month".equalsIgnoreCase(name)) return String.format("%02d", date.getMonthValue());
        if ("Day".equalsIgnoreCase(name)) return String.format("%02d", date.getDayOfMonth());
        if ("Hour".equalsIgnoreCase(name)) return String.format("%02d", date.getHour());
        if ("Minute".equalsIgnoreCase(name)) return String.format("%02d", date.getMinute());
        if ("Second".equalsIgnoreCase(name)) return String.format("%02d", date.getSecond());
        return "{" + name + "}";
    }

    private static String sanitize(String value) {
        return value.replaceAll("[<>:\"|?*]", "_").trim();
    }
}
