package com.pg85.otg.shared.dimensions;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.config.settings.preset.GameRuleSettings;
import com.pg85.otg.dimensions.DimensionDatapack;
import com.pg85.otg.dimensions.DimensionInfo;
import com.pg85.otg.dimensions.OTGWorldStorage;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.gamerules.GameRuleApplier;
import com.pg85.otg.shared.gamerules.GameRuleManager;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.OTGLog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

public class DimensionManager {

    private final PlatformDimensionHelper helper;
    private OTGWorldStorage storage;
    private DimensionDatapack datapack;
    private MinecraftServer server;
    private @Nullable WorldPresetConfig activeWorldPresetConfig;
    private @Nullable Path activeWorldPresetConfigPath;

    public DimensionManager(PlatformDimensionHelper helper) {
        this.helper = helper;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        this.storage = new OTGWorldStorage(helper.getWorldPath(server));
        this.datapack = new DimensionDatapack(helper.getDatapackPath(server));
        this.storage.load();

        for (DimensionInfo info : storage.getAllDimensions()) {
            DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(info.getPreset());
            if (preset != null) {
                try {
                    datapack.createDimensionFiles(info, preset.getConfig().getDimensionSettings());
                } catch (Exception e) {
                    OTGLog.error("Failed to regenerate datapack for {}: {}", info.getName(), e.getMessage());
                }
            } else {
                OTGLog.warn("Preset {} not found for dimension {}", info.getPreset(), info.getName());
            }
        }

        // Restore persisted GameRules for all dimensions
        for (var entry : storage.getAllGameRules().entrySet()) {
            String dimKeyStr = entry.getKey();
            ResourceKey<Level> levelKey = parseDimensionKey(dimKeyStr);
            if (levelKey != null) {
                GameRules rules = GameRuleApplier.fromMap(entry.getValue());
                GameRuleManager.register(levelKey, rules);
            }
        }
        if (!storage.getAllGameRules().isEmpty()) {
            OTGLog.info("Restored GameRules for {} dimensions", storage.getAllGameRules().size());
        }

        // Load WorldPreset YAMLs once for both detection and GameRules application
        List<WorldPresetConfig> worldPresetConfigs = WorldPresetConfigLoader.loadAll(
            OTG.getEngine().getOTGRootFolder());

        // Detect which WorldPreset YAML was used to create this world (first start only)
        detectWorldPreset(server, worldPresetConfigs);

        // Cache active WorldPreset config for portal overrides and gating
        String activePresetName = storage.getWorldPreset();
        if (activePresetName != null) {
            this.activeWorldPresetConfig = worldPresetConfigs.stream()
                .filter(c -> activePresetName.equals(c.DisplayName))
                .findFirst().orElse(null);

            if (this.activeWorldPresetConfig != null) {
                this.activeWorldPresetConfigPath = findYamlPathByDisplayName(activePresetName);
            }
        }

        // Apply WorldPreset GameRules (first-time only, per-dimension)
        applyWorldPresetGameRules(server);

        // Fallback: Apply overworld GameRules from OTG preset if no WorldPreset was used
        if (storage.getGameRules("minecraft:overworld").isEmpty()) {
            applyOverworldGameRulesIfOTG(server);
        }
    }

    public CreateResult createDimension(String presetName) {
        DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(presetName);
        if (preset == null) {
            return CreateResult.error("Unknown preset '" + presetName + "'. Use /otg preset list");
        }

        String normalizedName = DimensionNameUtils.normalizeName(presetName);

        if (storage.exists(normalizedName)) {
            return CreateResult.error("Dimension otg:" + normalizedName + " already exists");
        }

        long seed = new Random().nextLong();
        DimensionInfo info = DimensionInfo.create(presetName, seed);

        try {
            datapack.createDimensionFiles(info, preset.getConfig().getDimensionSettings());
            storage.addDimension(info);

            // Apply GameRules from preset + optional WorldPresetConfig override
            GameRuleSettings gameRuleSettings = preset.getConfig().getGameRuleSettings();
            WorldPresetConfig.GameRules dimConfigOverrides = loadDimensionConfigGameRules(presetName);
            GameRules gameRules = GameRuleApplier.createGameRules(gameRuleSettings, dimConfigOverrides, server);
            ResourceKey<Level> levelKey = DimensionKeys.otg(normalizedName);
            GameRuleManager.register(levelKey, gameRules);
            storage.putGameRules("otg:" + normalizedName, GameRuleApplier.toMap(gameRules));

            helper.createDimensionRuntime(server, normalizedName, presetName, seed);
            return CreateResult.success(info);
        } catch (Exception e) {
            OTGLog.error("Failed to create dimension: {}", e.getMessage());
            try {
                datapack.deleteDimensionFiles(normalizedName);
                storage.removeDimension(normalizedName);
                GameRuleManager.unregister(DimensionKeys.otg(normalizedName));
                storage.removeGameRules("otg:" + normalizedName);
            } catch (Exception ignored) { OTGLog.error("Failed to cleanup after failed dimension creation: {}", ignored.getMessage()); }
            return CreateResult.error("Failed to create dimension: " + e.getMessage());
        }
    }

