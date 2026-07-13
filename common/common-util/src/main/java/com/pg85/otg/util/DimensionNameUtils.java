package com.pg85.otg.util;

/**
 * Utility for normalizing dimension and preset names across platforms.
 */
public final class DimensionNameUtils {

    private DimensionNameUtils() {} // utility class

    /**
     * Normalize dimension/preset name: lowercase, spaces to underscores.
     * Used for creating consistent dimension identifiers across platforms.
     *
     * @param name The raw name (e.g., "My Preset")
     * @return Normalized name (e.g., "my_preset")
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replace(" ", "_");
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
