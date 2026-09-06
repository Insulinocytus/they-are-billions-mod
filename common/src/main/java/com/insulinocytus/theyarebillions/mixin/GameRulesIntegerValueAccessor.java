package com.insulinocytus.theyarebillions.mixin;

import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.IntegerValue.class)
public interface GameRulesIntegerValueAccessor {
    @Invoker("create")
    static GameRules.Type<GameRules.IntegerValue> theyarebillions$create(
            int defaultValue,
            int min,
            int max,
            BiConsumer<MinecraftServer, GameRules.IntegerValue> changeCallback) {
        throw new AssertionError();
    }
}
