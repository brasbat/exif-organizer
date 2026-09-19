package org.brasbat.exiforganizer;

import java.util.ArrayList;
import java.util.List;

public final class SyncSetup {
    private final String name;
    private final List<String> sourceFolders;
    private final String destination;
    private final boolean includeSubfolders;
    private final boolean move;
    private final List<String> structureTokens;

    public SyncSetup(String name, List<String> sourceFolders, String destination,
                     boolean includeSubfolders, boolean move, List<String> structureTokens) {
        this.name = name;
        this.sourceFolders = List.copyOf(sourceFolders);
        this.destination = destination;
        this.includeSubfolders = includeSubfolders;
        this.move = move;
        this.structureTokens = List.copyOf(structureTokens);
    }

    public String getName() {
        return name;
    }

    public List<String> getSourceFolders() {
        return new ArrayList<>(sourceFolders);
    }

    public String getDestination() {
        return destination;
    }

    public boolean isIncludeSubfolders() {
        return includeSubfolders;
    }

    public boolean isMove() {
        return move;
    }

    public List<String> getStructureTokens() {
        return new ArrayList<>(structureTokens);
    }

    @Override
    public String toString() {
        return name;
    }
}
