package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HordeSimulationTest {
    @Test
    void usesExactInitialDistanceTiers() {
        assertEquals(HordeSimulation.Tier.NEAR, HordeSimulation.tier(null, squared(24.0)));
        assertEquals(HordeSimulation.Tier.MEDIUM, HordeSimulation.tier(null, squared(24.01)));
        assertEquals(HordeSimulation.Tier.MEDIUM, HordeSimulation.tier(null, squared(64.0)));
        assertEquals(HordeSimulation.Tier.FAR, HordeSimulation.tier(null, squared(64.01)));
    }

    @Test
    void keepsDistanceTiersStableAcrossFourBlockHysteresis() {
        assertEquals(HordeSimulation.Tier.NEAR, HordeSimulation.tier(HordeSimulation.Tier.NEAR, squared(24.0)));
        assertEquals(HordeSimulation.Tier.MEDIUM, HordeSimulation.tier(HordeSimulation.Tier.NEAR, squared(24.01)));
        assertEquals(HordeSimulation.Tier.MEDIUM, HordeSimulation.tier(HordeSimulation.Tier.MEDIUM, squared(20.01)));
        assertEquals(HordeSimulation.Tier.NEAR, HordeSimulation.tier(HordeSimulation.Tier.MEDIUM, squared(20.0)));
        assertEquals(HordeSimulation.Tier.FAR, HordeSimulation.tier(HordeSimulation.Tier.MEDIUM, squared(64.01)));
        assertEquals(HordeSimulation.Tier.FAR, HordeSimulation.tier(HordeSimulation.Tier.FAR, squared(60.01)));
        assertEquals(HordeSimulation.Tier.MEDIUM, HordeSimulation.tier(HordeSimulation.Tier.FAR, squared(60.0)));
    }

    @Test
    void staggersCollisionAndRepathWorkWithinTierLimits() {
        assertEquals(Integer.MAX_VALUE, HordeSimulation.Tier.NEAR.collisionNeighborLimit());
        assertEquals(8, HordeSimulation.Tier.MEDIUM.collisionNeighborLimit());
        assertEquals(4, HordeSimulation.Tier.FAR.collisionNeighborLimit());

        assertEquals(true, HordeSimulation.scheduled(HordeSimulation.Tier.NEAR, 10, 3));
        assertEquals(false, HordeSimulation.scheduled(HordeSimulation.Tier.MEDIUM, 10, 3));
        assertEquals(true, HordeSimulation.scheduled(HordeSimulation.Tier.MEDIUM, 11, 3));
        assertEquals(3, HordeSimulation.delayUntilScheduled(HordeSimulation.Tier.FAR, 8, -1));
        assertEquals(0, HordeSimulation.delayUntilScheduled(HordeSimulation.Tier.FAR, 11, -1));
    }

    private static double squared(double distance) {
        return distance * distance;
    }
}
