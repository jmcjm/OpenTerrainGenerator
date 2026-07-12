package com.pg85.otg.client.preview.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewWorld implements BlockAndTintGetter {

    private final Map<Long, PreviewChunk> chunks = new ConcurrentHashMap<>();
    private int minY = -64;
    private int maxY = 320;

    public void addChunkFromAccess(ChunkAccess access) {
        int cx = access.getPos().x;
        int cz = access.getPos().z;
        int chunkMinY = access.getMinBuildHeight();
        int chunkHeight = access.getHeight();
        PreviewChunk chunk = new PreviewChunk(chunkMinY, chunkHeight);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunkMinY; y < chunkMinY + chunkHeight; y++) {
                    pos.set(cx * 16 + x, y, cz * 16 + z);
                    chunk.setBlockState(x, y, z, access.getBlockState(pos));
                }
            }
        }

        for (int bx = 0; bx < 4; bx++) {
            for (int bz = 0; bz < 4; bz++) {
                for (int by = 0; by < chunkHeight / 4; by++) {
                    Holder<Biome> biome = access.getNoiseBiome(
                        bx + cx * 4, by + (chunkMinY >> 2), bz + cz * 4
                    );
                    chunk.setBiome(bx, by, bz, biome);
                }
            }
        }

        chunks.put(chunkKey(cx, cz), chunk);
        minY = Math.min(minY, chunkMinY);
        maxY = Math.max(maxY, chunkMinY + chunkHeight);
    }

    /**
     * Set a block directly (for BO preview). Creates chunk on demand.
     */
    public void setBlockState(BlockPos pos, BlockState state) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = chunkKey(cx, cz);
        PreviewChunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new PreviewChunk(minY, maxY - minY);
            chunks.put(key, chunk);
        }
        chunk.setBlockState(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state);
    }

    public void clear() {
        chunks.clear();
    }

    /**
     * Assigns a single biome to every quart of every loaded chunk, so biome-tinted blocks render
     * with colour. Used by single-biome previews (terrain preview, BO preview) that place blocks
     * without populating biome data; the full world preview sets biomes via {@link #addChunkFromAccess}.
     */
    public void fillBiome(Holder<Biome> biome) {
        for (PreviewChunk chunk : chunks.values()) {
            int quartsY = chunk.getHeight() / 4;
            for (int bx = 0; bx < 4; bx++) {
                for (int bz = 0; bz < 4; bz++) {
                    for (int by = 0; by < quartsY; by++) {
                        chunk.setBiome(bx, by, bz, biome);
                    }
                }
            }
        }
    }

    public boolean hasChunk(int cx, int cz) {
        return chunks.containsKey(chunkKey(cx, cz));
    }

    public Collection<Long> getChunkKeys() {
        return chunks.keySet();
    }

    public static int chunkXFromKey(long key) { return (int)(key >> 32); }
    public static int chunkZFromKey(long key) { return (int)(key & 0xFFFFFFFFL); }

    private PreviewChunk getChunk(BlockPos pos) {
        return chunks.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // --- BlockAndTintGetter / BlockGetter / LevelHeightAccessor ---

    @Override
    public BlockState getBlockState(BlockPos pos) {
        PreviewChunk chunk = getChunk(pos);
        if (chunk == null) return Blocks.AIR.defaultBlockState();
        return chunk.getBlockState(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return maxY - minY;
    }

    @Override
    public int getMinBuildHeight() {
        return minY;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return switch (direction) {
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.6f;
            case UP -> 1.0f;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException("PreviewWorld has no light engine");
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkening) {
        return 15;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        PreviewChunk chunk = getChunk(pos);
        if (chunk == null) return -1;
        Holder<Biome> biome = chunk.getBiome(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        if (biome == null) return -1;
        return colorResolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }
}
