package com.insulinocytus.theyarebillions.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
    @Invoker("canMoveDirectly")
    boolean theyarebillions$canMoveDirectly(Vec3 from, Vec3 to);
}
