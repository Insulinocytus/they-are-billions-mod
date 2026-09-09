package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.client.HordeClientRendering;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
    @Inject(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            ),
            cancellable = true
    )
    private void theyarebillions$skipFarHordeEffectParticles(CallbackInfo ci) {
        if (!HordeClientRendering.renderNonessentialEffects((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
