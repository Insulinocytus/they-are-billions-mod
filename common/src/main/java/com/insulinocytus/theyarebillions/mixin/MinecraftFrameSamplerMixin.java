package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.perf.PerfHarness;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameSamplerMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void theyarebillions$beginFrame(boolean tick, CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level != null) {
            PerfHarness.beginFrame();
        }
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void theyarebillions$endFrame(boolean tick, CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level != null) {
            PerfHarness.endFrame();
        }
    }
}
