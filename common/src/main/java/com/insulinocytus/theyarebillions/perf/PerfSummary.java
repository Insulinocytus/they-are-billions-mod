package com.insulinocytus.theyarebillions.perf;

import java.util.Arrays;

public record PerfSummary(
        int samples,
        double averageMs,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double maxMs
) {
    public static PerfSummary of(long[] durationNanos) {
        if (durationNanos.length == 0) {
            return new PerfSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        long[] sorted = durationNanos.clone();
        Arrays.sort(sorted);
        long total = 0L;
        long max = 0L;
        for (long value : sorted) {
            total += value;
            if (value > max) {
                max = value;
            }
        }
        return new PerfSummary(
                sorted.length,
                total / (1_000_000.0 * sorted.length),
                percentileMs(sorted, 50.0),
                percentileMs(sorted, 95.0),
                percentileMs(sorted, 99.0),
                max / 1_000_000.0
        );
    }

    static double percentileMs(long[] sortedNanos, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedNanos.length) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= sortedNanos.length) {
            index = sortedNanos.length - 1;
        }
        return sortedNanos[index] / 1_000_000.0;
    }
}
