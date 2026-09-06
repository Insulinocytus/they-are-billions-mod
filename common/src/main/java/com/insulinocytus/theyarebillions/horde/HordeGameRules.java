package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.mixin.GameRulesAccessor;
import com.insulinocytus.theyarebillions.mixin.GameRulesIntegerValueAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public final class HordeGameRules {
    public static final String HORDE_TARGET_RULE_NAME = "theyAreBillionsHordeTarget";

    public static GameRules.Key<GameRules.IntegerValue> HORDE_TARGET;

    private HordeGameRules() {
    }

    public static void register() {
        HORDE_TARGET = GameRulesAccessor.theyarebillions$register(
                HORDE_TARGET_RULE_NAME,
                GameRules.Category.SPAWNING,
                GameRulesIntegerValueAccessor.theyarebillions$create(
                        HordePlanner.DEFAULT_TARGET,
                        HordePlanner.MIN_TARGET,
                        HordePlanner.MAX_TARGET,
                        (server, value) -> {}));
    }

    public static int target(ServerLevel level) {
        return level.getGameRules().getInt(HORDE_TARGET);
    }
}
