package com.pg85.otg.client.preview.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * Builds transient {@link Biome} instances (wrapped via {@link Holder#direct}) for the editor
 * previews. Without a biome, {@link PreviewWorld#getBlockTint} returns {@code -1} (white), so
 * tinted blocks (grass, leaves, water, vines) render white. These biomes are never registered —
 * only their colour and climate matter, which is all {@link net.minecraft.world.level.ColorResolver}
 * tinting reads.
 */
public final class PreviewBiomes {

    private PreviewBiomes() {}

    /** Neutral temperate biome (climate-derived green foliage/grass, blue water) for BO previews, which carry no biome. */
    public static Holder<Biome> defaultForest() {
        return build(0.7f, 0.8f, 0xC0D8FF, 0x050533, 0x3F76E4, 0x7BA5FF, null, null, null);
    }

    /**
     * @param foliageOverride explicit foliage colour, or {@code null} to use the climate-derived (vanilla) colour
     * @param grassOverride   explicit grass colour, or {@code null} to use the climate-derived (vanilla) colour
     * @param grassModifier   swamp/dark-forest grass tint, or {@code null} for none
     */
    public static Holder<Biome> build(float temperature, float downfall,
                                      int fogColor, int waterFogColor, int waterColor, int skyColor,
                                      Integer foliageOverride, Integer grassOverride,
                                      BiomeSpecialEffects.GrassColorModifier grassModifier) {
        BiomeSpecialEffects.Builder fx = new BiomeSpecialEffects.Builder()
                .fogColor(fogColor)
                .waterFogColor(waterFogColor)
                .waterColor(waterColor)
                .skyColor(skyColor);
        if (foliageOverride != null) fx.foliageColorOverride(foliageOverride);
        if (grassOverride != null) fx.grassColorOverride(grassOverride);
        if (grassModifier != null) fx.grassColorModifier(grassModifier);

        Biome biome = new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(temperature)
                .downfall(downfall)
                .specialEffects(fx.build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        return Holder.direct(biome);
    }
}
