package io.github.insulinocytus.theyarebillions.fabric;

import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.RenderType;

public final class TheyAreBillionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderTypeRegistry.register(RenderType.cutout(), TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get());
    }
}
