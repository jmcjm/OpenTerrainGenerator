package com.pg85.otg.fabric.dimensions;

import com.pg85.otg.OTG;
import com.pg85.otg.dimensions.DimensionDatapack;
import com.pg85.otg.dimensions.DimensionInfo;
import com.pg85.otg.dimensions.DimensionStorage;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.OTGLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class FabricDimensionManager {

    private final FabricDimensionHelper helper;
    private DimensionStorage storage;
    private DimensionDatapack datapack;
    private MinecraftServer server;

    public FabricDimensionManager() {
        this.helper = new FabricDimensionHelper();
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        this.storage = new DimensionStorage(helper.getWorldPath(server));
        this.datapack = new DimensionDatapack(helper.getDatapackPath(server));
        this.storage.load();

        // Verify datapack files exist for all stored dimensions
        for (DimensionInfo info : storage.getAllDimensions()) {
            Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(info.getPreset());
            if (preset != null) {
                try {
                    datapack.createDimensionFiles(info, preset.getPresetConfig().getDimensionSettings());
                } catch (Exception e) {
                    OTGLog.error("Failed to regenerate datapack for %s: %s", info.getName(), e.getMessage());
                }
            } else {
                OTGLog.warn("Preset %s not found for dimension %s", info.getPreset(), info.getName());
            }
        }
    }

    public CreateResult createDimension(String presetName) {
        // Validate preset exists
        Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetName);
        if (preset == null) {
            return CreateResult.error("Unknown preset '" + presetName + "'. Use /otg preset list");
        }

        String normalizedName = DimensionNameUtils.normalizeName(presetName);

        // Check if dimension already exists
        if (storage.exists(normalizedName)) {
            return CreateResult.error("Dimension otg:" + normalizedName + " already exists");
        }

        // Generate random seed
        long seed = new Random().nextLong();

        // Create dimension info
        DimensionInfo info = DimensionInfo.create(presetName, seed);

        try {
            // Create datapack files
            datapack.createDimensionFiles(info, preset.getPresetConfig().getDimensionSettings());

            // Save to storage
            storage.addDimension(info);

            // Try runtime creation (will log that restart is needed)
            helper.createDimensionRuntime(server, normalizedName, presetName, seed);

            return CreateResult.success(info);
        } catch (Exception e) {
            OTGLog.error("Failed to create dimension: %s", e.getMessage());
            // Rollback
            try {
                datapack.deleteDimensionFiles(normalizedName);
                storage.removeDimension(normalizedName);
            } catch (Exception ignored) {}
            return CreateResult.error("Failed to create dimension: " + e.getMessage());
        }
    }

    public DeleteResult deleteDimension(String name, boolean purge, boolean confirmed) {
        String normalizedName = DimensionNameUtils.normalizeName(name);

        // Validate dimension exists
        Optional<DimensionInfo> dimOpt = storage.getDimension(normalizedName);
        if (dimOpt.isEmpty()) {
            return DeleteResult.error("Unknown dimension '" + name + "'. Use /otg dimension list");
        }

        // Check for vanilla dimensions
        if (normalizedName.equals("overworld") || normalizedName.equals("the_nether") || normalizedName.equals("the_end")) {
            return DeleteResult.error("Cannot delete vanilla dimensions");
        }

        // Purge requires confirmation
        if (purge && !confirmed) {
            return DeleteResult.needsConfirmation(normalizedName);
        }

        try {
            // Get players in dimension before deletion
            List<ServerPlayer> players = helper.getPlayersInDimension(server, normalizedName);
            int playerCount = players.size();

            // Delete runtime if loaded
            if (helper.isDimensionLoaded(server, normalizedName)) {
                helper.deleteDimensionRuntime(server, normalizedName);
            }

            // Delete datapack files
            datapack.deleteDimensionFiles(normalizedName);

            // Remove from storage
            storage.removeDimension(normalizedName);

            // Purge world data if requested
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

    /**
     * Load an existing dimension at runtime (without server restart).
     * Used by portals when entering a dimension that exists in storage but isn't loaded yet.
     *
     * @param name The dimension name
     * @return true if dimension was loaded successfully, false if not found or failed
     */
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

    public FabricDimensionHelper getHelper() {
        return helper;
    }

    // Result classes
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
