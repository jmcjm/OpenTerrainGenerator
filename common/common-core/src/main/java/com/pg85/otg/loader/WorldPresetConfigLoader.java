package com.pg85.otg.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorldPresetConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Loads all WorldPreset YAML files from the WorldPresets/ folder.
     */
    public static List<WorldPresetConfig> loadAll(Path otgRootFolder) {
        List<WorldPresetConfig> configs = new ArrayList<>();
        File worldPresetsDir = otgRootFolder.resolve(Constants.WORLD_PRESETS_FOLDER).toFile();

        if (!worldPresetsDir.exists() || !worldPresetsDir.isDirectory()) {
            return configs;
        }

        File[] yamlFiles = worldPresetsDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (yamlFiles == null) return configs;

        for (File yamlFile : yamlFiles) {
            WorldPresetConfig config = fromFile(yamlFile);
            if (config != null) {
                configs.add(config);
            }
        }

        return configs;
    }

    /**
     * Loads a single WorldPreset YAML file.
     */
    public static @Nullable WorldPresetConfig fromFile(File yamlFile) {
        try {
            String content = Files.readString(yamlFile.toPath());
            return fromYamlString(content);
        } catch (IOException e) {
            OTGLog.error(LogCategory.CONFIGS, "Failed to read WorldPreset file {}: {}",
                yamlFile.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Parses a WorldPreset YAML string. Invalid dimension entries are pruned with a log.
     */
    public static @Nullable WorldPresetConfig fromYamlString(String input) {
        try {
            WorldPresetConfig config = YAML_MAPPER.readValue(input, WorldPresetConfig.class);
            if (config != null) {
                validateAndPrune(config);
            }
            return config;
        } catch (IOException e) {
            OTGLog.error(LogCategory.CONFIGS, "Failed to parse WorldPreset YAML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Enforces source rules on dimension entries:
     * - exactly one of PresetFolderName / NonOTGWorldType per entry
     * - non-OTG custom dimension entries need a DimensionName
     * - DimensionName is meaningless on Overworld/Nether/End slots
     * Invalid Dimensions entries are removed; invalid slot blocks fall back to vanilla.
     */
    private static void validateAndPrune(WorldPresetConfig config) {
        String name = config.DisplayName != null ? config.DisplayName : "<unnamed>";

        validateSlot(config.Overworld, "Overworld", name);
        validateSlot(config.Nether, "Nether", name);
        validateSlot(config.End, "End", name);

        if (config.Dimensions == null) return;
        config.Dimensions.removeIf(dim -> {
            if (dim.hasPreset() && dim.isNonOTG()) {
                OTGLog.error(LogCategory.CONFIGS,
                    "WorldPreset '{}': Dimensions entry has both PresetFolderName and NonOTGWorldType, skipping entry", name);
                return true;
            }
            if (dim.isNonOTG() && (dim.DimensionName == null || dim.DimensionName.isBlank())) {
                OTGLog.error(LogCategory.CONFIGS,
                    "WorldPreset '{}': non-OTG Dimensions entry needs a DimensionName, skipping entry", name);
                return true;
            }
            if (!dim.hasPreset() && !dim.isNonOTG()) {
                OTGLog.warn(LogCategory.CONFIGS,
                    "WorldPreset '{}': Dimensions entry has neither PresetFolderName nor NonOTGWorldType, skipping entry", name);
                return true;
            }
            if (dim.isNonOTG() && dim.Seed != 0) {
                OTGLog.warn(LogCategory.CONFIGS,
                    "WorldPreset '{}': Seed on non-OTG dimension '{}' is ignored (vanilla generators use the world seed)",
                    name, dim.DimensionName);
            }
            return false;
        });
    }

    private static void validateSlot(WorldPresetConfig.OTGDimension slot, String slotName, String configName) {
        if (slot == null) return;
        if (slot.hasPreset() && slot.isNonOTG()) {
            OTGLog.error(LogCategory.CONFIGS,
                "WorldPreset '{}': {} block has both PresetFolderName and NonOTGWorldType, falling back to vanilla", configName, slotName);
            slot.PresetFolderName = null;
            slot.NonOTGWorldType = null;
        }
        if (slot.DimensionName != null) {
            OTGLog.warn(LogCategory.CONFIGS,
                "WorldPreset '{}': DimensionName on the {} block is ignored (slot keys are fixed)", configName, slotName);
            slot.DimensionName = null;
        }
    }
}
