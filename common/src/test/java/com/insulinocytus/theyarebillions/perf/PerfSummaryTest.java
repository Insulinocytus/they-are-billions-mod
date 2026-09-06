package com.insulinocytus.theyarebillions.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PerfSummaryTest {
    @Test
    void emptySamplesAreZero() {
        PerfSummary summary = PerfSummary.of(new long[0]);
        assertEquals(0, summary.samples());
        assertEquals(0.0, summary.averageMs());
        assertEquals(0.0, summary.p95Ms());
    }

    @Test
    void nearestRankPercentilesMatchOneToOneHundredMilliseconds() {
        long[] samples = new long[100];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i + 1) * 1_000_000L;
        }
        PerfSummary summary = PerfSummary.of(samples);
        assertEquals(100, summary.samples());
        assertEquals(50.5, summary.averageMs(), 0.0001);
        assertEquals(50.0, summary.p50Ms(), 0.0001);
        assertEquals(95.0, summary.p95Ms(), 0.0001);
        assertEquals(99.0, summary.p99Ms(), 0.0001);
        assertEquals(100.0, summary.maxMs(), 0.0001);
    }
}
