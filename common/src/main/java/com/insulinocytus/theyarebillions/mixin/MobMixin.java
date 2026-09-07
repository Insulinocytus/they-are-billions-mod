package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "canPickUpLoot", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$noHordePickup(CallbackInfoReturnable<Boolean> cir) {
        if (HordeIdentity.isHordeMember((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$noHordeEquipment(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (!stack.isEmpty() && HordeIdentity.isHordeMember((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
