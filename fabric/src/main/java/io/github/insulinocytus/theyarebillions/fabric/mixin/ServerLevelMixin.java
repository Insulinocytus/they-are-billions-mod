package io.github.insulinocytus.theyarebillions.fabric.mixin;

import io.github.insulinocytus.theyarebillions.fabric.ZombieSpawnBlockFlag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void blockMarkedZombie(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof ZombieSpawnBlockFlag flag && flag.they_are_billions$blockedSpawn()) {
            callback.setReturnValue(false);
        }
    }
}
