package com.insulinocytus.theyarebillions.horde.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class FakePlayersImpl {
    private FakePlayersImpl() {
    }

    public static boolean isFake(ServerPlayer player) {
        return player instanceof FakePlayer;
    }
}
