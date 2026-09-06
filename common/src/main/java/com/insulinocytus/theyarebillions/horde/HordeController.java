package com.insulinocytus.theyarebillions.horde;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class HordeController {
    private static final Map<ResourceKey<Level>, HordeNightState> NIGHT_STATES = new ConcurrentHashMap<>();

    private HordeController() {
    }

    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(HordeController::tick);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(level -> {
            if (level instanceof ServerLevel serverLevel) {
                NIGHT_STATES.remove(serverLevel.dimension());
            }
        });
    }

    static void tick(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        HordePlan plan = HordePlanner.plan(HordeWorldProbe.capture(level, NIGHT_STATES.get(dimension)));
        NIGHT_STATES.put(dimension, plan.nightState());
        HordeSpawner.spawn(plan, new ServerHordeSpawnWorld(level));
    }
}
