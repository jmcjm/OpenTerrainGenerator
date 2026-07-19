package com.pg85.otg.shared.biome;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import com.pg85.otg.biome.BiomePlan;
import com.pg85.otg.biome.BiomePlanResolver;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.biome.BiomeTemplate;
import com.pg85.otg.config.biome.TemplateBiome;
import com.pg85.otg.config.preset.DimensionPresetConfig;
import com.pg85.otg.config.settings.biome.BiomeStructureTagConfig;
import com.pg85.otg.config.settings.biome.BiomeTagSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.gen.biome.layers.BiomeLayerData;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IBiomeResourceLocation;
import com.pg85.otg.config.settings.preset.DimensionPresetSettings;
import com.pg85.otg.presets.LocalDimensionPresetLoader;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.biome.MCBiomeResourceLocation;
import com.pg85.otg.util.biome.OTGBiomeResourceLocation;
import com.pg85.otg.util.logging.LogCategory;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Shared biome loader that uses composition (BiomePlatformAdapter) instead of
 * abstract methods. Both Fabric and NeoForge use this class directly, passing
 * their platform-specific adapter at construction time.
 */
public class SharedDimensionPresetBiomeLoader extends LocalDimensionPresetLoader {
    // Static fields for bootstrap context holders (set by BiomeDataMixin, overridden by OTGRegistryHelper)
    public static HolderGetter<PlacedFeature> PLACED_FEATURE_HOLDER;
    public static HolderGetter<ConfiguredWorldCarver<?>> CONFIGURED_CARVER_HOLDER;
    public static boolean BIOME_DATA_INITIALIZED = false;

    private static final Map<ResourceKey<Biome>, BiomeStructureTagConfig> structureTagConfigs = new LinkedHashMap<>();
    private static final Map<ResourceKey<Biome>, BiomeTagSettings> biomeTagConfigs = new LinkedHashMap<>();

    public static Map<ResourceKey<Biome>, BiomeStructureTagConfig> getStructureTagConfigs() {
        return structureTagConfigs;
    }

    public static Map<ResourceKey<Biome>, BiomeTagSettings> getBiomeTagConfigs() {
        return biomeTagConfigs;
    }

    private final BiomePlatformAdapter platformAdapter;
    private Map<String, List<ResourceKey<Biome>>> biomesByPresetFolderName = new LinkedHashMap<>();

    public SharedDimensionPresetBiomeLoader(Path otgRootFolder, BiomePlatformAdapter platformAdapter) {
        super(otgRootFolder);
        this.platformAdapter = platformAdapter;
    }

    public List<ResourceKey<Biome>> getBiomeResourceKeys(String presetFolderName) {
        return this.biomesByPresetFolderName.get(presetFolderName);
    }

    public void reloadPresetFromDisk(String presetFolderName, WritableRegistry<Biome> biomeRegistry) {
        clearBiomeData();
        this.biomesByPresetFolderName = new LinkedHashMap<>();
        structureTagConfigs.clear();
        biomeTagConfigs.clear();

        if (this.presetsDir.exists() && this.presetsDir.isDirectory()) {
            for (File presetDir : Objects.requireNonNull(this.presetsDir.listFiles())) {
                if (presetDir.isDirectory() && presetDir.getName().equals(presetFolderName)) {
                    for (File file : Objects.requireNonNull(presetDir.listFiles())) {
                        if (file.getName().equals(Constants.DIMENSION_PRESET_CONFIG_FILE)) {
                            DimensionPreset preset = loadPreset(presetDir.toPath());
                            DimensionPreset existingPreset = this.presets.get(preset.getFolderName());
                            existingPreset.update(preset);
                            break;
                        }
                    }
                }
            }
        }
        registerBiomes(biomeRegistry);
    }

    public void reRegisterBiomes(String presetFolderName, WritableRegistry<Biome> biomeRegistry) {
        removeBiomeData(presetFolderName);
        this.biomesByPresetFolderName.remove(presetFolderName);
        registerBiomes(biomeRegistry);
    }

    public void registerBiomes() {
        registerBiomes(null);
    }

    public void registerBiomes(WritableRegistry<Biome> biomeRegistry) {
        for (DimensionPreset preset : this.presets.values()) {
            registerBiomesForPreset(preset, biomeRegistry);
        }
    }

