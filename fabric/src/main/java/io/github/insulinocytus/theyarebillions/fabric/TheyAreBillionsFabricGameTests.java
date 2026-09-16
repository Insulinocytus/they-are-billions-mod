package io.github.insulinocytus.theyarebillions.fabric;

import io.github.insulinocytus.theyarebillions.gametest.BrainInAJarGameTest;
import io.github.insulinocytus.theyarebillions.gametest.BrainInAJarHordeGameTest;
import io.github.insulinocytus.theyarebillions.gametest.HordeZombieGameTest;
import io.github.insulinocytus.theyarebillions.gametest.ZombieSpawnFilterGameTest;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class TheyAreBillionsFabricGameTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void brainInAJar(GameTestHelper helper) {
        BrainInAJarGameTest.verify(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brain_in_a_jar_horde_tickets", timeoutTicks = 200)
    public void brainInAJarEntityTicking(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyEntityTickingRange(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brain_in_a_jar_horde_direction", timeoutTicks = 600)
    public void brainInAJarDirectionalHorde(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyDirectionalNightHorde(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brain_in_a_jar_horde_capacity", timeoutTicks = 1200)
    public void brainInAJarHordeCapacity(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyCapacityAndGates(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brain_in_a_jar_independent_hordes", timeoutTicks = 4000)
    public void brainInAJarIndependentHordes(GameTestHelper helper) {
        BrainInAJarHordeGameTest.verifyIndependentHordes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hordeZombie(GameTestHelper helper) {
        HordeZombieGameTest.verify(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void zombieSpawnFilter(GameTestHelper helper) {
        ZombieSpawnFilterGameTest.verify(helper);
    }
}
