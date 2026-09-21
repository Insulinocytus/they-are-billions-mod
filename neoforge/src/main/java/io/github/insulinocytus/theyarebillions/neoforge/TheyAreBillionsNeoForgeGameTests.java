package io.github.insulinocytus.theyarebillions.neoforge;

import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import io.github.insulinocytus.theyarebillions.gametest.BrainInAJarGameTest;
import io.github.insulinocytus.theyarebillions.gametest.BrainInAJarHordeGameTest;
import io.github.insulinocytus.theyarebillions.gametest.HordeZombieGameTest;
import io.github.insulinocytus.theyarebillions.gametest.ZombieSpawnFilterGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheyAreBillions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheyAreBillionsNeoForgeGameTests {
    @GameTest(template = "empty")
    public static void brainInAJar(GameTestHelper helper) {
        BrainInAJarGameTest.verify(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_horde_tickets", timeoutTicks = 200)
    public static void brainInAJarEntityTicking(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyEntityTickingRange(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_horde_direction", timeoutTicks = 600)
    public static void brainInAJarDirectionalHorde(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyDirectionalNightHorde(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_sunrise", timeoutTicks = 400)
    public static void brainInAJarSunriseStopsRefill(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifySunriseStopsRefill(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_horde_capacity", timeoutTicks = 1200)
    public static void brainInAJarHordeCapacity(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyCapacityAndGates(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_independent_hordes", timeoutTicks = 4000)
    public static void brainInAJarIndependentHordes(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyIndependentHordes(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_remote_ownership", timeoutTicks = 4000)
    public static void brainInAJarRemoteOwnershipRecovery(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyRemoteOwnershipRecovery(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_unowned_unload", timeoutTicks = 1200)
    public static void brainInAJarUnownedRuntimeUnload(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyUnownedRuntimeUnload(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_horde_targeting", timeoutTicks = 4000)
    public static void brainInAJarHordeTargeting(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyTargetingStateMachine(helper);
    }

    @GameTest(template = "empty", batch = "brain_in_a_jar_horde_rebinding", timeoutTicks = 4000)
    public static void brainInAJarHordeRebinding(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyLostOwnershipAndRebinding(helper);
    }

    @GameTest(template = "empty")
    public static void hordeZombie(GameTestHelper helper) {
        HordeZombieGameTest.verify(helper);
    }

    @GameTest(template = "empty", batch = "horde_zombie_sunrise", timeoutTicks = 100)
    public static void hordeZombieSunriseDeath(GameTestHelper helper) {
        HordeZombieGameTest.verifySunriseDeath(helper);
    }

    @GameTest(template = "empty", batch = "horde_zombie_decay", timeoutTicks = 800)
    public static void hordeZombieCoveredDecay(GameTestHelper helper) {
        HordeZombieGameTest.verifyCoveredDecay(helper);
    }

    @GameTest(template = "empty")
    public static void zombieSpawnFilter(GameTestHelper helper) {
        ZombieSpawnFilterGameTest.verify(helper);
    }
}
