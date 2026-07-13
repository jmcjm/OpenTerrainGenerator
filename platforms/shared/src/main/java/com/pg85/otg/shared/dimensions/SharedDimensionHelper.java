package com.pg85.otg.shared.dimensions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Lifecycle;
import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.DimensionSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.shared.biome.SharedOTGBiomeProvider;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.mixin.MappedRegistryAccessor;
import com.pg85.otg.shared.mixin.MinecraftServerAccessor;
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
import java.util.Map;
import java.util.concurrent.Executor;

public class SharedDimensionHelper implements PlatformDimensionHelper {

    // --- Abstract methods: platform-specific mixin access ---

    protected Map<ResourceKey<Level>, ServerLevel> getLevels(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getLevels();
    }

    protected Executor getExecutor(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getExecutor();
    }

    protected LevelStorageSource.LevelStorageAccess getStorageSource(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getStorageSource();
    }

    protected boolean isRegistryFrozen(MappedRegistry<?> registry) {
        return ((MappedRegistryAccessor) registry).isFrozen();
    }

    protected void setRegistryFrozen(MappedRegistry<?> registry, boolean frozen) {
        ((MappedRegistryAccessor) registry).setFrozen(frozen);
    }

    protected LevelStem createOTGLevelStem(
            String presetName, long seed,
            Holder<DimensionType> dimTypeHolder,
            Holder<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        SharedOTGChunkGenerator chunkGenerator = new SharedOTGChunkGenerator(
                new SharedOTGBiomeProvider(presetName, seed),
                noiseSettings,
                biomeRegistry
        );
        return new LevelStem(dimTypeHolder, chunkGenerator);
    }

    // --- Shared implementation ---

    @Override
    public Path getWorldPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    @Override
    public Path getDatapackPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR);
    }

    @Override
    public void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    @Override
    public void teleportToDimension(ServerPlayer player, String dimensionName) {
        ResourceKey<Level> dimKey = DimensionKeys.otg(dimensionName);
        ServerLevel level = player.server.getLevel(dimKey);
        if (level != null) {
            BlockPos safeSpawn = PlatformDimensionHelper.findSafeSpawn(level);
            player.teleportTo(level, safeSpawn.getX() + 0.5, safeSpawn.getY(), safeSpawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            OTGLog.warn("Cannot teleport to dimension %s - not loaded (server restart may be required)", dimensionName);
        }
    }

    @Override
    public List<ServerPlayer> getPlayersInDimension(MinecraftServer server, String dimensionName) {
        ResourceKey<Level> dimKey = DimensionKeys.otg(dimensionName);
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            return List.of();
        }
        return level.players();
    }

    @Override
    public boolean isDimensionLoaded(MinecraftServer server, String dimensionName) {
        ResourceKey<Level> dimKey = DimensionKeys.otg(dimensionName);
        return server.getLevel(dimKey) != null;
    }

    @Override
    public void createDimensionRuntime(MinecraftServer server, String name, String presetName, long seed) throws Exception {
        Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetName);
        if (preset == null) {
            throw new IllegalArgumentException("Preset not found: " + presetName);
        }

        DimensionSettings dimSettings = preset.getPresetConfig().getDimensionSettings();

        ResourceKey<Level> levelKey = DimensionKeys.otg(name);
        ResourceLocation dimLocation = levelKey.location();
        ResourceKey<DimensionType> dimTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, dimLocation);

        if (server.getLevel(levelKey) != null) {
            OTGLog.warn("Dimension %s already loaded", name);
            return;
        }

        Registry<DimensionType> dimTypeRegistry = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        boolean wasFrozen = false;
        if (dimTypeRegistry instanceof MappedRegistry<DimensionType> mappedRegistry) {
            wasFrozen = isRegistryFrozen(mappedRegistry);
            if (wasFrozen) {
                setRegistryFrozen(mappedRegistry, false);
            }
        }

        try {
            Holder<DimensionType> dimTypeHolder;
            if (dimTypeRegistry.containsKey(dimTypeKey)) {
                dimTypeHolder = dimTypeRegistry.getHolderOrThrow(dimTypeKey);
                OTGLog.info("Using existing dimension type: %s", dimTypeKey.location());
            } else {
                DimensionType dimensionType = createDimensionType(dimSettings);
                if (dimTypeRegistry instanceof MappedRegistry<DimensionType> mappedRegistry) {
                    mappedRegistry.register(dimTypeKey, dimensionType, Lifecycle.stable());
                    OTGLog.info("Registered new dimension type: %s", dimTypeKey.location());
                }
                dimTypeHolder = dimTypeRegistry.getHolderOrThrow(dimTypeKey);
            }

            Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
            Registry<NoiseGeneratorSettings> noiseRegistry = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
            Holder<NoiseGeneratorSettings> noiseHolder = noiseRegistry.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);

            LevelStem levelStem = createOTGLevelStem(presetName, seed, dimTypeHolder, noiseHolder, biomeRegistry);

            WorldData worldData = server.getWorldData();
            DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());

            ChunkProgressListener progressListener = new ChunkProgressListener() {
                @Override public void updateSpawnPos(ChunkPos pos) {}
                @Override public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}
                @Override public void start() {}
                @Override public void stop() {}
            };

            Executor executor = getExecutor(server);
            LevelStorageSource.LevelStorageAccess storageSource = getStorageSource(server);

            ServerLevel serverLevel = new ServerLevel(
                    server,
                    executor,
                    storageSource,
                    derivedLevelData,
                    levelKey,
                    levelStem,
                    progressListener,
                    false,
                    BiomeManager.obfuscateSeed(seed),
                    ImmutableList.of(),
                    false,
                    null
            );

            getLevels(server).put(levelKey, serverLevel);

            OTGLog.info("Created dimension %s at runtime - no restart required!", name);

        } finally {
            if (wasFrozen && dimTypeRegistry instanceof MappedRegistry<DimensionType> mappedRegistry) {
                setRegistryFrozen(mappedRegistry, true);
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

    @Override
    public void deleteDimensionRuntime(MinecraftServer server, String name) throws Exception {
        ResourceKey<Level> dimKey = DimensionKeys.otg(name);

        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            OTGLog.info("Dimension %s not currently loaded", name);
            return;
        }

        for (ServerPlayer player : List.copyOf(level.players())) {
            teleportToOverworldSpawn(player);
        }

        level.save(null, true, false);

        try {
            level.getChunkSource().close();
        } catch (IOException e) {
            OTGLog.error("Error closing chunk source: %s", e.getMessage());
        }

        getLevels(server).remove(dimKey);

        OTGLog.info("Dimension %s unloaded", name);
    }

    @Override
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
