package com.pg85.otg.fabric.portals;

import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.materials.FabricMaterialData;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.shared.portals.PortalConfigLookup;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Fabric-specific portal configuration resolver.
 * Delegates platform-agnostic logic to PortalConfigLookup,
 * handles Fabric-specific material conversion.
 */
public final class PortalConfigResolver {

    private PortalConfigResolver() {} // utility class

    /**
     * Find a preset by its configured portal color.
     * Delegates to shared PortalConfigLookup.
     */
    public static Optional<Preset> findPresetByColor(String portalColor) {
        return PortalConfigLookup.findPresetByColor(portalColor);
    }

    /**
     * Get PortalSettings for a color.
     * Delegates to shared PortalConfigLookup.
     */
    public static Optional<PortalSettings> findSettingsByColor(String portalColor) {
        return PortalConfigLookup.findSettingsByColor(portalColor);
    }

    /**
     * Check if a block is a valid frame block for the given frame materials.
     * Fabric-specific: converts LocalMaterialData to Fabric BlockState.
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
     */
    public static boolean isFrameBlock(BlockState state, List<LocalMaterialData> frameBlocks) {
        return isFrameBlock(state.getBlock(), frameBlocks);
    }

    /**
     * Get frame block for a dimension. Tries generator first, falls back to preset by color.
     * Fabric-specific: returns Fabric BlockState.
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
                .map(PortalConfigLookup::getPortalMinWidth)
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
                .map(PortalConfigLookup::getPortalMinHeight)
                .orElse(3);
    }

    /**
     * Normalize portal color string for comparison.
     * Delegates to shared utility.
     */
    public static String normalizeColor(String color) {
        return DimensionNameUtils.normalizeColor(color);
    }
}
