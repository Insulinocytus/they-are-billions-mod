package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class HordeBlockBreaking {
    private static final double STEP_DISTANCE = 0.6;
    private static final Map<ServerLevel, LevelDigging> LEVELS = new IdentityHashMap<>();
    private static final HordeDiggingCoordinator<SiteKey> COORDINATOR = new HordeDiggingCoordinator<>();

    private HordeBlockBreaking() {
    }

    static void setActiveSiteLimit(int limit) {
        COORDINATOR.setLimit(limit);
    }

    static int activeSiteCount() {
        return COORDINATOR.activeCount();
    }

    static void onServerTick(MinecraftServer server) {
        COORDINATOR.tick(server.getTickCount());
        for (Map.Entry<ServerLevel, LevelDigging> entry : LEVELS.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level.getServer() == server) {
                advanceRuntimes(level, entry.getValue());
            }
        }
    }

    static void onLevelUnload(ServerLevel level) {
        COORDINATOR.removeMatching(key -> key.dimension().equals(level.dimension()));
        LEVELS.remove(level);
    }

    static StartResult start(ServerLevel level, Zombie zombie, BlockPos nextStep) {
        LevelDigging digging = level(level);
        long tick = level.getServer().getTickCount();
        Candidate candidate = blockingBlock(
                level, zombie, nextStep, pos -> COORDINATOR.isDenied(key(level, pos), tick));
        if (candidate == null) {
            return StartResult.NONE;
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            logDiggingFailure(candidate.pos(), "mobGriefing");
            return new StartResult(null, candidate.pos());
        }
        ServerPlayer player = prepareActionPlayer(level, zombie);
        if (!canBreak(level, player, candidate.pos())) {
            COORDINATOR.deny(key(level, candidate.pos()), tick);
            logDiggingFailure(candidate.pos(), "permission");
            return new StartResult(null, candidate.pos());
        }

        BlockState state = level.getBlockState(candidate.pos());
        player = prepareSpeedPlayer(level, zombie);
        float progressPerTick = state.getDestroyProgress(player, level, candidate.pos());
        if (!(progressPerTick > 0.0F)) {
            logDiggingFailure(candidate.pos(), "unbreakable");
            return StartResult.NONE;
        }
        progressPerTick = Math.min(1.0F, progressPerTick);

        if (!COORDINATOR.request(
                key(level, candidate.pos()),
                zombie.getId(),
                nearestValidPlayerDistanceSquared(level, candidate.pos()),
                progressPerTick,
                tick)) {
            return StartResult.NONE;
        }

        SiteRuntime runtime = digging.runtimes.get(candidate.pos());
        if (runtime == null) {
            runtime = new SiteRuntime(candidate.pos(), state, candidate.face(), zombie.getId());
            digging.runtimes.put(candidate.pos(), runtime);
        }
        zombie.getNavigation().stop();
        return new StartResult(new Digging(candidate.pos(), progressPerTick), null);
    }

    static TickResult tick(ServerLevel level, Zombie zombie, Digging digging) {
        LevelDigging levelDigging = LEVELS.get(level);
        if (levelDigging == null || !COORDINATOR.contains(key(level, digging.pos))) {
            return TickResult.COMPLETE;
        }
        SiteRuntime runtime = levelDigging.runtimes.get(digging.pos);
        long tick = level.getServer().getTickCount();
        if (runtime == null) {
            COORDINATOR.release(key(level, digging.pos), zombie.getId(), tick);
            return TickResult.COMPLETE;
        }
        if (!level.getBlockState(digging.pos).equals(runtime.state)) {
            COORDINATOR.complete(key(level, digging.pos));
            clearCrack(level, runtime);
            levelDigging.runtimes.remove(digging.pos);
            return TickResult.COMPLETE;
        }
        if (!isAdjacent(zombie, digging.pos)) {
            COORDINATOR.release(key(level, digging.pos), zombie.getId(), tick);
            return TickResult.COMPLETE;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            COORDINATOR.deny(key(level, digging.pos), tick);
            logDiggingFailure(digging.pos, "mobGriefing");
            return TickResult.DENIED;
        }
        if (!COORDINATOR.request(
                key(level, digging.pos),
                zombie.getId(),
                nearestValidPlayerDistanceSquared(level, digging.pos),
                digging.progressPerTick,
                tick)) {
            return TickResult.COMPLETE;
        }
        if (!COORDINATOR.isActive(key(level, digging.pos))) {
            return TickResult.ACTIVE;
        }
        if (!runtime.started) {
            ServerPlayer player = prepareActionPlayer(level, zombie);
            if (!HordeBlockBreakingAccess.start(level, zombie, player, digging.pos, runtime.face)) {
                COORDINATOR.deny(key(level, digging.pos), tick);
                logDiggingFailure(digging.pos, "start");
                return TickResult.DENIED;
            }
            runtime.started = true;
        }
        if (tick % 4 == 0) {
            zombie.swing(InteractionHand.MAIN_HAND);
            if (runtime.lastSoundTick != tick) {
                SoundType sound = HordeBlockBreakingAccess.sound(level, zombie, digging.pos, runtime.state);
                level.playSound(
                        null,
                        digging.pos,
                        sound.getHitSound(),
                        SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 8.0F,
                        sound.getPitch() * 0.5F);
                runtime.lastSoundTick = tick;
            }
        }
        float progress = COORDINATOR.progress(key(level, digging.pos));
        int stage = Math.min(9, (int) (progress * 10.0F));
        if (stage != runtime.lastStage) {
            level.destroyBlockProgress(runtime.breakerId, digging.pos, stage);
            runtime.lastStage = stage;
        }
        if (progress < 1.0F) {
            return TickResult.ACTIVE;
        }
        ServerPlayer player = prepareActionPlayer(level, zombie);
        if (!canBreak(level, player, digging.pos)
                || !HordeBlockBreakingAccess.destroy(level, zombie, digging.pos)
                || level.getBlockState(digging.pos).equals(runtime.state)) {
            COORDINATOR.deny(key(level, digging.pos), tick);
            logDiggingFailure(digging.pos, "destroy");
            return TickResult.DENIED;
        }
        level.levelEvent(2001, digging.pos, Block.getId(runtime.state));
        COORDINATOR.complete(key(level, digging.pos));
        clearCrack(level, runtime);
        levelDigging.runtimes.remove(digging.pos);
        return TickResult.COMPLETE;
    }

    static void stop(ServerLevel level, Zombie zombie, Digging digging) {
        LevelDigging levelDigging = LEVELS.get(level);
        if (levelDigging != null) {
            COORDINATOR.release(key(level, digging.pos), zombie.getId(), level.getServer().getTickCount());
        }
    }

    private static void advanceRuntimes(ServerLevel level, LevelDigging digging) {
        Iterator<SiteRuntime> iterator = digging.runtimes.values().iterator();
        while (iterator.hasNext()) {
            SiteRuntime runtime = iterator.next();
            if (!COORDINATOR.contains(key(level, runtime.pos))) {
                clearCrack(level, runtime);
                iterator.remove();
            }
        }
    }

    private static void clearCrack(ServerLevel level, SiteRuntime runtime) {
        if (runtime.lastStage != -1) {
            level.destroyBlockProgress(runtime.breakerId, runtime.pos, -1);
            runtime.lastStage = -1;
        }
    }

    private static LevelDigging level(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new LevelDigging());
    }

    private static SiteKey key(ServerLevel level, BlockPos pos) {
        return new SiteKey(level.dimension(), pos);
    }

    private static double nearestValidPlayerDistanceSquared(ServerLevel level, BlockPos pos) {
        double nearest = Double.MAX_VALUE;
        Vec3 at = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (HordeSpawner.isValidPlayer(player)) {
                nearest = Math.min(nearest, player.distanceToSqr(at));
            }
        }
        return nearest;
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

    private static Candidate blockingBlock(
            ServerLevel level, Zombie zombie, BlockPos nextStep, Predicate<BlockPos> skip) {
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
                if (skip.test(pos)) {
                    continue;
                }
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

    private static void logDiggingFailure(BlockPos pos, String reason) {
        if (HordeAdmin.debugEnabled()) {
            TheyAreBillions.LOGGER.debug("Horde digging failed at {} ({})", pos, reason);
        }
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
        private final float progressPerTick;

        private Digging(BlockPos pos, float progressPerTick) {
            this.pos = pos;
            this.progressPerTick = progressPerTick;
        }

        BlockPos pos() {
            return pos;
        }
    }

    private record Candidate(BlockPos pos, Direction face) {
    }

    private record SiteKey(ResourceKey<Level> dimension, BlockPos pos) {
        SiteKey {
            pos = pos.immutable();
        }
    }

    private static final class LevelDigging {
        private final Map<BlockPos, SiteRuntime> runtimes = new HashMap<>();
    }

    private static final class SiteRuntime {
        private final BlockPos pos;
        private final BlockState state;
        private final Direction face;
        private final int breakerId;
        private int lastStage = -1;
        private long lastSoundTick = Long.MIN_VALUE;
        private boolean started;

        private SiteRuntime(BlockPos pos, BlockState state, Direction face, int breakerId) {
            this.pos = pos;
            this.state = state;
            this.face = face;
            this.breakerId = breakerId;
        }
    }
}
