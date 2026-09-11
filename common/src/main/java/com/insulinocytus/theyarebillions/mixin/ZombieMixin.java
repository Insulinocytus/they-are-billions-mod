package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import com.insulinocytus.theyarebillions.horde.HordeMemberState;
import net.minecraft.nbt.CompoundTag;
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
    @Unique
    private boolean theyarebillions$doorBreakingDisabled;

    @Unique
    private boolean theyarebillions$couldBreakDoors;
    @Unique
    private static final String THEYAREBILLIONS_DOOR_BREAKING_DISABLED = "TheyAreBillionsDoorBreakingDisabled";

    @Unique
    private static final String THEYAREBILLIONS_COULD_BREAK_DOORS = "TheyAreBillionsCouldBreakDoors";


    @Override
    public boolean theyarebillions$isSyncedHordeMember() {
        return theyarebillions$hordeMember;
    }

    @Override
    public void theyarebillions$setSyncedHordeMember(boolean hordeMember) {
        theyarebillions$hordeMember = hordeMember;
    }

    @Override
    public void theyarebillions$disableVanillaDoorBreaking() {
        Zombie zombie = (Zombie) (Object) this;
        if (!theyarebillions$doorBreakingDisabled) {
            theyarebillions$couldBreakDoors = zombie.canBreakDoors();
            theyarebillions$doorBreakingDisabled = true;
        }
        zombie.setCanBreakDoors(false);
    }

    @Override
    public void theyarebillions$restoreVanillaDoorBreaking() {
        if (theyarebillions$doorBreakingDisabled) {
            ((Zombie) (Object) this).setCanBreakDoors(theyarebillions$couldBreakDoors);
            theyarebillions$doorBreakingDisabled = false;
        }
    }


    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void theyarebillions$saveDoorBreakingState(CompoundTag tag, CallbackInfo ci) {
        if (theyarebillions$doorBreakingDisabled) {
            tag.putBoolean(THEYAREBILLIONS_DOOR_BREAKING_DISABLED, true);
            tag.putBoolean(THEYAREBILLIONS_COULD_BREAK_DOORS, theyarebillions$couldBreakDoors);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void theyarebillions$loadDoorBreakingState(CompoundTag tag, CallbackInfo ci) {
        theyarebillions$doorBreakingDisabled = tag.getBoolean(THEYAREBILLIONS_DOOR_BREAKING_DISABLED);
        theyarebillions$couldBreakDoors = tag.getBoolean(THEYAREBILLIONS_COULD_BREAK_DOORS);
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
