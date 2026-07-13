package com.pg85.otg.dimensions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pg85.otg.util.OTGLog;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class OTGWorldStorage {
    private static final String STORAGE_FILE = "otg_world_data.json";
    private static final String LEGACY_FILE = "otg_dimensions.json";
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Data
    @NoArgsConstructor
    public static class StorageData {
        @JsonProperty("version")
        private int version = 2;

        @JsonProperty("dimensions")
        private Map<String, DimensionInfo> dimensions = new LinkedHashMap<>();

        @JsonProperty("gameRules")
        private Map<String, Map<String, Object>> gameRules = new LinkedHashMap<>();
    }

    private final Path worldPath;
    private StorageData data;

    public OTGWorldStorage(Path worldPath) {
        this.worldPath = worldPath;
        this.data = new StorageData();
    }

    public void load() {
        Path storagePath = worldPath.resolve(STORAGE_FILE);
        Path legacyPath = worldPath.resolve(LEGACY_FILE);

        if (Files.exists(storagePath)) {
            loadV2(storagePath);
        } else if (Files.exists(legacyPath)) {
            migrateFromV1(legacyPath);
        } else {
            data = new StorageData();
        }
    }

    private void loadV2(Path storagePath) {
        try {
            String json = Files.readString(storagePath);
            data = mapper.readValue(json, StorageData.class);
            OTGLog.info("Loaded %s OTG dimensions from storage", data.getDimensions().size());
        } catch (IOException e) {
            OTGLog.error("Failed to load world storage, starting fresh: %s", e.getMessage());
            try {
                Files.move(storagePath, storagePath.resolveSibling(STORAGE_FILE + ".backup"));
            } catch (IOException ignored) {
                OTGLog.error("Failed to backup corrupt storage file: %s", ignored.getMessage());
            }
            data = new StorageData();
        }
    }

    private void migrateFromV1(Path legacyPath) {
        OTGLog.info("Migrating from v1 (otg_dimensions.json) to v2 (otg_world_data.json)");
        try {
            String json = Files.readString(legacyPath);
            var legacyData = mapper.readTree(json);
            data = new StorageData();

            var dimsNode = legacyData.get("dimensions");
            if (dimsNode != null && dimsNode.isArray()) {
                for (var dimNode : dimsNode) {
                    try {
                        DimensionInfo info = mapper.treeToValue(dimNode, DimensionInfo.class);
                        data.getDimensions().put(info.getName(), info);
                    } catch (Exception e) {
                        OTGLog.error("Skipping corrupt dimension entry during v1 migration: %s", e.getMessage());
                    }
                }
            }

            save();
            // Keep legacy file as backup
            Files.move(legacyPath, legacyPath.resolveSibling(LEGACY_FILE + ".v1backup"));
            OTGLog.info("Migration complete: %s dimensions migrated", data.getDimensions().size());
        } catch (IOException e) {
            OTGLog.error("Failed to migrate v1 storage: %s", e.getMessage());
            data = new StorageData();
        }
    }

    public void save() {
        Path storagePath = worldPath.resolve(STORAGE_FILE);
        try {
            String json = mapper.writeValueAsString(data);
            Files.writeString(storagePath, json);
        } catch (IOException e) {
            OTGLog.error("Failed to save world storage: %s", e.getMessage());
        }
    }

    // --- Dimension operations ---

    public void addDimension(DimensionInfo info) {
        data.getDimensions().put(info.getName(), info);
        save();
    }

    public boolean removeDimension(String name) {
        boolean removed = data.getDimensions().remove(name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<DimensionInfo> getDimension(String name) {
        return Optional.ofNullable(data.getDimensions().get(name));
    }

    public List<DimensionInfo> getAllDimensions() {
        return new ArrayList<>(data.getDimensions().values());
    }

    public boolean exists(String name) {
        return data.getDimensions().containsKey(name);
    }

    // --- GameRules operations ---

    public void putGameRules(String dimensionKey, Map<String, Object> rules) {
        data.getGameRules().put(dimensionKey, new LinkedHashMap<>(rules));
        save();
    }

    public Optional<Map<String, Object>> getGameRules(String dimensionKey) {
        return Optional.ofNullable(data.getGameRules().get(dimensionKey));
    }

    public Map<String, Map<String, Object>> getAllGameRules() {
        return Collections.unmodifiableMap(data.getGameRules());
    }

    public void removeGameRules(String dimensionKey) {
        if (data.getGameRules().remove(dimensionKey) != null) {
            save();
        }
    }
}
