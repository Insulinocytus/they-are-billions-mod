package com.insulinocytus.theyarebillions.horde;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class HordeIdentityNetworking {
    private HordeIdentityNetworking() {
    }

    @ExpectPlatform
    public static void sendToTracking(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayer player, Entity entity) {
        throw new AssertionError();
    }
}
