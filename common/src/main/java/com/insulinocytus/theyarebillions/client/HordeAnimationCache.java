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

    public static boolean reuseIfCurrent(Zombie zombie, HumanoidModel<?> model, int sampleIntervalTicks) {
        if (sampleIntervalTicks == 0) {
            CACHED_POSES.remove(zombie);
            return false;
        }
        int sampleBucket = zombie.tickCount / sampleIntervalTicks;
        CachedPose cachedPose = CACHED_POSES.get(zombie);
        if (cachedPose == null || cachedPose.sampleBucket != sampleBucket) {
            return false;
        }
        cachedPose.apply(model);
        return true;
    }

    public static void captureIfNeeded(Zombie zombie, HumanoidModel<?> model, int sampleIntervalTicks) {
        if (sampleIntervalTicks == 0) {
            return;
        }
        int sampleBucket = zombie.tickCount / sampleIntervalTicks;
        CachedPose cachedPose = CACHED_POSES.computeIfAbsent(zombie, ignored -> new CachedPose());
        if (cachedPose.sampleBucket != sampleBucket) {
            cachedPose.capture(model, sampleBucket);
        }
    }

    private static final class CachedPose {
        private static final int VALUES_PER_PART = 9;
        private static final int PART_COUNT = 7;

        private final float[] values = new float[VALUES_PER_PART * PART_COUNT];
        private int sampleBucket = Integer.MIN_VALUE;

        private void capture(HumanoidModel<?> model, int sampleBucket) {
            capturePart(model.head, 0);
            capturePart(model.hat, 1);
            capturePart(model.body, 2);
            capturePart(model.rightArm, 3);
            capturePart(model.leftArm, 4);
            capturePart(model.rightLeg, 5);
            capturePart(model.leftLeg, 6);
            this.sampleBucket = sampleBucket;
        }

        private void apply(HumanoidModel<?> model) {
            applyPart(model.head, 0);
            applyPart(model.hat, 1);
            applyPart(model.body, 2);
            applyPart(model.rightArm, 3);
            applyPart(model.leftArm, 4);
            applyPart(model.rightLeg, 5);
            applyPart(model.leftLeg, 6);
        }

        private void capturePart(ModelPart part, int partIndex) {
            int offset = partIndex * VALUES_PER_PART;
            values[offset] = part.x;
            values[offset + 1] = part.y;
            values[offset + 2] = part.z;
            values[offset + 3] = part.xRot;
            values[offset + 4] = part.yRot;
            values[offset + 5] = part.zRot;
            values[offset + 6] = part.xScale;
            values[offset + 7] = part.yScale;
            values[offset + 8] = part.zScale;
        }

        private void applyPart(ModelPart part, int partIndex) {
            int offset = partIndex * VALUES_PER_PART;
            part.x = values[offset];
            part.y = values[offset + 1];
            part.z = values[offset + 2];
            part.xRot = values[offset + 3];
            part.yRot = values[offset + 4];
            part.zRot = values[offset + 5];
            part.xScale = values[offset + 6];
            part.yScale = values[offset + 7];
            part.zScale = values[offset + 8];
        }
    }
}
