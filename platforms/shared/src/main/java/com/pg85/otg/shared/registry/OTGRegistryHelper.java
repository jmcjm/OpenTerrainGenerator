package com.pg85.otg.shared.registry;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.settings.preset.*;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import com.pg85.otg.platform.noise.OTGNoiseParamRegistry;
import com.pg85.otg.platform.noise.OTGNoiseRouterBuilder;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.biome.SharedDimensionPresetBiomeLoader;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.minecraft.OTGDimensionType;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.NotNull;

import com.pg85.otg.shared.i18n.OTGTranslations;

import java.util.*;
import java.util.function.Function;

/**
 * Shared utility methods extracted from platform-specific RegistryLoaderMixin classes.
 * All methods are static and platform-agnostic.
 */
public final class OTGRegistryHelper {

    private OTGRegistryHelper() {}

    // --- Functional interfaces for platform-specific operations ---

    @FunctionalInterface
    public interface ChunkGeneratorFactory {
        ChunkGenerator create(String presetFolder, Holder<NoiseGeneratorSettings> noiseRef, Registry<Biome> biomeReg);
    }

    // --- Registry lookup ---

    @SuppressWarnings("unchecked")
    public static <T> WritableRegistry<T> getRegistry(List<RegistryDataLoader.Loader<?>> loaders, ResourceKey<Registry<T>> key) {
        for (RegistryDataLoader.Loader<?> entry : loaders) {
            if (entry.registry().key().equals(key)) {
                return (WritableRegistry<T>) entry.registry();
            }
        }
        return null;
    }

    public static <T> WritableRegistry<T> getRegistryOrThrow(List<RegistryDataLoader.Loader<?>> loaders, ResourceKey<Registry<T>> key) {
        WritableRegistry<T> registry = getRegistry(loaders, key);
        if (registry == null) {
            throw new RuntimeException("Could not find registry for key " + key.location());
        }
        return registry;
    }

    // --- Noise ---

    public static @NotNull NoiseRouter getZeroNoiseRouter() {
        return new NoiseRouter(
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero()
        );
    }

    // --- Dimension types ---

    public static @NotNull DimensionType getDimensionType(DimensionSettings settings) {
        return new DimensionType(
                settings.getFixedTime(),
                settings.isHasSkyLight(),
                settings.isHasCeiling(),
                settings.isUltraWarm(),
                settings.isNatural(),
                settings.getCoordinateScale(),
                settings.isBedWorks(),
                settings.isRespawnAnchorWorks(),
                settings.getMinY(),
                settings.getHeight(),
                settings.getLogicalHeight(),
                TagKey.create(Registries.BLOCK, ResourceLocation.parse(settings.getInfiniburn())),
                ResourceLocation.parse(settings.getEffectsLocation().toLowerCase(Locale.ROOT)),
                (float) settings.getAmbientLight(),
                new DimensionType.MonsterSettings(
                        settings.isPiglinSafe(),
                        settings.isHasRaids(),
                        UniformInt.of(
                                settings.getMonsterSpawnLightVariationMin(),
                                settings.getMonsterSpawnLightVariationMax()
                        ),
                        settings.getMonsterSpawnLightLimit()
                )
        );
    }

