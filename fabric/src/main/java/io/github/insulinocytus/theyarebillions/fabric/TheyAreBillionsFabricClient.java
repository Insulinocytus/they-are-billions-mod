package io.github.insulinocytus.theyarebillions.fabric;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ZombieRenderer;

public final class TheyAreBillionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderTypeRegistry.register(RenderType.cutout(), TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get());
        EntityRendererRegistry.register(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE, ZombieRenderer::new);
    }
}
