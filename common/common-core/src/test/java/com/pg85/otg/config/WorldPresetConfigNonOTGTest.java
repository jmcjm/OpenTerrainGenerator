package com.pg85.otg.config;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import com.pg85.otg.util.DimensionNameUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldPresetConfigNonOTGTest {

    @Test
    void nonOTGCustomDimensionParses() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Dimensions:
            - DimensionName: "bop_world"
              NonOTGWorldType: "biomesoplenty:biomesoplenty"
              PortalColor: "purple"
              PortalBlocks: "minecraft:amethyst_block"
              GameRules:
                KeepInventory: true
            """);
        assertNotNull(config);
        assertEquals(1, config.Dimensions.size());
        WorldPresetConfig.OTGDimension dim = config.Dimensions.get(0);
        assertTrue(dim.isNonOTG());
        assertFalse(dim.hasPreset());
        assertEquals("bop_world", dim.DimensionName);
        assertEquals("biomesoplenty:biomesoplenty", dim.NonOTGWorldType);
        assertEquals(Boolean.TRUE, dim.GameRules.KeepInventory);
    }

    @Test
    void netherSlotAcceptsNonOTGWorldType() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Nether:
              NonOTGWorldType: "minecraft:flat"
            """);
        assertNotNull(config);
        assertTrue(config.Nether.isNonOTG());
        assertFalse(config.Nether.hasPreset());
    }

    @Test
    void overworldNonOTGStillParses() {
        // Backward compat: NonOTGWorldType on the Overworld block keeps working
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              NonOTGWorldType: "minecraft:amplified"
            """);
        assertNotNull(config);
        assertTrue(config.Overworld.isNonOTG());
        assertEquals("minecraft:amplified", config.Overworld.NonOTGWorldType);
    }

    @Test
    void entryWithBothSourcesIsPruned() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Dimensions:
            - DimensionName: "broken"
              PresetFolderName: "SomePreset"
              NonOTGWorldType: "minecraft:amplified"
            """);
        assertNotNull(config);
        assertTrue(config.Dimensions.isEmpty());
    }

    @Test
    void nonOTGEntryWithoutDimensionNameIsPruned() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Dimensions:
            - NonOTGWorldType: "minecraft:amplified"
            """);
        assertNotNull(config);
        assertTrue(config.Dimensions.isEmpty());
    }

    @Test
    void netherSlotWithBothSourcesFallsBackToVanilla() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Nether:
              PresetFolderName: "SomePreset"
              NonOTGWorldType: "minecraft:flat"
            """);
        assertNotNull(config);
        // both sources cleared -> registrar treats it as "no source" -> vanilla fallback
        assertFalse(config.Nether.isNonOTG());
        assertFalse(config.Nether.hasPreset());
    }

    @Test
    void cloneCopiesNewFields() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Dimensions:
            - DimensionName: "bop_world"
              NonOTGWorldType: "biomesoplenty:biomesoplenty"
              NonOTGGeneratorSettings: "{}"
            """);
        assertNotNull(config);
        WorldPresetConfig clone = config.clone();
        WorldPresetConfig.OTGDimension dim = clone.Dimensions.get(0);
        assertEquals("bop_world", dim.DimensionName);
        assertEquals("biomesoplenty:biomesoplenty", dim.NonOTGWorldType);
        assertEquals("{}", dim.NonOTGGeneratorSettings);
    }

    @Test
    void validEntriesSurvivePruning() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Biome Bundle"
            Dimensions:
            - PresetFolderName: "Wildlands"
              Seed: 420
            - DimensionName: "amp"
              NonOTGWorldType: "minecraft:amplified"
            """);
        assertNotNull(config);
        assertEquals(2, config.Dimensions.size());
    }

    @Test
    void normalizeNameSanitizesToValidRegistryChars() {
        assertEquals("bop_world_", DimensionNameUtils.normalizeName("BoP World!"));
        assertEquals("my_dim", DimensionNameUtils.normalizeName("my/dim"));
    }

    @Test
    void dimensionsEntryWithNeitherSourceIsPruned() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Dimensions:
            - Seed: 5
            """);
        assertNotNull(config);
        assertTrue(config.Dimensions.isEmpty());
    }

    @Test
    void dimensionNameOnSlotIsCleared() {
        WorldPresetConfig config = WorldPresetConfigLoader.fromYamlString("""
            DisplayName: "Test"
            Overworld:
              PresetFolderName: "Default"
            Nether:
              PresetFolderName: "X"
              DimensionName: "foo"
            """);
        assertNotNull(config);
        assertNull(config.Nether.DimensionName);
    }
}
