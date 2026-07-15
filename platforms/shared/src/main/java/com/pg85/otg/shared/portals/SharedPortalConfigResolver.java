package com.pg85.otg.shared.portals;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.dimensions.WorldPresetConfig.OTGDimension;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.materials.IBlockStateMaterial;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class SharedPortalConfigResolver {

    private SharedPortalConfigResolver() {}

    public static boolean isFrameBlock(Block block, List<LocalMaterialData> frameBlocks) {
        if (frameBlocks == null || frameBlocks.isEmpty()) {
            return false;
        }
        for (LocalMaterialData material : frameBlocks) {
            if (material instanceof IBlockStateMaterial blockMaterial
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
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            // Check YAML override first
            WorldPresetConfig activeWorldPreset = WorldPresetPortalResolver.getActiveWorldPreset();
            if (activeWorldPreset != null) {
                DimensionPreset preset = gen.getPreset();
                if (preset != null) {
                    OTGDimension dimEntry = WorldPresetPortalResolver.findDimensionEntry(activeWorldPreset, preset.getFolderName());
                    if (dimEntry != null) {
                        ArrayList<LocalMaterialData> overrideBlocks = WorldPresetPortalResolver.parsePortalBlocks(dimEntry.PortalBlocks);
                        if (overrideBlocks != null && overrideBlocks.get(0) instanceof IBlockStateMaterial blockMaterial) {
                            return blockMaterial.getState();
                        }
                    }
                }
            }

            // Fall back to DimensionPreset
            List<LocalMaterialData> portalBlocks = gen.getPortalBlocks();
            if (portalBlocks != null && !portalBlocks.isEmpty()
                    && portalBlocks.get(0) instanceof IBlockStateMaterial blockMaterial) {
                return blockMaterial.getState();
            }
        }

        return PortalTargetResolver.findByColor(portalColor)
                .filter(t -> !t.frameBlocks().isEmpty()
                        && t.frameBlocks().get(0) instanceof IBlockStateMaterial)
                .map(t -> ((IBlockStateMaterial) t.frameBlocks().get(0)).getState())
                .orElse(Blocks.QUARTZ_BLOCK.defaultBlockState());
    }

    public static int getPortalMinWidth(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            return Math.max(2, gen.getPortalMinWidth());
        }
        return PortalTargetResolver.findByColor(portalColor)
                .map(t -> Math.max(2, t.minWidth()))
                .orElse(2);
    }

    public static int getPortalMinHeight(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            return Math.max(3, gen.getPortalMinHeight());
        }
        return PortalTargetResolver.findByColor(portalColor)
                .map(t -> Math.max(3, t.minHeight()))
                .orElse(3);
    }

    public static String normalizeColor(String color) {
        return DimensionNameUtils.normalizeColor(color);
    }
}