    private void registerBiomesForPreset(DimensionPreset preset, WritableRegistry<Biome> biomeRegistry) {
        if (!BIOME_DATA_INITIALIZED) {
            throw new IllegalStateException("BiomeDataMixin not initialized");
        }
        HolderGetter<PlacedFeature> featureHolder = PLACED_FEATURE_HOLDER;
        HolderGetter<ConfiguredWorldCarver<?>> carverHolder = CONFIGURED_CARVER_HOLDER;

        List<ResourceKey<Biome>> presetBiomes = new ArrayList<>();
        this.biomesByPresetFolderName.put(preset.getFolderName(), presetBiomes);

        DimensionPresetSettings presetConfig = preset.getConfig();

        List<BiomeConfig> biomeConfigs = preset.getBiomeConfigList();
        List<BiomeTemplate> biomeTemplates = preset.getBiomeTemplateList();

        Map<String, BiomeSettings> biomeConfigsByName = new HashMap<>();
        Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation = new LinkedHashMap<>();
        List<String> blackListedBiomes = presetConfig.getGenerationSettings().getBlackListedBiomes();

        processTemplateBiomes(preset.getFolderName(), presetConfig, biomeTemplates, biomeConfigsByResourceLocation, biomeConfigsByName, blackListedBiomes, biomeRegistry);

        for (BiomeConfig biomeConfig : biomeConfigs) {
            if (!biomeConfig.getIdentitySettings().isTemplateForBiome()) {
                IBiomeResourceLocation otgLocation = new OTGBiomeResourceLocation(preset.getFolder(), preset.getRegistryName(), biomeConfig.getIdentitySettings().getBiomeName());
                biomeConfig.setRegistryKey(otgLocation);
                biomeConfigsByResourceLocation.put(otgLocation, biomeConfig);
                biomeConfigsByName.put(biomeConfig.getIdentitySettings().getBiomeName(), biomeConfig);
            }
        }

        MobInheritanceHandler.handleMobInheritance(biomeRegistry, biomeConfigs);

        BiomePlan plan = BiomePlanResolver.resolve(presetConfig, biomeConfigsByResourceLocation, biomeConfigsByName);

        BiomeFactory biomeFactory = new BiomeFactory(featureHolder, carverHolder);
        BiomeRegistrar.RegistrationResult result = BiomeRegistrar.register(
                plan, biomeConfigsByResourceLocation, biomeFactory,
                presetConfig, biomeRegistry, platformAdapter::createPlatformBiome);

        presetBiomes.addAll(result.presetBiomes());
        structureTagConfigs.putAll(result.structureTagConfigs());
        biomeTagConfigs.putAll(result.biomeTagConfigs());
        putGlobalIdMapping(preset.getFolderName(), result.globalIdMapping());

        BiomeLayerData data = new BiomeLayerData(
                preset.getFolder(), presetConfig, plan.oceanBiomeConfig(), plan.oceanTemperatures(),
                plan.groupRegistry(), plan.biomeDepths(), plan.groupDepths(),
                plan.isleBiomesAtDepth(), plan.borderBiomesAtDepth(), plan.biomeIdsByName(),
                plan.biomeColorMap(), result.globalIdMapping()
        );

        putPresetGenerationData(preset.getFolderName(), data);
    }

