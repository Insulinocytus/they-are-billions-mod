package io.github.insulinocytus.theyarebillions;

import java.util.Objects;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

public final class TheyAreBillions {
    public static final String MOD_ID = "they_are_billions";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(MOD_ID, Registries.MOB_EFFECT);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(MOD_ID, Registries.ENTITY_TYPE);
    public static final RegistrySupplier<Block> BRAIN_IN_A_JAR_BLOCK = BLOCKS.register("brain_in_a_jar", BrainInAJarBlock::new);
    public static final RegistrySupplier<Item> BRAIN_IN_A_JAR_ITEM = ITEMS.register(
        "brain_in_a_jar", () -> new BlockItem(BRAIN_IN_A_JAR_BLOCK.get(), new Item.Properties())
    );
    public static final RegistrySupplier<BlockEntityType<BrainInAJarBlockEntity>> BRAIN_IN_A_JAR_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
        "brain_in_a_jar", () -> BlockEntityType.Builder.of(BrainInAJarBlockEntity::new, BRAIN_IN_A_JAR_BLOCK.get()).build(null)
    );
    public static final RegistrySupplier<MobEffect> DECAY_EFFECT = MOB_EFFECTS.register(
        "decay", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x5B6D3A) { }
    );
    public static final RegistrySupplier<EntityType<HordeZombie>> HORDE_ZOMBIE_ENTITY_TYPE = ENTITY_TYPES.register(
        "horde_zombie",
        () -> {
            EntityDimensions vanillaZombieSize = EntityType.ZOMBIE.getDimensions();
            Vec3 riderSeat = vanillaZombieSize.attachments().get(EntityAttachment.PASSENGER, 0, 0.0F);
            return EntityType.Builder.of(HordeZombie::new, MobCategory.MONSTER)
                .sized(vanillaZombieSize.width(), vanillaZombieSize.height())
                .eyeHeight(vanillaZombieSize.eyeHeight())
                .passengerAttachments(riderSeat)
                .clientTrackingRange(8)
                .build("horde_zombie");
        }
    );
    public static final RegistrySupplier<CreativeModeTab> CREATIVE_TAB = TABS.register(
        "main",
        () -> CreativeTabRegistry.create(builder -> builder
            .title(Component.translatable("itemGroup." + MOD_ID))
            .icon(() -> new ItemStack(BRAIN_IN_A_JAR_ITEM.get()))
            .displayItems((parameters, output) -> output.accept(BRAIN_IN_A_JAR_ITEM.get()))
        )
    );
    private static Holder<MobEffect> decayEffectHolder;

    private TheyAreBillions() {
    }

    public static Holder<MobEffect> decayEffect() {
        if (decayEffectHolder == null) {
            decayEffectHolder = Objects.requireNonNull(
                DECAY_EFFECT.getRegistrar().getHolder(DECAY_EFFECT.getId()),
                "Decay effect is not registered"
            );
        }
        return decayEffectHolder;
    }

    public static void init() {
        BLOCKS.register();
        BLOCK_ENTITY_TYPES.register();
        ITEMS.register();
        TABS.register();
        MOB_EFFECTS.register();
        ENTITY_TYPES.register();
        EntityAttributeRegistry.register(HORDE_ZOMBIE_ENTITY_TYPE, Zombie::createAttributes);
        TickEvent.SERVER_LEVEL_POST.register(HordeZombie::validatePendingOwnership);
        LifecycleEvent.SERVER_STOPPED.register(server -> HordeZombie.clearPendingOwnership());
    }
}
