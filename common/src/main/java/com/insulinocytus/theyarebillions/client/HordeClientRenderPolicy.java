package com.insulinocytus.theyarebillions.client;

public final class HordeClientRenderPolicy {
    public static final String ENABLED_PROPERTY = "theyarebillions.clientRenderOptimization.enabled";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    static final double REDUCED_ANIMATION_DISTANCE = 64.0;
    static final double FAR_ANIMATION_DISTANCE = 96.0;
    static final double SHADOW_DISTANCE = 12.0;
    static final double NONESSENTIAL_EFFECT_DISTANCE = 48.0;

    private HordeClientRenderPolicy() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    static int animationSampleIntervalTicks(boolean hordeMember, double distanceToPlayerSqr) {
        return animationSampleIntervalTicks(ENABLED, hordeMember, distanceToPlayerSqr);
    }

    static int animationSampleIntervalTicks(boolean enabled, boolean hordeMember, double distanceToPlayerSqr) {
        if (!enabled
                || !hordeMember
                || distanceToPlayerSqr <= REDUCED_ANIMATION_DISTANCE * REDUCED_ANIMATION_DISTANCE) {
            return 0;
        }
        return distanceToPlayerSqr <= FAR_ANIMATION_DISTANCE * FAR_ANIMATION_DISTANCE ? 1 : 2;
    }

    static boolean renderShadow(boolean hordeMember, double distanceToPlayerSqr) {
        return renderShadow(ENABLED, hordeMember, distanceToPlayerSqr);
    }

    static boolean renderShadow(boolean enabled, boolean hordeMember, double distanceToPlayerSqr) {
        return !enabled
                || !hordeMember
                || distanceToPlayerSqr <= SHADOW_DISTANCE * SHADOW_DISTANCE;
    }

    static boolean renderNonessentialEffects(boolean hordeMember, double distanceToPlayerSqr) {
        return renderNonessentialEffects(ENABLED, hordeMember, distanceToPlayerSqr);
    }

    static boolean renderNonessentialEffects(boolean enabled, boolean hordeMember, double distanceToPlayerSqr) {
        return !enabled
                || !hordeMember
                || distanceToPlayerSqr <= NONESSENTIAL_EFFECT_DISTANCE * NONESSENTIAL_EFFECT_DISTANCE;
    }
}
