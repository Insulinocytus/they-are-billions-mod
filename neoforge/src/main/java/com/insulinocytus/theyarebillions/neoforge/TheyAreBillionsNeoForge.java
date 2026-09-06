package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@Mod(TheyAreBillions.MOD_ID)
public final class TheyAreBillionsNeoForge {
    public TheyAreBillionsNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(TheyAreBillionsNeoForgeNetworking::registerPayloadHandlers);
        modEventBus.addListener(TheyAreBillionsNeoForge::registerGameTests);
        TheyAreBillions.initialize();
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(TheyAreBillionsNeoForgeGameTests.class);
    }
}
