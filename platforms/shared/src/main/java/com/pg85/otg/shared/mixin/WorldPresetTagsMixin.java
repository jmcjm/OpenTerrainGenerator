package com.pg85.otg.shared.mixin;

import com.pg85.otg.config.settings.biome.BiomeStructureTagConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.biome.SharedDimensionPresetBiomeLoader;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.biome.StructureTagMapper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(ReloadableServerResources.class)
public class WorldPresetTagsMixin {
    @Inject(method = "updateRegistryTags()V", at = @At("RETURN"))
    private void addOurOwnTags(CallbackInfo ci) {
        ReloadableServerResources self = (ReloadableServerResources) (Object) this;
        RegistryAccess registryAccess = self.fullRegistries().get();
        OTGLog.info("Adding OTG presets to the world preset tags");
        var presets = registryAccess.registryOrThrow(Registries.WORLD_PRESET);
        var tags = presets.getTags();
        HashMap<TagKey<WorldPreset>, List<Holder<WorldPreset>>> collected = tags.collect(
                HashMap::new,
                (tagMap, pair) -> tagMap.put(pair.getFirst(), new ArrayList<>(pair.getSecond().stream().toList())),
                HashMap::putAll);
        presets.keySet().forEach(id -> {
            if (!id.getNamespace().equalsIgnoreCase(Constants.MOD_ID_SHORT)) {
                return;
            }
            Holder.Reference<WorldPreset> holder = getAsReference(presets, id).orElse(null);
            if (holder == null) {
                OTGLog.error("Preset {} does not exist!", id.toString());
                return;
            }
            OTGLog.info("Adding preset {} to the tags", id.toString());
            collected.computeIfAbsent(WorldPresetTags.NORMAL, tag -> new ArrayList<>())
                    .add(holder);
        });

        presets.bindTags(collected);

        addBiomesToStructureTags(registryAccess);
    }

    private void addBiomesToStructureTags(RegistryAccess registryAccess) {
        Map<ResourceKey<Biome>, BiomeStructureTagConfig> structureConfigs =
                SharedDimensionPresetBiomeLoader.getStructureTagConfigs();

        if (structureConfigs.isEmpty()) return;

        var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        var existingTags = biomeRegistry.getTags();
        HashMap<TagKey<Biome>, List<Holder<Biome>>> biomeTagMap = existingTags.collect(
                HashMap::new,
                (tagMap, pair) -> tagMap.put(pair.getFirst(), new ArrayList<>(pair.getSecond().stream().toList())),
                HashMap::putAll
        );

        int addedCount = 0;
        for (var entry : structureConfigs.entrySet()) {
            ResourceKey<Biome> biomeKey = entry.getKey();
            BiomeStructureTagConfig config = entry.getValue();

            Optional<Holder.Reference<Biome>> holderOpt = biomeRegistry.getHolder(biomeKey);
            if (holderOpt.isEmpty()) {
                OTGLog.warn("Could not find holder for biome {} when injecting structure tags", biomeKey.location());
                continue;
            }
            Holder.Reference<Biome> holder = holderOpt.get();

            for (String tagPath : StructureTagMapper.getStructureTags(config)) {
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, ResourceLocation.parse(tagPath));
                biomeTagMap.computeIfAbsent(tagKey, k -> new ArrayList<>()).add(holder);
                addedCount++;
            }
        }

        biomeRegistry.bindTags(biomeTagMap);
        OTGLog.info("Injected {} structure tag entries for {} OTG biomes",
                addedCount, structureConfigs.size());
    }

    private static <T> Optional<Holder.Reference<T>> getAsReference(Registry<T> registry, ResourceLocation key) {
        return registry.getOptional(key)
                .flatMap(registry::getResourceKey)
                .flatMap(registry::getHolder);
    }
}
