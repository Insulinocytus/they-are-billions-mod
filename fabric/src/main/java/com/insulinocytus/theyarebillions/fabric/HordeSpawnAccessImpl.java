package com.insulinocytus.theyarebillions.fabric;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeSpawnAccessImpl {
    private HordeSpawnAccessImpl() {
    }

    public static boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    public static boolean checkSpawnPlacement(ServerLevel level, BlockPos pos) {
        return HordeSpawnAccess.isVanillaNonLightSpawnRulesOk(level, pos);
    }

    public static boolean checkSpawnPosition(Mob mob, ServerLevel level) {
        return HordeSpawnAccess.isVanillaNonLightPositionOk(mob, level);
    }

    public static boolean finalizeHordeSpawn(Mob mob, ServerLevel level) {
        mob.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT,
                new Zombie.ZombieGroupData(false, false));
        return true;
    }
}
