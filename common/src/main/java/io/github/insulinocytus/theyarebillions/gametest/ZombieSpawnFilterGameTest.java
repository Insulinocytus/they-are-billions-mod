package io.github.insulinocytus.theyarebillions.gametest;

import io.github.insulinocytus.theyarebillions.TheyAreBillions;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;

public final class ZombieSpawnFilterGameTest {
    private static final BlockPos SPAWN_POS = new BlockPos(2, 1, 2);

    private ZombieSpawnFilterGameTest() {
    }

    public static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var spawnPos = helper.absolutePos(SPAWN_POS);

        EntityType.ZOMBIE.spawn(level, spawnPos, MobSpawnType.NATURAL);
        EntityType.ZOMBIE.spawn(level, spawnPos, MobSpawnType.SPAWNER);
        EntityType.ZOMBIE.spawn(level, spawnPos, MobSpawnType.TRIAL_SPAWNER);
        helper.assertEntityNotPresent(EntityType.ZOMBIE);

        TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().spawn(level, spawnPos, MobSpawnType.NATURAL);
        helper.assertEntityPresent(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get());

        EntityType.ZOMBIE.spawn(level, spawnPos, MobSpawnType.SPAWN_EGG);
        EntityType.ZOMBIE.spawn(level, spawnPos, MobSpawnType.COMMAND);
        EntityType.ZOMBIE.spawn(level, spawnPos, MobSpawnType.EVENT);
        helper.assertEntitiesPresent(EntityType.ZOMBIE, 3);
        helper.succeed();
    }
}
