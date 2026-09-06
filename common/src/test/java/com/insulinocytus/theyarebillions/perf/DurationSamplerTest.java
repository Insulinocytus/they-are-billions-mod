package com.insulinocytus.theyarebillions.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationSamplerTest {
    @Test
    void ignoresEndWithoutMatchingBegin() {
        DurationSampler sampler = new DurationSampler();
        sampler.end(50_000_000L);
        assertEquals(0, sampler.size());
    }

    @Test
    void recordsOnlyPairedBeginAndEnd() {
        DurationSampler sampler = new DurationSampler();
        sampler.begin(10_000_000L);
        sampler.end(25_000_000L);
        sampler.end(40_000_000L);
        long[] values = sampler.snapshot();
        assertEquals(1, values.length);
        assertEquals(15_000_000L, values[0]);
    }
}
