package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;

public final class HordeSpawnAccessImpl {
    private HordeSpawnAccessImpl() {
    }

    public static boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    public static boolean checkSpawnPlacement(ServerLevel level, BlockPos pos) {
        // EventHooks.checkSpawnPlacements is the SpawnPlacementCheck publisher.
        // Pass non-light default so torches cannot shut the spawn ring.
        return EventHooks.checkSpawnPlacements(
                EntityType.ZOMBIE,
                level,
                MobSpawnType.EVENT,
                pos,
                level.random,
                HordeSpawnAccess.isVanillaNonLightSpawnRulesOk(level, pos));
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

    public static boolean finalizeHordeSpawn(Mob mob, ServerLevel level) {
        EventHooks.finalizeMobSpawn(
                mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        return !mob.isSpawnCancelled();
    }
}
