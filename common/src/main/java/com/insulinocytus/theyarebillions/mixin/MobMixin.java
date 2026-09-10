package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import com.insulinocytus.theyarebillions.horde.HordeNavigation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {
    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Unique
    private boolean theyarebillions$managedNavigation;

    @Inject(
            method = "serverAiStep",
            at = {
                @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
                        ordinal = 1),
                @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
                        ordinal = 1)
            })
    private void theyarebillions$updateHordeNavigationBeforeMoveGoals(CallbackInfo ci) {
        if ((Object) this instanceof Zombie zombie) {
            theyarebillions$setManagedNavigation(HordeNavigation.tick(zombie));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void theyarebillions$restoreHordeMoveControlAfterVanillaFlags(CallbackInfo ci) {
        if ((Object) this instanceof Zombie) {
            goalSelector.setControlFlag(Goal.Flag.MOVE, !theyarebillions$managedNavigation);
        }
    }

    @Unique
    private void theyarebillions$setManagedNavigation(boolean managedNavigation) {
        goalSelector.setControlFlag(Goal.Flag.MOVE, !managedNavigation);
        if (managedNavigation && !theyarebillions$managedNavigation) {
            for (var goal : goalSelector.getAvailableGoals()) {
                if (goal.isRunning() && goal.getFlags().contains(Goal.Flag.MOVE)) {
                    goal.stop();
                }
            }
        }
        theyarebillions$managedNavigation = managedNavigation;
    }

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
