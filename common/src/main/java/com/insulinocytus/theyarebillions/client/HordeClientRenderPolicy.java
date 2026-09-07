package com.insulinocytus.theyarebillions.client;

public final class HordeClientRenderPolicy {
    public static final String ENABLED_PROPERTY = "theyarebillions.clientRenderOptimization.enabled";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    static final double REDUCED_ANIMATION_DISTANCE = 64.0;
    static final double FAR_ANIMATION_DISTANCE = 96.0;
    private static final double ANIMATION_DISTANCE_HYSTERESIS = 4.0;
    static final double SHADOW_DISTANCE = 12.0;

    private HordeClientRenderPolicy() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    static int animationSampleIntervalTicks(boolean hordeMember, double distanceToPlayerSqr) {
        return animationSampleIntervalTicks(ENABLED, hordeMember, distanceToPlayerSqr, 0);
    }

    static int animationSampleIntervalTicks(boolean hordeMember, double distanceToPlayerSqr, int previousInterval) {
        return animationSampleIntervalTicks(ENABLED, hordeMember, distanceToPlayerSqr, previousInterval);
    }

    static int animationSampleIntervalTicks(boolean enabled, boolean hordeMember, double distanceToPlayerSqr) {
        return animationSampleIntervalTicks(enabled, hordeMember, distanceToPlayerSqr, 0);
    }

    static int animationSampleIntervalTicks(
            boolean enabled,
            boolean hordeMember,
            double distanceToPlayerSqr,
            int previousInterval
    ) {
        if (!enabled || !hordeMember) {
            return 0;
        }
        if (distanceToPlayerSqr <= REDUCED_ANIMATION_DISTANCE * REDUCED_ANIMATION_DISTANCE) {
            return previousInterval != 0
                    && distanceToPlayerSqr
                    > (REDUCED_ANIMATION_DISTANCE - ANIMATION_DISTANCE_HYSTERESIS)
                    * (REDUCED_ANIMATION_DISTANCE - ANIMATION_DISTANCE_HYSTERESIS)
                    ? previousInterval
                    : 0;
        }
        if (distanceToPlayerSqr <= FAR_ANIMATION_DISTANCE * FAR_ANIMATION_DISTANCE) {
            return previousInterval == 2
                    && distanceToPlayerSqr
                    > (FAR_ANIMATION_DISTANCE - ANIMATION_DISTANCE_HYSTERESIS)
                    * (FAR_ANIMATION_DISTANCE - ANIMATION_DISTANCE_HYSTERESIS)
                    ? 2
                    : 1;
        }
        return 2;
    }

    static boolean renderShadow(boolean hordeMember, double distanceToPlayerSqr) {
        return renderShadow(ENABLED, hordeMember, distanceToPlayerSqr);
    }

    static boolean renderShadow(boolean enabled, boolean hordeMember, double distanceToPlayerSqr) {
        return !enabled
                || !hordeMember
                || distanceToPlayerSqr <= SHADOW_DISTANCE * SHADOW_DISTANCE;
    }
}
