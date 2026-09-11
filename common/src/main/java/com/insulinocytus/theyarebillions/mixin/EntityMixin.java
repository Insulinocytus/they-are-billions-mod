package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import com.insulinocytus.theyarebillions.horde.HordeChunkTickets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "addTag", at = @At("RETURN"))
    private void theyarebillions$syncAddedHordeTag(String tag, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && HordeIdentity.HORDE_TAG.equals(tag)) {
            HordeIdentity.syncClientState((Entity) (Object) this);
        }
    }

    @Inject(method = "removeTag", at = @At("RETURN"))
    private void theyarebillions$syncRemovedHordeTag(String tag, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && HordeIdentity.HORDE_TAG.equals(tag)) {
            HordeIdentity.syncClientState((Entity) (Object) this);
        }
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"))
    private void theyarebillions$acquireDestinationTicket(double x, double y, double z, CallbackInfo ci) {
        HordeChunkTickets.beforePositionChange((Entity) (Object) this, x, y, z);
    }

    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void theyarebillions$detachNamedHorde(Component name, CallbackInfo ci) {
        HordeIdentity.detachIfNamed((Entity) (Object) this);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void theyarebillions$detachLoadedNamedHorde(CompoundTag nbt, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        HordeIdentity.detachIfNamed(entity);
        HordeIdentity.syncClientState(entity);
    }
}
