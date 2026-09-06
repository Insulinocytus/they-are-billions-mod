package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import com.insulinocytus.theyarebillions.horde.HordeGameTests;
import com.insulinocytus.theyarebillions.horde.HordeSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheyAreBillions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HordeSpawnGameTests {
    @GameTest(template = "empty")
    public static void taggedVanillaZombieSpawns(GameTestHelper helper) {
        HordeGameTests.taggedVanillaZombieSpawns(helper);
    }

    @GameTest(template = "empty")
    public static void positionCheckFailBlocksSpawn(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.GRASS_BLOCK);
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
        FailPositionCheck denier = new FailPositionCheck();
        NeoForge.EVENT_BUS.register(denier);
        try {
            helper.assertFalse(
                    HordeSpawner.spawnHordeMember(helper.getLevel(), feet),
                    "PositionCheck FAIL should block spawn");
        } finally {
            NeoForge.EVENT_BUS.unregister(denier);
        }
        helper.succeed();
    }

    public static final class FailPositionCheck {
        @SubscribeEvent
        public void deny(MobSpawnEvent.PositionCheck event) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
