package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.client.HordeClientRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void theyarebillions$skipFarHordeShadow(
            PoseStack poseStack,
            MultiBufferSource buffers,
            Entity entity,
            float opacity,
            float partialTick,
            LevelReader level,
            float radius,
            CallbackInfo ci) {
        if (!HordeClientRendering.renderShadow(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$skipFarHordeFlame(
            PoseStack poseStack,
            MultiBufferSource buffers,
            Entity entity,
            Quaternionf cameraOrientation,
            CallbackInfo ci) {
        if (!HordeClientRendering.renderNonessentialEffects(entity)) {
            ci.cancel();
        }
    }
}
