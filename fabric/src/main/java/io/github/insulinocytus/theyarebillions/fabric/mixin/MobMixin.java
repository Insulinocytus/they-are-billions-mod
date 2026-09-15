package io.github.insulinocytus.theyarebillions.fabric.mixin;

import io.github.insulinocytus.theyarebillions.ZombieSpawnFilter;
import io.github.insulinocytus.theyarebillions.fabric.ZombieSpawnBlockFlag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
abstract class MobMixin implements ZombieSpawnBlockFlag {
    @Unique
    private boolean they_are_billions$blockedSpawn;

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void markBlockedSpawn(
        ServerLevelAccessor level,
        DifficultyInstance difficulty,
        MobSpawnType spawnType,
        SpawnGroupData spawnData,
        CallbackInfoReturnable<SpawnGroupData> callback
    ) {
        they_are_billions$blockedSpawn = ZombieSpawnFilter.blocks(((Mob) (Object) this).getType(), spawnType);
    }

    @Override
    public boolean they_are_billions$blockedSpawn() {
        return they_are_billions$blockedSpawn;
    }
}
