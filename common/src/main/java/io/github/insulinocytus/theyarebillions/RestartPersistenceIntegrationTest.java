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
    private static final BlockPos TARGET_POS = BRAIN_POS.offset(-8, 0, 0);
    private static final int PERSISTED_HORDE_SIZE = 290;
    private static final int SAVED_OWNERSHIP_SIZE = 295;
    private static final int MAX_HORDE_SIZE = 300;
    private static final int PRE_TIMEOUT_CHECK_TICK = 42;
    private static final int TARGET_JOIN_TICK = 40;
    private static final UUID TARGET_ID = UUID.fromString("ecf0db3b-79aa-45bd-b4cb-a96b4dfe8f55");
    private static final UUID REPRESENTATIVE_ID = UUID.fromString("13e0d839-42d4-4dd0-831f-1edafafb16b6");
    private static final TicketType<BlockPos> TEST_TICKET = TicketType.create(
        "they_are_billions:restart_persistence", BlockPos::compareTo
    );
    private static final int RETARGET_COOLDOWN = 37;
    private static final int DECAY_TICKS = 13;
    private static final int TIMEOUT_TICKS = 260;

    private static final String phase = System.getProperty(PHASE_PROPERTY, "");
    private static int stage;
    private static int ticks;

    private static ServerPlayer target;
    private static boolean representativeStateVerified;
    private static boolean restorationGateVerified;
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
        require(++ticks <= TIMEOUT_TICKS, "Restart persistence test timed out in " + phase + " phase");
        if (SETUP.equals(phase)) {
            setup(level);
        } else if (VERIFY.equals(phase)) {
            verify(level);
        } else {
            throw new IllegalStateException("Unknown restart persistence test phase: " + phase);
        }
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
        if (stage == 0) {
            stage = 1;
            level.setDayTime(18000L);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            prepareArena(level);
            level.getChunkSource().addRegionTicket(TEST_TICKET, new ChunkPos(HORDE_POS), 2, HORDE_POS);
            level.getChunk(HORDE_POS);
            level.setBlockAndUpdate(BRAIN_POS, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
            return;
        }
        if (stage == 1) {
            if (!level.isPositionEntityTicking(HORDE_POS)) {
                return;
            }
            stage = 2;
            return;
        }
        if (stage != 2) {
            return;
        }
        stage = 3;

        BrainInAJarBlockEntity brain = brain(level);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        prepareSpawnSector(level, Direction.from2DDataValue(brainTag.getInt("HordeDirection")));
        for (int index = 0; index < PERSISTED_HORDE_SIZE; index++) {
            BlockPos zombiePos = HORDE_POS.offset(index % 20, 0, index / 20);
            HordeZombie zombie = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
            require(zombie != null, "Could not create a horde zombie");
            zombie.setBrainPos(BRAIN_POS);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            if (index == 0) {
                zombie.setUUID(REPRESENTATIVE_ID);
                zombie.addEffect(new MobEffectInstance(TheyAreBillions.decayEffect(), Integer.MAX_VALUE, 0, false, false, false));
                CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
                tag.putUUID("PlayerTarget", TARGET_ID);
                tag.putInt("PlayerRetargetCooldown", RETARGET_COOLDOWN);
                tag.putInt("DecayTicks", DECAY_TICKS);
                zombie.load(tag);
            }
            zombie.moveTo(zombiePos.getX() + 0.5, zombiePos.getY(), zombiePos.getZ() + 0.5, 0.0F, 0.0F);
            require(brain.tryClaimHordeZombie(zombie.getUUID()), "Brain could not claim horde zombie " + index);
            require(level.addFreshEntity(zombie), "Could not add horde zombie " + index);
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
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> finish(level)));
        stage++;
    }

    private static void finish(ServerLevel level) {
        if (SETUP.equals(phase)) {
            writeRestartState(level);
        }
        completed = true;
        level.getServer().halt(false);
    }

    private static void verify(ServerLevel level) {
        if (stage++ == 0) {
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            level.setChunkForced(BRAIN_POS.getX() >> 4, BRAIN_POS.getZ() >> 4, true);
            level.getChunk(BRAIN_POS);
            return;
        }
        RestartState expected = readRestartState();
        if (stage == TARGET_JOIN_TICK) {
            connectTarget(level);
            return;
        }
        List<HordeZombie> zombies = hordeZombies(level);
        if (!representativeStateVerified) {
            zombies.stream()
                .filter(zombie -> REPRESENTATIVE_ID.equals(zombie.getUUID()))
                .findFirst()
                .ifPresent(representative -> {
                    CompoundTag zombieTag = representative.saveWithoutId(new CompoundTag());
                    require(
                        zombieTag.hasUUID("PlayerTarget") && TARGET_ID.equals(zombieTag.getUUID("PlayerTarget")),
                        "Restart cleared the persisted player target before the player reconnected"
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
                    require(representative.hasEffect(TheyAreBillions.decayEffect()), "Restart lost the decay effect");
                    representativeStateVerified = true;
                });
        }
        if (!representativeStateVerified || stage < PRE_TIMEOUT_CHECK_TICK || target == null) {
            return;
        }

        BrainInAJarBlockEntity brain = brain(level);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        require(brainTag.getInt("HordeDirection") == expected.direction(), "Restart changed the Brain direction");
        int owned = brainTag.getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY).size();
        if (!restorationGateVerified) {
            require(
                owned == SAVED_OWNERSHIP_SIZE,
                "Restart replenished before pending entities restored: expected "
                    + SAVED_OWNERSHIP_SIZE + ", got " + owned
            );
            HordeZombie representative = representative(zombies, "Restart did not restore the representative zombie");
            CompoundTag zombieTag = representative.saveWithoutId(new CompoundTag());
            require(
                zombieTag.hasUUID("PlayerTarget") && TARGET_ID.equals(zombieTag.getUUID("PlayerTarget")),
                "Restart did not restore the player target: player alive=" + target.isAlive()
                    + ", player pos=" + target.blockPosition() + ", zombie pos=" + representative.blockPosition()
                    + ", live target=" + representative.getTarget()
            );
            require(representative.getTarget() == target, "Restart did not re-derive the live player target");
            restorationGateVerified = true;
            return;
        }
        if (owned == SAVED_OWNERSHIP_SIZE) {
            return;
        }
        require(owned <= MAX_HORDE_SIZE, "Restart exceeded the horde capacity: " + owned);
        require(
            zombies.size() == PERSISTED_HORDE_SIZE,
            "Restart restored " + zombies.size() + " of " + PERSISTED_HORDE_SIZE + " saved horde zombies"
        );
        finish(level);
        stage++;
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

    private static HordeZombie representative(List<HordeZombie> zombies, String failureMessage) {
        return zombies.stream()
            .filter(zombie -> REPRESENTATIVE_ID.equals(zombie.getUUID()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(failureMessage));
    }

    private static void writeRestartState(ServerLevel level) {
        BrainInAJarBlockEntity brain = brain(level);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        HordeZombie representative = representative(hordeZombies(level), "Setup did not retain the representative zombie");
        CompoundTag zombieTag = representative.saveWithoutId(new CompoundTag());
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
            for (int z = -1; z <= 20; z++) {
                BlockPos pos = BRAIN_POS.offset(x, 0, z);
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
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
