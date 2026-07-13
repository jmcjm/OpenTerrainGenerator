package com.pg85.otg.shared.portals;

import com.pg85.otg.config.settings.preset.PortalColors;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates and tracks the per-color portal block instances.
 * Platforms register the returned blocks with their loader's mechanism
 * (direct registry on fabric, DeferredRegister on forge).
 */
public final class SharedPortalBlocks {

    private static final Map<String, SharedOTGPortalBlock> PORTAL_BLOCKS = new LinkedHashMap<>();

    private SharedPortalBlocks() {}

    /** Creates one portal block per color; idempotent. Registry ids are otg:otg_portal_<color>. */
    public static Map<String, SharedOTGPortalBlock> createBlocks() {
        if (PORTAL_BLOCKS.isEmpty()) {
            for (String color : PortalColors.COLORS) {
                create(color);
            }
        }
        return PORTAL_BLOCKS;
    }

    /**
     * Creates (or returns) the portal block for one color. Block construction
     * touches the block registry (intrusive holders), so on forge this must run
     * inside a DeferredRegister supplier, not at mod construction time.
     */
    public static SharedOTGPortalBlock create(String color) {
        SharedOTGPortalBlock.init(SharedPortalBlocks::getPortalBlock);
        return PORTAL_BLOCKS.computeIfAbsent(color, c -> new SharedOTGPortalBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_RED)
                        .noCollission()
                        .randomTicks()
                        .strength(-1.0F)
                        .sound(SoundType.GLASS)
                        .lightLevel(state -> 11),
                c
        ));
    }

    public static SharedOTGPortalBlock getPortalBlock(String color) {
        String normalizedColor = color.toLowerCase().trim();
        return PORTAL_BLOCKS.getOrDefault(normalizedColor, PORTAL_BLOCKS.get("default"));
    }
}
