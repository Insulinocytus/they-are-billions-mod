package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.client.HordeClientRendering;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
    @Redirect(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            )
    )
    private void theyarebillions$skipFarHordeEffectParticles(
            Level level,
            ParticleOptions particle,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        if (HordeClientRendering.renderNonessentialEffects((LivingEntity) (Object) this)) {
            level.addParticle(particle, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
