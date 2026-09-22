package io.github.insulinocytus.theyarebillions;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Test-only lifecycle driver for the deterministic two-process restart scenario. It is inert unless
 * a dedicated development server sets {@value #PHASE_PROPERTY} to {@code setup} or {@code verify}.
 */
final class RestartPersistenceIntegrationTest {
    static final String PHASE_PROPERTY = "they_are_billions.restart_test_phase";
    private static final String STATE_PATH_PROPERTY = "they_are_billions.restart_test_state";

    private static final int UNLOAD_BRAIN_X = 8;
    private static final int UNLOAD_BRAIN_Z = 8;
    private static final int REMOTE_X = 392;
    private static final int REMOTE_Z = 8;
    private static final int PERSISTENCE_BRAIN_X = 512;
    private static final int PERSISTENCE_BRAIN_Z = 8;
    private static final int TEST_SURFACE_Y = -59;
    private static final int PERSISTED_HORDE_SIZE = 290;
    private static final int SAVED_OWNERSHIP_SIZE = 295;
    private static final int MAX_HORDE_SIZE = 300;
    private static final int REMOTE_TICKING_TICKET_RADIUS = 2;
    private static final int REMOTE_TRACKING_TICKET_RADIUS = 0;
    private static final int TARGET_GUARD_TICKS = 5;
    private static final int TARGET_RESTORE_DEADLINE_TICKS = 40;
    private static final int PERSISTED_RETARGET_COOLDOWN = 180;
    private static final int MIN_PERSISTED_RETARGET_DELAY_TICKS = 140;
    private static final int DELAYED_TARGET_CONNECT_TICKS = 160;
    private static final int RETARGET_COOLDOWN_DEADLINE_TICKS = 260;
    private static final int REFILL_DEADLINE_TICKS = 1200;
    private static final long STAGE_TIMEOUT_NANOS = 4L * 60L * 1_000_000_000L;
    private static final int DECAY_TICKS = 19;
    private static final int DECAY_RESUME_DEADLINE_TICKS = 5;

    private static final long PERSISTED_UUID_MSB = 0x5A17_0000_0000_0000L;
    private static final long STALE_UUID_MSB = 0x5A18_0000_0000_0000L;
    private static final UUID RUNTIME_UNLOAD_ZOMBIE_ID = UUID.fromString("8a91028f-a0bf-4019-8466-b95053048d51");
    private static final UUID DESTROYED_BRAIN_ZOMBIE_ID = UUID.fromString("5f3ba839-b5ec-4260-bbd3-fc047656d06e");
    private static final UUID TARGET_ID = UUID.fromString("ecf0db3b-79aa-45bd-b4cb-a96b4dfe8f55");
    private static final UUID FALLBACK_TARGET_ID = UUID.fromString("f2b395f8-237f-4201-aa3f-d235d447abaf");
    private static final UUID DELAYED_TARGET_ID = UUID.fromString("49cc908f-35fb-49cb-96c6-3360c46e067c");
    private static final TicketType<BlockPos> TEST_TICKET = TicketType.create(
        "they_are_billions:restart_persistence", BlockPos::compareTo
    );

    private static final Phase phase = Phase.parse(System.getProperty(PHASE_PROPERTY, ""));
    private static Stage stage = phase == Phase.SETUP ? Stage.SETUP_INITIALIZE
        : phase == Phase.VERIFY ? Stage.VERIFY_INITIALIZE : null;
    private static int stageTicks;
    private static long stageStartedNanos;
    private static int behaviorTicks;
    private static int decayObservationTicks;
    private static boolean decayPersistenceVerified;
    private static int remoteTicketRadius = -1;
    private static boolean reconciliationVerified;
    private static boolean completed;

    private static BlockPos unloadBrainPos;
    private static BlockPos remotePos;
    private static BlockPos persistenceBrainPos;
    private static BlockPos representativePos;
    private static BlockPos delayedZombiePos;
    private static BlockPos decayPos;
    private static BlockPos targetPos;
    private static BlockPos fallbackTargetPos;
    private static BlockPos delayedPlayerPos;

    private static HordeZombie runtimeUnloadZombie;
    private static HordeZombie destroyedBrainZombie;
    private static ServerPlayer target;
    private static ServerPlayer fallbackTarget;
    private static ServerPlayer delayedTarget;
    private static RestartState expectedState;

    private record RestartState(int direction, float decayMaxHealth, int brainY) {
    }

    private enum Phase {
        SETUP,
        VERIFY;

        private static Phase parse(String value) {
            return switch (value) {
                case "setup" -> SETUP;
                case "verify" -> VERIFY;
                case "" -> null;
                default -> throw new IllegalStateException("Unknown restart persistence test phase: " + value);
            };
        }
    }

    private enum Stage {
        SETUP_INITIALIZE,
        WAIT_RUNTIME_CHUNK_TICKING,
        WAIT_RUNTIME_CHUNK_UNLOADED,
        WAIT_RUNTIME_ENTITY_REMOVED,
        WAIT_RUNTIME_CHUNK_RELOADED,
        WAIT_DESTROYED_CHUNK_TICKING,
        WAIT_DESTROYED_BRAIN_UNOWNED,
        WAIT_DESTROYED_CHUNK_UNLOADED,
        WAIT_DESTROYED_ENTITY_REMOVED,
        WAIT_DESTROYED_CHUNK_RELOADED,
        WAIT_PERSISTENCE_BRAIN_READY,
        WAIT_PERSISTED_TARGET_ACQUIRED,
        VERIFY_INITIALIZE,
        WAIT_VERIFY_READY,
        VERIFY_TARGET_GUARD,
        WAIT_RESTORED_TARGET,
        VERIFY_RETARGET_COOLDOWN,
        WAIT_DELAYED_TARGET,
        WAIT_HORDE_REFILL,
        STOPPING
    }

    private RestartPersistenceIntegrationTest() {
    }

    static boolean enabled() {
        return phase != null;
    }

    static void stop(MinecraftServer server) {
        Thread exitThread = new Thread(() -> System.exit(completed ? 0 : 1), "restart-test-exit");
        exitThread.start();
    }

    static void tick(ServerLevel level) {
        if (!enabled() || !level.dimension().equals(Level.OVERWORLD) || stage == Stage.STOPPING) {
            return;
        }
        stageTicks++;
        if (stageStartedNanos == 0L) {
            stageStartedNanos = System.nanoTime();
        }
        require(System.nanoTime() - stageStartedNanos <= STAGE_TIMEOUT_NANOS, "Restart test stage timed out");
        switch (stage) {
            case SETUP_INITIALIZE -> initializeSetup(level);
            case WAIT_RUNTIME_CHUNK_TICKING -> waitForRuntimeChunk(level);
            case WAIT_RUNTIME_CHUNK_UNLOADED -> waitForRuntimeChunkUnload(level);
            case WAIT_RUNTIME_ENTITY_REMOVED -> waitForRuntimeRemoval(level);
            case WAIT_RUNTIME_CHUNK_RELOADED -> waitForRuntimeReload(level);
            case WAIT_DESTROYED_CHUNK_TICKING -> waitForDestroyedChunk(level);
            case WAIT_DESTROYED_BRAIN_UNOWNED -> verifyDestroyedBrainZombieUnowned(level);
            case WAIT_DESTROYED_CHUNK_UNLOADED -> waitForDestroyedChunkUnload(level);
            case WAIT_DESTROYED_ENTITY_REMOVED -> waitForDestroyedRemoval(level);
            case WAIT_DESTROYED_CHUNK_RELOADED -> waitForDestroyedReload(level);
            case WAIT_PERSISTENCE_BRAIN_READY -> waitForPersistenceBrain(level);
            case WAIT_PERSISTED_TARGET_ACQUIRED -> waitForPersistedTarget(level);
            case VERIFY_INITIALIZE -> initializeVerification(level);
            case WAIT_VERIFY_READY -> waitForVerificationReadiness(level);
            case VERIFY_TARGET_GUARD -> verifyTargetGuard(level);
            case WAIT_RESTORED_TARGET -> waitForRestoredTarget(level);
            case VERIFY_RETARGET_COOLDOWN -> verifyRetargetCooldown(level);
            case WAIT_DELAYED_TARGET -> waitForDelayedTarget(level);
            case WAIT_HORDE_REFILL -> waitForHordeRefill(level);
            case STOPPING -> {
            }
        }
    }

    private static void initializeSetup(ServerLevel level) {
        configureWorld(level, false);
        initializeUnloadPositions();
        level.setBlockAndUpdate(unloadBrainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
        setRemoteTicket(level, REMOTE_TICKING_TICKET_RADIUS);
        level.getChunk(remotePos);
        transition(Stage.WAIT_RUNTIME_CHUNK_TICKING);
    }

    private static void waitForRuntimeChunk(ServerLevel level) {
        if (!level.isPositionEntityTicking(remotePos) || brainIfLoaded(level, unloadBrainPos) == null) {
            return;
        }
        BrainInAJarBlockEntity brain = brain(level, unloadBrainPos);
        runtimeUnloadZombie = createOwnedZombie(level, brain, unloadBrainPos, remotePos, RUNTIME_UNLOAD_ZOMBIE_ID);
        setRemoteTicket(level, -1);
        transition(Stage.WAIT_RUNTIME_CHUNK_UNLOADED);
    }

    private static void waitForRuntimeChunkUnload(ServerLevel level) {
        if (level.areEntitiesLoaded(new ChunkPos(remotePos).toLong())) {
            return;
        }
        executeCommand(level, "save-all flush");
        transition(Stage.WAIT_RUNTIME_ENTITY_REMOVED);
    }

    private static void waitForRuntimeRemoval(ServerLevel level) {
        BrainInAJarBlockEntity brain = brain(level, unloadBrainPos);
        if (!runtimeUnloadZombie.isRemoved() || brain.ownsHordeZombie(RUNTIME_UNLOAD_ZOMBIE_ID)) {
            return;
        }
        require(
            runtimeUnloadZombie.getRemovalReason() == Entity.RemovalReason.DISCARDED,
            "Runtime-unloaded horde zombie used the wrong removal reason"
        );
        setRemoteTicket(level, REMOTE_TRACKING_TICKET_RADIUS);
        transition(Stage.WAIT_RUNTIME_CHUNK_RELOADED);
    }

    private static void waitForRuntimeReload(ServerLevel level) {
        if (!level.areEntitiesLoaded(new ChunkPos(remotePos).toLong())) {
            return;
        }
        require(level.getEntity(RUNTIME_UNLOAD_ZOMBIE_ID) == null, "Runtime-unloaded zombie reappeared after chunk reload");
        require(remoteHordeZombies(level).isEmpty(), "Runtime-unloaded zombie was saved into its chunk");
        setRemoteTicket(level, REMOTE_TICKING_TICKET_RADIUS);
        transition(Stage.WAIT_DESTROYED_CHUNK_TICKING);
    }

    private static void waitForDestroyedChunk(ServerLevel level) {
        if (!level.isPositionEntityTicking(remotePos)) {
            return;
        }
        BrainInAJarBlockEntity brain = brain(level, unloadBrainPos);
        destroyedBrainZombie = createOwnedZombie(level, brain, unloadBrainPos, remotePos, DESTROYED_BRAIN_ZOMBIE_ID);
        require(level.destroyBlock(unloadBrainPos, false), "Could not destroy the runtime-unload Brain");
        transition(Stage.WAIT_DESTROYED_BRAIN_UNOWNED);
    }

    private static void verifyDestroyedBrainZombieUnowned(ServerLevel level) {
        require(!destroyedBrainZombie.isRemoved(), "Destroying the Brain immediately removed its horde zombie");
        require(destroyedBrainZombie.getBrainPos() == null, "Destroying the Brain left stale zombie ownership");
        setRemoteTicket(level, -1);
        transition(Stage.WAIT_DESTROYED_CHUNK_UNLOADED);
    }

    private static void waitForDestroyedChunkUnload(ServerLevel level) {
        if (level.areEntitiesLoaded(new ChunkPos(remotePos).toLong())) {
            return;
        }
        transition(Stage.WAIT_DESTROYED_ENTITY_REMOVED);
    }

    private static void waitForDestroyedRemoval(ServerLevel level) {
        if (!destroyedBrainZombie.isRemoved()) {
            return;
        }
        require(
            destroyedBrainZombie.getRemovalReason() == Entity.RemovalReason.DISCARDED,
            "The unowned horde zombie used the wrong runtime-unload removal reason"
        );
        setRemoteTicket(level, REMOTE_TRACKING_TICKET_RADIUS);
        transition(Stage.WAIT_DESTROYED_CHUNK_RELOADED);
    }

    private static void waitForDestroyedReload(ServerLevel level) {
        if (!level.areEntitiesLoaded(new ChunkPos(remotePos).toLong())) {
            return;
        }
        require(level.getEntity(DESTROYED_BRAIN_ZOMBIE_ID) == null, "Abandoned zombie reappeared after runtime unload");
        require(remoteHordeZombies(level).isEmpty(), "Abandoned zombie persisted across runtime chunk unload");
        setRemoteTicket(level, -1);
        initializePersistencePositions();
        level.setBlockAndUpdate(
            persistenceBrainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState()
        );
        transition(Stage.WAIT_PERSISTENCE_BRAIN_READY);
    }

    private static void waitForPersistenceBrain(ServerLevel level) {
        BrainInAJarBlockEntity brain = brainIfLoaded(level, persistenceBrainPos);
        if (brain == null || !level.isPositionEntityTicking(persistenceBrainPos)) {
            return;
        }
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        if (!brainTag.contains("HordeDirection", Tag.TAG_INT)) {
            return;
        }
        target = connectPlayer(level, TARGET_ID, "restart-target", targetPos);
        createPersistenceFixture(level, brain);
        transition(Stage.WAIT_PERSISTED_TARGET_ACQUIRED);
    }

    private static void waitForPersistedTarget(ServerLevel level) {
        HordeZombie representative = persistedZombie(level, 0);
        if (representative == null || representative.getTarget() != target) {
            return;
        }
        HordeZombie decayProbe = requirePersistedZombie(level, 2);
        CompoundTag decayTag = decayProbe.saveWithoutId(new CompoundTag());
        decayTag.putInt("DecayTicks", DECAY_TICKS);
        decayProbe.load(decayTag);
        require(decayProbe.hasEffect(TheyAreBillions.decayEffect()), "Setup lost the decay effect");

        BrainInAJarBlockEntity brain = brain(level, persistenceBrainPos);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        writeRestartState(new RestartState(
            brainTag.getInt("HordeDirection"), decayProbe.getMaxHealth(), persistenceBrainPos.getY()
        ));
        executeCommand(level, "save-all flush");
        require(level.getEntity(persistedZombieId(0)) == representative, "Live save removed the target horde zombie");
        require(brain.ownsHordeZombie(persistedZombieId(0)), "Live save released horde ownership");
        completeAndStop(level);
    }

    private static void initializeVerification(ServerLevel level) {
        configureWorld(level, false);
        expectedState = readRestartState();
        initializePersistencePositions(expectedState.brainY());
        level.getChunkSource().addRegionTicket(
            TEST_TICKET, new ChunkPos(persistenceBrainPos), REMOTE_TICKING_TICKET_RADIUS, persistenceBrainPos
        );
        LevelChunk chunk = level.getChunk(persistenceBrainPos.getX() >> 4, persistenceBrainPos.getZ() >> 4);
        require(
            chunk.getBlockEntity(persistenceBrainPos, LevelChunk.EntityCreationType.IMMEDIATE)
                instanceof BrainInAJarBlockEntity,
            "Saved Brain is missing at " + persistenceBrainPos + "; block=" + level.getBlockState(persistenceBrainPos)
        );
        transition(Stage.WAIT_VERIFY_READY);
    }

    private static void waitForVerificationReadiness(ServerLevel level) {
        observePersistedDecay(level);
        BrainInAJarBlockEntity brain = brainIfLoaded(level, persistenceBrainPos);
        if (brain == null
            || !level.isPositionEntityTicking(persistenceBrainPos)
            || !allPersistedZombiesLoaded(level, brain)
            || !decayPersistenceVerified) {
            return;
        }
        level.getChunkSource().removeRegionTicket(
            TEST_TICKET, new ChunkPos(persistenceBrainPos), REMOTE_TICKING_TICKET_RADIUS, persistenceBrainPos
        );
        require(brain.isRestoringHorde(), "Brain completed restoration before the saved horde was ready");
        require(ownedCount(level, brain) == SAVED_OWNERSHIP_SIZE, "Restart changed ownership before verification readiness");
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        require(brainTag.getInt("HordeDirection") == expectedState.direction(), "Restart changed the Brain direction");
        require(level.getServer().getPlayerList().getPlayers().isEmpty(), "A test player connected before readiness");

        HordeZombie representative = requirePersistedZombie(level, 0);
        HordeZombie delayedTargetZombie = requirePersistedZombie(level, 1);
        HordeZombie decayProbe = requirePersistedZombie(level, 2);
        HordeZombie cooldownProbe = requirePersistedZombie(level, 3);
        require(persistenceBrainPos.equals(representative.getBrainPos()), "Restart lost representative ownership");
        require(persistenceBrainPos.equals(delayedTargetZombie.getBrainPos()), "Restart lost delayed-target ownership");
        require(persistenceBrainPos.equals(decayProbe.getBrainPos()), "Restart lost decay-probe ownership");
        require(persistenceBrainPos.equals(cooldownProbe.getBrainPos()), "Restart lost cooldown-probe ownership");
        require(representative.getTarget() == null, "Offline restored target resolved before reconnect");
        require(delayedTargetZombie.getTarget() == null, "Delayed restored target resolved before reconnect");
        require(decayProbe.hasEffect(TheyAreBillions.decayEffect()), "Restart lost the decay effect");
        require(cooldownProbe.getTarget() == null, "Persisted cooldown probe targeted a player before reconnect");
        decayProbe.removeEffect(TheyAreBillions.decayEffect());

        fallbackTarget = connectPlayer(level, FALLBACK_TARGET_ID, "restart-fallback", fallbackTargetPos);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
        behaviorTicks = 0;
        transition(Stage.VERIFY_TARGET_GUARD);
    }

    private static void verifyTargetGuard(ServerLevel level) {
        behaviorTicks++;
        verifyRestorationInvariant(level);
        verifyPersistedCooldown(level);
        HordeZombie representative = requirePersistedZombie(level, 0);
        HordeZombie delayedTargetZombie = requirePersistedZombie(level, 1);
        require(representative.getTarget() == null, "Restored target switched to the fallback player");
        require(delayedTargetZombie.getTarget() == null, "Delayed restored target switched to the fallback player");
        if (stageTicks < TARGET_GUARD_TICKS) {
            return;
        }
        target = connectPlayer(level, TARGET_ID, "restart-target", targetPos);
        transition(Stage.WAIT_RESTORED_TARGET);
    }

    private static void waitForRestoredTarget(ServerLevel level) {
        behaviorTicks++;
        verifyRestorationInvariant(level);
        HordeZombie representative = requirePersistedZombie(level, 0);
        HordeZombie delayedTargetZombie = requirePersistedZombie(level, 1);
        verifyPersistedCooldown(level);
        require(representative.getTarget() != fallbackTarget, "Restored target switched to the fallback player");
        require(delayedTargetZombie.getTarget() == null, "Delayed restored target switched before its player reconnected");

        if (representative.getTarget() == target) {
            transition(Stage.VERIFY_RETARGET_COOLDOWN);
            return;
        }
        require(
            stageTicks <= TARGET_RESTORE_DEADLINE_TICKS,
            "Restored target did not resume before the business deadline"
        );
    }

    private static void verifyRetargetCooldown(ServerLevel level) {
        behaviorTicks++;
        verifyRestorationInvariant(level);
        verifyPersistedCooldown(level);
        HordeZombie delayedTargetZombie = requirePersistedZombie(level, 1);
        require(delayedTargetZombie.getTarget() == null, "Delayed restored target switched before its player reconnected");
        if (behaviorTicks < DELAYED_TARGET_CONNECT_TICKS) {
            return;
        }
        delayedTarget = connectPlayer(level, DELAYED_TARGET_ID, "restart-delayed-target", delayedPlayerPos);
        transition(Stage.WAIT_DELAYED_TARGET);
    }

    private static void waitForDelayedTarget(ServerLevel level) {
        behaviorTicks++;
        verifyRestorationInvariant(level);
        verifyPersistedCooldown(level);
        HordeZombie delayedTargetZombie = requirePersistedZombie(level, 1);
        HordeZombie cooldownProbe = requirePersistedZombie(level, 3);
        boolean delayedTargetRestored = delayedTargetZombie.getTarget() == delayedTarget;
        require(
            delayedTargetZombie.getTarget() == null || delayedTargetRestored,
            "Delayed restored target switched to another player"
        );
        require(
            delayedTargetRestored || stageTicks <= TARGET_RESTORE_DEADLINE_TICKS,
            "Delayed restored target did not resume after its player reconnected"
        );
        require(
            cooldownProbe.getTarget() != null || behaviorTicks <= RETARGET_COOLDOWN_DEADLINE_TICKS,
            "Persisted player retarget cooldown never expired"
        );
        if (delayedTargetRestored && cooldownProbe.getTarget() != null) {
            transition(Stage.WAIT_HORDE_REFILL);
        }
    }

    private static void waitForHordeRefill(ServerLevel level) {
        behaviorTicks++;
        verifyRestorationInvariant(level);
        BrainInAJarBlockEntity brain = brain(level, persistenceBrainPos);
        int owned = ownedCount(level, brain);
        require(owned <= MAX_HORDE_SIZE, "Restart exceeded the horde capacity: " + owned);
        if (!reconciliationVerified || owned < MAX_HORDE_SIZE) {
            require(
                stageTicks <= REFILL_DEADLINE_TICKS,
                "Restored horde did not refill to capacity before the business deadline"
            );
            return;
        }
        require(allOwnershipLive(level, brain), "Restart retained ghost ownership after refilling");
        completeAndStop(level);
    }

    private static void verifyRestorationInvariant(ServerLevel level) {
        BrainInAJarBlockEntity brain = brain(level, persistenceBrainPos);
        int owned = ownedCount(level, brain);
        if (brain.isRestoringHorde()) {
            require(
                owned == SAVED_OWNERSHIP_SIZE,
                "Brain supplemented or released ownership before restoration completed: " + owned
            );
            return;
        }
        if (reconciliationVerified) {
            require(owned <= MAX_HORDE_SIZE, "Brain exceeded capacity after restoration: " + owned);
            return;
        }

        for (int index = 0; index < PERSISTED_HORDE_SIZE; index++) {
            require(brain.ownsHordeZombie(persistedZombieId(index)), "Restoration released a live horde zombie");
        }
        for (int index = PERSISTED_HORDE_SIZE; index < SAVED_OWNERSHIP_SIZE; index++) {
            require(!brain.ownsHordeZombie(staleOwnershipId(index)), "Restoration retained stale ownership");
        }
        require(
            owned >= PERSISTED_HORDE_SIZE && owned <= PERSISTED_HORDE_SIZE + 1,
            "Restoration removed or added unexpected ownership records: " + owned
        );
        require(allOwnershipLive(level, brain), "Restoration reconciliation left a ghost ownership slot");
        reconciliationVerified = true;
    }

    private static void observePersistedDecay(ServerLevel level) {
        if (decayPersistenceVerified) {
            return;
        }
        HordeZombie decayProbe = persistedZombie(level, 2);
        if (decayProbe == null || !level.isPositionEntityTicking(decayProbe.blockPosition())) {
            return;
        }
        if (decayProbe.getMaxHealth() < expectedState.decayMaxHealth()) {
            decayPersistenceVerified = true;
            return;
        }
        decayObservationTicks++;
        require(
            decayObservationTicks <= DECAY_RESUME_DEADLINE_TICKS,
            "Persisted decay counter did not resume before a fresh counter could expire"
        );
    }

    private static void verifyPersistedCooldown(ServerLevel level) {
        HordeZombie cooldownProbe = requirePersistedZombie(level, 3);
        if (behaviorTicks < MIN_PERSISTED_RETARGET_DELAY_TICKS) {
            require(cooldownProbe.getTarget() == null, "Persisted player retarget cooldown expired too early");
            return;
        }
        if (cooldownProbe.getTarget() != null) {
            require(
                cooldownProbe.getTarget() == fallbackTarget || cooldownProbe.getTarget() == target,
                "Cooldown probe targeted an unexpected entity"
            );
        }
    }

    private static void configureWorld(ServerLevel level, boolean mobSpawning) {
        MinecraftServer server = level.getServer();
        server.setDifficulty(Difficulty.HARD, true);
        server.getPlayerList().setSimulationDistance(8);
        level.setDayTime(18000L);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(mobSpawning, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
    }

    private static void initializeUnloadPositions() {
        unloadBrainPos = surfacePos(UNLOAD_BRAIN_X, UNLOAD_BRAIN_Z);
        remotePos = surfacePos(REMOTE_X, REMOTE_Z);
    }

    private static void initializePersistencePositions() {
        initializePersistencePositions(TEST_SURFACE_Y);
    }

    private static void initializePersistencePositions(int y) {
        persistenceBrainPos = new BlockPos(PERSISTENCE_BRAIN_X, y, PERSISTENCE_BRAIN_Z);
        representativePos = persistenceBrainPos.offset(2, 0, 0);
        delayedZombiePos = persistenceBrainPos.offset(3, 0, 0);
        decayPos = persistenceBrainPos.offset(1, 0, 0);
        targetPos = persistenceBrainPos.offset(-2, 0, 0);
        fallbackTargetPos = persistenceBrainPos.offset(-1, 0, 0);
        delayedPlayerPos = persistenceBrainPos.offset(-3, 0, 0);
    }

    private static BlockPos surfacePos(int x, int z) {
        return new BlockPos(x, TEST_SURFACE_Y, z);
    }

    private static void createPersistenceFixture(ServerLevel level, BrainInAJarBlockEntity brain) {
        for (int index = 0; index < PERSISTED_HORDE_SIZE; index++) {
            HordeZombie zombie = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
            require(zombie != null, "Could not create persisted horde zombie " + index);
            zombie.setUUID(persistedZombieId(index));
            zombie.setBrainPos(persistenceBrainPos);
            zombie.setNoAi(index >= 2);
            zombie.setNoGravity(index >= 2);
            if (index == 1) {
                CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
                tag.putUUID("PlayerTarget", DELAYED_TARGET_ID);
                zombie.load(tag);
            } else if (index == 2) {
                zombie.addEffect(new MobEffectInstance(
                    TheyAreBillions.decayEffect(), Integer.MAX_VALUE, 0, false, false, false
                ));
                CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
                tag.putInt("DecayTicks", DECAY_TICKS);
                zombie.load(tag);
            } else if (index == 3) {
                CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
                tag.putInt("PlayerRetargetCooldown", PERSISTED_RETARGET_COOLDOWN);
                zombie.load(tag);
            }
            BlockPos position = persistedPosition(index);
            zombie.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
            require(brain.tryClaimHordeZombie(zombie.getUUID()), "Brain could not claim persisted zombie " + index);
            require(level.addFreshEntity(zombie), "Could not add persisted horde zombie " + index);
        }
        for (int index = PERSISTED_HORDE_SIZE; index < SAVED_OWNERSHIP_SIZE; index++) {
            require(brain.tryClaimHordeZombie(staleOwnershipId(index)), "Brain could not claim stale ownership " + index);
        }
        require(ownedCount(level, brain) == SAVED_OWNERSHIP_SIZE, "Setup created the wrong ownership count");
    }

    private static BlockPos persistedPosition(int index) {
        if (index == 0) {
            return representativePos;
        }
        if (index == 1) {
            return delayedZombiePos;
        }
        if (index == 2) {
            return decayPos;
        }
        int slot = index - 3;
        int layer = slot / 144;
        int withinLayer = slot % 144;
        int chunkMinX = persistenceBrainPos.getX() & ~15;
        int chunkMinZ = persistenceBrainPos.getZ() & ~15;
        return new BlockPos(
            chunkMinX + 4 + withinLayer % 12,
            persistenceBrainPos.getY() + 3 + layer * 3,
            chunkMinZ + withinLayer / 12
        );
    }

    private static HordeZombie createOwnedZombie(
        ServerLevel level,
        BrainInAJarBlockEntity brain,
        BlockPos brainPos,
        BlockPos zombiePos,
        UUID zombieId
    ) {
        HordeZombie zombie = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
        require(zombie != null, "Could not create runtime-unload horde zombie");
        zombie.setUUID(zombieId);
        zombie.setBrainPos(brainPos);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.moveTo(zombiePos.getX() + 0.5, zombiePos.getY(), zombiePos.getZ() + 0.5, 0.0F, 0.0F);
        require(brain.tryClaimHordeZombie(zombieId), "Brain could not claim runtime-unload horde zombie");
        require(level.addFreshEntity(zombie), "Could not add runtime-unload horde zombie");
        return zombie;
    }

    private static ServerPlayer connectPlayer(ServerLevel level, UUID id, String name, BlockPos position) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        var player = new RestartTestPlayer(level, cookie);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
        player.setNoGravity(true);
        return player;
    }

    private static void setRemoteTicket(ServerLevel level, int radius) {
        ChunkPos chunk = new ChunkPos(remotePos);
        if (remoteTicketRadius >= 0) {
            level.getChunkSource().removeRegionTicket(TEST_TICKET, chunk, remoteTicketRadius, remotePos);
        }
        remoteTicketRadius = radius;
        if (radius >= 0) {
            level.getChunkSource().addRegionTicket(TEST_TICKET, chunk, radius, remotePos);
        }
    }

    private static java.util.List<HordeZombie> remoteHordeZombies(ServerLevel level) {
        return level.getEntitiesOfClass(HordeZombie.class, new AABB(remotePos).inflate(8.0, 16.0, 8.0));
    }

    private static boolean allPersistedZombiesLoaded(ServerLevel level, BrainInAJarBlockEntity brain) {
        for (int index = 0; index < PERSISTED_HORDE_SIZE; index++) {
            UUID zombieId = persistedZombieId(index);
            if (!(level.getEntity(zombieId) instanceof HordeZombie) || !brain.ownsHordeZombie(zombieId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allOwnershipLive(ServerLevel level, BrainInAJarBlockEntity brain) {
        for (Tag ownedTag : brain.saveWithoutMetadata(level.registryAccess()).getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY)) {
            if (!(level.getEntity(NbtUtils.loadUUID(ownedTag)) instanceof HordeZombie)) {
                return false;
            }
        }
        return true;
    }

    private static UUID persistedZombieId(int index) {
        return new UUID(PERSISTED_UUID_MSB, index + 1L);
    }

    private static UUID staleOwnershipId(int index) {
        return new UUID(STALE_UUID_MSB, index + 1L);
    }

    private static HordeZombie persistedZombie(ServerLevel level, int index) {
        return level.getEntity(persistedZombieId(index)) instanceof HordeZombie zombie ? zombie : null;
    }

    private static HordeZombie requirePersistedZombie(ServerLevel level, int index) {
        HordeZombie zombie = persistedZombie(level, index);
        require(zombie != null, "Restart lost persisted horde zombie " + index);
        return zombie;
    }

    private static BrainInAJarBlockEntity brain(ServerLevel level, BlockPos pos) {
        BrainInAJarBlockEntity brain = brainIfLoaded(level, pos);
        require(brain != null, "Expected Brain in a Jar is not loaded at " + pos);
        return brain;
    }

    private static BrainInAJarBlockEntity brainIfLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) instanceof BrainInAJarBlockEntity brain
            ? brain : null;
    }

    private static int ownedCount(ServerLevel level, BrainInAJarBlockEntity brain) {
        return brain.saveWithoutMetadata(level.registryAccess()).getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY).size();
    }

    private static void executeCommand(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(
            level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), command
        );
    }

    private static void completeAndStop(ServerLevel level) {
        completed = true;
        transition(Stage.STOPPING);
        executeCommand(level, "stop");
    }

    private static void transition(Stage next) {
        stage = next;
        stageTicks = 0;
        stageStartedNanos = System.nanoTime();
    }


    private static void writeRestartState(RestartState state) {
        try {
            Path statePath = restartStatePath();
            Files.createDirectories(statePath.getParent());
            Files.writeString(statePath, state.direction() + "," + state.decayMaxHealth() + "," + state.brainY());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not record restart state", exception);
        }
    }

    private static RestartState readRestartState() {
        try {
            String[] values = Files.readString(restartStatePath()).split(",");
            require(values.length == 3, "Restart state file is malformed");
            return new RestartState(
                Integer.parseInt(values[0]), Float.parseFloat(values[1]), Integer.parseInt(values[2])
            );
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Could not read restart state", exception);
        }
    }

    private static Path restartStatePath() {
        String statePath = System.getProperty(STATE_PATH_PROPERTY);
        require(statePath != null, "Restart state path is not configured");
        return Path.of(statePath);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " [" + statusSuffix() + "]");
        }
    }

    private static String statusSuffix() {
        return "phase=" + phase + ", stage=" + stage + ", stageTicks=" + stageTicks + ", behaviorTicks=" + behaviorTicks;
    }

    private static final class RestartTestPlayer extends ServerPlayer {
        private RestartTestPlayer(ServerLevel level, CommonListenerCookie cookie) {
            super(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }
}
