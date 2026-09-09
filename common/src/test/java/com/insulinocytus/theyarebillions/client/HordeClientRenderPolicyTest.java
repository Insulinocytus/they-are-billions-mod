package com.insulinocytus.theyarebillions.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void distantHordeSamplesVanillaAnimationAtLowerFrequency() {
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

    @Test
    void animationDistanceTiersHaveHysteresis() {
        assertEquals(0, HordeClientRenderPolicy.animationSampleIntervalTicks(true, 63.0 * 63.0, 1));
        assertEquals(0, HordeClientRenderPolicy.animationSampleIntervalTicks(true, 67.0 * 67.0, 0));
        assertEquals(1, HordeClientRenderPolicy.animationSampleIntervalTicks(true, 67.0 * 67.0, 1));
        assertEquals(1, HordeClientRenderPolicy.animationSampleIntervalTicks(true, 95.0 * 95.0, 2));
        assertEquals(1, HordeClientRenderPolicy.animationSampleIntervalTicks(true, 99.0 * 99.0, 1));
        assertEquals(2, HordeClientRenderPolicy.animationSampleIntervalTicks(true, 99.0 * 99.0, 2));
    }

    @Test
    void farAnimationSamplingIsPhasedAndInterpolated() {
        assertEquals(
                HordeAnimationCache.sampleBucket(10, 0, 2),
                HordeAnimationCache.sampleBucket(11, 0, 2));
        assertNotEquals(
                HordeAnimationCache.sampleBucket(11, 0, 2),
                HordeAnimationCache.sampleBucket(11, 1, 2));
        assertEquals(0.0F, HordeAnimationCache.interpolationProgress(10.0F, 10.0F, 2));
        assertEquals(0.5F, HordeAnimationCache.interpolationProgress(11.0F, 10.0F, 2));
        assertEquals(1.0F, HordeAnimationCache.interpolationProgress(12.0F, 10.0F, 2));
    }
}
