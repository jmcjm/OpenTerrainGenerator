package com.pg85.otg.shared.portals;

import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.materials.IBlockStateMaterial;
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
        // The frame of an auto-created portal matches the preset/dimension the color
        // belongs to, so a portal leading to Biome Bundle is built from Biome Bundle's
        // PortalBlocks regardless of which level it stands in. PortalTarget frame blocks
        // already carry the YAML overrides (R1).
        Optional<BlockState> colorFrame = PortalTargetResolver.findByColor(portalColor)
                .filter(t -> !t.frameBlocks().isEmpty()
                        && t.frameBlocks().get(0) instanceof IBlockStateMaterial)
                .map(t -> ((IBlockStateMaterial) t.frameBlocks().get(0)).getState());
        if (colorFrame.isPresent()) {
            return colorFrame.get();
        }

        // Fall back to the level's own preset blocks, then quartz.
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            List<LocalMaterialData> portalBlocks = gen.getPortalBlocks();
            if (portalBlocks != null && !portalBlocks.isEmpty()
                    && portalBlocks.get(0) instanceof IBlockStateMaterial blockMaterial) {
                return blockMaterial.getState();
            }
        }

        return Blocks.QUARTZ_BLOCK.defaultBlockState();
    }

    public static int getPortalMinWidth(ServerLevel level, String portalColor) {
        return PortalTargetResolver.findByColor(portalColor)
                .map(t -> Math.max(2, t.minWidth()))
                .orElseGet(() -> level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen
                        ? Math.max(2, gen.getPortalMinWidth()) : 2);
    }

    public static int getPortalMinHeight(ServerLevel level, String portalColor) {
        return PortalTargetResolver.findByColor(portalColor)
                .map(t -> Math.max(3, t.minHeight()))
                .orElseGet(() -> level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen
                        ? Math.max(3, gen.getPortalMinHeight()) : 3);
    }

    public static String normalizeColor(String color) {
        return DimensionNameUtils.normalizeColor(color);
    }
}
