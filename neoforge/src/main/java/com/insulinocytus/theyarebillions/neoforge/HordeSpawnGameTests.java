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
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
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
    public static void hordeMemberDeathDropsNothing(GameTestHelper helper) {
        HordeGameTests.hordeMemberDeathDropsNothing(helper);
    }

    @GameTest(template = "empty")
    public static void namingRemovesHordeMarkAndRestoresVanillaBehavior(GameTestHelper helper) {
        HordeGameTests.namingRemovesHordeMarkAndRestoresVanillaBehavior(helper);
    }

    @GameTest(template = "empty")
    public static void explicitSpawnsStayVanilla(GameTestHelper helper) {
        HordeGameTests.explicitSpawnsStayVanilla(helper);
    }

    @GameTest(template = "empty")
    public static void ordinaryZombieNaturalPopulationStaysTakenOver(GameTestHelper helper) {
        HordeGameTests.ordinaryZombieNaturalPopulationStaysTakenOver(helper);
    }

    @GameTest(template = "empty")
    public static void spawnPlacementCheckFailBlocksSpawn(GameTestHelper helper) {
        assertEventBlocksSpawn(helper, new FailSpawnPlacement(), "SpawnPlacementCheck FAIL should block spawn");
    }

    @GameTest(template = "empty")
    public static void positionCheckFailBlocksSpawn(GameTestHelper helper) {
        assertEventBlocksSpawn(helper, new FailPositionCheck(), "PositionCheck FAIL should block spawn");
    }

    @GameTest(template = "empty")
    public static void finalizeSpawnCancelBlocksSpawn(GameTestHelper helper) {
        assertEventBlocksSpawn(helper, new CancelFinalizeSpawn(), "FinalizeSpawn cancel should block spawn");
    }

    private static void assertEventBlocksSpawn(GameTestHelper helper, Object denier, String message) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.GRASS_BLOCK);
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
        NeoForge.EVENT_BUS.register(denier);
        try {
            helper.assertFalse(HordeSpawner.spawnHordeMember(helper.getLevel(), feet), message);
        } finally {
            NeoForge.EVENT_BUS.unregister(denier);
        }
        helper.succeed();
    }

    public static final class FailSpawnPlacement {
        @SubscribeEvent
        public void deny(MobSpawnEvent.SpawnPlacementCheck event) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    public static final class FailPositionCheck {
        @SubscribeEvent
        public void deny(MobSpawnEvent.PositionCheck event) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    public static final class CancelFinalizeSpawn {
        @SubscribeEvent
        public void deny(FinalizeSpawnEvent event) {
            event.setSpawnCancelled(true);
        }
    }
}
