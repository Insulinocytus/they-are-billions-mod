package io.github.insulinocytus.theyarebillions;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.TicketType;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.TickTask;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;

/**
 * Test-only lifecycle driver for the two-process restart scenario. It is inert unless a dedicated
 * development server sets {@value #PHASE_PROPERTY} to {@code setup} or {@code verify}.
 */
final class RestartPersistenceIntegrationTest {
    static final String PHASE_PROPERTY = "they_are_billions.restart_test_phase";
    private static final String STATE_PATH_PROPERTY = "they_are_billions.restart_test_state";
    private static final String SETUP = "setup";
    private static final String VERIFY = "verify";
    private static final BlockPos BRAIN_POS = new BlockPos(8, 300, 8);
    private static final BlockPos HORDE_POS = BRAIN_POS.offset(8, 0, 0);
    private static final BlockPos ABANDONED_POS = BRAIN_POS.offset(20, 0, -8);
    private static final BlockPos TARGET_POS = BRAIN_POS.offset(-8, 0, 0);
    private static final BlockPos UNOWNED_POS = BRAIN_POS.offset(4, 0, -8);
    private static final BlockPos CROSS_LEVEL_POS = new BlockPos(8, 64, 8);
    private static final int PERSISTED_HORDE_SIZE = 290;
    private static final int SAVED_OWNERSHIP_SIZE = 295;
    private static final int MAX_HORDE_SIZE = 300;
    private static final int MOVEMENT_CHECK_TICK = 80;
    private static final int PRE_RECONNECT_CHECK_TICK = 20;
    private static final int TARGET_JOIN_TICK = 40;
    private static final int POST_RESTORED_TARGET_WAIT_CHECK_TICK = 160;
    private static final int PRE_RECONCILIATION_CHECK_TICK = 180;
    private static final UUID TARGET_ID = UUID.fromString("ecf0db3b-79aa-45bd-b4cb-a96b4dfe8f55");
    private static final UUID REPRESENTATIVE_ID = UUID.fromString("13e0d839-42d4-4dd0-831f-1edafafb16b6");
    private static final UUID ABANDONED_TARGET_ID = UUID.fromString("49cc908f-35fb-49cb-96c6-3360c46e067c");
    private static final UUID ABANDONED_ZOMBIE_ID = UUID.fromString("731bed6f-1d7e-48c7-b8df-d9bf912504b8");
    private static final UUID UNOWNED_ZOMBIE_ID = UUID.fromString("57ed1cc7-27aa-41d0-99db-9fe0ebbc415e");
    private static final UUID CROSS_LEVEL_ZOMBIE_ID = UUID.fromString("e8d3e327-764a-4e9e-b22a-768e1fd7cd85");
    private static final TicketType<BlockPos> TEST_TICKET = TicketType.create(
        "they_are_billions:restart_persistence", BlockPos::compareTo
    );
    private static final int RETARGET_COOLDOWN = 37;
    private static final int DECAY_TICKS = 13;
    private static final int TIMEOUT_TICKS = 400;

    private static final String phase = System.getProperty(PHASE_PROPERTY, "");
    private static int setupStage;
    private static int verifyTick;
    private static int ticks;

    private static ServerPlayer target;
    private static boolean persistedStateVerified;
    private static boolean offlineTargetVerified;
    private static boolean directionVerified;
    private static boolean restoredTargetVerified;
    private static boolean abandonedTargetReleased;
    private static boolean restorationGateVerified;
    private static boolean restorationReconciled;
    private static boolean offlineMovementVerified;
    private static boolean spawnSectorPrepared;
    private static boolean completed;

    private record RestartState(int direction, int playerRetargetCooldown, int decayTicks) {
    }

    private RestartPersistenceIntegrationTest() {
    }

    static void stop(net.minecraft.server.MinecraftServer server) {
        Thread exitThread = new Thread(() -> System.exit(completed ? 0 : 1), "restart-test-exit");
        exitThread.start();
    }

    static boolean enabled() {
        return !phase.isEmpty();
    }

