package org.brasbat.exiforganizer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class SyncSetupStore {
    private static final Path FILE = Path.of(
            System.getProperty("user.home"), ".exif-organizer", "sync-setups.properties");

    private SyncSetupStore() {
    }

    public static List<SyncSetup> load() throws IOException {
        if (!Files.isRegularFile(FILE)) {
            return new ArrayList<>();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        int count = parseCount(properties.getProperty("setup.count"));
        List<SyncSetup> setups = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "setup." + index + ".";
            String name = properties.getProperty(prefix + "name");
            String destination = properties.getProperty(prefix + "destination", "");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            int sourceCount = parseCount(properties.getProperty(prefix + "source.count"));
            List<String> sources = values(properties, prefix + "source.", sourceCount);
            int structureCount = parseCount(properties.getProperty(prefix + "structure.count"));
            List<String> structure = values(properties, prefix + "structure.", structureCount);
            if (!sources.isEmpty() && !structure.isEmpty()) {
                setups.add(new SyncSetup(name, sources, destination,
                        Boolean.parseBoolean(properties.getProperty(prefix + "includeSubfolders", "true")),
                        Boolean.parseBoolean(properties.getProperty(prefix + "move", "false")),
                        structure));
            }
        }
        return setups;
    }

    public static void save(List<SyncSetup> setups) throws IOException {
        Files.createDirectories(FILE.getParent());
        Properties properties = new Properties();
        properties.setProperty("setup.count", Integer.toString(setups.size()));
        for (int index = 0; index < setups.size(); index++) {
            SyncSetup setup = setups.get(index);
            String prefix = "setup." + index + ".";
            properties.setProperty(prefix + "name", setup.getName());
            properties.setProperty(prefix + "destination", setup.getDestination());
            properties.setProperty(prefix + "includeSubfolders",
                    Boolean.toString(setup.isIncludeSubfolders()));
            properties.setProperty(prefix + "move", Boolean.toString(setup.isMove()));
            putValues(properties, prefix + "source.", setup.getSourceFolders());
            putValues(properties, prefix + "structure.", setup.getStructureTokens());
        }

        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "EXIF Organizer sync setups");
        }
        try {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<String> values(Properties properties, String prefix, int count) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String value = properties.getProperty(prefix + index);
            if (value != null && !value.trim().isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static void putValues(Properties properties, String prefix, List<String> values) {
        properties.setProperty(prefix.substring(0, prefix.length() - 1) + ".count",
                Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            properties.setProperty(prefix + index, values.get(index));
        }
    }

    private static int parseCount(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
