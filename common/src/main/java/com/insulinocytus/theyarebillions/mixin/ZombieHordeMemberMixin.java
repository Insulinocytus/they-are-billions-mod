package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeMemberMarker;
import com.insulinocytus.theyarebillions.horde.HordeMembers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieHordeMemberMixin implements HordeMemberMarker {
    @Unique
    private boolean theyarebillions$hordeMember;

    @Override
    public boolean theyarebillions$isHordeMember() {
        return theyarebillions$hordeMember;
    }

    @Override
    public void theyarebillions$setHordeMember(boolean hordeMember) {
        theyarebillions$hordeMember = hordeMember;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void theyarebillions$saveHordeMark(CompoundTag tag, CallbackInfo callback) {
        tag.putBoolean(HordeMembers.NBT_KEY, theyarebillions$hordeMember);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void theyarebillions$loadHordeMark(CompoundTag tag, CallbackInfo callback) {
        theyarebillions$hordeMember = tag.getBoolean(HordeMembers.NBT_KEY);
    }
}
