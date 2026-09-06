package com.insulinocytus.theyarebillions.fabric;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

public final class HordeSpawnAccessImpl {
    private HordeSpawnAccessImpl() {
    }

    public static boolean checkSpawnPosition(Mob mob, ServerLevel level) {
        return HordeSpawnAccess.isVanillaNonLightPositionOk(mob, level);
    }

    public static boolean isSpawnCancelled(Mob mob) {
        return false;
    }
}
