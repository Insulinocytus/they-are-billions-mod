package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeMembers;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobDespawnMixin {
    @Inject(method = "removeWhenFarAway", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$keepHordeMember(double distanceToClosestPlayer, CallbackInfoReturnable<Boolean> callback) {
        if (HordeMembers.suppressesVanillaDistanceDespawn((Mob) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}
