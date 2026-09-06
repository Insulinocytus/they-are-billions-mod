package com.insulinocytus.theyarebillions.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PerfSettingsTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("theyarebillions.perf.enabled");
        System.clearProperty("theyarebillions.perf.mode");
        System.clearProperty("theyarebillions.perf.scenario");
        System.clearProperty("theyarebillions.perf.loader");
        System.clearProperty("theyarebillions.perf.durationTicks");
        System.clearProperty("theyarebillions.perf.request");
    }

    @Test
    void disabledByDefault() {
        assertFalse(PerfSettings.load().enabled());
    }

    @Test
    void idleModeEnablesHarnessAndKeepsDefaultTenMinuteWindow() {
        System.setProperty("theyarebillions.perf.mode", "idle");
        System.setProperty("theyarebillions.perf.scenario", "walled-mountain");
        System.setProperty("theyarebillions.perf.loader", "fabric");
        PerfSettings settings = PerfSettings.load();
        assertTrue(settings.enabled());
        assertTrue(settings.isIdle());
        assertEquals("walled-mountain", settings.scenario());
        assertEquals("fabric", settings.loader());
        assertEquals(PerfSettings.DEFAULT_DURATION_TICKS, settings.durationTicks());
        assertEquals(10 * 60 * 20, settings.durationTicks());
    }
}
