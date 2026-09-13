package io.github.insulinocytus.theyarebillions;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class TheyAreBillions {
    public static final String MOD_ID = "they_are_billions";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final RegistrySupplier<Block> BRAIN_IN_A_JAR_BLOCK = BLOCKS.register("brain_in_a_jar", BrainInAJarBlock::new);
    public static final RegistrySupplier<Item> BRAIN_IN_A_JAR_ITEM = ITEMS.register(
        "brain_in_a_jar", () -> new BlockItem(BRAIN_IN_A_JAR_BLOCK.get(), new Item.Properties())
    );
    public static final RegistrySupplier<CreativeModeTab> CREATIVE_TAB = TABS.register(
        "main",
        () -> CreativeTabRegistry.create(builder -> builder
            .title(Component.translatable("itemGroup." + MOD_ID))
            .icon(() -> new ItemStack(BRAIN_IN_A_JAR_ITEM.get()))
            .displayItems((parameters, output) -> output.accept(BRAIN_IN_A_JAR_ITEM.get()))
        )
    );

    private TheyAreBillions() {
    }

    public static void init() {
        BLOCKS.register();
        ITEMS.register();
        TABS.register();
    }
}
