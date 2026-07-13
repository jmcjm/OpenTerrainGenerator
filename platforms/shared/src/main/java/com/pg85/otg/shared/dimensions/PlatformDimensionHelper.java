package com.pg85.otg.shared.dimensions;

import com.pg85.otg.util.OTGLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.nio.file.Path;
import java.util.List;

public interface PlatformDimensionHelper {

    Path getWorldPath(MinecraftServer server);

    Path getDatapackPath(MinecraftServer server);

    void teleportToOverworldSpawn(ServerPlayer player);

    void teleportToDimension(ServerPlayer player, String dimensionName);

    List<ServerPlayer> getPlayersInDimension(MinecraftServer server, String dimensionName);

    boolean isDimensionLoaded(MinecraftServer server, String dimensionName);

    void createDimensionRuntime(MinecraftServer server, String name, String presetName, long seed) throws Exception;

    void deleteDimensionRuntime(MinecraftServer server, String name) throws Exception;

    void purgeWorldData(MinecraftServer server, String name) throws Exception;

    // --- Static spawn utility methods (shared across all platforms) ---

    static BlockPos findSafeSpawn(ServerLevel level) {
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos safe = findSafeY(level, worldSpawn.getX(), worldSpawn.getZ());
        if (safe != null) {
            return safe;
        }

        int maxRadius = 1000;
        int step = 16;

        for (int radius = 0; radius <= maxRadius; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    safe = findSafeY(level, dx, dz);
                    if (safe != null) {
                        OTGLog.info("Found safe spawn at %s, %s, %s", safe.getX(), safe.getY(), safe.getZ());
                        return safe;
                    }
                }
            }
        }

        OTGLog.warn("Could not find safe spawn, using fallback at 0, 256, 0");
        return new BlockPos(0, 256, 0);
    }

    static BlockPos findSafeSpawnNear(ServerLevel level, BlockPos searchCenter, int maxRadius) {
        BlockPos safe = findSafeY(level, searchCenter.getX(), searchCenter.getZ());
        if (safe != null) {
            return safe;
        }

        int step = 8;

        for (int radius = step; radius <= maxRadius; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    safe = findSafeY(level, searchCenter.getX() + dx, searchCenter.getZ() + dz);
                    if (safe != null) {
                        OTGLog.info("Found safe location at %s, %s, %s (near %s, %s)",
                                safe.getX(), safe.getY(), safe.getZ(),
                                searchCenter.getX(), searchCenter.getZ());
                        return safe;
                    }
                }
            }
        }

        return null;
    }

    static BlockPos findSafeY(ServerLevel level, int x, int z) {
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        if (chunk == null) {
            return null;
        }

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int y = maxY - 2; y > minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos above1 = pos.above();
            BlockPos above2 = above1.above();

            BlockState ground = level.getBlockState(pos);
            BlockState air1 = level.getBlockState(above1);
            BlockState air2 = level.getBlockState(above2);

            if (isSolidGround(ground) && isPassable(air1) && isPassable(air2)) {
                return above1;
            }
        }

        return null;
    }

    private static boolean isSolidGround(BlockState state) {
        return state.isSolid() && !state.liquid();
    }

    private static boolean isPassable(BlockState state) {
        return state.isAir() || (!state.isSolid() && !state.liquid());
    }
}
