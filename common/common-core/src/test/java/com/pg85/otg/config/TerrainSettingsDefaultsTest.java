package com.pg85.otg.config;

import com.pg85.otg.config.io.SimpleSettingsMap;
import com.pg85.otg.config.settings.preset.TerrainSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainSettingsDefaultsTest {

    @Test
    void continentalDefaultsMatchUpstream() {
        SimpleSettingsMap empty = new SimpleSettingsMap("test", Path.of("test.ini"));
        TerrainSettings t = TerrainSettings.getTerrainSettings(empty);
        assertEquals(0.2, t.getContinentalScale(), 1e-9);
        assertEquals(-0.05, t.getContinentalBias(), 1e-9);
        assertEquals(0.46875, t.getBaseHeightFraction(), 1e-9);
        assertEquals(0.125, t.getBiomeHeightWeight(), 1e-9);
        assertEquals(0.25, t.getContinentalHeightWeight(), 1e-9);
        assertEquals(6.0, t.getFalloffSteepness(), 1e-9);
        assertEquals(1.0, t.getNoiseAmplitude(), 1e-9);
    }
}
