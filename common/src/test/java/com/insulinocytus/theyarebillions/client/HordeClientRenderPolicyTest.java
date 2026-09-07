package com.insulinocytus.theyarebillions.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HordeClientRenderPolicyTest {
    @Test
    void optimizationIsEnabledByDefault() {
        assertTrue(HordeClientRenderPolicy.isEnabled());
    }

    @Test
    void nearbyHordeKeepsVanillaAnimationAndVisuals() {
        double nearby = 8.0 * 8.0;
        assertEquals(0, HordeClientRenderPolicy.animationSampleIntervalTicks(true, nearby));
        assertTrue(HordeClientRenderPolicy.renderShadow(true, nearby));
        assertTrue(HordeClientRenderPolicy.renderNonessentialEffects(true, nearby));
    }

    @Test
    void distantHordeSamplesVanillaAnimationAtLowerFrequencyAndDropsEffects() {
        double distant = 80.0 * 80.0;
        assertEquals(1, HordeClientRenderPolicy.animationSampleIntervalTicks(true, distant));
        assertFalse(HordeClientRenderPolicy.renderShadow(true, distant));
        assertFalse(HordeClientRenderPolicy.renderNonessentialEffects(true, distant));
    }

    @Test
    void veryDistantHordeSamplesAnimationEveryTwoTicks() {
        double distant = 128.0 * 128.0;
        assertEquals(2, HordeClientRenderPolicy.animationSampleIntervalTicks(true, distant));
    }

    @Test
    void ordinaryZombiesNeverUseHordeRenderingRules() {
        double distant = 256.0 * 256.0;
        assertEquals(0, HordeClientRenderPolicy.animationSampleIntervalTicks(false, distant));
        assertTrue(HordeClientRenderPolicy.renderShadow(false, distant));
        assertTrue(HordeClientRenderPolicy.renderNonessentialEffects(false, distant));
    }

    @Test
    void disabledOptimizationKeepsAllVanillaRendering() {
        double distant = 256.0 * 256.0;
        assertEquals(0, HordeClientRenderPolicy.animationSampleIntervalTicks(false, true, distant));
        assertTrue(HordeClientRenderPolicy.renderShadow(false, true, distant));
        assertTrue(HordeClientRenderPolicy.renderNonessentialEffects(false, true, distant));
    }
}
