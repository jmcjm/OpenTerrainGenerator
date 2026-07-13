package com.pg85.otg.config.settings.preset;

import java.util.List;

public class PortalColors {

    public static final List<String> COLORS = List.of(
            "default", "beige", "black", "blue", "crystalblue", "darkblue",
            "darkgreen", "darkred", "emerald", "flame", "gold", "green",
            "grey", "lightblue", "lightgreen", "orange", "pink", "red",
            "white", "yellow"
    );

    public static boolean isValidColor(String color) {
        return COLORS.contains(color.toLowerCase());
    }

    public static int getColorIndex(String color) {
        int index = COLORS.indexOf(color.toLowerCase());
        return index >= 0 ? index : 0;
    }

    public static String getColorName(int index) {
        if (index < 0 || index >= COLORS.size()) {
            return COLORS.get(0);
        }
        return COLORS.get(index);
    }

    public static String getNextColor(String currentColor) {
        int index = COLORS.indexOf(currentColor.toLowerCase());
        if (index == -1 || index == COLORS.size() - 1) {
            return COLORS.get(0);
        }
        return COLORS.get(index + 1);
    }
}
