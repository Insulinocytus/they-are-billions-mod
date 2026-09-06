package com.insulinocytus.theyarebillions.horde;

import net.minecraft.world.entity.MobSpawnType;

public final class HordePopulation {
    private HordePopulation() {
    }

    public static boolean owns(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL
                || spawnType == MobSpawnType.CHUNK_GENERATION
                || spawnType == MobSpawnType.REINFORCEMENT;
    }
}
