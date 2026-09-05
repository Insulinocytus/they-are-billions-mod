package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TheyAreBillionsNeoForgeNetworking {
    private TheyAreBillionsNeoForgeNetworking() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(TheyAreBillions.VERSION).playBidirectional(
                VersionPayload.TYPE, VersionPayload.STREAM_CODEC, (payload, context) -> {
                });
    }

    private record VersionPayload() implements CustomPacketPayload {
        private static final Type<VersionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                TheyAreBillions.MOD_ID, "version"));
        private static final StreamCodec<RegistryFriendlyByteBuf, VersionPayload> STREAM_CODEC = StreamCodec.unit(
                new VersionPayload());

        @Override
        public Type<VersionPayload> type() {
            return TYPE;
        }
    }
}
