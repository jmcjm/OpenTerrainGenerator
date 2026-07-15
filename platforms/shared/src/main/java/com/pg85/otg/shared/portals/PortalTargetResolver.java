package com.pg85.otg.shared.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.dimensions.WorldPresetConfig.OTGDimension;
import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.dimensions.DimensionKeys;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the list of portal destinations for the active world: every OTG DimensionPreset
 * (with YAML overrides + gating, as before) plus non-OTG dimensions defined in the active
 * WorldPreset YAML (portal config comes solely from the YAML entry).
 *
 * Color collisions are resolved deterministically (sorted iteration + getNextColor), so
 * every caller sees the same effective color for a given target.
 */
public final class PortalTargetResolver {

    // Defaults for non-OTG entries, mirroring PortalSettings setting defaults
    // (PortalMinWidth=2, PortalMaxWidth=21, PortalMinHeight=3, PortalMaxHeight=21).
    private static final int DEFAULT_MIN_WIDTH = 2;
    private static final int DEFAULT_MAX_WIDTH = 21;
    private static final int DEFAULT_MIN_HEIGHT = 3;
    private static final int DEFAULT_MAX_HEIGHT = 21;
    private static final String DEFAULT_IGNITION_SOURCE = "minecraft:flint_and_steel";

    private PortalTargetResolver() {}

    public static List<PortalTarget> resolveTargets() {
        List<PortalTarget> targets = new ArrayList<>();
        List<String> usedColors = new ArrayList<>();

        WorldPresetConfig activeWorldPreset = WorldPresetPortalResolver.getActiveWorldPreset();
        Set<String> allowedPresets = WorldPresetPortalResolver.getAllowedPresetFolders(activeWorldPreset);

        // OTG DimensionPresets (existing semantics: gating R2, YAML overrides R1)
        List<DimensionPreset> presets = new ArrayList<>(OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets());
        presets.sort(Comparator.comparing(DimensionPreset::getFolderName));

        for (DimensionPreset preset : presets) {
            if (preset.getConfig() == null) continue;
            if (allowedPresets != null && !allowedPresets.contains(preset.getFolderName())) continue;

            PortalSettings portalSettings = preset.getConfig().getPortalSettings();
            if (portalSettings == null) continue;

            List<LocalMaterialData> frameBlocks = portalSettings.getPortalBlocks();
            String ignitionSource = portalSettings.getPortalIgnitionSource();
            String rawColor = portalSettings.getPortalColor();

            if (activeWorldPreset != null) {
                OTGDimension dimEntry = WorldPresetPortalResolver.findDimensionEntry(activeWorldPreset, preset.getFolderName());
                if (dimEntry != null) {
                    ArrayList<LocalMaterialData> overrideBlocks = WorldPresetPortalResolver.parsePortalBlocks(dimEntry.PortalBlocks);
                    if (overrideBlocks != null) {
                        frameBlocks = overrideBlocks;
                    }
                    if (WorldPresetPortalResolver.hasOverride(dimEntry.PortalColor)) {
                        rawColor = dimEntry.PortalColor;
                    }
                    if (WorldPresetPortalResolver.hasOverride(dimEntry.PortalIgnitionSource)) {
                        ignitionSource = dimEntry.PortalIgnitionSource;
                    }
                }
            }

            if (frameBlocks == null || frameBlocks.isEmpty()) continue;

            targets.add(new PortalTarget(
                    DimensionKeys.otg(preset.getFolderName()),
                    preset.getFolderName(),
                    claimColor(rawColor, usedColors),
                    frameBlocks,
                    ignitionSource,
                    portalSettings.getPortalMinWidth(),
                    portalSettings.getPortalMaxWidth(),
                    portalSettings.getPortalMinHeight(),
                    portalSettings.getPortalMaxHeight()
            ));
        }

        // Non-OTG dimensions from the active WorldPreset YAML (portal config = YAML only)
        if (activeWorldPreset != null && activeWorldPreset.Dimensions != null) {
            for (OTGDimension dim : activeWorldPreset.Dimensions) {
                if (!dim.isNonOTG()) continue;

                ArrayList<LocalMaterialData> frameBlocks = WorldPresetPortalResolver.parsePortalBlocks(dim.PortalBlocks);
                if (frameBlocks == null) {
                    OTGLog.info("Non-OTG dimension '{}' has no PortalBlocks — no OTG portal for it", dim.DimensionName);
                    continue;
                }

                String ignitionSource = WorldPresetPortalResolver.hasOverride(dim.PortalIgnitionSource)
                        ? dim.PortalIgnitionSource : DEFAULT_IGNITION_SOURCE;

                targets.add(new PortalTarget(
                        DimensionKeys.otg(dim.DimensionName),
                        null,
                        claimColor(dim.PortalColor, usedColors),
                        frameBlocks,
                        ignitionSource,
                        DEFAULT_MIN_WIDTH,
                        DEFAULT_MAX_WIDTH,
                        DEFAULT_MIN_HEIGHT,
                        DEFAULT_MAX_HEIGHT
                ));
            }
        }

        return targets;
    }

    public static Optional<PortalTarget> findByColor(String portalColor) {
        String normalized = DimensionNameUtils.normalizeColor(portalColor);
        return resolveTargets().stream()
                .filter(t -> t.color().equals(normalized))
                .findFirst();
    }

    public static Optional<PortalTarget> findByLevelKey(ResourceKey<Level> levelKey) {
        return resolveTargets().stream()
                .filter(t -> t.levelKey().equals(levelKey))
                .findFirst();
    }

    private static String claimColor(String rawColor, List<String> usedColors) {
        String color = DimensionNameUtils.normalizeColor(rawColor);
        while (usedColors.contains(color)) {
            color = PortalColors.getNextColor(color);
        }
        usedColors.add(color);
        return color;
    }
}
