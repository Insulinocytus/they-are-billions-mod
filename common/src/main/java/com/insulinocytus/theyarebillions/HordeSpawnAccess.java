package com.insulinocytus.theyarebillions;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Mob;

public final class HordeSpawnAccess {
    private HordeSpawnAccess() {
    }

    @ExpectPlatform
    public static boolean isSpawnCancelled(Mob mob) {
        throw new AssertionError();
    }
}
