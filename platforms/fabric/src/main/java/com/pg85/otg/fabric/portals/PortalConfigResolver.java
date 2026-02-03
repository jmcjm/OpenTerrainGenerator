package com.pg85.otg.fabric.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.materials.FabricMaterialData;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Centralized utility for portal configuration lookup.
 * Resolves portal settings from either the dimension's chunk generator or preset by color.
 */
public final class PortalConfigResolver {

    private PortalConfigResolver() {} // utility class

    /**
     * Find a preset by its configured portal color.
     * @param portalColor The color to search for (case-insensitive)
     * @return Optional containing the matching preset, or empty if not found
     */
    public static Optional<Preset> findPresetByColor(String portalColor) {
        String targetColor = normalizeColor(portalColor);
        return OTG.getEngine().getPresetLoader().getAllPresets().stream()
                .filter(p -> p.getPresetConfig() != null)
                .filter(p -> p.getPresetConfig().getPortalSettings() != null)
                .filter(p -> {
                    PortalSettings settings = p.getPresetConfig().getPortalSettings();
                    return settings.getPortalBlocks() != null && !settings.getPortalBlocks().isEmpty();
                })
                .filter(p -> targetColor.equals(normalizeColor(
                        p.getPresetConfig().getPortalSettings().getPortalColor())))
                .findFirst();
    }

    /**
     * Get PortalSettings for a color, searching all presets.
     * @param portalColor The color to search for
     * @return Optional containing the matching settings, or empty if not found
     */
    public static Optional<PortalSettings> findSettingsByColor(String portalColor) {
        return findPresetByColor(portalColor)
                .map(p -> p.getPresetConfig().getPortalSettings());
    }

    /**
     * Check if a block is a valid frame block for the given frame materials.
     * @param block The block to check
     * @param frameBlocks List of valid frame materials
     * @return true if the block matches any frame material
     */
    public static boolean isFrameBlock(Block block, List<LocalMaterialData> frameBlocks) {
        if (frameBlocks == null || frameBlocks.isEmpty()) {
            return false;
        }
        for (LocalMaterialData material : frameBlocks) {
            if (((FabricMaterialData) material).getState().getBlock() == block) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a BlockState is a valid frame block.
     * Convenience overload for code that has BlockState instead of Block.
     */
    public static boolean isFrameBlock(BlockState state, List<LocalMaterialData> frameBlocks) {
        return isFrameBlock(state.getBlock(), frameBlocks);
    }

    /**
     * Get frame block for a dimension. Tries generator first, falls back to preset by color.
     * @param level The server level (may be OTG dimension)
     * @param portalColor Fallback color to search presets
     * @return The frame block state, defaults to QUARTZ_BLOCK
     */
    public static BlockState getFrameBlock(ServerLevel level, String portalColor) {
        // First try dimension's chunk generator
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            List<LocalMaterialData> portalBlocks = gen.getPortalBlocks();
            if (portalBlocks != null && !portalBlocks.isEmpty()) {
                return ((FabricMaterialData) portalBlocks.get(0)).getState();
            }
        }

        // Fall back to preset by color
        return findSettingsByColor(portalColor)
                .filter(s -> s.getPortalBlocks() != null && !s.getPortalBlocks().isEmpty())
                .map(s -> ((FabricMaterialData) s.getPortalBlocks().get(0)).getState())
                .orElse(Blocks.QUARTZ_BLOCK.defaultBlockState());
    }

    /**
     * Get portal minimum width. Tries generator first, falls back to preset by color.
     */
    public static int getPortalMinWidth(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            return Math.max(2, gen.getPortalMinWidth());
        }
        return findSettingsByColor(portalColor)
                .map(s -> Math.max(2, s.getPortalMinWidth()))
                .orElse(2);
    }

    /**
     * Get portal minimum height. Tries generator first, falls back to preset by color.
     */
    public static int getPortalMinHeight(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            return Math.max(3, gen.getPortalMinHeight());
        }
        return findSettingsByColor(portalColor)
                .map(s -> Math.max(3, s.getPortalMinHeight()))
                .orElse(3);
    }

    /**
     * Normalize portal color string for comparison.
     */
    public static String normalizeColor(String color) {
        return color == null ? "default" : color.toLowerCase().trim();
    }
}
