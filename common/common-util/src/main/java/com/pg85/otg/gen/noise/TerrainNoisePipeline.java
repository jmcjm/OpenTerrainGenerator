package com.pg85.otg.gen.noise;

import com.pg85.otg.util.helpers.MathHelper;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * Terrain-height noise pipeline: continental extra height, column center height,
 * falloff gradient and volatility-blended 3D noise. Shared by OTGChunkGenerator
 * (world gen) and the editor's BiomeHeightmapGenerator (terrain preview) so the
 * preview always matches the real generator.
 *
 * Ported from upstream PG85/OTG d789142e2 with deliberate divergences:
 * - surfaceSections is our fixed 33.5 reference (256-block-world scale), not
 *   WorldHeightScale-derived — keeps base heights stable on 384-height worlds.
 * - generateColumn keeps our quadratic anti-floating-terrain penalty.
 */
public record TerrainNoisePipeline(
        OctavePerlinNoiseSampler interpolationNoise,
        OctavePerlinNoiseSampler lowerNoise,
        OctavePerlinNoiseSampler upperNoise,
        OctavePerlinNoiseSampler depthNoise,
        int noiseSizeY,
        double surfaceSections,
        double continentalScale,
        double continentalBias,
        double baseHeightFraction,
        double biomeHeightWeight,
        double continentalHeightWeight,
        double falloffSteepness,
        double noiseAmplitude
) {
    // Vanilla Minecraft base frequency for terrain noise coordinate scaling.
    // Not normalization — controls feature size in world space.
    public static final double WORLD_GEN_CONSTANT = 684.412;
    // Column-height reference frozen at old 256-block-world scale (33.5 sections)
    // so BiomeHeight means the same thing regardless of actual world height.
    public static final double REFERENCE_SURFACE_SECTIONS = 33.5;
    private static final int INTERPOLATION_OCTAVES = 8;
    private static final int TERRAIN_OCTAVES = 16;
    private static final double LEGACY_NOISE_SCALE = 128.0D;

    public record NoiseSamplers(
            OctavePerlinNoiseSampler interpolation,
            OctavePerlinNoiseSampler lower,
            OctavePerlinNoiseSampler upper,
            OctavePerlinNoiseSampler depth
    ) {}

    /**
     * Creates all 4 noise samplers in OTG's canonical order.
     * In OTGChunkGenerator.setSeed(), biomeBlocksNoiseGen follows these 4 samplers
     * and consumes the same Random. This factory must be called before biomeBlocksNoiseGen.
     */
    public static NoiseSamplers createNoiseSamplers(Random rng) {
        var interpolation = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-7, 0));
        var lower = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        var upper = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        var depth = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        return new NoiseSamplers(interpolation, lower, upper, depth);
    }

    // --- Raw accumulation ---
    // Inverted amplitude pattern: later octaves contribute exponentially more.
    // Normalized by theoretical max (2^octaveCount - 1) and clamped to [-1, 1].
    // Output is always bounded regardless of octave count or world height.
    public double accumulateOctaves(
            OctavePerlinNoiseSampler sampler,
            int x, int y, int z,
            double horizontalScale, double verticalScale,
            int octaveCount
    ) {
        double noise = 0.0D;
        double amplitude = 1.0D;
        for (int i = 0; i < octaveCount; ++i) {
            double scaledX = OctavePerlinNoiseSampler.maintainPrecision((double) x * horizontalScale * amplitude);
            double scaledY = OctavePerlinNoiseSampler.maintainPrecision((double) y * verticalScale * amplitude);
            double scaledZ = OctavePerlinNoiseSampler.maintainPrecision((double) z * horizontalScale * amplitude);
            double scaledVerticalScale = verticalScale * amplitude;

            PerlinNoiseSampler perlinNoiseSampler = sampler.getOctave(i);
            if (perlinNoiseSampler != null) {
                noise += perlinNoiseSampler.sample(
                        scaledX, scaledY, scaledZ,
                        scaledVerticalScale,
                        (double) y * scaledVerticalScale
                ) / amplitude;
            }
            amplitude /= 2.0D;
        }
        double theoreticalMax = Math.pow(2, octaveCount) - 1;
        return MathHelper.clamp(noise / theoreticalMax, -1.0, 1.0);
    }

    // Maps normalized [-1,1] noise to [0,1] for use as volatility blend weight.
    public double interpolate(int x, int y, int z, double horizontalStretch, double verticalStretch) {
        double raw = accumulateOctaves(this.interpolationNoise, x, y, z, horizontalStretch, verticalStretch, INTERPOLATION_OCTAVES);
        return MathHelper.clamp((raw * 25.5 + 1.0) / 2.0, 0.0, 1.0); // 25.5 = 255/10, restores old range
    }

    // Selects/blends between two noise layers based on interpolation delta.
    // Noise is [-1,1], volatility controls per-biome amplitude, noiseAmplitude scales globally.
    public double selectVolatility(
            double delta,
            int x, int y, int z,
            double horizontalScale, double verticalScale,
            double volatility1, double volatility2,
            double volatilityWeight1, double volatilityWeight2
    ) {
        double result;
        if (delta < volatilityWeight1) {
            double noise = accumulateOctaves(this.lowerNoise, x, y, z, horizontalScale, verticalScale, TERRAIN_OCTAVES);
            result = noise * volatility1;
        } else if (delta > volatilityWeight2) {
            double noise = accumulateOctaves(this.upperNoise, x, y, z, horizontalScale, verticalScale, TERRAIN_OCTAVES);
            result = noise * volatility2;
        } else {
            double noise1 = accumulateOctaves(this.lowerNoise, x, y, z, horizontalScale, verticalScale, TERRAIN_OCTAVES);
            double noise2 = accumulateOctaves(this.upperNoise, x, y, z, horizontalScale, verticalScale, TERRAIN_OCTAVES);
            result = MathHelper.lerp(delta, noise1 * volatility1, noise2 * volatility2);
        }
        return result * LEGACY_NOISE_SCALE * this.noiseAmplitude;
    }

    // Continental height: large-scale vertical offset to terrain surface.
    // Bias shifts the zero-crossing to control valley/peak distribution.
    // Output range: [-valleyFactor, peakFactor], then * continentalScale in generateColumn.
    public double computeExtraHeight(int x, int z, double valleyFactor, double peakFactor) {
        // Raw multi-octave sample is typically ±0.3; *3 spreads it over ~±1 so the clamps below engage.
        double noise = this.depthNoise.sample(x * 200, 10.0D, z * 200, 1.0D, 0.0D, true) * 3.0D;
        noise += this.continentalBias;
        if (noise > 0) {
            return Math.min(noise, 1.0) * peakFactor;
        } else {
            return Math.max(noise, -1.0) * valleyFactor;
        }
    }

    // Places terrain surface as a fraction of surfaceSections.
    public double computeColumnHeight(float height, float extraHeight) {
        return this.surfaceSections * (this.baseHeightFraction + height * this.biomeHeightWeight + extraHeight * this.continentalHeightWeight);
    }

    // Density gradient: the primary signal determining solid vs air.
    // *4 when positive (below center): asymmetric — solid side steeper than air side.
    public double computeFalloff(double centerHeight, int y, double biomeVolatility) {
        double falloff = (centerHeight - y) * this.falloffSteepness / biomeVolatility;
        if (falloff > 0.0) {
            falloff *= 4.0;
        }
        return falloff;
    }

    public void generateColumn(
            double[] noiseColumn,
            int noiseX, int noiseZ,
            BlendedBiomeParams params,
            double[] chc,
            boolean disableBiomeHeight
    ) {
        float extraHeight = (float) (computeExtraHeight(noiseX, noiseZ, params.valleyFactor(), params.peakFactor()) * this.continentalScale);

        // *0.9+0.1 ensures biomeVolatility never reaches 0 (would divide by zero in falloff).
        float biomeVolatility = params.biomeVolatility() * 0.9f + 0.1f;
        double centerHeight = computeColumnHeight(params.height(), extraHeight);

        double horizontalScale = WORLD_GEN_CONSTANT * params.horizontalFracture();
        double verticalScale = WORLD_GEN_CONSTANT * params.verticalFracture();

        for (int y = 0; y <= this.noiseSizeY; ++y) {
            double falloff = computeFalloff(centerHeight, y, biomeVolatility);
            double delta = interpolate(noiseX, y, noiseZ, horizontalScale / 80, verticalScale / 160);
            double noise = selectVolatility(
                    delta, noiseX, y, noiseZ,
                    horizontalScale, verticalScale,
                    params.volatility1(), params.volatility2(),
                    params.volatilityWeight1(), params.volatilityWeight2()
            );

            if (!disableBiomeHeight) {
                noise += falloff;

                // Anti-floating-terrain: quadratic penalty above 4 sections over the column center.
                double heightDiff = y - centerHeight;
                if (heightDiff > 4) {
                    noise -= (heightDiff - 4) * (heightDiff - 4) * 0.5;
                }

                // Top-of-world fade: forces air in the top 4 noise cells.
                if (y > this.noiseSizeY - 4) {
                    noise = MathHelper.clampedLerp(noise, -10, ((double) y - (this.noiseSizeY - 4)) / 4.0);
                }
            }
            noise += chc[y];
            noiseColumn[y] = noise;
        }
    }
}
