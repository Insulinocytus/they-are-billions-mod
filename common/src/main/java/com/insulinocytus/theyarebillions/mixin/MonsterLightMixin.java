package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeSpawnContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public abstract class MonsterLightMixin {
    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void theyarebillions$ignoreLightForHorde(
            ServerLevelAccessor level,
            BlockPos pos,
            RandomSource random,
            CallbackInfoReturnable<Boolean> callback) {
        if (HordeSpawnContext.ignoresLight()) {
            callback.setReturnValue(true);
        }
    }
}
