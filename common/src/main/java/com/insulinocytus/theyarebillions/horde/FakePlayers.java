package com.insulinocytus.theyarebillions.horde;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;

public final class FakePlayers {
    private FakePlayers() {
    }

    @ExpectPlatform
    public static boolean isFake(ServerPlayer player) {
        throw new AssertionError();
    }
}
