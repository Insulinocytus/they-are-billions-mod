package com.insulinocytus.theyarebillions.perf;

import java.util.Arrays;

public final class DurationSampler {
    private long[] values;
    private int size;
    private long startNanos;

    public DurationSampler() {
        this(4096);
    }

    public DurationSampler(int initialCapacity) {
        this.values = new long[Math.max(8, initialCapacity)];
    }

    public void begin(long nanos) {
        startNanos = nanos;
    }

    public void end(long nanos) {
        add(nanos - startNanos);
    }

    public synchronized void add(long durationNanos) {
        if (durationNanos < 0) {
            return;
        }
        if (size == values.length) {
            values = Arrays.copyOf(values, values.length * 2);
        }
        values[size++] = durationNanos;
    }

    public synchronized long[] snapshot() {
        return Arrays.copyOf(values, size);
    }

    public synchronized int size() {
        return size;
    }
}
