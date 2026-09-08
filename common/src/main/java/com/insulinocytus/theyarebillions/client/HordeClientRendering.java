package com.insulinocytus.theyarebillions.client;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public final class HordeClientRendering {
    private static final Map<Entity, Integer> ANIMATION_INTERVALS = new WeakHashMap<>();

    private HordeClientRendering() {
    }

    public static int animationSampleIntervalTicks(Entity entity) {
        boolean hordeMember = HordeIdentity.isClientHordeMember(entity);
        if (!hordeMember) {
            ANIMATION_INTERVALS.remove(entity);
            return 0;
        }
        int interval = HordeClientRenderPolicy.animationSampleIntervalTicks(
                true,
                distanceToPlayerSqr(entity),
                ANIMATION_INTERVALS.getOrDefault(entity, 0));
        ANIMATION_INTERVALS.put(entity, interval);
        return interval;
    }

    public static boolean renderShadow(Entity entity) {
        return HordeClientRenderPolicy.renderShadow(
                HordeIdentity.isClientHordeMember(entity),
                distanceToPlayerSqr(entity));
    }

    public static boolean renderNonessentialEffects(Entity entity) {
        return HordeClientRenderPolicy.renderNonessentialEffects(
                HordeIdentity.isClientHordeMember(entity),
                distanceToPlayerSqr(entity));
    }

    private static double distanceToPlayerSqr(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0 : player.distanceToSqr(entity);
    }
}
