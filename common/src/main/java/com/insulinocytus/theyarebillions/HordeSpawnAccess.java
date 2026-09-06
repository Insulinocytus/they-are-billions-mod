package com.insulinocytus.theyarebillions;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;

public final class HordeSpawnAccess {
    private HordeSpawnAccess() {
    }

    @ExpectPlatform
    public static boolean checkSpawnPosition(Mob mob, ServerLevel level) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isSpawnCancelled(Mob mob) {
        throw new AssertionError();
    }

    public static boolean isVanillaNonLightPositionOk(Mob mob, ServerLevel level) {
        BlockPos pos = mob.blockPosition();
        return SpawnPlacements.isSpawnPositionOk(EntityType.ZOMBIE, level, pos)
                && Monster.checkAnyLightMonsterSpawnRules(
                        EntityType.ZOMBIE, level, MobSpawnType.EVENT, pos, level.random)
                && mob.checkSpawnObstruction(level);
    }
}
