package com.insulinocytus.theyarebillions.fabric.client;

import com.insulinocytus.theyarebillions.fabric.TheyAreBillionsFabric;
import com.insulinocytus.theyarebillions.horde.HordeIdentityPayload;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public final class TheyAreBillionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLoginNetworking.registerGlobalReceiver(TheyAreBillionsFabric.VERSION_CHANNEL,
                (client, listener, request, callbacksConsumer) -> {
                    var response = PacketByteBufs.create();
                    response.writeUtf(TheyAreBillionsFabric.localVersion());
                    return CompletableFuture.completedFuture(response);
                });
        ClientPlayNetworking.registerGlobalReceiver(HordeIdentityPayload.TYPE, (payload, context) -> {
            if (context.client().level != null) {
                payload.apply(context.client().level);
            }
        });
    }
}
