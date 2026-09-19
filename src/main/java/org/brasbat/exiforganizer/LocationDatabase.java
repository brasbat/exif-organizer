package org.brasbat.exiforganizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LocationDatabase {
    private static final String DATA_URL =
            "https://download.geonames.org/export/dump/cities500.zip";
    private static final String REGIONS_URL =
            "https://download.geonames.org/export/dump/admin1CodesASCII.txt";
    private static final Path DATABASE =
            Path.of(System.getProperty("user.home"), ".exif-organizer", "locations.db");
    private static final Duration MAX_AGE = Duration.ofDays(7);
    private static final int SCHEMA_VERSION = 2;
    private static volatile boolean initialized;

    private LocationDatabase() {
    }

    public static synchronized void initialize() throws IOException {
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(DATABASE.getParent());
            if (!isCurrent()) {
                update();
            } else {
                ensureSchema();
            }
            initialized = true;
        } catch (SQLException ex) {
            throw new IOException("Could not initialize location database", ex);
        }
    }

    public static String findNearest(double latitude, double longitude) {
        try {
            initialize();
            return queryNearest(latitude, longitude);
        } catch (IOException | SQLException ex) {
            return "Location unavailable";
        }
    }

    private static boolean isCurrent() throws IOException, SQLException {
        if (!Files.isRegularFile(DATABASE)) {
            return false;
        }
        Instant modified = Files.getLastModifiedTime(DATABASE).toInstant();
        if (!modified.isAfter(Instant.now().minus(MAX_AGE))) {
            return false;
        }
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT value FROM metadata WHERE key = 'schema_version'")) {
            return result.next() && Integer.toString(SCHEMA_VERSION).equals(result.getString(1));
        } catch (SQLException ex) {
            return false;
        }
    }

    private static void ensureSchema() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cities (" +
                    "id INTEGER PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, " +
                    "longitude REAL NOT NULL, country TEXT, region TEXT)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cities_lat_lon " +
                    "ON cities(latitude, longitude)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS regions (" +
                    "code TEXT PRIMARY KEY, name TEXT NOT NULL, ascii_name TEXT)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_regions_code ON regions(code)");
        }
    }

    private static void update() throws IOException, SQLException {
        Path archive = Files.createTempFile("exif-organizer-cities", ".zip");
        Path extracted = Files.createTempFile("exif-organizer-cities", ".txt");
        Path regions = Files.createTempFile("exif-organizer-regions", ".txt");
        Path replacement = DATABASE.resolveSibling("locations-new.db");
        try {
            download(archive);
            extractData(archive, extracted);
            downloadFile(REGIONS_URL, regions);
            importData(extracted, regions, replacement);
            Files.move(replacement, DATABASE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(extracted);
            Files.deleteIfExists(regions);
            Files.deleteIfExists(replacement);
        }
    }

    private static void downloadFile(String url, Path target) throws IOException {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "EXIF-Organizer/1.0")
                    .GET()
                    .build();
            try {
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("GeoNames download failed with HTTP " + response.statusCode());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("GeoNames download interrupted", ex);
        }
    }

    private static void download(Path target) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DATA_URL))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "EXIF-Organizer/1.0")
                .GET()
                .build();
        try {
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GeoNames download failed with HTTP " + response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("GeoNames download interrupted", ex);
        }
    }

    private static void extractData(Path archive, Path target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".txt")) {
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("GeoNames archive did not contain city data");
    }

    private static void importData(Path source, Path regions, Path target) throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement schema = connection.createStatement()) {
            schema.executeUpdate("PRAGMA journal_mode=DELETE");
            schema.executeUpdate("CREATE TABLE cities (" +
                    "id INTEGER PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, " +
                    "longitude REAL NOT NULL, country TEXT, region TEXT)");
            schema.executeUpdate("CREATE INDEX idx_cities_lat_lon ON cities(latitude, longitude)");
            schema.executeUpdate("CREATE TABLE regions (" +
                    "code TEXT PRIMARY KEY, name TEXT NOT NULL, ascii_name TEXT)");
            schema.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO cities(id,name,latitude,longitude,country,region) VALUES(?,?,?,?,?,?)");
                 BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length < 12) {
                        continue;
                    }
                    try {
                        insert.setLong(1, Long.parseLong(fields[0]));
                        insert.setString(2, fields[1]);
                        insert.setDouble(3, Double.parseDouble(fields[4]));
                        insert.setDouble(4, Double.parseDouble(fields[5]));
                        insert.setString(5, fields[8]);
                        insert.setString(6, fields[10]);
                        insert.addBatch();
                    } catch (NumberFormatException ignored) {
                        // Skip malformed source rows.
                    }
                }
                insert.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO regions(code,name,ascii_name) VALUES(?,?,?)");
                 BufferedReader reader = Files.newBufferedReader(regions, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length >= 3) {
                        insert.setString(1, fields[0]);
                        insert.setString(2, fields[1]);
                        insert.setString(3, fields[2]);
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
            connection.commit();
            try (PreparedStatement metadata = connection.prepareStatement(
                    "INSERT INTO metadata(key,value) VALUES('schema_version',?)")) {
                metadata.setString(1, Integer.toString(SCHEMA_VERSION));
                metadata.executeUpdate();
            }
            connection.commit();
        }
    }

    private static String queryNearest(double latitude, double longitude)
            throws SQLException {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return "Invalid GPS location";
        }
        try (Connection connection = connection()) {
            double radius = 2.0;
            for (int attempt = 0; attempt < 7; attempt++) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT c.name, c.latitude, c.longitude, c.country, " +
                                "r.name, r.ascii_name FROM cities c " +
                                "LEFT JOIN regions r ON r.code = c.country || '.' || c.region " +
                                "WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?")) {
                    statement.setDouble(1, latitude - radius);
                    statement.setDouble(2, latitude + radius);
                    statement.setDouble(3, longitude - radius);
                    statement.setDouble(4, longitude + radius);
                    String nearestCity = null;
                    String nearestRegion = null;
                    String nearestCountry = null;
                    double distance = Double.MAX_VALUE;
                    try (ResultSet results = statement.executeQuery()) {
                        while (results.next()) {
                            double candidateDistance = distance(latitude, longitude,
                                    results.getDouble(2), results.getDouble(3));
                            if (candidateDistance < distance) {
                                distance = candidateDistance;
                                nearestCity = results.getString(1);
                                nearestCountry = results.getString(4);
                                nearestRegion = results.getString(5);
                            }
                        }
                    }
                    if (nearestCity != null) {
                        if (nearestRegion != null && !nearestRegion.trim().isEmpty()) {
                            return nearestCity + "-" + nearestRegion;
                        }
                        return nearestCountry == null || nearestCountry.trim().isEmpty()
                                ? nearestCity : nearestCity + "-" + nearestCountry;
                    }
                }
                radius *= 2;
            }
        }
        return "Region unavailable";
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.toRadians(lat2 - lat1);
        double lon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
    }
}
