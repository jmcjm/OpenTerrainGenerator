package com.pg85.otg.fabric.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PortalIgnitionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register(PortalIgnitionHandler::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        // Only in overworld or OTG dimensions
        if (serverLevel.dimension() != Level.OVERWORLD &&
            !(serverLevel.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        BlockPos hitPos = hitResult.getBlockPos();
        BlockPos portalPos = hitPos.relative(hitResult.getDirection());

        // Check each OTG dimension's portal config
        List<PortalConfig> configs = getPortalConfigs(serverLevel);

        for (PortalConfig config : configs) {
            // Check if item matches ignition source
            if (!isIgnitionSource(stack, config.ignitionSource)) {
                continue;
            }

            // Check if clicked on or next to frame block
            boolean adjacentToFrame = false;
            for (Direction dir : Direction.values()) {
                if (PortalConfigResolver.isFrameBlock(serverLevel.getBlockState(portalPos.relative(dir)), config.frameBlocks())) {
                    adjacentToFrame = true;
                    break;
                }
            }

            if (!adjacentToFrame) {
                continue;
            }

            // Try to create portal
            if (OTGPortalBlock.tryCreatePortal(serverLevel, portalPos, config.frameBlocks, config.portalColor,
                    config.minWidth, config.maxWidth, config.minHeight, config.maxHeight)) {
                // Success
                player.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
                player.swing(hand);

                if (!player.isCreative()) {
                    if (stack.isDamageableItem()) {
                        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                    } else {
                        stack.shrink(1);
                    }
                }

                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private static List<PortalConfig> getPortalConfigs(ServerLevel level) {
        List<PortalConfig> configs = new ArrayList<>();
        List<String> usedColors = new ArrayList<>();

        // Get all OTG presets directly from PresetLoader (works even if dimension not loaded)
        List<Preset> presets = new ArrayList<>(OTG.getEngine().getPresetLoader().getAllPresets());

        // Sort by name for consistent color assignment
        presets.sort(Comparator.comparing(Preset::getFolderName));

        for (Preset preset : presets) {
            if (preset.getPresetConfig() == null) continue;

            PortalSettings portalSettings = preset.getPresetConfig().getPortalSettings();
            if (portalSettings == null) continue;

            // Skip if no portal blocks configured
            if (portalSettings.getPortalBlocks() == null || portalSettings.getPortalBlocks().isEmpty()) {
                continue;
            }

            String color = DimensionNameUtils.normalizeColor(portalSettings.getPortalColor());
            while (usedColors.contains(color)) {
                color = PortalColors.getNextColor(color);
            }
            usedColors.add(color);

            configs.add(new PortalConfig(
                    preset.getFolderName(),
                    portalSettings.getPortalBlocks(),
                    portalSettings.getPortalIgnitionSource(),
                    color,
                    portalSettings.getPortalMinWidth(),
                    portalSettings.getPortalMaxWidth(),
                    portalSettings.getPortalMinHeight(),
                    portalSettings.getPortalMaxHeight()
            ));
        }

        return configs;
    }

    private static boolean isIgnitionSource(ItemStack stack, String ignitionSource) {
        if (ignitionSource == null || ignitionSource.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.toString().equals(ignitionSource);
    }

    private record PortalConfig(
            String presetName,
            List<LocalMaterialData> frameBlocks,
            String ignitionSource,
            String portalColor,
            int minWidth,
            int maxWidth,
            int minHeight,
            int maxHeight
    ) {}
}
