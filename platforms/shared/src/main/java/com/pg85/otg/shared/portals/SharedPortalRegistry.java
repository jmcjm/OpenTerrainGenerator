package com.pg85.otg.shared.portals;

import com.pg85.otg.shared.dimensions.DimensionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks portal positions per (dimension, color) so the teleporter can reuse
 * the portal the player came from instead of scanning blocks (a sparse block
 * scan misses 1-block-thick portal planes). Every portal is created either by
 * ignition or by the teleporter, so both register here. Persisted through
 * {@link com.pg85.otg.dimensions.OTGWorldStorage} when the dimension manager
 * is available.
 */
public final class SharedPortalRegistry {

    private static final Map<String, List<BlockPos>> RUNTIME = new ConcurrentHashMap<>();

    private SharedPortalRegistry() {}

    private static String key(ResourceKey<Level> level, String color) {
        return level.location() + "|" + color.toLowerCase().trim();
    }

    public static void register(ResourceKey<Level> level, String color, BlockPos pos) {
        List<BlockPos> positions = RUNTIME.computeIfAbsent(key(level, color), k -> new ArrayList<>());
        synchronized (positions) {
            if (!positions.contains(pos)) {
                positions.add(pos.immutable());
            }
        }
        persist();
    }

    public static void unregister(ResourceKey<Level> level, String color, BlockPos pos) {
        List<BlockPos> positions = RUNTIME.get(key(level, color));
        if (positions != null) {
            synchronized (positions) {
                positions.remove(pos);
            }
            persist();
        }
    }

    public static List<BlockPos> get(ResourceKey<Level> level, String color) {
        List<BlockPos> positions = RUNTIME.get(key(level, color));
        if (positions == null) {
            return List.of();
        }
        synchronized (positions) {
            return List.copyOf(positions);
        }
    }

    public static void load(Map<String, List<String>> stored) {
        RUNTIME.clear();
        for (var entry : stored.entrySet()) {
            List<BlockPos> positions = new ArrayList<>();
            for (String s : entry.getValue()) {
                String[] parts = s.split(",");
                if (parts.length == 3) {
                    try {
                        positions.add(new BlockPos(
                                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            RUNTIME.put(entry.getKey(), positions);
        }
    }

    /** Forgets all portals of a level, e.g. when its dimension is deleted. */
    public static void removeLevel(ResourceKey<Level> level) {
        String prefix = level.location() + "|";
        if (RUNTIME.keySet().removeIf(k -> k.startsWith(prefix))) {
            persist();
        }
    }

    public static void clear() {
        RUNTIME.clear();
    }

    private static void persist() {
        DimensionManager manager = DimensionManager.get();
        if (manager == null) {
            return;
        }
        Map<String, List<String>> out = new ConcurrentHashMap<>();
        for (var entry : RUNTIME.entrySet()) {
            List<String> positions = new ArrayList<>();
            synchronized (entry.getValue()) {
                for (BlockPos pos : entry.getValue()) {
                    positions.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
                }
            }
            out.put(entry.getKey(), positions);
        }
        manager.persistPortals(out);
    }
}
