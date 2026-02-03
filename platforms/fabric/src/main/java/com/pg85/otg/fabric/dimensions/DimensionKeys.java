package com.pg85.otg.fabric.dimensions;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.DimensionNameUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Fabric-specific utility for creating OTG dimension ResourceKeys.
 */
public final class DimensionKeys {

    private DimensionKeys() {} // utility class

    /**
     * Create a ResourceKey for an OTG dimension.
     * @param name The dimension name (will be normalized)
     * @return ResourceKey for the dimension
     */
    public static ResourceKey<Level> otg(String name) {
        return ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, DimensionNameUtils.normalizeName(name)));
    }

    /**
     * Normalize dimension name - delegates to shared utility.
     * @deprecated Use {@link DimensionNameUtils#normalizeName(String)} directly
     */
    @Deprecated
    public static String normalizeName(String name) {
        return DimensionNameUtils.normalizeName(name);
    }
}
