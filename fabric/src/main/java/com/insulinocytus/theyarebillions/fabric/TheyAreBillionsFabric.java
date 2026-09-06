package com.insulinocytus.theyarebillions.fabric;

import com.insulinocytus.theyarebillions.ModVersionHandshake;
import com.insulinocytus.theyarebillions.TheyAreBillions;
import com.insulinocytus.theyarebillions.perf.PerfHarness;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TheyAreBillionsFabric implements ModInitializer {
    public static final ResourceLocation VERSION_CHANNEL = ResourceLocation.fromNamespaceAndPath(
            TheyAreBillions.MOD_ID, "version");

    @Override
    public void onInitialize() {
        TheyAreBillions.initialize();
        ServerTickEvents.START_SERVER_TICK.register(PerfHarness::onTickStart);
        ServerTickEvents.END_SERVER_TICK.register(PerfHarness::onTickEnd);
        ServerLoginConnectionEvents.QUERY_START.register((listener, server, sender, synchronizer) -> {
            ServerLoginNetworking.registerReceiver(listener, VERSION_CHANNEL,
                    (server1, listener1, understood, response, synchronizer1, responseSender) -> {
                        String clientVersion = null;
                        if (understood) {
                            try {
                                clientVersion = response.readUtf(64);
                            } catch (RuntimeException ignored) {
                            }
                        }
                        String receivedVersion = clientVersion;
                        synchronizer1.waitFor(server1.submit(() -> {
                            if (!ModVersionHandshake.accepts(localVersion(), receivedVersion)) {
                                listener1.disconnect(Component.literal("They Are Billions version must match the server."));
                            }
                        }));
                    });
            sender.sendPacket(VERSION_CHANNEL, PacketByteBufs.create());
        });
    }

    public static String localVersion() {
        return FabricLoader.getInstance().getModContainer(TheyAreBillions.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("missing");
    }
}
