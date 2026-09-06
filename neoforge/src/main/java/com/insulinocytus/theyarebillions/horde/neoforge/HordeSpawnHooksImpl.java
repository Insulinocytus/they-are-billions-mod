package com.insulinocytus.theyarebillions.horde.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

public final class HordeSpawnHooksImpl {
    private HordeSpawnHooksImpl() {
    }

    public static boolean checkPosition(Zombie zombie, ServerLevel level, MobSpawnType spawnType) {
        MobSpawnEvent.PositionCheck event = new MobSpawnEvent.PositionCheck(zombie, level, spawnType, null);
        NeoForge.EVENT_BUS.post(event);
        return switch (event.getResult()) {
            case FAIL -> false;
            case SUCCEED -> true;
            case DEFAULT -> zombie.checkSpawnObstruction(level);
        };
    }

    @SuppressWarnings("deprecation")
    public static boolean finalizeSpawn(
            Zombie zombie, ServerLevel level, DifficultyInstance difficulty, MobSpawnType spawnType) {
        FinalizeSpawnEvent event = new FinalizeSpawnEvent(
                zombie,
                level,
                zombie.getX(),
                zombie.getY(),
                zombie.getZ(),
                difficulty,
                spawnType,
                null,
                null);
        NeoForge.EVENT_BUS.post(event);
        if (event.isSpawnCancelled()) {
            return false;
        }
        if (!event.isCanceled()) {
            zombie.finalizeSpawn(level, event.getDifficulty(), event.getSpawnType(), event.getSpawnData());
        }
        return true;
    }
}
