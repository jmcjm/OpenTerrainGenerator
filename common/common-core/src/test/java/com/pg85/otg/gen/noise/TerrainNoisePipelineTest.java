package com.pg85.otg.gen.noise;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TerrainNoisePipelineTest {

    private static TerrainNoisePipeline defaultPipeline() {
        var s = TerrainNoisePipeline.createNoiseSamplers(new Random(12345L));
        return new TerrainNoisePipeline(
                s.interpolation(), s.lower(), s.upper(), s.depth(),
                48,                                            // noiseSizeY (384/8)
                TerrainNoisePipeline.REFERENCE_SURFACE_SECTIONS,
                0.2, -0.05,                                    // continentalScale, continentalBias
                0.46875, 0.125, 0.25,                          // baseHeightFraction, biomeHeightWeight, continentalHeightWeight
                6.0, 1.0                                       // falloffSteepness, noiseAmplitude
        );
    }

    @Test
    void columnHeightMatchesLegacyFormula() {
        TerrainNoisePipeline p = defaultPipeline();
        // Legacy: 33.5 * (2 + (h*4-1)/8) / 4  ==  33.5 * (0.46875 + 0.125*h)
        for (float h : new float[]{-1.0f, 0.0f, 0.1f, 0.5f, 1.0f, 2.0f}) {
            float legacyTransformed = (h * 4.0f - 1.0f) / 8.0f;
            double legacy = 33.5f * (2.0f + legacyTransformed) / 4.0f;
            // computeColumnHeight takes the RAW BiomeHeight (h), not the transformed value
            assertEquals(legacy, p.computeColumnHeight(h, 0.0f), 1e-3, "h=" + h);
        }
    }

    @Test
    void falloffAsymmetric() {
        TerrainNoisePipeline p = defaultPipeline();
        // Below center (y < centerHeight): positive, *4
        assertEquals((10.0 - 5) * 6.0 / 0.55 * 4.0, p.computeFalloff(10.0, 5, 0.55), 1e-9);
        // Above center: negative, no *4
        assertEquals((10.0 - 15) * 6.0 / 0.55, p.computeFalloff(10.0, 15, 0.55), 1e-9);
    }

    @Test
    void extraHeightClampsAndScalesByFactors() {
        var s = TerrainNoisePipeline.createNoiseSamplers(new Random(1L));
        // bias +10 forces the peak branch and saturates the clamp: result == peakFactor exactly
        var peakForced = new TerrainNoisePipeline(s.interpolation(), s.lower(), s.upper(), s.depth(),
                48, 33.5, 0.2, 10.0, 0.46875, 0.125, 0.25, 6.0, 1.0);
        assertEquals(2.5, peakForced.computeExtraHeight(7, -3, 9.9, 2.5), 1e-9);
        // bias -10 forces the valley branch: result == -valleyFactor exactly
        var valleyForced = new TerrainNoisePipeline(s.interpolation(), s.lower(), s.upper(), s.depth(),
                48, 33.5, 0.2, -10.0, 0.46875, 0.125, 0.25, 6.0, 1.0);
        assertEquals(-1.5, valleyForced.computeExtraHeight(7, -3, 1.5, 9.9), 1e-9);
    }

    @Test
    void accumulateOctavesIsBounded() {
        TerrainNoisePipeline p = defaultPipeline();
        for (int i = 0; i < 50; i++) {
            double v = p.accumulateOctaves(p.lowerNoise(), i * 17, i % 12, -i * 31, 684.412, 684.412, 16);
            assertTrue(v >= -1.0 && v <= 1.0, "unbounded at i=" + i + ": " + v);
        }
    }

    @Test
    void zeroAmplitudeColumnIsPureFalloffShape() {
        var s = TerrainNoisePipeline.createNoiseSamplers(new Random(7L));
        var p = new TerrainNoisePipeline(s.interpolation(), s.lower(), s.upper(), s.depth(),
                48, 33.5, 0.2, -0.05, 0.46875, 0.125, 0.25, 6.0, 0.0); // continentalScale=0, noiseAmplitude=0
        BlendedBiomeParams params = new BlendedBiomeParams(
                0.1f, 0.3f, 1.0, 1.0, 1.0, 1.0, 0.45, 0.5, 1.0, 1.0);
        double[] column = new double[49];
        p.generateColumn(column, 0, 0, params, new double[49], false);
        // Solid (positive) at bottom, air (negative) at top
        assertTrue(column[0] > 0, "bottom should be solid, was " + column[0]);
        assertTrue(column[48] < 0, "top should be air, was " + column[48]);
        // disableBiomeHeight=true with zero noise and zero CHC => all zeros
        double[] flat = new double[49];
        p.generateColumn(flat, 0, 0, params, new double[49], true);
        for (double v : flat) assertEquals(0.0, v, 1e-9);
    }
}
