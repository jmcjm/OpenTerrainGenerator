package com.pg85.otg.shared.portals;

import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
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

import java.util.List;

public final class SharedPortalIgnitionHandler {

    private SharedPortalIgnitionHandler() {}

    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        List<PortalTarget> targets = PortalTargetResolver.resolveTargets();

        if (serverLevel.dimension() != Level.OVERWORLD &&
            !(serverLevel.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator) &&
            targets.stream().noneMatch(t -> t.levelKey().equals(serverLevel.dimension()))) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        BlockPos hitPos = hitResult.getBlockPos();
        BlockPos portalPos = hitPos.relative(hitResult.getDirection());

        for (PortalTarget target : targets) {
            if (!isIgnitionSource(stack, target.ignitionSource())) {
                continue;
            }

            boolean adjacentToFrame = false;
            for (Direction dir : Direction.values()) {
                if (SharedPortalConfigResolver.isFrameBlock(serverLevel.getBlockState(portalPos.relative(dir)), target.frameBlocks())) {
                    adjacentToFrame = true;
                    break;
                }
            }

            if (!adjacentToFrame) {
                continue;
            }

            if (SharedOTGPortalBlock.tryCreatePortal(serverLevel, portalPos, target.frameBlocks(), target.color(),
                    target.minWidth(), target.maxWidth(), target.minHeight(), target.maxHeight())) {
                player.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
                player.swing(hand);

                if (!player.isCreative()) {
                    if (stack.isDamageableItem()) {
                        stack.hurtAndBreak(1, player,
                                hand == InteractionHand.MAIN_HAND
                                        ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                                        : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    } else {
                        stack.shrink(1);
                    }
                }

                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private static boolean isIgnitionSource(ItemStack stack, String ignitionSource) {
        if (ignitionSource == null || ignitionSource.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.toString().equals(ignitionSource);
    }
}
