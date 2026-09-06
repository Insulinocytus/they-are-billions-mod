package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;

public final class HordeSpawnAccessImpl {
    private HordeSpawnAccessImpl() {
    }

    public static boolean checkSpawnPosition(Mob mob, ServerLevel level) {
        // EventHooks.checkSpawnPosition DEFAULT applies Mob#checkSpawnRules (light).
        PositionCheck event = new PositionCheck(mob, level, MobSpawnType.EVENT, null);
        NeoForge.EVENT_BUS.post(event);
        if (event.getResult() == PositionCheck.Result.DEFAULT) {
            return HordeSpawnAccess.isVanillaNonLightPositionOk(mob, level);
        }
        return event.getResult() == PositionCheck.Result.SUCCEED;
    }

    public static boolean isSpawnCancelled(Mob mob) {
        return mob.isSpawnCancelled();
    }
}
