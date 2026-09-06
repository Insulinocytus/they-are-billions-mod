package com.insulinocytus.theyarebillions.horde;

import java.util.function.Supplier;

public final class HordeSpawnContext {
    private static final ThreadLocal<Boolean> IGNORE_LIGHT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private HordeSpawnContext() {
    }

    public static boolean ignoresLight() {
        return IGNORE_LIGHT.get();
    }

    public static <T> T ignoringLight(Supplier<T> action) {
        IGNORE_LIGHT.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            IGNORE_LIGHT.set(Boolean.FALSE);
        }
    }
}
