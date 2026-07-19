package com.pg85.otg.shared.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes MC-188578: sleeping in a non-overworld dimension does not advance time.
 *
 * Vanilla routes the sleep time-skip through {@code ServerLevel.setDayTime}, which
 * delegates to the level's {@link ServerLevelData}. Every dimension except the
 * overworld is backed by {@link DerivedLevelData}, whose {@code setDayTime} is an
 * empty no-op — so players wake up and it is still night. Day time is global in
 * vanilla, so the correct behaviour is to apply the skip to the overworld data.
 *
 * Only the call site inside the sleep block of {@code ServerLevel.tick} is
 * redirected: a blanket delegate on DerivedLevelData would also un-no-op the
 * per-tick {@code tickTime()} increment and make time advance once per dimension
 * per tick.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSleepMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"
            )
    )
    private void otg$sleepSetsGlobalDayTime(ServerLevel level, long dayTime) {
        // Level.levelData and ServerLevel.serverLevelData are the same object,
        // so the public getLevelData() avoids a @Shadow field (which would need
        // a refmap entry the shared-module fabric pipeline does not reliably emit).
        if (level.getLevelData() instanceof DerivedLevelData) {
            level.getServer().overworld().setDayTime(dayTime);
        } else {
            level.setDayTime(dayTime);
        }
    }
}
