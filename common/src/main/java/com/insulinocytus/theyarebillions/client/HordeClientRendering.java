package com.insulinocytus.theyarebillions.client;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public final class HordeClientRendering {
    private HordeClientRendering() {
    }

    public static int animationSampleIntervalTicks(Entity entity) {
        return HordeClientRenderPolicy.animationSampleIntervalTicks(
                HordeIdentity.isHordeMember(entity),
                distanceToPlayerSqr(entity));
    }

    public static boolean renderShadow(Entity entity) {
        return HordeClientRenderPolicy.renderShadow(
                HordeIdentity.isHordeMember(entity),
                distanceToPlayerSqr(entity));
    }

    public static boolean renderNonessentialEffects(Entity entity) {
        return HordeClientRenderPolicy.renderNonessentialEffects(
                HordeIdentity.isHordeMember(entity),
                distanceToPlayerSqr(entity));
    }

    private static double distanceToPlayerSqr(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0 : player.distanceToSqr(entity);
    }
}
