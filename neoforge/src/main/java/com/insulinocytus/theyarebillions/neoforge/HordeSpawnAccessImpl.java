package com.insulinocytus.theyarebillions.neoforge;

import net.minecraft.world.entity.Mob;

public final class HordeSpawnAccessImpl {
    private HordeSpawnAccessImpl() {
    }

    public static boolean isSpawnCancelled(Mob mob) {
        return mob.isSpawnCancelled();
    }
}
