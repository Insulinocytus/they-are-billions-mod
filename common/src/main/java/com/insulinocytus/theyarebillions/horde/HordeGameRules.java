package com.insulinocytus.theyarebillions.horde;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public final class HordeGameRules {
    public static GameRules.Key<GameRules.IntegerValue> HORDE_TARGET;

    private HordeGameRules() {
    }

    public static void register() {
        if (HORDE_TARGET != null) {
            return;
        }
        HORDE_TARGET = GameRules.register(
                HordePlanner.HORDE_TARGET_RULE,
                GameRules.Category.SPAWNING,
                GameRules.IntegerValue.create(
                        HordePlanner.DEFAULT_HORDE_TARGET,
                        0,
                        HordePlanner.MAX_HORDE_TARGET,
                        (server, value) -> {
                        }));
    }

    public static int target(ServerLevel level) {
        return HordePlanner.clampTarget(level.getGameRules().getInt(HORDE_TARGET));
    }
}