    public static @NotNull HashMap<DimensionPreset, ResourceKey<DimensionType>> registerDimensionTypes(List<RegistryDataLoader.Loader<?>> loaders) {
        var map = new HashMap<DimensionPreset, ResourceKey<DimensionType>>();
        for (DimensionPreset preset : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
            OTGDimensionType otgDimensionType = preset.getConfig().getDimensionSettings().getDimensionType();
            ResourceKey<DimensionType> dimensionKey = switch (otgDimensionType) {
                case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
                case NETHER -> BuiltinDimensionTypes.NETHER;
                case END -> BuiltinDimensionTypes.END;
                case OTG -> {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName().toLowerCase(Locale.ROOT));
                    ResourceKey<DimensionType> dimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, id);
                    DimensionType dimensionType = getDimensionType(preset.getConfig().getDimensionSettings());
                    WritableRegistry<DimensionType> dimensionTypes = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE);
                    dimensionTypes.register(dimensionTypeKey, dimensionType, RegistrationInfo.BUILT_IN);
                    OTGLog.info("Registered dimension type: {}", dimensionTypeKey.location());
                    yield dimensionTypeKey;
                }
            };
            map.put(preset, dimensionKey);
        }
        return map;
    }

    // --- World presets ---

    public static void registerWorldPresets(
            DimensionPreset preset,
            WritableRegistry<WorldPreset> worldPresets,
            Map<ResourceKey<LevelStem>, LevelStem> levelStems,
            Set<String> yamlClaimedIds
    ) {
        String presetId = preset.getRegistryName().toLowerCase(Locale.ROOT);
        // An explicit WorldPreset YAML with the same normalized id is the source of truth and
        // gets registered later in loadOTGPresets. Defer to it instead of racing for the same
        // otg:<id> key (both paths registering it is the duplicate-key crash on world load).
        if (yamlClaimedIds.contains(presetId)) {
            OTGLog.info("World preset otg:{} is defined by a WorldPreset YAML; skipping auto-registration from DimensionPreset '{}'", presetId, preset.getRegistryName());
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, presetId);
        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, id);
        if (worldPresets.containsKey(key)) {
            OTGLog.warn("World preset {} already registered; skipping duplicate", key.location());
            return;
        }
        worldPresets.register(key, new WorldPreset(levelStems), RegistrationInfo.BUILT_IN);
        OTGTranslations.put(
            "generator." + Constants.MOD_ID_SHORT + "." + presetId,
            preset.getConfig().getPresetInfo().getDisplayName());
        OTGLog.info("Registered world preset: {}", key.location());
    }

    // --- Biome registration ---

    public static void registerBiomes(List<RegistryDataLoader.Loader<?>> loaders) {
        if (OTG.getEngine().getPluginConfig().getDeveloperModeEnabled()) {
            OTG.getEngine().getCustomObjectManager().reloadCustomObjectFiles();
            OTG.getEngine().getDimensionPresetLoader().loadDimensionPresetsFromDisk();
        }

        // Override bootstrap HolderGetters with real registry lookups.
        // The bootstrap context (BiomeDataMixin) creates unbound holder references
        // that never get bound because they belong to a transient builder, not the
        // materialized registry. Using the actual loaded registries here gives us
        // holders that ARE (or will be) properly bound when the registry freezes.
        WritableRegistry<net.minecraft.world.level.levelgen.placement.PlacedFeature> pfRegistry =
                getRegistry(loaders, Registries.PLACED_FEATURE);
        if (pfRegistry != null) {
            SharedDimensionPresetBiomeLoader.PLACED_FEATURE_HOLDER = pfRegistry.asLookup();
        }
        WritableRegistry<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> carverRegistry =
                getRegistry(loaders, Registries.CONFIGURED_CARVER);
        if (carverRegistry != null) {
            SharedDimensionPresetBiomeLoader.CONFIGURED_CARVER_HOLDER = carverRegistry.asLookup();
        }

        SharedDimensionPresetBiomeLoader loader = (SharedDimensionPresetBiomeLoader) OTG.getEngine().getDimensionPresetLoader();
        WritableRegistry<Biome> biomeWritableRegistry = getRegistry(loaders, Registries.BIOME);
        if (biomeWritableRegistry == null) {
            OTGLog.error("Could not find biome registry");
            return;
        }
        loader.registerBiomes(biomeWritableRegistry);
    }

    // --- Noise generator settings ---

    public static void registerNoiseGenSettings(
            DimensionPreset preset,
            List<RegistryDataLoader.Loader<?>> loaders,
            RegistryAccess registryAccess,
            Function<LocalMaterialData, BlockState> toBlockState) {
        DimensionPresetSettings presetSettings = preset.getConfig();
        DimensionSettings dimensionSettings = presetSettings.getDimensionSettings();
        BlockSettings blockSettings = presetSettings.getBlockSettings();
        ResourceSettings resourceSettings = presetSettings.getResourceSettings();
        boolean modernCaves = presetSettings.getCarverSettings().isUseModernCaves();
        boolean aquifers = presetSettings.getNoiseCaveSettings().isAquifersEnabled();
        boolean oreVeins = presetSettings.getNoiseCaveSettings().isVeinsEnabled() && !resourceSettings.isDisableOreGen();

        NoiseGeneratorSettings ngs;
        if (modernCaves) {
            WritableRegistry<NormalNoise.NoiseParameters> noiseRegistry = getRegistry(loaders, Registries.NOISE);
            if (noiseRegistry != null) {
                OTGNoiseParamRegistry.registerNoiseParameters(presetSettings, noiseRegistry);
            }
            HolderGetter<DensityFunction> densityGetter = getRegistryOrThrow(loaders, Registries.DENSITY_FUNCTION).asLookup();
            HolderGetter<NormalNoise.NoiseParameters> noiseGetter = getRegistryOrThrow(loaders, Registries.NOISE).asLookup();
            OTGNoiseRouterBuilder routerBuilder = new OTGNoiseRouterBuilder(densityGetter, noiseGetter);
            String presetName = preset.getRegistryName().toLowerCase(Locale.ROOT);
            NoiseRouter router = routerBuilder.buildWithSettings(presetSettings.getNoiseCaveSettings(), presetName, false, false);

            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    toBlockState.apply(blockSettings.getDefaultStoneBlock()),
                    toBlockState.apply(blockSettings.getWaterBlock()),
                    router,
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    aquifers,
                    oreVeins,
                    !resourceSettings.isDisableOreGen(),
                    false
            );
        } else {
            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    toBlockState.apply(blockSettings.getDefaultStoneBlock()),
                    toBlockState.apply(blockSettings.getWaterBlock()),
                    getZeroNoiseRouter(),
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    false,
                    false,
                    !resourceSettings.isDisableOreGen(),
                    false
            );
        }

        WritableRegistry<NoiseGeneratorSettings> registry = getRegistry(loaders, Registries.NOISE_SETTINGS);
        if (registry == null) {
            throw new RuntimeException("Could not find noise settings registry");
        }
        ResourceKey<NoiseGeneratorSettings> key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName()));
        registry.register(key, ngs, RegistrationInfo.BUILT_IN);
    }

    // --- Level stems ---

    public static Map<ResourceKey<LevelStem>, LevelStem> createLevelStems(
            DimensionPreset preset,
            List<RegistryDataLoader.Loader<?>> loaders,
            ChunkGeneratorFactory factory
    ) {
        var dimensionNames = preset.getDimensionNames();
        int counter = 0;

        Map<ResourceKey<LevelStem>, LevelStem> levelStems = new HashMap<>();
        HolderGetter<DimensionType> dimensionHolders = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE).asLookup();
        HolderGetter<NoiseGeneratorSettings> noiseHolders = getRegistryOrThrow(loaders, Registries.NOISE_SETTINGS).asLookup();

        for (String dim : dimensionNames) {
            ResourceKey<LevelStem> key;
            LevelStem levelStem;
            if (counter == 0) {
                key = LevelStem.OVERWORLD;
            } else if (counter == 1) {
                key = LevelStem.NETHER;
            } else if (counter == 2) {
                key = LevelStem.END;
            } else {
                key = ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.parse(dim));
            }

            ChunkGenerator chunkGenerator;

            if (dim.startsWith(Constants.MOD_ID_SHORT)) {
                DimensionPreset dimPreset = null;
                for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
                    if (dim.equals(Constants.MOD_ID_SHORT + ":" + p.getRegistryName())) {
                        dimPreset = p;
                        break;
                    }
                }
                if (dimPreset == null) {
                    OTGLog.error("Could not find preset for dimension {}", dim);
                    continue;
                }

                ResourceKey<DimensionType> dimensionKey = switch (dimPreset.getConfig().getDimensionSettings().getDimensionType()) {
                    case OTG -> ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, dimPreset.getRegistryName()));
                    case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
                    case NETHER -> BuiltinDimensionTypes.NETHER;
                    case END -> BuiltinDimensionTypes.END;
                };

                // Always use otg:<name> — registerNoiseGenSettings() registers ALL OTG presets under this key
                ResourceKey<NoiseGeneratorSettings> noiseKey = ResourceKey.create(
                    Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, dimPreset.getRegistryName()));

                Holder.Reference<DimensionType> dimensionReference = dimensionHolders.getOrThrow(dimensionKey);
                if (!dimensionReference.isBound()) {
                    OTGLog.error("Dimension reference for dimension {} is not bound", dim);
                }

                Holder.Reference<NoiseGeneratorSettings> noiseReference = noiseHolders.getOrThrow(noiseKey);
                if (!noiseReference.isBound()) {
                    OTGLog.error("Noise reference for dimension {} is not bound", dim);
                }

                Registry<Biome> biomeRegistry = getRegistryOrThrow(loaders, Registries.BIOME);

                chunkGenerator = factory.create(preset.getFolderName(), noiseReference, biomeRegistry);
                levelStem = new LevelStem(
                        dimensionReference,
                        chunkGenerator
                );
            } else {
                ResourceKey<DimensionType> dimensionKey = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse(dim));
                Optional<Holder.Reference<DimensionType>> dimensionTypeHolder = dimensionHolders.get(dimensionKey);
                if (dimensionTypeHolder.isEmpty()) {
                    OTGLog.error("Could not find dimension reference for dimension {}", dimensionKey.location());
                    continue;
                }
                if (!dimensionTypeHolder.get().isBound()) {
                    OTGLog.error("Dimension reference for dimension {} is not bound", dimensionKey.location());
                    continue;
                }

                if (dimensionKey == BuiltinDimensionTypes.OVERWORLD) {
                    Holder<MultiNoiseBiomeSourceParameterList> overworldBiomeSource = getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .asLookup().getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
                    if (!overworldBiomeSource.isBound()) {
                        OTGLog.error("Overworld biome source is not bound");
                    }
                    Holder<NoiseGeneratorSettings> overworldNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            MultiNoiseBiomeSource.createFromPreset(overworldBiomeSource),
                            overworldNoise
                    );
                } else if (dimensionKey == BuiltinDimensionTypes.NETHER) {
                    Holder<MultiNoiseBiomeSourceParameterList> netherBiomeSource = getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .asLookup().getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
                    if (!netherBiomeSource.isBound()) {
                        OTGLog.error("Nether biome source is not bound");
                    }
                    Holder<NoiseGeneratorSettings> netherNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.NETHER);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            MultiNoiseBiomeSource.createFromPreset(netherBiomeSource),
                            netherNoise
                    );
                } else if (dimensionKey == BuiltinDimensionTypes.END) {
                    var biomes = getRegistryOrThrow(loaders, Registries.BIOME).asLookup();
                    Holder<NoiseGeneratorSettings> endNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.END);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            TheEndBiomeSource.create(biomes),
                            endNoise
                    );
                } else {
                    OTGLog.error("Non-OTG dimension {} not yet supported", dimensionKey.location());
                    continue;
                }

                levelStem = new LevelStem(
                        dimensionTypeHolder.get(),
                        chunkGenerator
                );
            }
            levelStems.put(key, levelStem);
            counter++;
        }

        return levelStems;
    }

    // --- Main entry point (called from platform Mixin) ---

    public static void loadOTGPresets(
            List<RegistryDataLoader.Loader<?>> loaders,
            RegistryAccess registryAccess,
            ChunkGeneratorFactory chunkGeneratorFactory,
            Function<LocalMaterialData, BlockState> toBlockState
    ) {
        if (getRegistry(loaders, Registries.DIMENSION) != null) {
            return;
        }

        if (getRegistry(loaders, Registries.NOISE_SETTINGS) == null) {
            return;
        }

        OTGLog.info("Registering the following OTG presets: {}", OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets());

        registerBiomes(loaders);

        HashMap<DimensionPreset, ResourceKey<DimensionType>> dimensionTypes = registerDimensionTypes(loaders);

        for (DimensionPreset preset : dimensionTypes.keySet()) {
            registerNoiseGenSettings(preset, loaders, registryAccess, toBlockState);
        }

        WritableRegistry<WorldPreset> worldPresets = getRegistry(loaders, Registries.WORLD_PRESET);
        if (worldPresets == null) {
            OTGLog.error("Could not find world preset registry");
            return;
        }

        // Load WorldPreset YAMLs up-front and collect the otg:<id> keys they will claim, so the
        // per-DimensionPreset auto-registration below can defer to an explicit YAML of the same id
        // rather than both paths registering it (that collision is the duplicate-key crash).
        List<WorldPresetConfig> worldPresetConfigs = WorldPresetConfigLoader.loadAll(
            OTG.getEngine().getOTGRootFolder());
        Set<String> yamlClaimedIds = new HashSet<>();
        for (WorldPresetConfig config : worldPresetConfigs) {
            if (config.DisplayName != null && !config.DisplayName.isBlank()) {
                yamlClaimedIds.add(WorldPresetRegistrar.normalizeId(config.DisplayName));
            }
        }

        for (DimensionPreset preset : dimensionTypes.keySet()) {
            if (!preset.getConfig().getPresetInfo().isSelectableInWorldCreation()) {
                continue;
            }

            OTGLog.info("Registering world preset: {}", dimensionTypes.get(preset).location());

            Map<ResourceKey<LevelStem>, LevelStem> levelStems = createLevelStems(preset, loaders, chunkGeneratorFactory);

            registerWorldPresets(preset, worldPresets, levelStems, yamlClaimedIds);
        }

        // Register WorldPreset YAMLs as MC WorldPresets
        if (!worldPresetConfigs.isEmpty()) {
            Map<String, DimensionPreset> presetMap = new HashMap<>();
            for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
                presetMap.put(p.getFolderName(), p);
            }
            WorldPresetRegistrar.register(worldPresetConfigs, presetMap, loaders, chunkGeneratorFactory);
        }
    }
}
