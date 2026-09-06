package com.insulinocytus.theyarebillions.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;

public final class HordeGameTests {
    public static final String EMPTY_TEMPLATE = "theyarebillions:empty";

    private HordeGameTests() {
    }

    public static void taggedVanillaZombieSpawns(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.GRASS_BLOCK);
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(HordeSpawner.spawnHordeMember(helper.getLevel(), feet), "horde member should spawn");
        Zombie zombie = null;
        for (Zombie candidate : helper.getEntities(EntityType.ZOMBIE)) {
            if (candidate.getType() == EntityType.ZOMBIE) {
                zombie = candidate;
                break;
            }
        }
        helper.assertTrue(zombie != null, "vanilla zombie should exist");
        helper.assertTrue(HordeSpawner.isHordeMember(zombie), "zombie should carry horde tag");
        helper.assertTrue(HordeSpawner.hasPersistentHordeTag(zombie), "horde tag should persist in NBT");
        helper.succeed();
    }
}