    public DeleteResult deleteDimension(String name, boolean purge, boolean confirmed) {
        String normalizedName = DimensionNameUtils.normalizeName(name);

        Optional<DimensionInfo> dimOpt = storage.getDimension(normalizedName);
        if (dimOpt.isEmpty()) {
            return DeleteResult.error("Unknown dimension '" + name + "'. Use /otg dimension list");
        }

        if (normalizedName.equals("overworld") || normalizedName.equals("the_nether") || normalizedName.equals("the_end")) {
            return DeleteResult.error("Cannot delete vanilla dimensions");
        }

        if (purge && !confirmed) {
            return DeleteResult.needsConfirmation(normalizedName);
        }

        try {
            List<ServerPlayer> players = helper.getPlayersInDimension(server, normalizedName);
            int playerCount = players.size();

            if (helper.isDimensionLoaded(server, normalizedName)) {
                helper.deleteDimensionRuntime(server, normalizedName);
            }

            datapack.deleteDimensionFiles(normalizedName);
            storage.removeDimension(normalizedName);
            GameRuleManager.unregister(DimensionKeys.otg(normalizedName));
            storage.removeGameRules("otg:" + normalizedName);

            if (purge) {
                helper.purgeWorldData(server, normalizedName);
            }

            return DeleteResult.success(normalizedName, playerCount, purge);
        } catch (Exception e) {
            OTGLog.error("Failed to delete dimension: {}", e.getMessage());
            return DeleteResult.error("Failed to delete dimension: " + e.getMessage());
        }
    }

    public List<DimensionInfo> listDimensions() {
        return storage.getAllDimensions();
    }

    public Optional<DimensionInfo> getDimensionInfo(String name) {
        return storage.getDimension(DimensionNameUtils.normalizeName(name));
    }

    public void teleportPlayer(ServerPlayer player, String dimensionName) {
        helper.teleportToDimension(player, DimensionNameUtils.normalizeName(dimensionName));
    }

