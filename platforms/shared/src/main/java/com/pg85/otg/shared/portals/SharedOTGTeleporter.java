package com.pg85.otg.shared.portals;

import com.pg85.otg.shared.dimensions.PlatformDimensionHelper;
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

public class SharedOTGTeleporter {

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

        double sourceScale = entity.level().dimensionType().coordinateScale();
        double destScale = destination.dimensionType().coordinateScale();
        double scale = sourceScale / destScale;

        BlockPos sourcePos = entity.blockPosition();
        int destX = Mth.clamp((int)(sourcePos.getX() * scale),
                (int)border.getMinX() + 16, (int)border.getMaxX() - 16);
        int destZ = Mth.clamp((int)(sourcePos.getZ() * scale),
                (int)border.getMinZ() + 16, (int)border.getMaxZ() - 16);
        BlockPos searchPos = new BlockPos(destX, sourcePos.getY(), destZ);

        // Registered portals first: every portal is created via ignition or by this
        // teleporter, so the registry normally knows the portal the player came from.
        Optional<BlockPos> registered = findRegisteredPortal(destination, searchPos, portalColor);
        if (registered.isPresent()) {
            return registered.get();
        }

        Optional<BlockPos> existingPortal = findExistingPortal(destination, searchPos, portalColor);
        if (existingPortal.isPresent()) {
            return existingPortal.get();
        }

        return createPortal(destination, searchPos, portalColor);
    }

    /** Portals link within this horizontal distance of the scaled coordinates, like vanilla. */
    private static final int LINK_RADIUS = 128;

    private static Optional<BlockPos> findRegisteredPortal(ServerLevel level, BlockPos searchPos, String portalColor) {
        SharedOTGPortalBlock targetBlock = SharedOTGPortalBlock.lookupPortalBlock(portalColor);
        if (targetBlock == null) return Optional.empty();

        BlockPos best = null;
        double bestDistSq = (double) LINK_RADIUS * LINK_RADIUS;
        for (BlockPos pos : SharedPortalRegistry.get(level.dimension(), portalColor)) {
            if (level.getBlockState(pos).getBlock() != targetBlock) {
                // Portal was broken - forget it
                SharedPortalRegistry.unregister(level.dimension(), portalColor, pos);
                continue;
            }
            double dx = pos.getX() - searchPos.getX();
            double dz = pos.getZ() - searchPos.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = pos;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<BlockPos> findExistingPortal(ServerLevel level, BlockPos searchPos, String portalColor) {
        SharedOTGPortalBlock targetBlock = SharedOTGPortalBlock.lookupPortalBlock(portalColor);
        if (targetBlock == null) return Optional.empty();

        int searchRadius = 128;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int radius = 0; radius <= searchRadius; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (radius > 0 && Math.abs(dx) < radius && Math.abs(dz) < radius) {
                        continue;
                    }

                    int x = searchPos.getX() + dx;
                    int z = searchPos.getZ() + dz;

                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        mutable.set(x, y, z);
                        if (level.getBlockState(mutable).getBlock() == targetBlock) {
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
        SharedOTGPortalBlock portalBlock = SharedOTGPortalBlock.lookupPortalBlock(portalColor);
        if (portalBlock == null) return null;

        BlockState frameBlock = SharedPortalConfigResolver.getFrameBlock(level, portalColor);
        BlockPos portalPos = findSuitableLocation(level, pos);

        Direction.Axis axis = Direction.Axis.X;
        Direction facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);

        int width = SharedPortalConfigResolver.getPortalMinWidth(level, portalColor);
        int height = SharedPortalConfigResolver.getPortalMinHeight(level, portalColor);

        for (int i = -1; i <= width; i++) {
            level.setBlockAndUpdate(portalPos.relative(facing, i), frameBlock);
        }

        for (int i = -1; i <= width; i++) {
            level.setBlockAndUpdate(portalPos.relative(facing, i).above(height), frameBlock);
        }

        for (int h = 0; h <= height; h++) {
            level.setBlockAndUpdate(portalPos.relative(facing, -1).above(h), frameBlock);
            level.setBlockAndUpdate(portalPos.relative(facing, width).above(h), frameBlock);
        }

        BlockState portalState = portalBlock.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        for (int w = 0; w < width; w++) {
            for (int h = 1; h < height; h++) {
                level.setBlockAndUpdate(portalPos.relative(facing, w).above(h), portalState);
            }
        }

        SharedPortalRegistry.register(level.dimension(), portalColor, portalPos.above());
        return portalPos.above();
    }

    private static BlockPos findSuitableLocation(ServerLevel level, BlockPos searchPos) {
        BlockPos safe = PlatformDimensionHelper.findSafeSpawnNear(level, searchPos, 128);
        if (safe != null) {
            return safe;
        }

        safe = PlatformDimensionHelper.findSafeSpawn(level);
        if (safe != null && safe.getY() < 256) {
            return safe;
        }

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
