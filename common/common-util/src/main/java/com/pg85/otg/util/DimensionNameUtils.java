package com.pg85.otg.util;

import java.util.Locale;

/**
 * Utility for normalizing dimension and preset names across platforms.
 */
public final class DimensionNameUtils {

    private DimensionNameUtils() {} // utility class

    /**
     * Normalize dimension/preset name: lowercase, spaces to underscores, then sanitize
     * any remaining characters outside {@code [a-z0-9_.-]} to underscores. The result is
     * always a valid Minecraft ResourceLocation path, safe to pass to
     * {@code ResourceLocation.fromNamespaceAndPath} without it throwing.
     * Used for creating consistent dimension identifiers across platforms.
     *
     * @param name The raw name (e.g., "My Preset!")
     * @return Normalized name (e.g., "my_preset_")
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replace(" ", "_").replaceAll("[^a-z0-9_.-]", "_");
    }

    /**
     * Normalize portal color string for comparison.
     *
     * @param color The raw color string
     * @return Normalized color (lowercase, trimmed), or "default" if null
     */
    public static String normalizeColor(String color) {
        return color == null ? "default" : color.toLowerCase().trim();
    }
}
