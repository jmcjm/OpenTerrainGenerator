package com.pg85.otg.shared.biome;

import com.pg85.otg.biome.BiomePlan;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.config.settings.biome.BiomeStructureTagConfig;
import com.pg85.otg.config.settings.biome.BiomeTagSettings;
import com.pg85.otg.config.settings.preset.DimensionPresetSettings;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.IBiomeResourceLocation;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.biome.OTGBiomeResourceLocation;
import com.pg85.otg.util.logging.LogCategory;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.*;

/**
 * Registers biomes with the MC WritableRegistry.
 * The ONLY class in the pipeline that performs registry mutation side effects.
 */
public final class BiomeRegistrar {

    @FunctionalInterface
    public interface PlatformBiomeCreator {
        IBiome create(BiomeSettings settings, Biome biome, Holder.Reference<Biome> ref);
    }

    public record RegistrationResult(
        IBiome[] globalIdMapping,
        Map<ResourceKey<Biome>, BiomeStructureTagConfig> structureTagConfigs,
        Map<ResourceKey<Biome>, BiomeTagSettings> biomeTagConfigs,
        List<ResourceKey<Biome>> presetBiomes
    ) {}

    /**
     * Registers all biomes from a BiomePlan into the MC registry.
     */
    public static RegistrationResult register(
            BiomePlan plan,
            Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation,
            BiomeFactory biomeFactory,
            DimensionPresetSettings presetConfig,
            WritableRegistry<Biome> biomeRegistry,
            PlatformBiomeCreator platformBiomeCreator
    ) {
        IBiome[] presetIdMapping = new IBiome[plan.totalBiomeSlots()];
        List<ResourceKey<Biome>> presetBiomes = new ArrayList<>();
        Map<ResourceKey<Biome>, BiomeStructureTagConfig> structureTagConfigs = new LinkedHashMap<>();
        Map<ResourceKey<Biome>, BiomeTagSettings> biomeTagConfigs = new LinkedHashMap<>();

        Iterator<IBiomeResourceLocation> locationIterator = biomeConfigsByResourceLocation.keySet().iterator();

        for (BiomePlan.BiomeEntry entry : plan.biomeEntries()) {
            IBiomeResourceLocation iBiomeResourceLocation = locationIterator.next();
            BiomeSettings biomeSettings = entry.settings();
            int otgBiomeId = entry.otgBiomeId();

            ResourceLocation resourceLocation = ResourceLocation.parse(iBiomeResourceLocation.toResourceLocationString());
            ResourceKey<Biome> resourceKey;
            Biome biome;
            Holder.Reference<Biome> ref;

            if (biomeSettings.getIdentitySettings().isTemplateForBiome()) {
                biome = biomeRegistry.get(resourceLocation);
                if (biome == null) {
                    OTGLog.error(LogCategory.BIOME_REGISTRY,
                        "Could not find biome {} for biomeconfig {}", resourceLocation, biomeSettings.getIdentitySettings().getBiomeName());
                    continue;
                }
                Optional<ResourceKey<Biome>> key = biomeRegistry.getResourceKey(biome);
                resourceKey = key.orElse(null);
                if (resourceKey == null) {
                    OTGLog.error(LogCategory.BIOME_REGISTRY,
                        "Could not find resource key for biome {} for biomeconfig {}", resourceLocation, biomeSettings.getIdentitySettings().getBiomeName());
                    continue;
                }
                ref = biomeRegistry.getHolder(resourceKey).orElseThrow();
            } else {
                if (!(iBiomeResourceLocation instanceof OTGBiomeResourceLocation)) {
                    OTGLog.error(LogCategory.BIOME_REGISTRY,
                        "Could not process template biomeconfig {}, did you set TemplateForBiome:true in the BiomeConfig?", biomeSettings.getIdentitySettings().getBiomeName());
                    continue;
                }
                resourceKey = ResourceKey.create(Registries.BIOME, resourceLocation);
                BiomeConfig biomeConfig = (BiomeConfig) biomeSettings;
                biome = biomeFactory.createOTGBiome(presetConfig, biomeConfig);
                ref = biomeRegistry.register(resourceKey, biome, RegistrationInfo.BUILT_IN);

                if (biomeConfig.getBiomeStructureTagConfig() != null) {
                    structureTagConfigs.put(resourceKey, biomeConfig.getBiomeStructureTagConfig());
                }
                if (biomeConfig.getBiomeTagSettings() != null) {
                    biomeTagConfigs.put(resourceKey, biomeConfig.getBiomeTagSettings());
                }
            }

            presetBiomes.add(resourceKey);
            IBiome otgBiome = platformBiomeCreator.create(biomeSettings, biome, ref);
            presetIdMapping[otgBiomeId] = otgBiome;

            OTGLog.info(LogCategory.BIOME_REGISTRY,
                "Registered biome {} | {} with OTG id {}", resourceLocation, biomeSettings.getIdentitySettings().getBiomeName(), otgBiomeId);
        }

        // If no ocean biome was defined, shift array to fill ID 0
        if (plan.oceanBiomeConfig() == null) {
            System.arraycopy(presetIdMapping, 1, presetIdMapping, 0, presetIdMapping.length - 1);
        }

        return new RegistrationResult(presetIdMapping, structureTagConfigs, biomeTagConfigs, presetBiomes);
    }
}
