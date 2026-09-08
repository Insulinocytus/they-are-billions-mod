package com.insulinocytus.theyarebillions.horde.fabric;

import com.insulinocytus.theyarebillions.horde.HordeIdentityPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class HordeIdentityNetworkingImpl {
    private HordeIdentityNetworkingImpl() {
    }

    public static void sendToTracking(Entity entity) {
        HordeIdentityPayload payload = HordeIdentityPayload.from(entity);
        PlayerLookup.tracking(entity).forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendToPlayer(ServerPlayer player, Entity entity) {
        ServerPlayNetworking.send(player, HordeIdentityPayload.from(entity));
    }
}
