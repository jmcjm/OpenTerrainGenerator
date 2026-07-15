package com.pg85.otg.shared.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRestoreFromMixin {

    /**
     * restoreFrom() decides keepInventory via {@code this.level()} — the NEW player,
     * already placed in the respawn dimension. With per-dimension GameRules the death
     * and respawn dimensions can disagree: die() keeps the inventory using the death
     * dimension's rules (no drop), then this check reads the respawn dimension's rules
     * (no copy) — deleting the inventory entirely. The death dimension's rules govern:
     * read them from the OLD player's level.
     */
    @Redirect(
        method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getGameRules()Lnet/minecraft/world/level/GameRules;"
        )
    )
    private GameRules otg$deathDimensionGameRules(Level newPlayerLevel, ServerPlayer oldPlayer, boolean keepEverything) {
        return oldPlayer.level().getGameRules();
    }
}
