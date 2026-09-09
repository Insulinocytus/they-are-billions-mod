package com.insulinocytus.theyarebillions.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeAnimationCache {
    private static final Map<Zombie, CachedPose> CACHED_POSES = new WeakHashMap<>();

    private HordeAnimationCache() {
    }

    public static boolean reuseIfCurrent(
            Zombie zombie,
            HumanoidModel<?> model,
            int sampleIntervalTicks,
            float ageInTicks
    ) {
        if (sampleIntervalTicks == 0) {
            CACHED_POSES.remove(zombie);
            return false;
        }
        int sampleBucket = sampleBucket(zombie.tickCount, zombie.getId(), sampleIntervalTicks);
        CachedPose cachedPose = CACHED_POSES.get(zombie);
        if (cachedPose == null || cachedPose.sampleBucket != sampleBucket) {
            return false;
        }
        cachedPose.apply(model, interpolationProgress(ageInTicks, cachedPose.captureAgeInTicks, sampleIntervalTicks));
        return true;
    }

    public static void captureIfNeeded(
            Zombie zombie,
            HumanoidModel<?> model,
            int sampleIntervalTicks,
            float ageInTicks
    ) {
        if (sampleIntervalTicks == 0) {
            return;
        }
        int sampleBucket = sampleBucket(zombie.tickCount, zombie.getId(), sampleIntervalTicks);
        CachedPose cachedPose = CACHED_POSES.computeIfAbsent(zombie, ignored -> new CachedPose());
        if (cachedPose.sampleBucket != sampleBucket) {
            cachedPose.capture(model, sampleBucket, ageInTicks);
            cachedPose.apply(model, interpolationProgress(ageInTicks, cachedPose.captureAgeInTicks, sampleIntervalTicks));
        }
    }

    static int sampleBucket(int tickCount, int entityId, int sampleIntervalTicks) {
        return (tickCount + Math.floorMod(entityId, sampleIntervalTicks)) / sampleIntervalTicks;
    }

    static float interpolationProgress(float ageInTicks, float captureAgeInTicks, int sampleIntervalTicks) {
        return Math.min(1.0F, Math.max(0.0F, (ageInTicks - captureAgeInTicks) / sampleIntervalTicks));
    }

    private static final class CachedPose {
        private static final int VALUES_PER_PART = 9;
        private static final int PART_COUNT = 7;

        private final float[] currentValues = new float[VALUES_PER_PART * PART_COUNT];
        private final float[] previousValues = new float[VALUES_PER_PART * PART_COUNT];
        private int sampleBucket = Integer.MIN_VALUE;
        private float captureAgeInTicks;
        private boolean hasPreviousPose;

        private void capture(HumanoidModel<?> model, int sampleBucket, float ageInTicks) {
            hasPreviousPose = this.sampleBucket != Integer.MIN_VALUE;
            if (hasPreviousPose) {
                System.arraycopy(currentValues, 0, previousValues, 0, currentValues.length);
            }
            capturePart(model.head, 0);
            capturePart(model.hat, 1);
            capturePart(model.body, 2);
            capturePart(model.rightArm, 3);
            capturePart(model.leftArm, 4);
            capturePart(model.rightLeg, 5);
            capturePart(model.leftLeg, 6);
            this.sampleBucket = sampleBucket;
            this.captureAgeInTicks = ageInTicks;
        }

        private void apply(HumanoidModel<?> model, float interpolationProgress) {
            float progress = hasPreviousPose ? interpolationProgress : 1.0F;
            applyPart(model.head, 0, progress);
            applyPart(model.hat, 1, progress);
            applyPart(model.body, 2, progress);
            applyPart(model.rightArm, 3, progress);
            applyPart(model.leftArm, 4, progress);
            applyPart(model.rightLeg, 5, progress);
            applyPart(model.leftLeg, 6, progress);
        }

        private void capturePart(ModelPart part, int partIndex) {
            int offset = partIndex * VALUES_PER_PART;
            currentValues[offset] = part.x;
            currentValues[offset + 1] = part.y;
            currentValues[offset + 2] = part.z;
            currentValues[offset + 3] = part.xRot;
            currentValues[offset + 4] = part.yRot;
            currentValues[offset + 5] = part.zRot;
            currentValues[offset + 6] = part.xScale;
            currentValues[offset + 7] = part.yScale;
            currentValues[offset + 8] = part.zScale;
        }

        private void applyPart(ModelPart part, int partIndex, float interpolationProgress) {
            int offset = partIndex * VALUES_PER_PART;
            part.x = interpolate(offset, interpolationProgress);
            part.y = interpolate(offset + 1, interpolationProgress);
            part.z = interpolate(offset + 2, interpolationProgress);
            part.xRot = interpolate(offset + 3, interpolationProgress);
            part.yRot = interpolate(offset + 4, interpolationProgress);
            part.zRot = interpolate(offset + 5, interpolationProgress);
            part.xScale = interpolate(offset + 6, interpolationProgress);
            part.yScale = interpolate(offset + 7, interpolationProgress);
            part.zScale = interpolate(offset + 8, interpolationProgress);
        }

        private float interpolate(int offset, float interpolationProgress) {
            return previousValues[offset] + (currentValues[offset] - previousValues[offset]) * interpolationProgress;
        }
    }
}
