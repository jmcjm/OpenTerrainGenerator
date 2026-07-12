package com.pg85.otg.gen.noise;

import com.pg85.otg.util.helpers.MathHelper;

import java.util.Random;
import java.util.stream.IntStream;

public class TerrainNoiseComputer {

    public static final double WORLD_GEN_CONSTANT = 684.412;
    public static final float REFERENCE_Y_SECTIONS = 33.5f;
    public static final int INTERPOLATION_OCTAVES = 8;
    public static final int TERRAIN_OCTAVES = 16;

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

    /**
     * Samples terrain noise with volatility weight blending.
     * Uses MathHelper.lerp(delta, ...) with raw delta — identical to OTGChunkGenerator.
     */
    public static double sampleNoise(
            int x, int y, int z,
            double horizontalScale, double verticalScale,
            double horizontalStretch, double verticalStretch,
            double volatility1, double volatility2,
            double volatilityWeight1, double volatilityWeight2,
            OctavePerlinNoiseSampler interpolationNoise,
            OctavePerlinNoiseSampler lowerInterpolatedNoise,
            OctavePerlinNoiseSampler upperInterpolatedNoise
    ) {
        double delta = getInterpolationNoise(interpolationNoise, x, y, z, horizontalStretch, verticalStretch);

        if (delta < volatilityWeight1) {
            return getInterpolatedNoise(lowerInterpolatedNoise, x, y, z, horizontalScale, verticalScale) / 512.0D
                   * volatility1;
        } else if (delta > volatilityWeight2) {
            return getInterpolatedNoise(upperInterpolatedNoise, x, y, z, horizontalScale, verticalScale) / 512.0D
                   * volatility2;
        } else {
            return MathHelper.lerp(
                    delta,
                    getInterpolatedNoise(lowerInterpolatedNoise, x, y, z, horizontalScale, verticalScale) / 512.0D
                    * volatility1,
                    getInterpolatedNoise(upperInterpolatedNoise, x, y, z, horizontalScale, verticalScale) / 512.0D
                    * volatility2
            );
        }
    }

    /**
     * 8-octave interpolation noise, normalized to [0, 1].
     */
    public static double getInterpolationNoise(
            OctavePerlinNoiseSampler sampler,
            int x, int y, int z,
            double horizontalStretch, double verticalStretch
    ) {
        double interpolation = 0.0D;
        double amplitude = 1.0D;
        PerlinNoiseSampler octave;
        for (int i = 0; i < INTERPOLATION_OCTAVES; i++) {
            octave = sampler.getOctave(i);
            if (octave != null) {
                interpolation += octave.sample(
                        OctavePerlinNoiseSampler.maintainPrecision((double) x * horizontalStretch * amplitude),
                        OctavePerlinNoiseSampler.maintainPrecision((double) y * verticalStretch * amplitude),
                        OctavePerlinNoiseSampler.maintainPrecision((double) z * horizontalStretch * amplitude),
                        verticalStretch * amplitude,
                        (double) y * verticalStretch * amplitude
                ) / amplitude;
            }
            amplitude /= 2.0D;
        }
        return (interpolation / 10.0D + 1.0D) / 2.0D;
    }

    /**
     * 16-octave terrain shape noise.
     */
    public static double getInterpolatedNoise(
            OctavePerlinNoiseSampler sampler,
            int x, int y, int z,
            double horizontalScale, double verticalScale
    ) {
        double noise = 0.0D;
        double amplitude = 1.0D;
        PerlinNoiseSampler octave;
        for (int i = 0; i < TERRAIN_OCTAVES; ++i) {
            double scaledX = OctavePerlinNoiseSampler.maintainPrecision((double) x * horizontalScale * amplitude);
            double scaledY = OctavePerlinNoiseSampler.maintainPrecision((double) y * verticalScale * amplitude);
            double scaledZ = OctavePerlinNoiseSampler.maintainPrecision((double) z * horizontalScale * amplitude);
            double scaledVerticalScale = verticalScale * amplitude;

            octave = sampler.getOctave(i);
            if (octave != null) {
                noise += octave.sample(
                        scaledX, scaledY, scaledZ,
                        scaledVerticalScale,
                        (double) y * scaledVerticalScale
                ) / amplitude;
            }
            amplitude /= 2.0D;
        }
        return noise;
    }

    /**
     * Depth noise variation — terrain height variation independent of Fracture.
     * Samples at x*200, 10, z*200.
     */
    public static double getExtraHeightAt(
            OctavePerlinNoiseSampler depthNoise,
            int x, int z,
            double maxAverageDepth, double maxAverageHeight
    ) {
        double noiseHeight = depthNoise.sample(x * 200, 10.0D, z * 200, 1.0D, 0.0D, true) * 65535.0 / 8000.0;

        if (noiseHeight < 0.0D) {
            noiseHeight = -noiseHeight * 0.3D;
        }
        noiseHeight = noiseHeight * 3.0D - 2.0D;

        if (noiseHeight < 0.0D) {
            noiseHeight /= 2.0D;
            if (noiseHeight < -1.0D) {
                noiseHeight = -1.0D;
            }
            noiseHeight -= maxAverageDepth;
            noiseHeight /= 1.4D;
            noiseHeight /= 2.0D;
        } else {
            if (noiseHeight > 1.0D) {
                noiseHeight = 1.0D;
            }
            noiseHeight += maxAverageHeight;
            noiseHeight /= 8.0D;
        }

        return noiseHeight;
    }
}
