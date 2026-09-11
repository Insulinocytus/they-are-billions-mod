package com.insulinocytus.theyarebillions.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class HordeBlockBreaking {
    private static final double STEP_DISTANCE = 0.6;

    private HordeBlockBreaking() {
    }

    static StartResult start(ServerLevel level, Zombie zombie, BlockPos nextStep, BlockPos deniedPos) {
        Candidate candidate = blockingBlock(level, zombie, nextStep);
        if (candidate == null) {
            return StartResult.NONE;
        }
        if (candidate.pos().equals(deniedPos)) {
            return StartResult.NONE;
        }

        ServerPlayer player = prepareActionPlayer(level, zombie);
        if (!canBreak(level, player, candidate.pos())) {
            return new StartResult(null, candidate.pos());
        }

        BlockState state = level.getBlockState(candidate.pos());
        player = prepareSpeedPlayer(level, zombie);
        float progressPerTick = state.getDestroyProgress(player, level, candidate.pos());
        if (!(progressPerTick > 0.0F)) {
            return StartResult.NONE;
        }
        progressPerTick = Math.min(1.0F, progressPerTick);

        player = prepareActionPlayer(level, zombie);
        if (!HordeBlockBreakingAccess.start(
                level, zombie, player, candidate.pos(), candidate.face())) {
            return new StartResult(null, candidate.pos());
        }

        zombie.getNavigation().stop();
        return new StartResult(new Digging(candidate.pos(), state, progressPerTick, zombie), null);
    }

    static TickResult tick(ServerLevel level, Zombie zombie, Digging digging) {
        if (digging.finished) {
            return digging.result;
        }
        if (!level.getBlockState(digging.pos).equals(digging.state)) {
            finish(level, digging, TickResult.COMPLETE);
            return TickResult.COMPLETE;
        }
        if (!isAdjacent(zombie, digging.pos)) {
            finish(level, digging, TickResult.COMPLETE);
            return TickResult.COMPLETE;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            finish(level, digging, TickResult.DENIED);
            return TickResult.DENIED;
        }

        long tick = level.getGameTime();
        if (digging.lastProgressTick != tick) {
            digging.progress += digging.progressPerTick;
            digging.lastProgressTick = tick;
        }
        if (tick % 4 == 0) {
            zombie.swing(InteractionHand.MAIN_HAND);
            if (digging.lastSoundTick != tick) {
                SoundType sound = HordeBlockBreakingAccess.sound(level, zombie, digging.pos, digging.state);
                level.playSound(
                        null,
                        digging.pos,
                        sound.getHitSound(),
                        SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 8.0F,
                        sound.getPitch() * 0.5F);
                digging.lastSoundTick = tick;
            }
        }
        int stage = Math.min(9, (int) (digging.progress * 10.0F));
        if (stage != digging.lastStage) {
            level.destroyBlockProgress(digging.breakerId, digging.pos, stage);
            digging.lastStage = stage;
        }
        if (digging.progress < 1.0F) {
            return TickResult.ACTIVE;
        }

        ServerPlayer player = prepareActionPlayer(level, zombie);
        if (!canBreak(level, player, digging.pos)
                || !HordeBlockBreakingAccess.destroy(level, zombie, digging.pos)
                || level.getBlockState(digging.pos).equals(digging.state)) {
            finish(level, digging, TickResult.DENIED);
            return TickResult.DENIED;
        }

        level.levelEvent(2001, digging.pos, Block.getId(digging.state));
        finish(level, digging, TickResult.COMPLETE);
        return TickResult.COMPLETE;
    }

    static void stop(ServerLevel level, Zombie zombie, Digging digging) {
        finish(level, digging, TickResult.COMPLETE);
    }

    private static void finish(ServerLevel level, Digging digging, TickResult result) {
        if (digging.finished) {
            return;
        }
        if (digging.lastStage != -1) {
            level.destroyBlockProgress(digging.breakerId, digging.pos, -1);
        }
        digging.finished = true;
        digging.result = result;
    }
    private static ServerPlayer prepareSpeedPlayer(ServerLevel level, Zombie zombie) {
        ServerPlayer player = resetPlayer(level);
        player.moveTo(zombie.getX(), level.getMaxBuildHeight() + 16.0, zombie.getZ(), 0.0F, 0.0F);
        player.setOnGround(true);
        return player;
    }

    private static ServerPlayer prepareActionPlayer(ServerLevel level, Zombie zombie) {
        ServerPlayer player = resetPlayer(level);
        player.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), zombie.getXRot());
        player.setOnGround(true);
        return player;
    }

    private static ServerPlayer resetPlayer(ServerLevel level) {
        ServerPlayer player = HordeBlockBreakingAccess.player(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (MobEffectInstance effect : player.getActiveEffects().toArray(MobEffectInstance[]::new)) {
            player.removeEffect(effect.getEffect());
        }
        player.setSwimming(false);
        return player;
    }
    private static boolean canBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && level.getWorldBorder().isWithinBounds(pos)
                && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, GameType.SURVIVAL);
    }

    private static Candidate blockingBlock(ServerLevel level, Zombie zombie, BlockPos nextStep) {
        BlockPos feet = zombie.blockPosition();
        int dx = Integer.compare(nextStep.getX(), feet.getX());
        int dz = Integer.compare(nextStep.getZ(), feet.getZ());
        if (dx == 0 && dz == 0) {
            return null;
        }

        int[][] offsets = orderedOffsets(dx, dz, nextStep.getX() - feet.getX(), nextStep.getZ() - feet.getZ());
        for (int[] offset : offsets) {
            AABB moved = zombie.getBoundingBox().move(offset[0] * STEP_DISTANCE, 0.0, offset[1] * STEP_DISTANCE);
            for (int y = Mth.floor(moved.minY); y < Mth.ceil(moved.maxY); y++) {
                BlockPos pos = new BlockPos(feet.getX() + offset[0], y, feet.getZ() + offset[1]);
                BlockState state = level.getBlockState(pos);
                VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.of(zombie));
                if (!shape.isEmpty()
                        && Shapes.joinIsNotEmpty(
                                Shapes.create(moved),
                                shape.move(pos.getX(), pos.getY(), pos.getZ()),
                                BooleanOp.AND)) {
                    return new Candidate(pos, faceFor(offset[0], offset[1]));
                }
            }
        }
        return null;
    }

    private static int[][] orderedOffsets(int dx, int dz, int rawDx, int rawDz) {
        if (dx == 0) {
            return new int[][] {{0, dz}};
        }
        if (dz == 0) {
            return new int[][] {{dx, 0}};
        }
        if (Math.abs(rawDx) >= Math.abs(rawDz)) {
            return new int[][] {{dx, 0}, {0, dz}, {dx, dz}};
        }
        return new int[][] {{0, dz}, {dx, 0}, {dx, dz}};
    }

    private static Direction faceFor(int dx, int dz) {
        if (dx != 0) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static boolean isAdjacent(Zombie zombie, BlockPos pos) {
        BlockPos feet = zombie.blockPosition();
        return Math.abs(pos.getX() - feet.getX()) <= 1
                && Math.abs(pos.getZ() - feet.getZ()) <= 1
                && pos.getY() >= feet.getY()
                && pos.getY() <= feet.getY() + 1;
    }

    record StartResult(Digging digging, BlockPos deniedPos) {
        private static final StartResult NONE = new StartResult(null, null);
    }

    enum TickResult {
        ACTIVE,
        COMPLETE,
        DENIED
    }

    static final class Digging {
        private final BlockPos pos;
        private final BlockState state;
        private final float progressPerTick;
        private final int breakerId;
        private float progress;
        private int lastStage = -1;
        private long lastSoundTick = Long.MIN_VALUE;
        private long lastProgressTick = Long.MIN_VALUE;
        private boolean finished;
        private TickResult result = TickResult.ACTIVE;

        private Digging(BlockPos pos, BlockState state, float progressPerTick, Zombie zombie) {
            this.pos = pos;
            this.state = state;
            this.progressPerTick = progressPerTick;
            breakerId = zombie.getId();
        }

        BlockPos pos() {
            return pos;
        }
    }
    private record Candidate(BlockPos pos, Direction face) {
    }

}
