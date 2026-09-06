package com.insulinocytus.theyarebillions.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.class)
public interface GameRulesAccessor {
    @Invoker("register")
    static <T extends GameRules.Value<T>> GameRules.Key<T> theyarebillions$register(
            String name, GameRules.Category category, GameRules.Type<T> type) {
        throw new AssertionError();
    }
}
