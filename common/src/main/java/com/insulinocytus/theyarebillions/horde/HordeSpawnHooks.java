package com.insulinocytus.theyarebillions.horde;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeSpawnHooks {
    private HordeSpawnHooks() {
    }

    @ExpectPlatform
    public static boolean checkPosition(Zombie zombie, ServerLevel level, MobSpawnType spawnType) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean finalizeSpawn(
            Zombie zombie, ServerLevel level, DifficultyInstance difficulty, MobSpawnType spawnType) {
        throw new AssertionError();
    }
}
