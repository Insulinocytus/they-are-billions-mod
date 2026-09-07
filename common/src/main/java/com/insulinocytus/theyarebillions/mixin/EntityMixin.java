package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void theyarebillions$detachNamedHorde(Component name, CallbackInfo ci) {
        HordeIdentity.detachIfNamed((Entity) (Object) this);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void theyarebillions$detachLoadedNamedHorde(CompoundTag nbt, CallbackInfo ci) {
        HordeIdentity.detachIfNamed((Entity) (Object) this);
    }
}
