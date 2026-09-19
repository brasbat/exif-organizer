package org.brasbat.exiforganizer;

import java.nio.file.Path;

public class PhotoFile {
    private final Path source;
    private final ExifData metadata;
    private String target;
    private String status;

    public PhotoFile(Path source, ExifData metadata) {
        this.source = source;
        this.metadata = metadata;
        this.status = "Ready";
    }

    public Path getSource() {
        return source;
    }

    public ExifData getMetadata() {
        return metadata;
    }

    public String getFileName() {
        return source.getFileName().toString();
    }

    public String getTarget() {
        return target == null ? "" : target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