    private void processTemplateBiomes(
        String presetFolderName,
        DimensionPresetSettings presetConfig,
        List<BiomeTemplate> biomeTemplates,
        Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation,
        Map<String, BiomeSettings> biomeConfigsByName,
        List<String> blackListedBiomes,
        Registry<Biome> biomeRegistry
    ) {
        if (!(presetConfig instanceof DimensionPresetConfig)) {
            return;
        }

        for (TemplateBiome templateBiome : ((DimensionPresetConfig) presetConfig).getGenerationSettings().getTemplateBiomes()) {
            OTGLog.info(LogCategory.BIOME_REGISTRY, "Processing template biome: {}", templateBiome);

            BiomeTemplate biomeTemplate = biomeTemplates.stream()
                .filter(bt -> bt.getIdentitySettings().getBiomeName().equalsIgnoreCase(templateBiome.getName()))
                .findFirst()
                .orElse(null);

            if (biomeTemplate == null) {
                OTGLog.warn(LogCategory.BIOME_REGISTRY,
                    "No BiomeTemplate found for template biome: {}", templateBiome.getName());
                continue;
            }

            List<String> includeTags = new ArrayList<>();
            List<String> excludeTags = new ArrayList<>();
            List<String> includeMods = new ArrayList<>();
            List<String> excludeMods = new ArrayList<>();

            for (String tagString : templateBiome.getTags()) {
                String tag = tagString.trim().toLowerCase();

                if (tag.startsWith(Constants.MOD_BIOME_DICT_TAG_LABEL_EXCLUDE) ||
                    tag.startsWith(Constants.MC_BIOME_DICT_TAG_LABEL_EXCLUDE) ||
                    tag.startsWith(Constants.BIOME_DICT_TAG_LABEL_EXCLUDE)) {
                    String tagName = tag.replace(Constants.MOD_BIOME_DICT_TAG_LABEL_EXCLUDE, "")
                                        .replace(Constants.MC_BIOME_DICT_TAG_LABEL_EXCLUDE, "")
                                        .replace(Constants.BIOME_DICT_TAG_LABEL_EXCLUDE, "");
                    excludeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_BIOME_CATEGORY_LABEL_EXCLUDE) ||
                           tag.startsWith(Constants.MC_BIOME_CATEGORY_LABEL_EXCLUDE) ||
                           tag.startsWith(Constants.BIOME_CATEGORY_LABEL_EXCLUDE)) {
                    String tagName = tag.replace(Constants.MOD_BIOME_CATEGORY_LABEL_EXCLUDE, "")
                                        .replace(Constants.MC_BIOME_CATEGORY_LABEL_EXCLUDE, "")
                                        .replace(Constants.BIOME_CATEGORY_LABEL_EXCLUDE, "");
                    excludeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_LABEL_EXCLUDE)) {
                    excludeMods.add(tag.replace(Constants.MOD_LABEL_EXCLUDE, ""));
                } else if (tag.startsWith(Constants.MOD_BIOME_DICT_TAG_LABEL) ||
                           tag.startsWith(Constants.MC_BIOME_DICT_TAG_LABEL) ||
                           tag.startsWith(Constants.BIOME_DICT_TAG_LABEL)) {
                    String tagName = tag.replace(Constants.MOD_BIOME_DICT_TAG_LABEL, "")
                                        .replace(Constants.MC_BIOME_DICT_TAG_LABEL, "")
                                        .replace(Constants.BIOME_DICT_TAG_LABEL, "");
                    includeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_BIOME_CATEGORY_LABEL) ||
                           tag.startsWith(Constants.MC_BIOME_CATEGORY_LABEL) ||
                           tag.startsWith(Constants.BIOME_CATEGORY_LABEL)) {
                    String tagName = tag.replace(Constants.MOD_BIOME_CATEGORY_LABEL, "")
                                        .replace(Constants.MC_BIOME_CATEGORY_LABEL, "")
                                        .replace(Constants.BIOME_CATEGORY_LABEL, "");
                    includeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_LABEL)) {
                    includeMods.add(tag.replace(Constants.MOD_LABEL, ""));
                } else if (tag.contains(":")) {
                    processDirectBiomeReference(tag, presetFolderName, biomeTemplate, biomeConfigsByResourceLocation, biomeConfigsByName, biomeRegistry);
                }
            }

            if (!includeTags.isEmpty()) {
                for (ResourceKey<Biome> biomeKey : biomeRegistry.registryKeySet()) {
                    if (blackListedBiomes.contains(biomeKey.location().toString())) {
                        continue;
                    }

                    String namespace = biomeKey.location().getNamespace();
                    if (!includeMods.isEmpty() && !includeMods.contains(namespace)) {
                        continue;
                    }
                    if (excludeMods.contains(namespace)) {
                        continue;
                    }

                    boolean matchesAllInclude = true;
                    for (String includeTag : includeTags) {
                        if (!platformAdapter.biomeHasTag(biomeRegistry, biomeKey, includeTag)) {
                            matchesAllInclude = false;
                            break;
                        }
                    }
                    if (!matchesAllInclude) {
                        continue;
                    }

                    boolean matchesAnyExclude = false;
                    for (String excludeTag : excludeTags) {
                        if (platformAdapter.biomeHasTag(biomeRegistry, biomeKey, excludeTag)) {
                            matchesAnyExclude = true;
                            break;
                        }
                    }
                    if (matchesAnyExclude) {
                        continue;
                    }

                    Biome biome = biomeRegistry.get(biomeKey);
                    if (biome != null && !templateBiome.temperatureAllowed(biome.getBaseTemperature())) {
                        continue;
                    }

                    IBiomeResourceLocation location = new MCBiomeResourceLocation(
                        biomeKey.location().getNamespace(),
                        biomeKey.location().getPath(),
                        presetFolderName
                    );

                    if (!biomeConfigsByResourceLocation.containsKey(location)) {
                        biomeTemplate.setRegistryKey(location);
                        biomeConfigsByResourceLocation.put(location, biomeTemplate);
                        biomeConfigsByName.put(biomeTemplate.getIdentitySettings().getBiomeName(), biomeTemplate);

                        OTGLog.info(LogCategory.BIOME_REGISTRY,
                            "Template biome {} matched: {}", templateBiome.getName(), biomeKey.location());
                    }
                }
            }
        }
    }

    private void processDirectBiomeReference(
        String biomeId,
        String presetFolderName,
        BiomeSettings biomeSettings,
        Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation,
        Map<String, BiomeSettings> biomeConfigsByName,
        Registry<Biome> biomeRegistry
    ) {
        ResourceLocation location = ResourceLocation.parse(biomeId);
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, location);

        if (biomeRegistry.containsKey(biomeKey)) {
            IBiomeResourceLocation otgLocation = new MCBiomeResourceLocation(
                location.getNamespace(),
                location.getPath(),
                presetFolderName
            );

            if (!biomeConfigsByResourceLocation.containsKey(otgLocation)) {
                biomeSettings.setRegistryKey(otgLocation);
                biomeConfigsByResourceLocation.put(otgLocation, biomeSettings);
                biomeConfigsByName.put(biomeSettings.getIdentitySettings().getBiomeName(), biomeSettings);

                OTGLog.info(LogCategory.BIOME_REGISTRY, "Direct biome reference matched: {}", biomeId);
            }
        } else {
            OTGLog.warn(LogCategory.BIOME_REGISTRY, "Direct biome reference not found in registry: {}", biomeId);
        }
    }
}
