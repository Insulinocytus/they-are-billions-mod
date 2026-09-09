package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import com.insulinocytus.theyarebillions.horde.HordeMemberState;
import com.insulinocytus.theyarebillions.horde.HordeNavigation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieMixin implements HordeMemberState {
    @Unique
    private boolean theyarebillions$hordeMember;

    @Override
    public boolean theyarebillions$isSyncedHordeMember() {
        return theyarebillions$hordeMember;
    }

    @Override
    public void theyarebillions$setSyncedHordeMember(boolean hordeMember) {
        theyarebillions$hordeMember = hordeMember;
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void theyarebillions$followPlayerGroup(CallbackInfo ci) {
        HordeNavigation.tick((Zombie) (Object) this);
    }

    @Inject(method = "convertsInWater", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$noDrownedConversion(CallbackInfoReturnable<Boolean> cir) {
        if (HordeIdentity.isHordeMember((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setBaby", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$keepAdult(boolean baby, CallbackInfo ci) {
        if (baby && HordeIdentity.isHordeMember((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
