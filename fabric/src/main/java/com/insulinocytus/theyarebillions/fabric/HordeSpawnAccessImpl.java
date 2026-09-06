package com.insulinocytus.theyarebillions.fabric;

import net.minecraft.world.entity.Mob;

public final class HordeSpawnAccessImpl {
    private HordeSpawnAccessImpl() {
    }

    public static boolean isSpawnCancelled(Mob mob) {
        return false;
    }
}
