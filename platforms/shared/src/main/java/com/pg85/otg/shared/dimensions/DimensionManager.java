package com.pg85.otg.shared.dimensions;

import com.pg85.otg.OTG;
import com.pg85.otg.dimensions.DimensionDatapack;
import com.pg85.otg.dimensions.DimensionInfo;
import com.pg85.otg.dimensions.OTGWorldStorage;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.config.settings.preset.GameRuleSettings;
import com.pg85.otg.shared.gamerules.GameRuleApplier;
import com.pg85.otg.shared.gamerules.GameRuleManager;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class DimensionManager {

    private final PlatformDimensionHelper helper;
    private OTGWorldStorage storage;
    private DimensionDatapack datapack;
    private MinecraftServer server;

    private static DimensionManager instance;

    public static void setInstance(DimensionManager manager) {
        instance = manager;
    }

    public static DimensionManager get() {
        return instance;
    }

    public DimensionManager(PlatformDimensionHelper helper) {
        this.helper = helper;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        this.storage = new OTGWorldStorage(helper.getWorldPath(server));
        this.datapack = new DimensionDatapack(helper.getDatapackPath(server));
        this.storage.load();

        for (DimensionInfo info : storage.getAllDimensions()) {
            Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(info.getPreset());
            if (preset != null) {
                try {
                    datapack.createDimensionFiles(info, preset.getPresetConfig().getDimensionSettings(),
                            preset.getPresetRegistryName().toLowerCase(java.util.Locale.ROOT));
                } catch (Exception e) {
                    OTGLog.error("Failed to regenerate datapack for %s: %s", info.getName(), e.getMessage());
                }
            } else {
                OTGLog.warn("Preset %s not found for dimension %s", info.getPreset(), info.getName());
            }
        }

        com.pg85.otg.shared.portals.SharedPortalRegistry.load(storage.getPortals());

        // Restore persisted GameRules for the created dimensions
        for (var entry : storage.getAllGameRules().entrySet()) {
            ResourceKey<Level> levelKey = parseDimensionKey(entry.getKey());
            if (levelKey != null) {
                GameRuleManager.register(levelKey, GameRuleApplier.fromMap(entry.getValue()));
            }
        }
    }

    private static ResourceKey<Level> parseDimensionKey(String dimKeyStr) {
        int colonIdx = dimKeyStr.indexOf(':');
        if (colonIdx < 0) {
            OTGLog.warn("Invalid dimension key in storage: %s", dimKeyStr);
            return null;
        }
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(dimKeyStr.substring(0, colonIdx), dimKeyStr.substring(colonIdx + 1)));
    }

    public CreateResult createDimension(String presetName) {
        Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetName);
        if (preset == null) {
            return CreateResult.error("Unknown preset '" + presetName + "'. Use /otg preset list");
        }

        String normalizedName = DimensionNameUtils.normalizeName(presetName);

        if (storage.exists(normalizedName)) {
            return CreateResult.error("Dimension otg:" + normalizedName + " already exists");
        }

        long seed = new Random().nextLong();
        DimensionInfo info = DimensionInfo.create(presetName, seed);

        // A previous dimension of the same name may have been deleted without --purge;
        // silently adopting its region files under a new seed would produce seams.
        try {
            helper.purgeWorldData(server, normalizedName);
        } catch (Exception e) {
            OTGLog.warn("Could not clean up stale world data for %s: %s", normalizedName, e.getMessage());
        }

        try {
            datapack.createDimensionFiles(info, preset.getPresetConfig().getDimensionSettings(),
                    preset.getPresetRegistryName().toLowerCase(java.util.Locale.ROOT));
            storage.addDimension(info);

            GameRuleSettings gameRuleSettings = preset.getPresetConfig().getGameRuleSettings();
            if (gameRuleSettings != null && gameRuleSettings.isOverrideGameRules()) {
                GameRules gameRules = GameRuleApplier.createGameRules(gameRuleSettings, server);
                GameRuleManager.register(DimensionKeys.otg(normalizedName), gameRules);
                storage.putGameRules("otg:" + normalizedName, GameRuleApplier.toMap(gameRules));
            }

            helper.createDimensionRuntime(server, normalizedName, presetName, seed);
            return CreateResult.success(info);
        } catch (Exception e) {
            OTGLog.error("Failed to create dimension: %s", e.getMessage());
            try {
                datapack.deleteDimensionFiles(normalizedName);
                storage.removeDimension(normalizedName);
            } catch (Exception ignored) { OTGLog.error("Failed to cleanup after failed dimension creation: %s", ignored.getMessage()); }
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
            com.pg85.otg.shared.portals.SharedPortalRegistry.removeLevel(DimensionKeys.otg(normalizedName));

            if (purge) {
                helper.purgeWorldData(server, normalizedName);
            }

            return DeleteResult.success(normalizedName, playerCount, purge);
        } catch (Exception e) {
            OTGLog.error("Failed to delete dimension: %s", e.getMessage());
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
            OTGLog.error("Failed to load dimension %s at runtime: %s", normalizedName, e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        com.pg85.otg.shared.portals.SharedPortalRegistry.clear();
    }

    public PlatformDimensionHelper getHelper() {
        return helper;
    }

    public void persistPortals(java.util.Map<String, java.util.List<String>> portals) {
        if (storage != null) {
            storage.setPortals(portals);
        }
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
