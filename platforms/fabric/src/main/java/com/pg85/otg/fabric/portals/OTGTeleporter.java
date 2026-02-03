package com.pg85.otg.fabric.portals;

import com.pg85.otg.fabric.dimensions.FabricDimensionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

public class OTGTeleporter {

    public static void teleport(Entity entity, ServerLevel destination, String portalColor) {
        BlockPos destPos = findOrCreatePortal(entity, destination, portalColor);
        if (destPos != null) {
            Vec3 teleportPos = findTeleportPosition(destination, destPos);

            if (entity instanceof ServerPlayer player) {
                player.teleportTo(destination, teleportPos.x, teleportPos.y, teleportPos.z,
                        player.getYRot(), player.getXRot());
            } else {
                entity.teleportTo(destination, teleportPos.x, teleportPos.y, teleportPos.z,
                        Set.of(), entity.getYRot(), entity.getXRot());
            }
        }
    }

    private static BlockPos findOrCreatePortal(Entity entity, ServerLevel destination, String portalColor) {
        WorldBorder border = destination.getWorldBorder();

        // Use coordinate scaling 1:1 for all dimensions
        double sourceScale = entity.level().dimensionType().coordinateScale();
        double destScale = destination.dimensionType().coordinateScale();
        double scale = sourceScale / destScale;

        BlockPos sourcePos = entity.blockPosition();
        int destX = Mth.clamp((int)(sourcePos.getX() * scale),
                (int)border.getMinX() + 16, (int)border.getMaxX() - 16);
        int destZ = Mth.clamp((int)(sourcePos.getZ() * scale),
                (int)border.getMinZ() + 16, (int)border.getMaxZ() - 16);
        BlockPos searchPos = new BlockPos(destX, sourcePos.getY(), destZ);

        // Try to find existing portal
        Optional<BlockPos> existingPortal = findExistingPortal(destination, searchPos, portalColor);
        if (existingPortal.isPresent()) {
            return existingPortal.get();
        }

        // Create new portal
        return createPortal(destination, searchPos, portalColor);
    }

    private static Optional<BlockPos> findExistingPortal(ServerLevel level, BlockPos searchPos, String portalColor) {
        OTGPortalBlock targetBlock = FabricPortalBlocks.getPortalBlock(portalColor);
        if (targetBlock == null) return Optional.empty();

        // Search in expanding squares from search position
        int searchRadius = 128;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // Search in expanding rings for closer portals first
        for (int radius = 0; radius <= searchRadius; radius += 8) {
            // Search the perimeter at this radius
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    // Only check perimeter points (skip inner area already checked)
                    if (radius > 0 && Math.abs(dx) < radius && Math.abs(dz) < radius) {
                        continue;
                    }

                    int x = searchPos.getX() + dx;
                    int z = searchPos.getZ() + dz;

                    // Scan entire Y column at this X/Z
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        mutable.set(x, y, z);
                        if (level.getBlockState(mutable).getBlock() == targetBlock) {
                            // Found portal, find bottom
                            while (level.getBlockState(mutable.below()).getBlock() == targetBlock) {
                                mutable.move(Direction.DOWN);
                            }
                            return Optional.of(mutable.immutable());
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static BlockPos createPortal(ServerLevel level, BlockPos pos, String portalColor) {
        OTGPortalBlock portalBlock = FabricPortalBlocks.getPortalBlock(portalColor);
        if (portalBlock == null) return null;

        // Use centralized resolver instead of local methods
        BlockState frameBlock = PortalConfigResolver.getFrameBlock(level, portalColor);
        BlockPos portalPos = findSuitableLocation(level, pos);

        Direction.Axis axis = Direction.Axis.X;
        Direction facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);

        // Use centralized resolver for dimensions
        int width = PortalConfigResolver.getPortalMinWidth(level, portalColor);
        int height = PortalConfigResolver.getPortalMinHeight(level, portalColor);

        // Bottom frame
        for (int i = -1; i <= width; i++) {
            level.setBlockAndUpdate(portalPos.relative(facing, i), frameBlock);
        }

        // Top frame
        for (int i = -1; i <= width; i++) {
            level.setBlockAndUpdate(portalPos.relative(facing, i).above(height), frameBlock);
        }

        // Side frames
        for (int h = 0; h <= height; h++) {
            level.setBlockAndUpdate(portalPos.relative(facing, -1).above(h), frameBlock);
            level.setBlockAndUpdate(portalPos.relative(facing, width).above(h), frameBlock);
        }

        // Portal blocks
        BlockState portalState = portalBlock.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        for (int w = 0; w < width; w++) {
            for (int h = 1; h < height; h++) {
                level.setBlockAndUpdate(portalPos.relative(facing, w).above(h), portalState);
            }
        }

        return portalPos.above();
    }

    private static BlockPos findSuitableLocation(ServerLevel level, BlockPos searchPos) {
        // Use the same safe spawn logic as dimension commands - spiral search for solid ground
        BlockPos safe = FabricDimensionHelper.findSafeSpawnNear(level, searchPos, 128);
        if (safe != null) {
            return safe;
        }

        // If spiral search failed, try world spawn as fallback
        safe = FabricDimensionHelper.findSafeSpawn(level);
        if (safe != null && safe.getY() < 256) { // Sanity check - not the ultimate fallback
            return safe;
        }

        // Last resort: create platform at searchPos
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(searchPos.getX(), 70, searchPos.getZ());
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(mutable.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }

        return mutable.immutable();
    }

    private static Vec3 findTeleportPosition(ServerLevel level, BlockPos portalPos) {
        return new Vec3(portalPos.getX() + 0.5, portalPos.getY(), portalPos.getZ() + 0.5);
    }
}
