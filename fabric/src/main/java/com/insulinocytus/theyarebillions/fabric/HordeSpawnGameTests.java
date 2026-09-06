package com.insulinocytus.theyarebillions.fabric;

import com.insulinocytus.theyarebillions.horde.HordeGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class HordeSpawnGameTests {
    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void taggedVanillaZombieSpawns(GameTestHelper helper) {
        HordeGameTests.taggedVanillaZombieSpawns(helper);
    }
}
