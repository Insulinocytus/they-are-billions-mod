package io.github.insulinocytus.theyarebillions.fabric;

import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.fabricmc.api.ModInitializer;

public final class TheyAreBillionsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TheyAreBillions.init();
    }
}
