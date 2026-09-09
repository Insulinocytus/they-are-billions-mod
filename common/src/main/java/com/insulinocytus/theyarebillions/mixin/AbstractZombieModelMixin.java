package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.client.HordeAnimationCache;
import com.insulinocytus.theyarebillions.client.HordeClientRendering;
import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractZombieModel.class)
public abstract class AbstractZombieModelMixin {
    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/monster/Monster;FFFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void theyarebillions$reuseFarHordeAnimation(
            Monster entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (!(entity instanceof Zombie zombie)) {
            return;
        }
        int sampleIntervalTicks = HordeClientRendering.animationSampleIntervalTicks(zombie);
        if (HordeAnimationCache.reuseIfCurrent(
                zombie,
                (HumanoidModel<?>) (Object) this,
                sampleIntervalTicks,
                ageInTicks)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/monster/Monster;FFFFF)V",
            at = @At("RETURN"))
    private void theyarebillions$cacheFarHordeAnimation(
            Monster entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (!(entity instanceof Zombie zombie)) {
            return;
        }
        HordeAnimationCache.captureIfNeeded(
                zombie,
                (HumanoidModel<?>) (Object) this,
                HordeClientRendering.animationSampleIntervalTicks(zombie),
                ageInTicks);
    }
}
