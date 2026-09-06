package com.insulinocytus.theyarebillions.gametest;

import com.insulinocytus.theyarebillions.horde.HordeMembers;
import com.insulinocytus.theyarebillions.horde.HordeSpawns;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;

public final class HordeSpawnGameTests {
    @GameTest(template = "theyarebillions:empty_platform", timeoutTicks = 200)
    public static void spawnsMarkedVanillaZombie(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos stand = new BlockPos(2, 1, 2);
        helper.setBlock(stand.below(), Blocks.STONE);
        helper.setBlock(stand, Blocks.AIR);
        helper.setBlock(stand.above(), Blocks.AIR);
        boolean spawned = HordeSpawns.spawnHordeMember(helper.getLevel(), helper.absolutePos(stand));
        helper.assertTrue(spawned, "expected a horde member to spawn");
        List<Zombie> zombies = helper.getEntities(EntityType.ZOMBIE);
        helper.assertFalse(zombies.isEmpty(), "expected a vanilla zombie");
        Zombie zombie = zombies.getFirst();
        helper.assertTrue(zombie.getType() == EntityType.ZOMBIE, "expected EntityType.ZOMBIE");
        helper.assertTrue(HordeMembers.isHordeMember(zombie), "expected persistent horde mark");
        helper.succeed();
    }

    @GameTest(template = "theyarebillions:empty_platform", timeoutTicks = 200)
    public static void vanillaNaturalZombiePopulationIsOwned(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        boolean allowed = SpawnPlacements.checkSpawnRules(
                EntityType.ZOMBIE, helper.getLevel(), MobSpawnType.NATURAL, pos, helper.getLevel().getRandom());
        helper.assertFalse(allowed, "vanilla natural zombie population is owned by the mod");
        helper.succeed();
    }
}
