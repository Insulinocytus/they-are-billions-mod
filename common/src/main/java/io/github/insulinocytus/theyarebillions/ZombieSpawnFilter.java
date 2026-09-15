package io.github.insulinocytus.theyarebillions;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;

public final class ZombieSpawnFilter {
    private ZombieSpawnFilter() {
    }

    public static boolean blocks(EntityType<?> entityType, MobSpawnType spawnType) {
        return entityType == EntityType.ZOMBIE
            && (spawnType == MobSpawnType.NATURAL || MobSpawnType.isSpawner(spawnType));
    }
}
