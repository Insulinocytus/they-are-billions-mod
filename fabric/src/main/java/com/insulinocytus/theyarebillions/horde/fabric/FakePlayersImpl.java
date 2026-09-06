package com.insulinocytus.theyarebillions.horde.fabric;

import net.minecraft.server.level.ServerPlayer;

public final class FakePlayersImpl {
    private FakePlayersImpl() {
    }

    public static boolean isFake(ServerPlayer player) {
        return false;
    }
}