    public boolean loadDimensionRuntime(String name) {
        String normalizedName = DimensionNameUtils.normalizeName(name);
        var info = storage.getDimension(normalizedName);
        if (info.isEmpty()) {
            return false;
        }

        try {
            helper.createDimensionRuntime(server, normalizedName, info.get().getPreset(), info.get().getSeed());
            return true;
        } catch (Exception e) {
            OTGLog.error("Failed to load dimension {} at runtime: {}", normalizedName, e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        GameRuleManager.clear();
    }

    public PlatformDimensionHelper getHelper() {
        return helper;
    }

    public @Nullable WorldPresetConfig getActiveWorldPresetConfig() {
        return activeWorldPresetConfig;
    }

    public @Nullable Path getActiveWorldPresetConfigPath() {
        return activeWorldPresetConfigPath;
    }

    private @Nullable Path findYamlPathByDisplayName(String displayName) {
        Path worldPresetsDir = OTG.getEngine().getOTGRootFolder()
            .resolve(com.pg85.otg.constants.Constants.WORLD_PRESETS_FOLDER);
        if (!Files.isDirectory(worldPresetsDir)) return null;
        try (Stream<Path> files = Files.list(worldPresetsDir)) {
            return files
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".yaml") || name.endsWith(".yml");
                })
                .filter(p -> {
                    WorldPresetConfig c = WorldPresetConfigLoader.fromFile(p.toFile());
                    return c != null && displayName.equals(c.DisplayName);
                })
                .findFirst().orElse(null);
        } catch (IOException e) {
            OTGLog.warn("Failed to list WorldPresets folder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Heuristically detects which WorldPreset YAML was used to create this world
     * by matching the OTG preset names of the running overworld/nether/end dimensions
     * against WorldPreset configs on disk. Only runs on first server start.
     *
     * Candidates matching on the three vanilla slots are disambiguated by their custom
     * dimensions: every otg:* level present in the world must be expected by the config,
     * and among the survivors the one with the fewest unexplained expectations wins
     * (a config may expect more dims than exist — e.g. a non-OTG entry whose WorldPreset
     * wasn't installed was skipped at registration).
     *
     * Known limitation: two YAMLs with identical slots AND identical custom dimension
     * keys (differing only in GameRules/portals) are still ambiguous — first match wins.
     * MC doesn't provide a callback for WorldPreset selection — this heuristic is the
     * pragmatic workaround.
     */
    private void detectWorldPreset(MinecraftServer server, List<WorldPresetConfig> configs) {
        if (storage.getWorldPreset() != null) return;
        if (configs.isEmpty()) return;

        String overworldPreset = getOTGPresetFolderName(server.overworld());
        ServerLevel netherLevel = server.getLevel(Level.NETHER);
        String netherPreset = netherLevel != null ? getOTGPresetFolderName(netherLevel) : null;
        ServerLevel endLevel = server.getLevel(Level.END);
        String endPreset = endLevel != null ? getOTGPresetFolderName(endLevel) : null;

        if (overworldPreset == null && netherPreset == null && endPreset == null) return;

        // Custom OTG dimensions actually present in this world (otg:* level keys).
        Set<String> presentCustomDims = new HashSet<>();
        for (ResourceKey<Level> levelKey : server.levelKeys()) {
            if (Constants.MOD_ID_SHORT.equals(levelKey.location().getNamespace())) {
                presentCustomDims.add(levelKey.location().getPath());
            }
        }

        WorldPresetConfig best = null;
        int bestSurplus = Integer.MAX_VALUE;
        for (WorldPresetConfig config : configs) {
            if (config.DisplayName == null) continue;
            if (!matchesDimensions(config, overworldPreset, netherPreset, endPreset)) continue;

            Set<String> expected = expectedCustomDimKeys(config);
            if (!expected.containsAll(presentCustomDims)) continue;

            int surplus = expected.size() - presentCustomDims.size();
            if (surplus < bestSurplus) {
                best = config;
                bestSurplus = surplus;
            }
        }

        if (best != null) {
            storage.setWorldPreset(best.DisplayName);
            OTGLog.info("Detected WorldPreset '{}' for this world", best.DisplayName);
        }
    }

    /** Normalized otg:* level-key paths this config's custom Dimensions entries produce. */
    private static Set<String> expectedCustomDimKeys(WorldPresetConfig config) {
        Set<String> keys = new HashSet<>();
        if (config.Dimensions == null) return keys;
        for (WorldPresetConfig.OTGDimension dim : config.Dimensions) {
            String rawName = dim.hasPreset() ? dim.PresetFolderName : dim.DimensionName;
            if (rawName != null && !rawName.isBlank()) {
                keys.add(DimensionNameUtils.normalizeName(rawName));
            }
        }
        return keys;
    }

    private static boolean matchesDimensions(WorldPresetConfig config,
            String overworldPreset, String netherPreset, String endPreset) {
        // Non-OTG slot entries (NonOTGWorldType) have PresetFolderName == null and their
        // levels report null from getOTGPresetFolderName() — they match by both being null.
        String configOverworld = (config.Overworld != null && !config.Overworld.isNonOTG())
            ? config.Overworld.PresetFolderName : null;
        if (!Objects.equals(configOverworld, overworldPreset)) return false;

        String configNether = (config.Nether != null) ? config.Nether.PresetFolderName : null;
        if (!Objects.equals(configNether, netherPreset)) return false;

        String configEnd = (config.End != null) ? config.End.PresetFolderName : null;
        return Objects.equals(configEnd, endPreset);
    }

    private static @Nullable String getOTGPresetFolderName(ServerLevel level) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        if (gen instanceof SharedOTGChunkGenerator otgGen) {
            DimensionPreset preset = otgGen.getPreset();
            return preset != null ? preset.getFolderName() : null;
        }
        return null;
    }

    private void applyWorldPresetGameRules(MinecraftServer server) {
        WorldPresetConfig config = this.activeWorldPresetConfig;
        if (config == null) return;

        applyWorldPresetDimensionGameRules(config, config.Overworld, Level.OVERWORLD, "minecraft:overworld", server);

        if (config.Nether != null && (config.Nether.hasPreset() || config.Nether.isNonOTG())) {
            applyWorldPresetDimensionGameRules(config, config.Nether, Level.NETHER, "minecraft:the_nether", server);
        }

        if (config.End != null && (config.End.hasPreset() || config.End.isNonOTG())) {
            applyWorldPresetDimensionGameRules(config, config.End, Level.END, "minecraft:the_end", server);
        }

        if (config.Dimensions != null) {
            for (WorldPresetConfig.OTGDimension dim : config.Dimensions) {
                String rawName = dim.hasPreset() ? dim.PresetFolderName : dim.DimensionName;
                if (rawName == null) continue;
                String normalizedName = DimensionNameUtils.normalizeName(rawName);
                ResourceKey<Level> levelKey = DimensionKeys.otg(normalizedName);
                applyWorldPresetDimensionGameRules(config, dim, levelKey, "otg:" + normalizedName, server);
            }
        }
    }

    private void applyWorldPresetDimensionGameRules(
            WorldPresetConfig config,
            WorldPresetConfig.OTGDimension dimEntry,
            ResourceKey<Level> levelKey,
            String storageKey,
            MinecraftServer server
    ) {
        if (!storage.getGameRules(storageKey).isEmpty()) return;
        if (dimEntry == null || (!dimEntry.hasPreset() && !dimEntry.isNonOTG())) return;

        GameRuleSettings gameRuleSettings = null;
        if (dimEntry.hasPreset()) {
            DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader()
                .getDimensionPresetByFolderName(dimEntry.PresetFolderName);
            if (preset == null) return;
            gameRuleSettings = preset.getConfig().getGameRuleSettings();
        }

        GameRules rules = GameRuleApplier.createGameRules(
            gameRuleSettings,
            config.GameRules,         // world-level overrides
            dimEntry.GameRules,       // per-dimension overrides
            server);

        GameRuleManager.register(levelKey, rules);
        storage.putGameRules(storageKey, GameRuleApplier.toMap(rules));
        OTGLog.info("Applied WorldPreset GameRules for dimension {}", storageKey);
    }

    private void applyOverworldGameRulesIfOTG(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ChunkGenerator gen = overworld.getChunkSource().getGenerator();
        if (gen instanceof SharedOTGChunkGenerator otgGen) {
            DimensionPreset preset = otgGen.getPreset();
            if (preset == null) return;

            String presetName = preset.getFolderName();
            GameRuleSettings gameRuleSettings = preset.getConfig().getGameRuleSettings();
            if (!gameRuleSettings.isOverrideGameRules()) return;

            WorldPresetConfig.GameRules overrides = loadDimensionConfigGameRules(presetName);
            GameRules rules = GameRuleApplier.createGameRules(gameRuleSettings, overrides, server);
            GameRuleManager.register(Level.OVERWORLD, rules);
            storage.putGameRules("minecraft:overworld", GameRuleApplier.toMap(rules));
            OTGLog.info("Applied GameRules for OTG overworld (preset: {})", presetName);
        }
    }

    private @Nullable WorldPresetConfig.GameRules loadDimensionConfigGameRules(String presetName) {
        // This stub exists for the legacy flow (applyOverworldGameRulesIfOTG) where worlds
        // are NOT created from a WorldPreset YAML. The old fromDisk() lookup was always broken
        // (searched by preset name but YAMLs are world-level configs, not per-preset).
        // WorldPreset-aware GameRules go through applyWorldPresetGameRules() instead.
        return null;
    }

    private static @Nullable ResourceKey<Level> parseDimensionKey(String dimKeyStr) {
        int colonIdx = dimKeyStr.indexOf(':');
        if (colonIdx < 0) {
            OTGLog.warn("Invalid dimension key in storage: {}", dimKeyStr);
            return null;
        }
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(
                        dimKeyStr.substring(0, colonIdx),
                        dimKeyStr.substring(colonIdx + 1)));
    }

    public record CreateResult(boolean success, String error, DimensionInfo info) {
        public static CreateResult success(DimensionInfo info) {
            return new CreateResult(true, null, info);
        }
        public static CreateResult error(String message) {
            return new CreateResult(false, message, null);
        }
    }

    public record DeleteResult(boolean success, String error, String dimensionName,
                               int playersRelocated, boolean purged, boolean needsConfirmation) {
        public static DeleteResult success(String name, int players, boolean purged) {
            return new DeleteResult(true, null, name, players, purged, false);
        }
        public static DeleteResult error(String message) {
            return new DeleteResult(false, message, null, 0, false, false);
        }
        public static DeleteResult needsConfirmation(String name) {
            return new DeleteResult(false, null, name, 0, false, true);
        }
    }
}
