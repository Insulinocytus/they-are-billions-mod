package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import com.insulinocytus.theyarebillions.perf.PerfHarness;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(TheyAreBillions.MOD_ID)
public final class TheyAreBillionsNeoForge {
    public TheyAreBillionsNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(TheyAreBillionsNeoForgeNetworking::registerPayloadHandlers);
        TheyAreBillions.initialize();
        NeoForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                ServerTickEvent.Pre.class,
                event -> PerfHarness.onTickStart(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                ServerTickEvent.Post.class,
                event -> PerfHarness.onTickEnd(event.getServer()));
    }
}
