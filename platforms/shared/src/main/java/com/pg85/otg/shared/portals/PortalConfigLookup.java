package com.pg85.otg.shared.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.DimensionNameUtils;

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

        return OTG.getEngine().getPresetLoader().getAllPresets().stream()
                .filter(p -> p.getPresetConfig() != null)
                .filter(p -> p.getPresetConfig().getPortalSettings() != null)
                .filter(p -> {
                    PortalSettings settings = p.getPresetConfig().getPortalSettings();
                    if (settings.getPortalBlocks() == null || settings.getPortalBlocks().isEmpty()) {
                        return false;
                    }
                    return targetColor.equals(DimensionNameUtils.normalizeColor(settings.getPortalColor()));
                })
                .findFirst();
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
