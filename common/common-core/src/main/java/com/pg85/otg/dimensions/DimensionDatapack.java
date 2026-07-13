package com.pg85.otg.dimensions;

import com.pg85.otg.config.settings.preset.DimensionSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.OTGLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DimensionDatapack {
    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 15,
                "description": "OTG Dynamic Dimensions"
              }
            }
            """;

    private final Path datapackPath;

    public DimensionDatapack(Path worldDatapacksPath) {
        this.datapackPath = worldDatapacksPath.resolve(Constants.MOD_ID_SHORT);
    }

    public void ensurePackMcmeta() throws IOException {
        Path packMcmeta = datapackPath.resolve("pack.mcmeta");
        if (!Files.exists(packMcmeta)) {
            Files.createDirectories(datapackPath);
            Files.writeString(packMcmeta, PACK_MCMETA);
        }
    }

    public void createDimensionFiles(DimensionInfo info, DimensionSettings settings, String presetRegistryName) throws IOException {
        ensurePackMcmeta();

        // NOTE: Do NOT create dimension_type JSON - OTG already registers dimension types
        // for all presets via RegistryLoaderMixin. Creating a datapack dimension_type would
        // cause a "duplicate ID" conflict.

        // Create dimension JSON only - this defines the actual world/level using the
        // dimension type that OTG already registered
        Path dimPath = datapackPath.resolve("data")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve("dimension")
                .resolve(info.getName() + ".json");
        Files.createDirectories(dimPath.getParent());
        Files.writeString(dimPath, generateDimensionJson(info, presetRegistryName));

        OTGLog.info("Created datapack files for dimension %s", info.getName());
    }

    public void deleteDimensionFiles(String name) throws IOException {
        Path dimPath = datapackPath.resolve("data")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve("dimension")
                .resolve(name + ".json");

        Files.deleteIfExists(dimPath);

        OTGLog.info("Deleted datapack files for dimension %s", name);
    }

    private String formatInfiniburn(String infiniburn) {
        // Minecraft requires # prefix for tag references in datapacks
        if (infiniburn != null && !infiniburn.startsWith("#")) {
            return "#" + infiniburn;
        }
        return infiniburn;
    }

    private String generateDimensionTypeJson(DimensionSettings settings) {
        return String.format("""
                {
                  "ultrawarm": %s,
                  "natural": %s,
                  "coordinate_scale": %s,
                  "has_skylight": %s,
                  "has_ceiling": %s,
                  "ambient_light": %s,
                  "piglin_safe": %s,
                  "bed_works": %s,
                  "respawn_anchor_works": %s,
                  "has_raids": %s,
                  "logical_height": %d,
                  "min_y": %d,
                  "height": %d,
                  "infiniburn": "%s",
                  "effects": "%s",
                  "monster_spawn_light_level": {
                    "type": "minecraft:uniform",
                    "value": {
                      "min_inclusive": %d,
                      "max_inclusive": %d
                    }
                  },
                  "monster_spawn_block_light_limit": %d
                }
                """,
                settings.isUltraWarm(),
                settings.isNatural(),
                settings.getCoordinateScale(),
                settings.isHasSkyLight(),
                settings.isHasCeiling(),
                settings.getAmbientLight(),
                settings.isPiglinSafe(),
                settings.isBedWorks(),
                settings.isRespawnAnchorWorks(),
                settings.isHasRaids(),
                settings.getLogicalHeight(),
                settings.getMinY(),
                settings.getHeight(),
                formatInfiniburn(settings.getInfiniburn()),
                settings.getEffectsLocation().toLowerCase(),
                settings.getMonsterSpawnLightVariationMin(),
                settings.getMonsterSpawnLightVariationMax(),
                settings.getMonsterSpawnLightLimit()
        );
    }

    private String generateDimensionJson(DimensionInfo info, String presetRegistryName) {
        // The dimension_type and noise settings references must match what
        // RegistryLoaderMixin registers: otg:<presetRegistryName>.
        // Format must match SharedOTGChunkGenerator.CODEC:
        // - biome_source: OTGFabricBiomeProvider with preset_name and seed
        // - settings: reference to noise settings (use overworld as default)
        return String.format("""
                {
                  "type": "%s:%s",
                  "generator": {
                    "type": "%s:%s",
                    "biome_source": {
                      "type": "%s:%s",
                      "preset_name": "%s",
                      "seed": %d
                    },
                    "settings": "%s:%s"
                  }
                }
                """,
                Constants.MOD_ID_SHORT, presetRegistryName,
                Constants.MOD_ID_SHORT, Constants.MOD_ID_SHORT,
                Constants.MOD_ID_SHORT, Constants.MOD_ID_SHORT,
                info.getPreset(),
                info.getSeed(),
                Constants.MOD_ID_SHORT, presetRegistryName
        );
    }
}
