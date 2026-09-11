package com.insulinocytus.theyarebillions.mixin;

import com.insulinocytus.theyarebillions.horde.HordeIdentity;
import com.insulinocytus.theyarebillions.horde.HordeSimulation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private static final EntityTypeTest<Entity, Entity> theyarebillions$ALL_ENTITIES =
            EntityTypeTest.forClass(Entity.class);

    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> theyarebillions$limitCollisionNeighbors(
            Level level,
            Entity except,
            AABB bounds,
            Predicate<? super Entity> predicate,
            Operation<List<Entity>> original) {
        if (!((Object) this instanceof Zombie zombie) || !HordeIdentity.isHordeMember(zombie)) {
            return original.call(level, except, bounds, predicate);
        }
        int limit = HordeSimulation.collisionNeighborLimit(zombie);
        if (limit == Integer.MAX_VALUE) {
            return original.call(level, except, bounds, predicate);
        }
        List<Entity> neighbors = new ArrayList<>(limit);
        level.getEntities(
                theyarebillions$ALL_ENTITIES,
                bounds,
                entity -> entity != except && predicate.test(entity),
                neighbors,
                limit);
        return neighbors;
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void theyarebillions$noHordeDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (HordeIdentity.isHordeMember((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
