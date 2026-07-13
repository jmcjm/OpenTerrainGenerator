package com.pg85.otg.shared.portals;

import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.materials.SharedMaterialData;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public final class SharedPortalConfigResolver {

    private SharedPortalConfigResolver() {}

    public static Optional<Preset> findPresetByColor(String portalColor) {
        return PortalConfigLookup.findPresetByColor(portalColor);
    }

    public static Optional<PortalSettings> findSettingsByColor(String portalColor) {
        return PortalConfigLookup.findSettingsByColor(portalColor);
    }

    public static boolean isFrameBlock(Block block, List<LocalMaterialData> frameBlocks) {
        if (frameBlocks == null || frameBlocks.isEmpty()) {
            return false;
        }
        for (LocalMaterialData material : frameBlocks) {
            if (material instanceof SharedMaterialData blockMaterial
                    && blockMaterial.getState().getBlock() == block) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFrameBlock(BlockState state, List<LocalMaterialData> frameBlocks) {
        return isFrameBlock(state.getBlock(), frameBlocks);
    }

    public static BlockState getFrameBlock(ServerLevel level, String portalColor) {
        // The frame of an auto-created portal matches the preset the color belongs to,
        // so a portal leading to Biome Bundle is built from Biome Bundle's PortalBlocks
        // regardless of which level it stands in.
        Optional<BlockState> colorFrame = findSettingsByColor(portalColor)
                .filter(s -> s.getPortalBlocks() != null && !s.getPortalBlocks().isEmpty()
                        && s.getPortalBlocks().get(0) instanceof SharedMaterialData)
                .map(s -> ((SharedMaterialData) s.getPortalBlocks().get(0)).getState());
        if (colorFrame.isPresent()) {
            return colorFrame.get();
        }

        PortalSettings settings = getLevelPortalSettings(level);
        if (settings != null && settings.getPortalBlocks() != null && !settings.getPortalBlocks().isEmpty()
                && settings.getPortalBlocks().get(0) instanceof SharedMaterialData blockMaterial) {
            return blockMaterial.getState();
        }

        return findSettingsByColor(portalColor)
                .filter(s -> s.getPortalBlocks() != null && !s.getPortalBlocks().isEmpty()
                        && s.getPortalBlocks().get(0) instanceof SharedMaterialData)
                .map(s -> ((SharedMaterialData) s.getPortalBlocks().get(0)).getState())
                .orElse(Blocks.QUARTZ_BLOCK.defaultBlockState());
    }

    public static int getPortalMinWidth(ServerLevel level, String portalColor) {
        return findSettingsByColor(portalColor)
                .map(PortalConfigLookup::getPortalMinWidth)
                .orElseGet(() -> {
                    PortalSettings settings = getLevelPortalSettings(level);
                    return settings != null ? Math.max(2, settings.getPortalMinWidth()) : 2;
                });
    }

    public static int getPortalMinHeight(ServerLevel level, String portalColor) {
        return findSettingsByColor(portalColor)
                .map(PortalConfigLookup::getPortalMinHeight)
                .orElseGet(() -> {
                    PortalSettings settings = getLevelPortalSettings(level);
                    return settings != null ? Math.max(3, settings.getPortalMinHeight()) : 3;
                });
    }

    /** Portal settings of the preset generating this level, or null for non-OTG levels. */
    private static PortalSettings getLevelPortalSettings(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen
                && gen.getPreset() != null && gen.getPreset().getPresetConfig() != null) {
            return gen.getPreset().getPresetConfig().getPortalSettings();
        }
        return null;
    }

    public static String normalizeColor(String color) {
        return DimensionNameUtils.normalizeColor(color);
    }
}
