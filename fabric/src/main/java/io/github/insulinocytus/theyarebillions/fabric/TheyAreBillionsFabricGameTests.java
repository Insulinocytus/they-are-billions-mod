package io.github.insulinocytus.theyarebillions.fabric;

import io.github.insulinocytus.theyarebillions.gametest.BrainInAJarGameTest;
import io.github.insulinocytus.theyarebillions.gametest.HordeZombieGameTest;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class TheyAreBillionsFabricGameTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void brainInAJar(GameTestHelper helper) {
        BrainInAJarGameTest.verify(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hordeZombie(GameTestHelper helper) {
        HordeZombieGameTest.verify(helper);
    }
}
