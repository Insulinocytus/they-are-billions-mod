package io.github.insulinocytus.theyarebillions.fabric;

import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import io.github.insulinocytus.theyarebillions.HordeZombie;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

public final class TheyAreBillionsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TheyAreBillions.init();
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof HordeZombie zombie) {
                zombie.onUnloaded(level);
            }
        });
    }
}
