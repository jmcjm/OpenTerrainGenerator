package com.pg85.otg.shared.registry;

import com.pg85.otg.config.dimensions.WorldPresetConfig;

import java.util.Locale;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.minecraft.OTGDimensionType;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import com.pg85.otg.shared.i18n.OTGTranslations;

import java.util.*;

/**
 * Registers WorldPreset YAML configs as MC WorldPresets in the registry.
 * Called from OTGRegistryHelper.loadOTGPresets() after individual DimensionPresets
 * are registered.
 */
public class WorldPresetRegistrar {

    /**
     * Registers WorldPreset YAML configs as MC WorldPresets.
     *
     * @param configs        loaded WorldPresetConfig objects from YAML files
     * @param loadedPresets  map of presetFolderName → DimensionPreset (already loaded)
     * @param loaders        registry data loaders (for dimension types, noise, biomes, etc.)
     * @param factory        platform-specific chunk generator factory
     */
    public static void register(
            List<WorldPresetConfig> configs,
            Map<String, DimensionPreset> loadedPresets,
            List<RegistryDataLoader.Loader<?>> loaders,
            OTGRegistryHelper.ChunkGeneratorFactory factory
    ) {
        WritableRegistry<WorldPreset> worldPresets = OTGRegistryHelper.getRegistry(loaders, Registries.WORLD_PRESET);
        if (worldPresets == null) {
            OTGLog.error("Could not find world preset registry for WorldPreset YAML registration");
            return;
        }

        HolderGetter<DimensionType> dimensionTypes = OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE).asLookup();
        HolderGetter<NoiseGeneratorSettings> noiseSettings = OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.NOISE_SETTINGS).asLookup();
        Registry<Biome> biomeRegistry = OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.BIOME);

        Set<String> registeredNames = new HashSet<>();

        for (WorldPresetConfig config : configs) {
            if (config.DisplayName == null || config.DisplayName.isBlank()) {
                OTGLog.warn("WorldPreset YAML has no DisplayName, skipping registration");
                continue;
            }

            String normalizedId = normalizeId(config.DisplayName);
            if (registeredNames.contains(normalizedId)) {
                OTGLog.warn("Duplicate WorldPreset DisplayName '{}', skipping", config.DisplayName);
                continue;
            }

            Map<ResourceKey<LevelStem>, LevelStem> levelStems = buildLevelStems(
                config, loadedPresets, loaders, factory, dimensionTypes, noiseSettings, biomeRegistry);

            if (levelStems.isEmpty()) {
                OTGLog.error("WorldPreset '{}' produced no valid dimensions, skipping", config.DisplayName);
                continue;
            }

            WorldPreset preset = new WorldPreset(levelStems);
            ResourceKey<WorldPreset> key = ResourceKey.create(
                Registries.WORLD_PRESET,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, normalizedId));

            worldPresets.register(key, preset, RegistrationInfo.BUILT_IN);
            OTGTranslations.put(
                "generator." + Constants.MOD_ID_SHORT + "." + normalizedId,
                config.DisplayName);

            registeredNames.add(normalizedId);
            OTGLog.info("Registered WorldPreset '{}' as otg:{}", config.DisplayName, normalizedId);
        }
    }

    private static Map<ResourceKey<LevelStem>, LevelStem> buildLevelStems(
            WorldPresetConfig config,
            Map<String, DimensionPreset> loadedPresets,
            List<RegistryDataLoader.Loader<?>> loaders,
            OTGRegistryHelper.ChunkGeneratorFactory factory,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();

        // Overworld
        if (config.Overworld != null) {
            if (config.Overworld.NonOTGWorldType != null && !config.Overworld.NonOTGWorldType.isBlank()) {
                LevelStem vanillaOverworld = createVanillaLevelStem(
                    LevelStem.OVERWORLD, config.Overworld.NonOTGWorldType,
                    loaders, dimensionTypes, noiseSettings, biomeRegistry);
                if (vanillaOverworld != null) {
                    stems.put(LevelStem.OVERWORLD, vanillaOverworld);
                }
            } else if (config.Overworld.PresetFolderName != null) {
                LevelStem stem = createOTGLevelStem(
                    config.Overworld.PresetFolderName, LevelStem.OVERWORLD,
                    loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
                if (stem != null) {
                    stems.put(LevelStem.OVERWORLD, stem);
                }
            }
        }

        // Nether
        if (config.Nether != null && config.Nether.isNonOTG()) {
            LevelStem stem = createVanillaLevelStem(
                LevelStem.NETHER, config.Nether.NonOTGWorldType, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (stem != null) {
                stems.put(LevelStem.NETHER, stem);
            }
        } else if (config.Nether != null && config.Nether.hasPreset()) {
            LevelStem stem = createOTGLevelStem(
                config.Nether.PresetFolderName, LevelStem.NETHER,
                loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
            if (stem != null) {
                stems.put(LevelStem.NETHER, stem);
            }
        } else {
            LevelStem vanillaNether = createVanillaLevelStem(
                LevelStem.NETHER, null, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (vanillaNether != null) {
                stems.put(LevelStem.NETHER, vanillaNether);
            }
        }

        // End
        if (config.End != null && config.End.isNonOTG()) {
            LevelStem stem = createVanillaLevelStem(
                LevelStem.END, config.End.NonOTGWorldType, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (stem != null) {
                stems.put(LevelStem.END, stem);
            }
        } else if (config.End != null && config.End.hasPreset()) {
            LevelStem stem = createOTGLevelStem(
                config.End.PresetFolderName, LevelStem.END,
                loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
            if (stem != null) {
                stems.put(LevelStem.END, stem);
            }
        } else {
            LevelStem vanillaEnd = createVanillaLevelStem(
                LevelStem.END, null, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (vanillaEnd != null) {
                stems.put(LevelStem.END, vanillaEnd);
            }
        }

        // Custom dimensions
        if (config.Dimensions != null) {
            Set<String> seenDimKeys = new HashSet<>();
            for (WorldPresetConfig.OTGDimension dim : config.Dimensions) {
                String rawName = dim.hasPreset() ? dim.PresetFolderName : dim.DimensionName;
                if (rawName == null || rawName.isBlank()) continue;
                String normalizedName = DimensionNameUtils.normalizeName(rawName);
                if (!seenDimKeys.add(normalizedName)) {
                    OTGLog.warn("WorldPreset has duplicate custom dimension '{}', skipping duplicate", normalizedName);
                    continue;
                }
                ResourceKey<LevelStem> key = ResourceKey.create(
                    Registries.LEVEL_STEM,
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, normalizedName));

                LevelStem stem;
                if (dim.isNonOTG()) {
                    // Non-OTG generator mounted as a custom dimension: use the referenced
                    // WorldPreset's overworld stem (that's the "main" generator of any preset).
                    stem = lookupWorldPresetStem(dim.NonOTGWorldType, LevelStem.OVERWORLD, loaders);
                    if (stem == null) {
                        OTGLog.warn("Non-OTG dimension '{}': WorldPreset '{}' not found, skipping dimension",
                            dim.DimensionName, dim.NonOTGWorldType);
                        continue;
                    }
                } else {
                    stem = createOTGLevelStem(
                        dim.PresetFolderName, key,
                        loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
                    if (stem == null) continue;
                }
                stems.put(key, stem);
            }
        }

        return stems;
    }

    private static LevelStem createOTGLevelStem(
            String presetFolderName,
            ResourceKey<LevelStem> stemKey,
            Map<String, DimensionPreset> loadedPresets,
            OTGRegistryHelper.ChunkGeneratorFactory factory,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        DimensionPreset preset = loadedPresets.get(presetFolderName);
        if (preset == null) {
            OTGLog.error("WorldPreset references unknown DimensionPreset '{}', skipping dimension", presetFolderName);
            return null;
        }

        // Dimension type: always respect the preset's own config, regardless of which slot it's in
        ResourceKey<DimensionType> dimTypeKey = switch (preset.getConfig().getDimensionSettings().getDimensionType()) {
            case OTG -> ResourceKey.create(Registries.DIMENSION_TYPE,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName()));
            case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
            case NETHER -> BuiltinDimensionTypes.NETHER;
            case END -> BuiltinDimensionTypes.END;
        };

        Optional<Holder.Reference<DimensionType>> dimType = dimensionTypes.get(dimTypeKey);
        if (dimType.isEmpty()) {
            OTGLog.error("DimensionType {} not found for preset {}", dimTypeKey.location(), presetFolderName);
            return null;
        }

        // Noise key: always use otg:<name> — registerNoiseGenSettings() registers ALL OTG presets there
        ResourceKey<NoiseGeneratorSettings> noiseKey = ResourceKey.create(Registries.NOISE_SETTINGS,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName()));

        Optional<Holder.Reference<NoiseGeneratorSettings>> noiseRef = noiseSettings.get(noiseKey);
        if (noiseRef.isEmpty()) {
            OTGLog.error("NoiseGeneratorSettings {} not found for preset {}", noiseKey.location(), presetFolderName);
            return null;
        }

        ChunkGenerator generator = factory.create(presetFolderName, noiseRef.get(), biomeRegistry);
        return new LevelStem(dimType.get(), generator);
    }

    /**
     * Creates a vanilla LevelStem for overworld/nether/end.
     *
     * With a worldType set: looks up the WorldPreset by name in MC's registry
     * (supports "flat", "amplified", "large_biomes", modded types like
     * "biomesoplenty:biomesoplenty", etc.) and extracts the matching LevelStem
     * (overworld/nether/end) from it.
     *
     * Without a worldType (or on lookup miss): creates standard vanilla generation.
     */
    private static LevelStem createVanillaLevelStem(
            ResourceKey<LevelStem> stemKey,
            String worldType,
            List<RegistryDataLoader.Loader<?>> loaders,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        // For a slot with a specific world type: look up the MC WorldPreset by name
        // and extract the matching LevelStem from it. Delegates 100% to vanilla/mods.
        if (worldType != null && !worldType.isBlank()) {
            LevelStem fromPreset = lookupWorldPresetStem(worldType, stemKey, loaders);
            if (fromPreset != null) {
                return fromPreset;
            }
            OTGLog.warn("WorldPreset '{}' not found in registry, falling back to vanilla", worldType);
        }

        // Standard vanilla fallback (normal overworld, nether, end)
        return createStandardVanillaLevelStem(stemKey, loaders, dimensionTypes, noiseSettings, biomeRegistry);
    }

    /**
     * Looks up a WorldPreset by name in MC's registry and extracts the LevelStem for the
     * given slot. Supports vanilla types ("flat", "amplified", "large_biomes") and modded
     * types (any mod that registers a WorldPreset, e.g. "biomesoplenty:biomesoplenty").
     * Custom OTG dimensions use the referenced preset's OVERWORLD stem.
     */
    private static LevelStem lookupWorldPresetStem(
            String worldType,
            ResourceKey<LevelStem> slot,
            List<RegistryDataLoader.Loader<?>> loaders
    ) {
        String type = worldType.trim().toLowerCase(Locale.ROOT);
        ResourceLocation loc = type.contains(":") ? ResourceLocation.tryParse(type) : ResourceLocation.withDefaultNamespace(type);
        if (loc == null) {
            OTGLog.warn("Invalid NonOTGWorldType: '{}'", worldType);
            return null;
        }

        ResourceKey<WorldPreset> presetKey = ResourceKey.create(Registries.WORLD_PRESET, loc);

        WritableRegistry<WorldPreset> presetRegistry = OTGRegistryHelper.getRegistry(loaders, Registries.WORLD_PRESET);
        if (presetRegistry == null) {
            OTGLog.error("WorldPreset registry not available");
            return null;
        }

        Optional<Holder.Reference<WorldPreset>> holder = presetRegistry.getHolder(presetKey);
        if (holder.isEmpty()) {
            return null;
        }

        LevelStem stem = holder.get().value().dimensions.get(slot);
        if (stem == null) {
            OTGLog.warn("WorldPreset '{}' has no {} dimension", loc, slot.location());
            return null;
        }

        OTGLog.info("Using {} from WorldPreset '{}' for NonOTGWorldType", slot.location(), loc);
        return stem;
    }

    /**
     * Creates a standard vanilla LevelStem (normal overworld, nether, or end).
     */
    private static LevelStem createStandardVanillaLevelStem(
            ResourceKey<LevelStem> stemKey,
            List<RegistryDataLoader.Loader<?>> loaders,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        ResourceKey<DimensionType> dimTypeKey;
        ChunkGenerator chunkGenerator;

        if (stemKey.equals(LevelStem.OVERWORLD)) {
            dimTypeKey = BuiltinDimensionTypes.OVERWORLD;
            Holder<MultiNoiseBiomeSourceParameterList> biomeSource =
                OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .asLookup().getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            Holder<NoiseGeneratorSettings> noise = noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            chunkGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(biomeSource), noise);
        } else if (stemKey.equals(LevelStem.NETHER)) {
            dimTypeKey = BuiltinDimensionTypes.NETHER;
            Holder<MultiNoiseBiomeSourceParameterList> biomeSource =
                OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .asLookup().getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            Holder<NoiseGeneratorSettings> noise = noiseSettings.getOrThrow(NoiseGeneratorSettings.NETHER);
            chunkGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(biomeSource), noise);
        } else if (stemKey.equals(LevelStem.END)) {
            dimTypeKey = BuiltinDimensionTypes.END;
            Holder<NoiseGeneratorSettings> noise = noiseSettings.getOrThrow(NoiseGeneratorSettings.END);
            chunkGenerator = new NoiseBasedChunkGenerator(
                TheEndBiomeSource.create(biomeRegistry.asLookup()), noise);
        } else {
            OTGLog.error("Cannot create vanilla LevelStem for unknown dimension {}", stemKey.location());
            return null;
        }

        Optional<Holder.Reference<DimensionType>> dimType = dimensionTypes.get(dimTypeKey);
        if (dimType.isEmpty()) {
            OTGLog.error("DimensionType {} not found", dimTypeKey.location());
            return null;
        }

        return new LevelStem(dimType.get(), chunkGenerator);
    }

    public static String normalizeId(String displayName) {
        return displayName.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }
}
