package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record HordeIdentityPayload(int entityId, boolean hordeMember) implements CustomPacketPayload {
    public static final Type<HordeIdentityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            TheyAreBillions.MOD_ID, "horde_identity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HordeIdentityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HordeIdentityPayload::entityId,
            ByteBufCodecs.BOOL, HordeIdentityPayload::hordeMember,
            HordeIdentityPayload::new);

    public static HordeIdentityPayload from(Entity entity) {
        return new HordeIdentityPayload(entity.getId(), HordeIdentity.isHordeMember(entity));
    }

    public void apply(Level level) {
        if (level.getEntity(entityId) instanceof HordeMemberState state) {
            state.theyarebillions$setSyncedHordeMember(hordeMember);
        }
    }

    @Override
    public Type<HordeIdentityPayload> type() {
        return TYPE;
    }
}
