package com.insulinocytus.theyarebillions.horde.neoforge;

import com.insulinocytus.theyarebillions.horde.HordeIdentityPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HordeIdentityNetworkingImpl {
    private HordeIdentityNetworkingImpl() {
    }

    public static void sendToTracking(Entity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, HordeIdentityPayload.from(entity));
    }

    public static void sendToPlayer(ServerPlayer player, Entity entity) {
        PacketDistributor.sendToPlayer(player, HordeIdentityPayload.from(entity));
    }
}