    static void tick(ServerLevel level) {
        if (phase.isEmpty() || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        require(++ticks <= TIMEOUT_TICKS, "Restart persistence test timed out in " + phase + " phase: " + status(level));
        if (SETUP.equals(phase)) {
            setup(level);
        } else if (VERIFY.equals(phase)) {
            verify(level);
        } else {
            throw new IllegalStateException("Unknown restart persistence test phase: " + phase);
        }
    }

    private static String status(ServerLevel level) {
        int owned = level.getBlockEntity(BRAIN_POS) instanceof BrainInAJarBlockEntity brain
            ? brain.saveWithoutMetadata(level.registryAccess()).getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY).size()
            : -1;
        return "setupStage=" + setupStage + ", verifyTick=" + verifyTick + ", owned=" + owned + ", day=" + level.isDay()
            + ", persisted=" + persistedStateVerified + ", offline=" + offlineTargetVerified
            + ", restored=" + restoredTargetVerified + ", abandoned=" + abandonedTargetReleased
            + ", gate=" + restorationGateVerified + ", reconciled=" + restorationReconciled;
    }

    private static void connectTarget(ServerLevel level) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(TARGET_ID, "restart-target"), false);
        target = new RestartTestPlayer(level, cookie);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, target, cookie);
        target.moveTo(TARGET_POS.getX() + 0.5, TARGET_POS.getY(), TARGET_POS.getZ() + 0.5, 0.0F, 0.0F);
        target.setNoGravity(true);
    }

    private static void setup(ServerLevel level) {
        if (setupStage == 0) {
            setupStage = 1;
            level.setDayTime(18000L);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            prepareArena(level);
            level.getChunkSource().addRegionTicket(TEST_TICKET, new ChunkPos(HORDE_POS), 2, HORDE_POS);
            level.getChunk(HORDE_POS);
            level.setBlockAndUpdate(BRAIN_POS, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
            return;
        }
        if (setupStage == 1) {
            if (!level.isPositionEntityTicking(HORDE_POS)) {
                return;
            }
            setupStage = 2;
            return;
        }
        if (setupStage == 2) {
            setupStage = 3;
            connectTarget(level);

            BrainInAJarBlockEntity brain = brain(level);
            CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
            prepareSpawnSector(level, Direction.from2DDataValue(brainTag.getInt("HordeDirection")));
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            require(nether != null, "Restart test has no Nether level");
            nether.setChunkForced(CROSS_LEVEL_POS.getX() >> 4, CROSS_LEVEL_POS.getZ() >> 4, true);
            nether.setBlock(CROSS_LEVEL_POS.below(), Blocks.STONE.defaultBlockState(), 2);
            nether.setBlock(CROSS_LEVEL_POS, Blocks.AIR.defaultBlockState(), 2);
            nether.setBlock(CROSS_LEVEL_POS.above(), Blocks.AIR.defaultBlockState(), 2);
            for (int index = 0; index < PERSISTED_HORDE_SIZE; index++) {
                ServerLevel zombieLevel = index == 2 ? nether : level;
                BlockPos zombiePos = index == 1
                    ? ABANDONED_POS
                    : index == 2 ? CROSS_LEVEL_POS : HORDE_POS.offset(index % 20, 0, index / 20);
                HordeZombie zombie = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(zombieLevel);
                require(zombie != null, "Could not create a horde zombie");
                zombie.setBrainPos(BRAIN_POS);
                zombie.setNoAi(index > 1);
                zombie.setNoGravity(index != 1);
                if (index == 0) {
                    zombie.setUUID(REPRESENTATIVE_ID);
                } else if (index == 1) {
                    zombie.setUUID(ABANDONED_ZOMBIE_ID);
                    zombie.addEffect(new MobEffectInstance(
                        TheyAreBillions.decayEffect(), Integer.MAX_VALUE, 0, false, false, false
                    ));
                    CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
                    tag.putUUID("PlayerTarget", ABANDONED_TARGET_ID);
                    tag.putInt("PlayerRetargetCooldown", RETARGET_COOLDOWN);
                    tag.putInt("DecayTicks", DECAY_TICKS);
                    zombie.load(tag);
                } else if (index == 2) {
                    zombie.setUUID(CROSS_LEVEL_ZOMBIE_ID);
                }
                zombie.moveTo(zombiePos.getX() + 0.5, zombiePos.getY(), zombiePos.getZ() + 0.5, 0.0F, 0.0F);
                require(brain.tryClaimHordeZombie(zombie.getUUID()), "Brain could not claim horde zombie " + index);
                require(zombieLevel.addFreshEntity(zombie), "Could not add horde zombie " + index);
            }
            for (int index = PERSISTED_HORDE_SIZE; index < SAVED_OWNERSHIP_SIZE; index++) {
                require(
                    brain.tryClaimHordeZombie(new UUID(0L, index + 1L)),
                    "Brain could not claim pending restoration record " + index
                );
            }
            require(
                brain.saveWithoutMetadata(level.registryAccess()).getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY).size()
                    == SAVED_OWNERSHIP_SIZE,
                "Setup did not create " + SAVED_OWNERSHIP_SIZE + " Brain ownership records"
            );
            return;
        }
        if (setupStage != 3) {
            return;
        }

        HordeZombie representative = representative(hordeZombies(level), "Setup lost the target horde zombie");
        if (representative.getTarget() != target) {
            return;
        }
        CompoundTag representativeTag = representative.saveWithoutId(new CompoundTag());
        require(
            representativeTag.hasUUID("PlayerTarget") && TARGET_ID.equals(representativeTag.getUUID("PlayerTarget")),
            "Setup horde zombie did not acquire the live player target"
        );
        HordeZombie unowned = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
        require(unowned != null, "Could not create an unowned horde zombie");
        unowned.setUUID(UNOWNED_ZOMBIE_ID);
        unowned.setNoAi(true);
        unowned.setNoGravity(true);
        CompoundTag unownedTag = unowned.saveWithoutId(new CompoundTag());
        unownedTag.putInt("RebindCooldown", 5);
        unowned.load(unownedTag);
        unowned.moveTo(UNOWNED_POS.getX() + 0.5, UNOWNED_POS.getY(), UNOWNED_POS.getZ() + 0.5, 0.0F, 0.0F);
        require(level.addFreshEntity(unowned), "Could not add an unowned horde zombie");
        level.getServer().getCommands().performPrefixedCommand(
            level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), "save-all flush"
        );
        require(level.getEntity(REPRESENTATIVE_ID) == representative, "Live save removed the target horde zombie");
        require(
            brain(level).ownsHordeZombie(REPRESENTATIVE_ID),
            "Live save released the target horde zombie ownership"
        );
        setupStage = 4;
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> finish(level)));
    }

    private static void finish(ServerLevel level) {
        if (SETUP.equals(phase)) {
            writeRestartState(level);
        }
        completed = true;
        level.getServer().halt(false);
    }

    private static void verify(ServerLevel level) {
        if (verifyTick++ == 0) {
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            level.setChunkForced(BRAIN_POS.getX() >> 4, BRAIN_POS.getZ() >> 4, true);
            level.getChunk(BRAIN_POS);
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            require(nether != null, "Restart test has no Nether level");
            nether.getChunk(CROSS_LEVEL_POS);
            return;
        }
        RestartState expected = readRestartState();
        List<HordeZombie> zombies = hordeZombies(level);
        HordeZombie abandoned = zombies.stream()
            .filter(zombie -> ABANDONED_ZOMBIE_ID.equals(zombie.getUUID()))
            .findFirst()
            .orElse(null);
        if (!persistedStateVerified && abandoned != null) {
            CompoundTag zombieTag = abandoned.saveWithoutId(new CompoundTag());
            require(
                zombieTag.hasUUID("PlayerTarget") && ABANDONED_TARGET_ID.equals(zombieTag.getUUID("PlayerTarget")),
                "Restart cleared the persisted abandoned player target immediately"
            );
            int playerRetargetCooldown = zombieTag.getInt("PlayerRetargetCooldown");
            require(
                playerRetargetCooldown == expected.playerRetargetCooldown(),
                "Restart changed the player retarget cooldown: expected "
                    + expected.playerRetargetCooldown() + ", got " + playerRetargetCooldown
            );
            int decayTicks = zombieTag.getInt("DecayTicks");
            require(
                decayTicks == expected.decayTicks() || decayTicks == (expected.decayTicks() + 1) % 20,
                "Restart changed the decay counter: expected " + expected.decayTicks()
                    + " or " + ((expected.decayTicks() + 1) % 20) + ", got " + decayTicks
            );
            require(abandoned.hasEffect(TheyAreBillions.decayEffect()), "Restart lost the decay effect");
            persistedStateVerified = true;
        }
        if (verifyTick == PRE_RECONNECT_CHECK_TICK) {
            require(level.getServer().getPlayerList().getPlayer(TARGET_ID) == null, "Target player connected before the test");
            HordeZombie representative = representative(zombies, "Restart did not restore the target horde zombie");
            CompoundTag zombieTag = representative.saveWithoutId(new CompoundTag());
            require(
                zombieTag.hasUUID("PlayerTarget") && TARGET_ID.equals(zombieTag.getUUID("PlayerTarget")),
                "Restart cleared the persisted player target before the player reconnected"
            );
            require(representative.getTarget() == null, "Offline target unexpectedly resolved to a live player");
            require(abandoned != null, "Restart did not restore the abandoned-target horde zombie");
            require(
                findHordeZombie(level, CROSS_LEVEL_ZOMBIE_ID) != null,
                "Restart did not restore the cross-level owned horde zombie"
            );
            require(level.getEntity(UNOWNED_ZOMBIE_ID) instanceof HordeZombie, "Restart lost the unowned horde zombie");
            HordeZombie unowned = (HordeZombie) level.getEntity(UNOWNED_ZOMBIE_ID);
            require(unowned.getBrainPos() == null, "An unowned horde zombie rebound during restoration");
            require(!brain(level).ownsHordeZombie(UNOWNED_ZOMBIE_ID), "Restoration admitted a new ownership claim");
            level.setDayTime(1000L);
            offlineTargetVerified = true;
        }
        if (!offlineMovementVerified && verifyTick >= MOVEMENT_CHECK_TICK && abandoned != null) {
            require(
                abandoned.distanceToSqr(BRAIN_POS.getCenter())
                    < ABANDONED_POS.getCenter().distanceToSqr(BRAIN_POS.getCenter()),
                "A horde zombie waiting for its restored player target did not move toward its Brain: position="
                    + abandoned.position() + ", noAi=" + abandoned.isNoAi()
                    + ", navigationDone=" + abandoned.getNavigation().isDone()
            );
            offlineMovementVerified = true;
        }
        if (verifyTick == TARGET_JOIN_TICK) {
            connectTarget(level);
            return;
        }
        if (!restoredTargetVerified && target != null) {
            HordeZombie representative = representative(zombies, "Restart lost the target horde zombie");
            if (representative.getTarget() == target) {
                restoredTargetVerified = true;
            }
        }
        if (!abandonedTargetReleased && verifyTick >= POST_RESTORED_TARGET_WAIT_CHECK_TICK && abandoned != null) {
            CompoundTag zombieTag = abandoned.saveWithoutId(new CompoundTag());
            require(!zombieTag.hasUUID("PlayerTarget"), "A player that never reconnected remained targeted");
            require(abandoned.getTarget() == null, "A player that never reconnected resolved to a live target");
            int cooldown = zombieTag.getInt("PlayerRetargetCooldown");
            require(cooldown > 0 && cooldown < 100, "Abandoned player target did not enter the 100-tick retarget cooldown");
            abandonedTargetReleased = true;
        }
        if (!persistedStateVerified || !offlineTargetVerified || !offlineMovementVerified
            || verifyTick < TARGET_JOIN_TICK + 2 || target == null) {
            return;
        }

        BrainInAJarBlockEntity brain = brain(level);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        if (!directionVerified) {
            require(brainTag.getInt("HordeDirection") == expected.direction(), "Restart changed the Brain direction");
            directionVerified = true;
        }
        var ownedTags = brainTag.getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY);
        int owned = ownedTags.size();
        if (!restorationGateVerified) {
            if (verifyTick < PRE_RECONCILIATION_CHECK_TICK) {
                return;
            }
            require(
                owned == SAVED_OWNERSHIP_SIZE,
                "Restart changed ownership before the bounded restoration wait ended: expected "
                    + SAVED_OWNERSHIP_SIZE + ", got " + owned
            );
            restorationGateVerified = true;
            return;
        }
        if (!restorationReconciled) {
            if (owned == SAVED_OWNERSHIP_SIZE) {
                return;
            }
            require(
                owned == PERSISTED_HORDE_SIZE,
                "Restart did not remove only unresolved stale ownership: expected "
                    + PERSISTED_HORDE_SIZE + ", got " + owned
            );
            restorationReconciled = true;
            level.setDayTime(18000L);
            return;
        }
        if (!spawnSectorPrepared) {
            prepareSpawnSector(level, Direction.from2DDataValue(brainTag.getInt("HordeDirection")));
            spawnSectorPrepared = true;
            return;
        }
        if (owned < MAX_HORDE_SIZE) {
            return;
        }
        require(owned == MAX_HORDE_SIZE, "Restart exceeded the horde capacity: " + owned);
        int liveOwned = 0;
        for (Tag ownedTag : ownedTags) {
            if (findHordeZombie(level, NbtUtils.loadUUID(ownedTag)) != null) {
                liveOwned++;
            }
        }
        require(liveOwned == MAX_HORDE_SIZE, "Restart retained ghost ownership slots: " + (owned - liveOwned));
        require(
            zombies.size() == PERSISTED_HORDE_SIZE,
            "Restart restored " + zombies.size() + " of " + PERSISTED_HORDE_SIZE + " saved horde zombies"
        );
        require(restoredTargetVerified, "Restart did not re-derive the live player target after reconnect");
        require(abandonedTargetReleased, "Restart did not release a player target that never reconnected");
        finish(level);
    }

    private static BrainInAJarBlockEntity brain(ServerLevel level) {
        require(level.getBlockState(BRAIN_POS).is(TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get()),
            "Restart did not restore the Brain block");
        LevelChunk chunk = level.getChunkAt(BRAIN_POS);
        require(chunk.getBlockEntity(BRAIN_POS, LevelChunk.EntityCreationType.IMMEDIATE) instanceof BrainInAJarBlockEntity,
            "Restart did not restore the Brain block entity");
        return (BrainInAJarBlockEntity) chunk.getBlockEntity(BRAIN_POS, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    private static List<HordeZombie> hordeZombies(ServerLevel level) {
        return level.getEntitiesOfClass(HordeZombie.class, new net.minecraft.world.phys.AABB(HORDE_POS).inflate(24.0, 8.0, 24.0));
    }

    private static HordeZombie findHordeZombie(ServerLevel level, UUID zombieId) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            if (serverLevel.getEntity(zombieId) instanceof HordeZombie zombie) {
                return zombie;
            }
        }
        return null;
    }

    private static HordeZombie representative(List<HordeZombie> zombies, String failureMessage) {
        return zombies.stream()
            .filter(zombie -> REPRESENTATIVE_ID.equals(zombie.getUUID()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(failureMessage));
    }

    private static void writeRestartState(ServerLevel level) {
        BrainInAJarBlockEntity brain = brain(level);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        HordeZombie abandoned = hordeZombies(level).stream()
            .filter(zombie -> ABANDONED_ZOMBIE_ID.equals(zombie.getUUID()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Setup did not retain the abandoned-target horde zombie"));
        CompoundTag zombieTag = abandoned.saveWithoutId(new CompoundTag());
        String state = brainTag.getInt("HordeDirection") + ","
            + zombieTag.getInt("PlayerRetargetCooldown") + ","
            + zombieTag.getInt("DecayTicks");
        try {
            Path statePath = restartStatePath();
            Files.createDirectories(statePath.getParent());
            Files.writeString(statePath, state);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not record restart state", exception);
        }
    }

    private static RestartState readRestartState() {
        try {
            String[] values = Files.readString(restartStatePath()).split(",");
            require(values.length == 3, "Restart state file is malformed");
            return new RestartState(
                Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])
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

    private static void prepareArena(ServerLevel level) {
        for (int x = -1; x <= 28; x++) {
            for (int z = -10; z <= 20; z++) {
                BlockPos pos = BRAIN_POS.offset(x, 0, z);
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos.above(2), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    private static void prepareSpawnSector(ServerLevel level, Direction direction) {
        for (int dx = -130; dx <= 130; dx++) {
            for (int dz = -130; dz <= 130; dz++) {
                double distance = Math.hypot(dx, dz);
                int forward = dx * direction.getStepX() + dz * direction.getStepZ();
                int side = dx * direction.getStepZ() - dz * direction.getStepX();
                if (distance < 62.0 || distance > 130.0 || forward <= 0 || Math.abs(side) > forward * 0.5) {
                    continue;
                }
                BlockPos pos = BRAIN_POS.offset(dx, 0, dz);
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
