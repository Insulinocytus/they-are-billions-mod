package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import com.insulinocytus.theyarebillions.horde.HordeMemberState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
    private static final EntityDataAccessor<Boolean> THEYAREBILLIONS_HORDE_MEMBER =
            SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void theyarebillions$defineHordeMemberState(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(THEYAREBILLIONS_HORDE_MEMBER, false);
    }

    @Override
    public boolean theyarebillions$isSyncedHordeMember() {
        return ((Zombie) (Object) this).getEntityData().get(THEYAREBILLIONS_HORDE_MEMBER);
    }

    @Override
    public void theyarebillions$setSyncedHordeMember(boolean hordeMember) {
        ((Zombie) (Object) this).getEntityData().set(THEYAREBILLIONS_HORDE_MEMBER, hordeMember);
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
