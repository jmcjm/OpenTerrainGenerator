package com.pg85.otg.config;

import com.pg85.otg.config.io.RawSettingValue;
import com.pg85.otg.config.io.RawSettingValue.ValueType;
import com.pg85.otg.config.io.SimpleSettingsMap;
import com.pg85.otg.config.settings.biome.BiomeTerrainSettings;
import com.pg85.otg.config.settings.preset.TerrainSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeakValleyMigrationTest {

    private static SimpleSettingsMap mapOf(String... rawLines) {
        SimpleSettingsMap map = new SimpleSettingsMap("test", Path.of("test.bc"));
        for (String line : rawLines) {
            map.addRawSetting(RawSettingValue.create(ValueType.PLAIN_SETTING, line));
        }
        return map;
    }

    private static BiomeTerrainSettings read(SimpleSettingsMap map) {
        // Mirror production order: rename legacy keys, then parse
        map.renameOldSetting("MaxAverageHeight", BiomeTerrainSettings.PEAK_FACTOR);
        map.renameOldSetting("MaxAverageDepth", BiomeTerrainSettings.VALLEY_FACTOR);
        TerrainSettings parent = TerrainSettings.getTerrainSettings(
                new SimpleSettingsMap("preset", Path.of("p.ini")));
        return BiomeTerrainSettings.getBiomeTerrainSettings(map, parent, 384);
    }

    @Test
    void v1FileWithMaxAverageGetsRenamedAndShifted() {
        // No ConfigVersion line => version 1
        BiomeTerrainSettings t = read(mapOf("MaxAverageHeight: 2.0", "MaxAverageDepth: -0.5"));
        assertEquals(3.0, t.getPeakFactor(), 1e-9);   // 2.0 + 1
        assertEquals(0.5, t.getValleyFactor(), 1e-9); // -0.5 + 1
    }

    @Test
    void v2FileWithMaxAverageZeroBecomesNeutral() {
        BiomeTerrainSettings t = read(mapOf("ConfigVersion: 2", "MaxAverageHeight: 0.0", "MaxAverageDepth: 0.0"));
        assertEquals(1.0, t.getPeakFactor(), 1e-9);   // 0.0 + 1 => neutral multiplier
        assertEquals(1.0, t.getValleyFactor(), 1e-9);
    }

    @Test
    void v3FileReadsPeakValleyVerbatim() {
        BiomeTerrainSettings t = read(mapOf("ConfigVersion: 3", "PeakFactor: 2.5", "ValleyFactor: 0.25"));
        assertEquals(2.5, t.getPeakFactor(), 1e-9);
        assertEquals(0.25, t.getValleyFactor(), 1e-9);
    }

    @Test
    void absentSettingsGetDefaultOne() {
        BiomeTerrainSettings t = read(mapOf("ConfigVersion: 3"));
        assertEquals(1.0, t.getPeakFactor(), 1e-9);
        assertEquals(1.0, t.getValleyFactor(), 1e-9);
    }
}
