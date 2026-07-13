package com.pg85.otg.shared.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.DimensionNameUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Platform-agnostic portal configuration lookup.
 * Contains logic that doesn't depend on Minecraft classes.
 */
public final class PortalConfigLookup {

    private PortalConfigLookup() {} // utility class

    /**
     * Find a preset by its portal color.
     * @param portalColor The color to search for (case-insensitive)
     * @return Optional containing the matching preset, or empty if not found
     */
    public static Optional<Preset> findPresetByColor(String portalColor) {
        String targetColor = DimensionNameUtils.normalizeColor(portalColor);
        return effectiveColorByPreset().entrySet().stream()
                .filter(e -> targetColor.equals(e.getValue()))
                .findFirst()
                .map(e -> OTG.getEngine().getPresetLoader().getPresetByFolderName(e.getKey()));
    }

    /**
     * The single source of truth for portal colors. Presets can share a
     * configured PortalColor; colors are assigned deterministically (presets
     * sorted by folder name, a taken color falls through to the next one),
     * so ignition, destination lookup and dimension color all agree.
     * @return preset folder name -> effective portal color
     */
    public static Map<String, String> effectiveColorByPreset() {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> usedColors = new ArrayList<>();

        List<Preset> presets = new ArrayList<>(OTG.getEngine().getPresetLoader().getAllPresets());
        presets.sort(Comparator.comparing(Preset::getFolderName));

        for (Preset preset : presets) {
            if (preset.getPresetConfig() == null) continue;
            PortalSettings settings = preset.getPresetConfig().getPortalSettings();
            if (settings == null || settings.getPortalBlocks() == null || settings.getPortalBlocks().isEmpty()) {
                continue;
            }
            String color = DimensionNameUtils.normalizeColor(settings.getPortalColor());
            while (usedColors.contains(color)) {
                color = PortalColors.getNextColor(color);
            }
            usedColors.add(color);
            result.put(preset.getFolderName(), color);
        }
        return result;
    }

    /** Effective portal color of a preset, or "default" if it has no portal. */
    public static String effectiveColorOf(String presetFolderName) {
        return effectiveColorByPreset().getOrDefault(presetFolderName, "default");
    }

    /**
     * Get PortalSettings for a color, searching all presets.
     * @param portalColor The color to search for
     * @return Optional containing the matching settings, or empty if not found
     */
    public static Optional<PortalSettings> findSettingsByColor(String portalColor) {
        return findPresetByColor(portalColor)
                .map(p -> p.getPresetConfig().getPortalSettings());
    }

    /**
     * Get portal minimum width from settings, with minimum bound.
     */
    public static int getPortalMinWidth(PortalSettings settings) {
        return settings != null ? Math.max(2, settings.getPortalMinWidth()) : 2;
    }

    /**
     * Get portal minimum height from settings, with minimum bound.
     */
    public static int getPortalMinHeight(PortalSettings settings) {
        return settings != null ? Math.max(3, settings.getPortalMinHeight()) : 3;
    }
}
