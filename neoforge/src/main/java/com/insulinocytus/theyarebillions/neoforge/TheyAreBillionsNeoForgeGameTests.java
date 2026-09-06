package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import com.insulinocytus.theyarebillions.gametest.HordeSpawnGameTests;
import com.insulinocytus.theyarebillions.horde.HordeSpawns;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheyAreBillions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheyAreBillionsNeoForgeGameTests {
    private TheyAreBillionsNeoForgeGameTests() {
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public static void spawnsMarkedVanillaZombie(GameTestHelper helper) {
        HordeSpawnGameTests.spawnsMarkedVanillaZombie(helper);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public static void vanillaNaturalZombiePopulationIsOwned(GameTestHelper helper) {
        HordeSpawnGameTests.vanillaNaturalZombiePopulationIsOwned(helper);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public static void hordeMemberSurvivesVanillaDistanceDespawn(GameTestHelper helper) {
        HordeSpawnGameTests.hordeMemberSurvivesVanillaDistanceDespawn(helper);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public static void cancelledInitializationStillSpawns(GameTestHelper helper) {
        withFinalizeListener(event -> event.setCanceled(true), () -> HordeSpawnGameTests.spawnsMarkedVanillaZombie(helper));
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public static void spawnCancelledDoesNotCreateHordeMember(GameTestHelper helper) {
        withFinalizeListener(event -> event.setSpawnCancelled(true), () -> {
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            BlockPos stand = new BlockPos(2, 1, 2);
            helper.setBlock(stand.below(), Blocks.STONE);
            helper.setBlock(stand, Blocks.AIR);
            helper.setBlock(stand.above(), Blocks.AIR);
            boolean spawned = HordeSpawns.spawnHordeMember(helper.getLevel(), helper.absolutePos(stand));
            helper.assertFalse(spawned, "setSpawnCancelled should abort spawning");
            helper.assertTrue(helper.getEntities(EntityType.ZOMBIE).isEmpty(), "no zombie should be added");
            helper.succeed();
        });
    }

    private static void withFinalizeListener(Consumer<FinalizeSpawnEvent> listener, Runnable action) {
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            action.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }
}
