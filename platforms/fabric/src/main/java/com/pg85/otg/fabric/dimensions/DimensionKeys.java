package com.pg85.otg.fabric.dimensions;

import com.pg85.otg.constants.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Utility for creating OTG dimension ResourceKeys consistently.
 */
public final class DimensionKeys {

    private DimensionKeys() {} // utility class

    /**
     * Create a ResourceKey for an OTG dimension.
     * @param name The dimension name (will be normalized: lowercase, spaces to underscores)
     * @return ResourceKey for the dimension
     */
    public static ResourceKey<Level> otg(String name) {
        return ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, normalizeName(name)));
    }

    /**
     * Normalize dimension name: lowercase, spaces to underscores.
     */
    public static String normalizeName(String name) {
        return name.toLowerCase().replace(" ", "_");
    }
}
