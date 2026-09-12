package com.insulinocytus.theyarebillions.fabric;

import com.insulinocytus.theyarebillions.horde.HordeGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class HordeSpawnGameTests {
    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void taggedVanillaZombieSpawns(GameTestHelper helper) {
        HordeGameTests.taggedVanillaZombieSpawns(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void hordeMemberKeepsItsPlayerGroup(GameTestHelper helper) {
        HordeGameTests.hordeMemberKeepsItsPlayerGroup(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void hordeMemberDeathDropsNothing(GameTestHelper helper) {
        HordeGameTests.hordeMemberDeathDropsNothing(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void removingHordeTagRestoresServerBehavior(GameTestHelper helper) {
        HordeGameTests.removingHordeTagRestoresServerBehavior(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void namingRemovesHordeMarkAndRestoresVanillaBehavior(GameTestHelper helper) {
        HordeGameTests.namingRemovesHordeMarkAndRestoresVanillaBehavior(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void explicitSpawnsStayVanilla(GameTestHelper helper) {
        HordeGameTests.explicitSpawnsStayVanilla(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void ordinaryZombieNaturalPopulationStaysTakenOver(GameTestHelper helper) {
        HordeGameTests.ordinaryZombieNaturalPopulationStaysTakenOver(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE, timeoutTicks = 200)
    public void nearbyMeleeAttackStillHits(GameTestHelper helper) {
        HordeGameTests.nearbyMeleeAttackStillHits(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE, timeoutTicks = 200)
    public void hordeMemberPassesOneBlockDoorway(GameTestHelper helper) {
        HordeGameTests.hordeMemberPassesOneBlockDoorway(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE, timeoutTicks = 200)
    public void nearbyPlayerStillGetsPushed(GameTestHelper helper) {
        HordeGameTests.nearbyPlayerStillGetsPushed(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE, timeoutTicks = 400)
    public void hordeTicketMakesChunkEntityTick(GameTestHelper helper) {
        HordeGameTests.hordeTicketMakesChunkEntityTick(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void hordeMemberIgnoresVanillaDistanceDespawn(GameTestHelper helper) {
        HordeGameTests.hordeMemberIgnoresVanillaDistanceDespawn(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE, timeoutTicks = 40)
    public void hordeDiggingUsesEmptySurvivalFakePlayerDrops(GameTestHelper helper) {
        HordeGameTests.hordeDiggingUsesEmptySurvivalFakePlayerDrops(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE, timeoutTicks = 400)
    public void daytimeCleanupRemovesSunlitWithoutDrops(GameTestHelper helper) {
        HordeGameTests.daytimeCleanupRemovesSunlitWithoutDrops(helper);
    }

    @GameTest(template = HordeGameTests.EMPTY_TEMPLATE)
    public void daytimeCleanupReleasesHordeTickets(GameTestHelper helper) {
        HordeGameTests.daytimeCleanupReleasesHordeTickets(helper);
    }
}
