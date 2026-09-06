package com.insulinocytus.theyarebillions.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeSpawns {
    private HordeSpawns() {
    }

    public static boolean spawnHordeMember(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (!SpawnPlacements.isSpawnPositionOk(EntityType.ZOMBIE, level, pos)) {
            return false;
        }
        boolean rulesOk = HordeSpawnContext.ignoringLight(() -> SpawnPlacements.checkSpawnRules(
                EntityType.ZOMBIE, level, MobSpawnType.EVENT, pos, level.getRandom()));
        if (!rulesOk) {
            return false;
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return false;
        }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!HordeSpawnHooks.checkPosition(zombie, level, MobSpawnType.EVENT)) {
            return false;
        }
        HordeMembers.setHordeMember(zombie, true);
        if (!HordeSpawnHooks.finalizeSpawn(zombie, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT)) {
            return false;
        }
        return level.addFreshEntity(zombie);
    }
}
