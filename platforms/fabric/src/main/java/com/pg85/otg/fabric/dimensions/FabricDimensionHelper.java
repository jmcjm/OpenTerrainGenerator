package com.pg85.otg.fabric.dimensions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Lifecycle;
import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.DimensionSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.fabric.biome.OTGFabricBiomeProvider;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.mixin.MappedRegistryAccessor;
import com.pg85.otg.fabric.mixin.MinecraftServerAccessor;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.OTGLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class FabricDimensionHelper {

    public Path getWorldPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    public Path getDatapackPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR);
    }

    public void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    public void teleportToDimension(ServerPlayer player, String dimensionName) {
        ResourceKey<Level> dimKey = DimensionKeys.otg(dimensionName);
        ServerLevel level = player.server.getLevel(dimKey);
        if (level != null) {
            BlockPos safeSpawn = findSafeSpawn(level);
            player.teleportTo(level, safeSpawn.getX() + 0.5, safeSpawn.getY(), safeSpawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            OTGLog.warn("Cannot teleport to dimension %s - not loaded (server restart may be required)", dimensionName);
        }
    }

    /**
     * Finds a safe spawn location in the given level.
     * Searches in a spiral pattern from world spawn for solid ground with air above.
     */
    public static BlockPos findSafeSpawn(ServerLevel level) {
        // First try the world spawn
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos safe = findSafeY(level, worldSpawn.getX(), worldSpawn.getZ());
        if (safe != null) {
            return safe;
        }

        // Search in spiral pattern from 0,0
        int maxRadius = 1000;
        int step = 16; // Check every chunk

        for (int radius = 0; radius <= maxRadius; radius += step) {
            // Check points at this radius
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    // Only check points on the edge of the square
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    safe = findSafeY(level, dx, dz);
                    if (safe != null) {
                        OTGLog.info("Found safe spawn at %d, %d, %d", safe.getX(), safe.getY(), safe.getZ());
                        return safe;
                    }
                }
            }
        }

        // Fallback: return high Y at 0,0 and hope for the best
        OTGLog.warn("Could not find safe spawn, using fallback at 0, 256, 0");
        return new BlockPos(0, 256, 0);
    }

    /**
     * Finds a safe spawn location near the given position.
     * Searches in a spiral pattern for solid ground with air above.
     * Used by portals to find suitable location for portal placement.
     *
     * @param level The server level to search in
     * @param searchCenter The center position to search around
     * @param maxRadius Maximum search radius (default 128 for portals)
     * @return A safe BlockPos or null if none found within radius
     */
    public static BlockPos findSafeSpawnNear(ServerLevel level, BlockPos searchCenter, int maxRadius) {
        // First try the exact position
        BlockPos safe = findSafeY(level, searchCenter.getX(), searchCenter.getZ());
        if (safe != null) {
            return safe;
        }

        // Search in spiral pattern from search center
        int step = 8; // Check more frequently than world spawn search

        for (int radius = step; radius <= maxRadius; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    // Only check points on the edge of the square
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    safe = findSafeY(level, searchCenter.getX() + dx, searchCenter.getZ() + dz);
                    if (safe != null) {
                        OTGLog.info("Found safe location at %d, %d, %d (near %d, %d)",
                                safe.getX(), safe.getY(), safe.getZ(),
                                searchCenter.getX(), searchCenter.getZ());
                        return safe;
                    }
                }
            }
        }

        return null; // Caller should handle fallback (e.g., create platform)
    }

    /**
     * Finds a safe Y level at the given X,Z coordinates.
     * Returns null if no safe spot found.
     */
    public static BlockPos findSafeY(ServerLevel level, int x, int z) {
        // Make sure chunk is loaded/generated
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        if (chunk == null) {
            return null;
        }

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // Scan from top down to find solid ground with air above
        for (int y = maxY - 2; y > minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos above1 = pos.above();
            BlockPos above2 = above1.above();

            BlockState ground = level.getBlockState(pos);
            BlockState air1 = level.getBlockState(above1);
            BlockState air2 = level.getBlockState(above2);

            // Need solid ground, with 2 blocks of air above for player
            if (isSolidGround(ground) && isPassable(air1) && isPassable(air2)) {
                return above1; // Return the position where player feet will be
            }
        }

        return null;
    }

    private static boolean isSolidGround(BlockState state) {
        // Check if block is solid and not liquid
        return state.isSolid() && !state.liquid();
    }

    private static boolean isPassable(BlockState state) {
        // Air or non-solid blocks player can stand in
        return state.isAir() || (!state.isSolid() && !state.liquid());
    }

    public List<ServerPlayer> getPlayersInDimension(MinecraftServer server, String dimensionName) {
        ResourceKey<Level> dimKey = DimensionKeys.otg(dimensionName);
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            return List.of();
        }
        return level.players();
    }

    public boolean isDimensionLoaded(MinecraftServer server, String dimensionName) {
        ResourceKey<Level> dimKey = DimensionKeys.otg(dimensionName);
        return server.getLevel(dimKey) != null;
    }

    public void createDimensionRuntime(MinecraftServer server, String name, String presetName, long seed) throws Exception {
        Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetName);
        if (preset == null) {
            throw new IllegalArgumentException("Preset not found: " + presetName);
        }

        DimensionSettings dimSettings = preset.getPresetConfig().getDimensionSettings();
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;

        // Create ResourceKeys
        ResourceKey<Level> levelKey = DimensionKeys.otg(name);
        ResourceLocation dimLocation = levelKey.location();
        ResourceKey<DimensionType> dimTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, dimLocation);

        // Check if already loaded
        if (server.getLevel(levelKey) != null) {
            OTGLog.warn("Dimension %s already loaded", name);
            return;
        }

        // Get dimension type registry and unfreeze
        Registry<DimensionType> dimTypeRegistry = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        boolean wasFrozen = false;
        if (dimTypeRegistry instanceof MappedRegistry<DimensionType> mappedRegistry) {
            wasFrozen = ((MappedRegistryAccessor) mappedRegistry).isFrozen();
            if (wasFrozen) {
                ((MappedRegistryAccessor) mappedRegistry).setFrozen(false);
            }
        }

        try {
            // Check if dimension type already exists (e.g., from datapack)
            Holder<DimensionType> dimTypeHolder;
            if (dimTypeRegistry.containsKey(dimTypeKey)) {
                // Use existing dimension type from datapack
                dimTypeHolder = dimTypeRegistry.getHolderOrThrow(dimTypeKey);
                OTGLog.info("Using existing dimension type: %s", dimTypeKey.location());
            } else {
                // Create and register new DimensionType
                DimensionType dimensionType = createDimensionType(dimSettings);
                if (dimTypeRegistry instanceof MappedRegistry<DimensionType> mappedRegistry) {
                    mappedRegistry.register(dimTypeKey, dimensionType, Lifecycle.stable());
                    OTGLog.info("Registered new dimension type: %s", dimTypeKey.location());
                }
                dimTypeHolder = dimTypeRegistry.getHolderOrThrow(dimTypeKey);
            }

            // Create ChunkGenerator
            Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
            Registry<NoiseGeneratorSettings> noiseRegistry = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
            Holder<NoiseGeneratorSettings> noiseHolder = noiseRegistry.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);

            OTGFabricChunkGenerator chunkGenerator = new OTGFabricChunkGenerator(
                    new OTGFabricBiomeProvider(presetName, seed),
                    noiseHolder,
                    biomeRegistry
            );

            // Create LevelStem
            LevelStem levelStem = new LevelStem(dimTypeHolder, chunkGenerator);

            // Create ServerLevelData
            WorldData worldData = server.getWorldData();
            DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());

            // Create no-op progress listener
            ChunkProgressListener progressListener = new ChunkProgressListener() {
                @Override public void updateSpawnPos(ChunkPos pos) {}
                @Override public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}
                @Override public void start() {}
                @Override public void stop() {}
            };

            // Get executor and storage from server
            Executor executor = serverAccessor.getExecutor();
            LevelStorageSource.LevelStorageAccess storageSource = serverAccessor.getStorageSource();

            // Create ServerLevel
            ServerLevel serverLevel = new ServerLevel(
                    server,
                    executor,
                    storageSource,
                    derivedLevelData,
                    levelKey,
                    levelStem,
                    progressListener,
                    false,  // isDebug
                    BiomeManager.obfuscateSeed(seed),
                    ImmutableList.of(),  // custom spawners
                    false,  // tickTime
                    null    // randomSequences
            );

            // Add to server's level map
            serverAccessor.getLevels().put(levelKey, serverLevel);

            OTGLog.info("Created dimension %s at runtime - no restart required!", name);

        } finally {
            // Re-freeze registry if it was frozen before
            if (wasFrozen && dimTypeRegistry instanceof MappedRegistry<DimensionType> mappedRegistry) {
                ((MappedRegistryAccessor) mappedRegistry).setFrozen(true);
            }
        }
    }

    private DimensionType createDimensionType(DimensionSettings settings) {
        return new DimensionType(
                settings.getFixedTime(),
                settings.isHasSkyLight(),
                settings.isHasCeiling(),
                settings.isUltraWarm(),
                settings.isNatural(),
                settings.getCoordinateScale(),
                settings.isBedWorks(),
                settings.isRespawnAnchorWorks(),
                settings.getMinY(),
                settings.getHeight(),
                settings.getLogicalHeight(),
                TagKey.create(Registries.BLOCK, new ResourceLocation(settings.getInfiniburn())),
                new ResourceLocation(settings.getEffectsLocation().toLowerCase(Locale.ROOT)),
                (float) settings.getAmbientLight(),
                new DimensionType.MonsterSettings(
                        settings.isPiglinSafe(),
                        settings.isHasRaids(),
                        UniformInt.of(
                                settings.getMonsterSpawnLightVariationMin(),
                                settings.getMonsterSpawnLightVariationMax()
                        ),
                        settings.getMonsterSpawnLightLimit()
                )
        );
    }

    public void deleteDimensionRuntime(MinecraftServer server, String name) throws Exception {
        ResourceKey<Level> dimKey = DimensionKeys.otg(name);

        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            OTGLog.info("Dimension %s not currently loaded", name);
            return;
        }

        // Teleport all players out first
        for (ServerPlayer player : List.copyOf(level.players())) {
            teleportToOverworldSpawn(player);
        }

        // Save the level
        level.save(null, true, false);

        // Close chunk source
        try {
            level.getChunkSource().close();
        } catch (IOException e) {
            OTGLog.error("Error closing chunk source: %s", e.getMessage());
        }

        // Remove from server's level map
        ((MinecraftServerAccessor) server).getLevels().remove(dimKey);

        OTGLog.info("Dimension %s unloaded", name);
    }

    public void purgeWorldData(MinecraftServer server, String name) throws Exception {
        Path dimensionFolder = getWorldPath(server)
                .resolve("dimensions")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve(name);

        if (Files.exists(dimensionFolder)) {
            Files.walk(dimensionFolder)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            OTGLog.error("Failed to delete %s: %s", path, e.getMessage());
                        }
                    });
            OTGLog.info("Purged world data for dimension %s", name);
        }
    }
}
