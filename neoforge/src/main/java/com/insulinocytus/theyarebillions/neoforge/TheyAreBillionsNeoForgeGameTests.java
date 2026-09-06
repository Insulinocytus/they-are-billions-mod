package com.insulinocytus.theyarebillions.neoforge;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import com.insulinocytus.theyarebillions.gametest.HordeSpawnGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
}
