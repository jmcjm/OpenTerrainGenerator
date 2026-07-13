package com.pg85.otg.shared.dimensions;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.DimensionNameUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class DimensionKeys {

    private DimensionKeys() {}

    public static ResourceKey<Level> otg(String name) {
        return ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, DimensionNameUtils.normalizeName(name)));
    }

    public static String normalizeName(String name) {
        return DimensionNameUtils.normalizeName(name);
    }
}
