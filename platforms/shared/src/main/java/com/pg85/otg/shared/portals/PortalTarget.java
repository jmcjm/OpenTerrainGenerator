package com.pg85.otg.shared.portals;

import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A resolved portal destination: the dimension's level key plus the effective portal
 * configuration (DimensionPreset settings with WorldPreset YAML overrides for OTG
 * dimensions; YAML-only for non-OTG dimensions).
 */
public record PortalTarget(
        ResourceKey<Level> levelKey,
        @Nullable String presetFolderName,
        String color,
        List<LocalMaterialData> frameBlocks,
        String ignitionSource,
        int minWidth,
        int maxWidth,
        int minHeight,
        int maxHeight
) {
    /** True for dimensions backed by a non-OTG generator (no DimensionPreset). */
    public boolean isNonOTG() {
        return presetFolderName == null;
    }
}
