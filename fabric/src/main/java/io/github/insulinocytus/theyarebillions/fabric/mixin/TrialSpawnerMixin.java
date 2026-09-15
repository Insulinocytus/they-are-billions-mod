package io.github.insulinocytus.theyarebillions.fabric.mixin;

import io.github.insulinocytus.theyarebillions.ZombieSpawnFilter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrialSpawner.class)
abstract class TrialSpawnerMixin {
    @Redirect(
        method = "spawnMob",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean blockZombieSpawn(ServerLevel level, Entity entity) {
        return !ZombieSpawnFilter.blocks(entity.getType(), MobSpawnType.TRIAL_SPAWNER)
            && level.tryAddFreshEntityWithPassengers(entity);
    }
}
