package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(TheyAreBillions.MOD_ID)
public final class TheyAreBillionsNeoForge {
    public TheyAreBillionsNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(TheyAreBillionsNeoForgeNetworking::registerPayloadHandlers);
        TheyAreBillions.initialize();
    }
}
