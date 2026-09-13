package io.github.insulinocytus.theyarebillions.neoforge;

import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = TheyAreBillions.MOD_ID)
public final class TheyAreBillionsNeoForgeClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RenderTypeRegistry.register(RenderType.cutout(), TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get());
    }
}
