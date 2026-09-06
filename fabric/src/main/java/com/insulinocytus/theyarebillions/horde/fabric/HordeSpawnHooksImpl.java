package com.insulinocytus.theyarebillions.horde.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeSpawnHooksImpl {
    private HordeSpawnHooksImpl() {
    }

    public static boolean checkPosition(Zombie zombie, ServerLevel level, MobSpawnType spawnType) {
        return zombie.checkSpawnObstruction(level);
    }

    @SuppressWarnings("deprecation")
    public static boolean finalizeSpawn(
            Zombie zombie, ServerLevel level, DifficultyInstance difficulty, MobSpawnType spawnType) {
        zombie.finalizeSpawn(level, difficulty, spawnType, null);
        return true;
    }
}
