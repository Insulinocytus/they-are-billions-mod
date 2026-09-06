package com.insulinocytus.theyarebillions.horde;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.entity.EntityTypeTest;

public final class OrdinaryZombies {
    private OrdinaryZombies() {
    }

    public static int countLoaded(ServerLevel level) {
        int count = 0;
        for (Zombie zombie : level.getEntities(
                EntityTypeTest.forExactClass(Zombie.class),
                candidate -> candidate.isAlive() && candidate.getType() == EntityType.ZOMBIE)) {
            count++;
        }
        return count;
    }
}
