package com.insulinocytus.theyarebillions;

import com.insulinocytus.theyarebillions.horde.HordeGameRules;
import com.insulinocytus.theyarebillions.horde.HordeChunkTickets;
import com.insulinocytus.theyarebillions.horde.HordeNavigation;
import com.insulinocytus.theyarebillions.horde.HordeSpawner;
import com.insulinocytus.theyarebillions.perf.PerfHarness;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TheyAreBillions {
    public static final String MOD_ID = "theyarebillions";
    public static final String MOD_NAME = "They Are Billions";
    public static final String VERSION = BuildConstants.VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private TheyAreBillions() {
    }

    public static void initialize() {
        HordeGameRules.register();
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(level -> {
            HordeChunkTickets.onLevelUnload(level);
            HordeNavigation.onLevelUnload(level);
        });
        LOGGER.info("{} {} loaded", MOD_NAME, VERSION);
        PerfHarness.initialize();
    }

    public static void onServerTickEnd(MinecraftServer server) {
        HordeSpawner.onServerTick(server);
        HordeNavigation.onServerTick(server);
        PerfHarness.onTickEnd(server);
    }
}
