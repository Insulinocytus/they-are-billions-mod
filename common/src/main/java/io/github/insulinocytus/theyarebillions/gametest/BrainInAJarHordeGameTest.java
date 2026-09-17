package io.github.insulinocytus.theyarebillions.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.insulinocytus.theyarebillions.HordeZombie;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class BrainInAJarHordeGameTest {
    private static final BlockPos REMOTE_BRAIN = new BlockPos(96, 2, 0);
    private static final BlockPos TARGET_TEST_BRAIN = new BlockPos(8, 2, 0);
    private static final int TEST_SIMULATION_DISTANCE = 6;
    private static final int PROBE_TICKET_RADIUS = 3;
    private static final int LIGHT_GRID_STEP = 8;
    private static final double SECTOR_TANGENT = Math.tan(Math.toRadians(22.5));

    private BrainInAJarHordeGameTest() {
    }

    public static void verifyEntityTickingRange(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        BlockPos brainPos = helper.absolutePos(REMOTE_BRAIN);
        var brainChunk = new ChunkPos(brainPos);
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        var boundaryChunk = new ChunkPos(brainChunk.x + TEST_SIMULATION_DISTANCE, brainChunk.z);
        BlockPos boundary = boundaryChunk.getMiddleBlockPosition(brainPos.getY());

        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setBlockAndUpdate(brainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(boundaryChunk.x + x, boundaryChunk.z + z);
            }
        }

        Runnable cleanup = () -> {
            level.removeBlock(brainPos, false);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(199, cleanup);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                level.isPositionEntityTicking(brainPos) && level.isPositionEntityTicking(boundary),
                "Brain in a Jar must keep its center and simulation-distance boundary entity ticking"
            ))
            .thenExecute(() -> level.destroyBlock(brainPos, false))
            .thenWaitUntil(() -> helper.assertTrue(
                !level.isPositionEntityTicking(brainPos) && !level.isPositionEntityTicking(boundary),
                "Removing Brain in a Jar must release its entity-ticking range"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyDirectionalNightHorde(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty originalDifficulty = level.getDifficulty();
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        BlockPos brainPos = helper.absolutePos(REMOTE_BRAIN);
        AABB query = new AABB(brainPos).inflate(144.0, 48.0, 144.0);
        Map<UUID, BlockPos> initialPositions = new LinkedHashMap<>();
        int[] previousCount = {0};

        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunk(brainPos);
        prepareSpawnArea(level, brainPos, true);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
        hordeZombies(level, query).forEach(Entity::discard);
        level.setDayTime(13000L);

        Runnable cleanup = () -> {
            hordeZombies(level, query).forEach(Entity::discard);
            level.removeBlock(brainPos, false);
            clearSpawnArea(level, brainPos, true);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(599, cleanup);
        helper.onEachTick(() -> {
            List<HordeZombie> zombies = hordeZombies(level, query);
            assertWithCleanup(
                helper,
                cleanup,
                zombies.size() <= previousCount[0] + 1,
                "A Brain in a Jar must add at most one horde zombie per tick"
            );
            previousCount[0] = zombies.size();
            zombies.forEach(zombie -> initialPositions.putIfAbsent(zombie.getUUID(), zombie.blockPosition()));
        });

        helper.startSequence()
            .thenIdle(10)
            .thenExecute(() -> assertWithCleanup(
                helper, cleanup, hordeZombies(level, query).isEmpty(), "Night without a Brain must not create a horde"
            ))
            .thenExecute(() -> {
                level.setDayTime(1000L);
                level.setBlockAndUpdate(brainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
            })
            .thenIdle(10)
            .thenExecute(() -> assertWithCleanup(
                helper, cleanup, hordeZombies(level, query).isEmpty(), "A daytime Brain must not create a horde"
            ))
            .thenExecute(() -> level.setDayTime(12999L))
            .thenWaitUntil(() -> {
                helper.assertTrue(initialPositions.size() >= 32, "The nighttime Brain did not create 32 horde zombies");
                assertDirectionalPositions(helper, level, brainPos, initialPositions.values());
            })
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyCapacityAndGates(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty originalDifficulty = level.getDifficulty();
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        BlockPos brainPos = helper.absolutePos(REMOTE_BRAIN);
        AABB query = new AABB(brainPos).inflate(160.0, 48.0, 160.0);
        int[] previousCount = {0};

        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunk(brainPos);
        prepareSpawnArea(level, brainPos, false);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
        hordeZombies(level, query).forEach(Entity::discard);
        level.setDayTime(18000L);
        level.setBlockAndUpdate(brainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());

        Runnable cleanup = () -> {
            hordeZombies(level, query).forEach(Entity::discard);
            level.removeBlock(brainPos, false);
            clearSpawnArea(level, brainPos, false);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(1199, cleanup);
        helper.onEachTick(() -> {
            List<HordeZombie> zombies = hordeZombies(level, query);
            int count = zombies.size();
            assertWithCleanup(
                helper, cleanup, count <= previousCount[0] + 1, "A Brain in a Jar must add at most one horde zombie per tick"
            );
            previousCount[0] = count;
            zombies.forEach(zombie -> zombie.setNoAi(true));
        });
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(hordeZombies(level, query).size() == 300, "The horde did not reach exactly 300 zombies"))
            .thenExecuteFor(20, () -> {
                if (hordeZombies(level, query).size() != 300) {
                    cleanup.run();
                    helper.fail("The horde exceeded or fell below 300 zombies");
                }
            })
            .thenExecute(() -> hordeZombies(level, query).getFirst().kill())
            .thenWaitUntil(() -> helper.assertTrue(hordeZombies(level, query).size() == 300, "A dead horde zombie was not refilled"))
            .thenExecute(() -> {
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                hordeZombies(level, query).getFirst().kill();
            })
            .thenExecuteFor(20, () -> {
                if (hordeZombies(level, query).size() != 299) {
                    cleanup.run();
                    helper.fail("doMobSpawning=false must pause refill at 299");
                }
            })
            .thenExecute(() -> gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server))
            .thenWaitUntil(() -> helper.assertTrue(hordeZombies(level, query).size() == 300, "Re-enabling mob spawning did not resume refill"))
            .thenExecute(() -> server.setDifficulty(Difficulty.PEACEFUL, true))
            .thenWaitUntil(() -> helper.assertTrue(hordeZombies(level, query).isEmpty(), "Peaceful difficulty did not clear the horde"))
            .thenExecute(() -> level.destroyBlock(brainPos, false))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyIndependentHordes(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty originalDifficulty = level.getDifficulty();
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        BlockPos firstBrain = helper.absolutePos(REMOTE_BRAIN);
        BlockPos secondBrain = firstBrain.offset(0, 0, 320);
        AABB firstQuery = new AABB(firstBrain).inflate(144.0, 48.0, 144.0);
        AABB secondQuery = new AABB(secondBrain).inflate(144.0, 48.0, 144.0);
        Map<UUID, BlockPos> firstInitialPositions = new LinkedHashMap<>();
        Map<UUID, BlockPos> secondInitialPositions = new LinkedHashMap<>();
        HordeZombie[] movedFromFirst = {null};
        HordeZombie[] killedFromSecond = {null};

        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunk(firstBrain);
        level.getChunk(secondBrain);
        prepareSpawnArea(level, firstBrain, true);
        prepareSpawnArea(level, secondBrain, true);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
        hordeZombies(level, firstQuery).forEach(Entity::discard);
        hordeZombies(level, secondQuery).forEach(Entity::discard);
        level.setDayTime(18000L);
        level.setBlockAndUpdate(firstBrain, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(secondBrain, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());

        Runnable cleanup = () -> {
            hordeZombies(level, firstQuery).forEach(Entity::discard);
            hordeZombies(level, secondQuery).forEach(Entity::discard);
            level.removeBlock(firstBrain, false);
            level.removeBlock(secondBrain, false);
            clearSpawnArea(level, firstBrain, true);
            clearSpawnArea(level, secondBrain, true);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(3999, cleanup);
        helper.onEachTick(() -> {
            List<HordeZombie> firstHordeZombies = hordeZombies(level, firstQuery);
            List<HordeZombie> secondHordeZombies = hordeZombies(level, secondQuery);
            firstHordeZombies.forEach(zombie -> {
                zombie.setNoAi(true);
                firstInitialPositions.putIfAbsent(zombie.getUUID(), zombie.blockPosition());
            });
            secondHordeZombies.forEach(zombie -> {
                zombie.setNoAi(true);
                secondInitialPositions.putIfAbsent(zombie.getUUID(), zombie.blockPosition());
            });
        });

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                hordeZombies(level, firstQuery).size() == 300 && hordeZombies(level, secondQuery).size() == 300,
                "Two Brains in a Jar did not independently reach 300 horde zombies each"
            ))
            .thenExecute(() -> {
                try {
                    assertDirectionalPositions(helper, level, firstBrain, firstInitialPositions.values());
                    assertDirectionalPositions(helper, level, secondBrain, secondInitialPositions.values());
                    movedFromFirst[0] = hordeZombies(level, firstQuery).getFirst();
                    killedFromSecond[0] = hordeZombies(level, secondQuery).getFirst();
                    movedFromFirst[0].moveTo(
                        secondBrain.getX() + 64.5,
                        secondBrain.getY(),
                        secondBrain.getZ() + 0.5,
                        movedFromFirst[0].getYRot(),
                        movedFromFirst[0].getXRot()
                    );
                } catch (RuntimeException exception) {
                    cleanup.run();
                    throw exception;
                }
            })
            .thenIdle(1)
            .thenExecuteFor(20, () -> {
                int firstCount = hordeZombies(level, firstQuery).size();
                int secondCount = hordeZombies(level, secondQuery).size();
                if (firstCount != 299 || secondCount != 301) {
                    cleanup.run();
                    helper.fail("Moving a horde zombie near another Brain changed ownership or capacity");
                }
            })
            .thenExecute(() -> killedFromSecond[0].kill())
            .thenWaitUntil(() -> helper.assertTrue(
                hordeZombies(level, firstQuery).size() == 299 && hordeZombies(level, secondQuery).size() == 301,
                "A death did not release and refill only the original Brain's slot"
            ))
            .thenExecute(() -> movedFromFirst[0].kill())
            .thenWaitUntil(() -> helper.assertTrue(
                hordeZombies(level, firstQuery).size() == 300 && hordeZombies(level, secondQuery).size() == 300,
                "The moved zombie did not release its original Brain's slot"
            ))
            .thenExecute(() -> level.destroyBlock(firstBrain, false))
            .thenExecute(() -> assertWithCleanup(
                helper,
                cleanup,
                hordeZombies(level, secondQuery).size() == 300,
                "Removing one Brain changed the other Brain's horde"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyRemoteOwnershipRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty originalDifficulty = level.getDifficulty();
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        BlockPos brainPos = helper.absolutePos(REMOTE_BRAIN);
        BlockPos remotePos = brainPos.offset(384, 0, 0);
        var remoteChunk = new ChunkPos(remotePos);
        var remoteTicket = TicketType.create("they_are_billions:test_remote_horde", BlockPos::compareTo);
        BlockPos pendingOwnerPos = brainPos.offset(16384, 0, 16384);
        var pendingOwnerChunk = new ChunkPos(pendingOwnerPos);
        var pendingOwnerTicket = TicketType.create("they_are_billions:test_pending_owner", BlockPos::compareTo);
        AABB localQuery = new AABB(brainPos).inflate(144.0, 48.0, 144.0);
        AABB remoteQuery = new AABB(remotePos).inflate(8.0, 16.0, 8.0);
        int[] remoteTicketRadius = {-1};
        boolean[] pendingOwnerTicketActive = {false};
        HordeZombie[] pendingOwnerProbe = {null};
        UUID[] remoteZombieId = {null};

        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunk(brainPos);
        prepareSpawnArea(level, brainPos, false);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
        hordeZombies(level, localQuery).forEach(Entity::discard);
        level.setDayTime(18000L);
        level.setBlockAndUpdate(brainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());

        Runnable cleanup = () -> {
            if (remoteTicketRadius[0] >= 0) {
                level.getChunkSource().removeRegionTicket(remoteTicket, remoteChunk, remoteTicketRadius[0], remotePos);
                remoteTicketRadius[0] = -1;
            }
            if (pendingOwnerTicketActive[0]) {
                level.getChunkSource().removeRegionTicket(pendingOwnerTicket, pendingOwnerChunk, 0, pendingOwnerPos);
                pendingOwnerTicketActive[0] = false;
            }
            if (pendingOwnerProbe[0] != null) {
                pendingOwnerProbe[0].discard();
            }
            hordeZombies(level, localQuery).forEach(Entity::discard);
            hordeZombies(level, remoteQuery).forEach(Entity::discard);
            level.removeBlock(brainPos, false);
            clearSpawnArea(level, brainPos, false);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(3999, cleanup);
        helper.onEachTick(() -> {
            hordeZombies(level, localQuery).forEach(zombie -> zombie.setNoAi(true));
            hordeZombies(level, remoteQuery).forEach(zombie -> zombie.setNoAi(true));
        });

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                hordeZombies(level, localQuery).size() == 300, "The horde did not reach exactly 300 zombies"
            ))
            .thenExecute(() -> {
                level.getChunkSource().addRegionTicket(pendingOwnerTicket, pendingOwnerChunk, 0, pendingOwnerPos);
                pendingOwnerTicketActive[0] = true;
                level.getChunkSource().getChunk(pendingOwnerChunk.x, pendingOwnerChunk.z, ChunkStatus.EMPTY, true);
                helper.assertTrue(
                    level.getChunkSource().hasChunk(pendingOwnerChunk.x, pendingOwnerChunk.z)
                        && level.getChunkSource().getChunkNow(pendingOwnerChunk.x, pendingOwnerChunk.z) == null,
                    "The test did not establish an owner chunk awaiting FULL promotion"
                );

                pendingOwnerProbe[0] = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
                helper.assertTrue(pendingOwnerProbe[0] != null, "Could not create the pending-owner recovery probe");
                CompoundTag tag = hordeZombies(level, localQuery).getFirst().saveWithoutId(new CompoundTag());
                tag.remove("UUID");
                tag.putLong("BrainPos", pendingOwnerPos.asLong());
                pendingOwnerProbe[0].load(tag);
                pendingOwnerProbe[0].moveTo(brainPos.getX() + 0.5, brainPos.getY() + 80.0, brainPos.getZ() + 0.5);
                pendingOwnerProbe[0].setNoGravity(true);

                helper.assertTrue(
                    level.addFreshEntity(pendingOwnerProbe[0]), "Could not add the pending-owner recovery probe"
                );
                helper.assertTrue(
                    level.getChunkSource().getChunkNow(pendingOwnerChunk.x, pendingOwnerChunk.z) == null,
                    "Loading a horde zombie synchronously promoted its owner chunk to FULL"
                );
            })
            .thenWaitUntil(() -> {
                CompoundTag tag = pendingOwnerProbe[0].saveWithoutId(new CompoundTag());
                helper.assertTrue(
                    level.getChunkSource().getChunkNow(pendingOwnerChunk.x, pendingOwnerChunk.z) != null
                        && !tag.contains("BrainPos"),
                    "The horde zombie was not revalidated after its owner chunk reached FULL"
                );
            })
            .thenExecute(() -> {
                pendingOwnerProbe[0].discard();
                helper.assertTrue(
                    level.getChunkSource().getChunkNow(pendingOwnerChunk.x, pendingOwnerChunk.z) != null,
                    "Unloading a horde zombie changed its already-loaded owner chunk"
                );
            })
            .thenExecute(() -> {
                remoteTicketRadius[0] = 2;
                level.getChunkSource().addRegionTicket(remoteTicket, remoteChunk, remoteTicketRadius[0], remotePos);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                level.isPositionEntityTicking(remotePos), "The remote ownership chunk did not become entity ticking"
            ))
            .thenExecute(() -> {
                var remoteZombie = hordeZombies(level, localQuery).getFirst();
                remoteZombieId[0] = remoteZombie.getUUID();
                remoteZombie.moveTo(remotePos.getX() + 0.5, remotePos.getY(), remotePos.getZ() + 0.5);
            })
            .thenIdle(1)
            .thenExecute(() -> {
                level.getChunkSource().removeRegionTicket(remoteTicket, remoteChunk, remoteTicketRadius[0], remotePos);
                remoteTicketRadius[0] = -1;
            })
            .thenWaitUntil(() -> helper.assertTrue(
                !level.areEntitiesLoaded(remoteChunk.toLong()), "The remote ownership chunk did not unload"
            ))
            .thenIdle(20)
            .thenExecute(() -> {
                var probe = EntityType.ARMOR_STAND.create(level);
                helper.assertTrue(probe != null, "Could not create the UUID ownership probe");
                probe.setUUID(remoteZombieId[0]);
                probe.moveTo(brainPos.getX() + 0.5, brainPos.getY(), brainPos.getZ() + 0.5);
                boolean added = level.tryAddFreshEntityWithPassengers(probe);
                if (added) {
                    probe.discard();
                }
                assertWithCleanup(
                    helper,
                    cleanup,
                    added,
                    "A runtime-unloaded zombie remained registered in the entity manager"
                );
            })
            .thenWaitUntil(() -> helper.assertTrue(
                hordeZombies(level, localQuery).size() == 300, "The unloaded owner slot was not refilled"
            ))
            .thenExecute(() -> {
                remoteTicketRadius[0] = 0;
                level.getChunkSource().addRegionTicket(remoteTicket, remoteChunk, remoteTicketRadius[0], remotePos);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                level.areEntitiesLoaded(remoteChunk.toLong()) && !level.isPositionEntityTicking(remotePos),
                "The remote ownership chunk did not reload as tracked but not entity ticking"
            ))
            .thenWaitUntil(() -> helper.assertTrue(
                hordeZombies(level, localQuery).size() == 300 && hordeZombies(level, remoteQuery).isEmpty(),
                "A tracked but non-ticking zombie rejoined and exceeded its Brain's 300-zombie budget"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyTargetingStateMachine(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        boolean originalMobGriefing = gameRules.getBoolean(GameRules.RULE_MOBGRIEFING);
        BlockPos brainPos = helper.absolutePos(TARGET_TEST_BRAIN);
        BlockPos turtleEggPos = brainPos.offset(8, 0, 1);
        Difficulty originalDifficulty = level.getDifficulty();
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        AABB query = new AABB(brainPos).inflate(144.0, 48.0, 144.0);
        HordeZombie[] zombie = {null};
        double[] initialBrainDistance = {0.0};
        ServerPlayer[] nearest = {null};
        ServerPlayer[] farther = {null};
        ServerPlayer[] creative = {null};
        ServerPlayer[] spectator = {null};

        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunk(brainPos);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_MOBGRIEFING).set(true, server);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(18000L);

        Runnable cleanup = () -> {
            removeTestPlayer(server, nearest[0]);
            removeTestPlayer(server, farther[0]);
            removeTestPlayer(server, creative[0]);
            removeTestPlayer(server, spectator[0]);
            hordeZombies(level, query).forEach(Entity::discard);
            level.removeBlock(brainPos, false);
            clearTargetArena(level, brainPos);
            gameRules.getRule(GameRules.RULE_MOBGRIEFING).set(originalMobGriefing, server);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(1599, cleanup);

        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                prepareTargetArena(level, brainPos);
                level.setBlockAndUpdate(brainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
                level.setBlockAndUpdate(turtleEggPos, Blocks.TURTLE_EGG.defaultBlockState());
            })
            .thenIdle(20)
            .thenExecute(() -> {
                zombie[0] = createOwnedHordeZombie(helper, level, brainPos, brainPos.offset(8, 0, 0));
                initialBrainDistance[0] = zombie[0].distanceToSqr(brainPos.getCenter());
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].distanceToSqr(brainPos.getCenter()) < initialBrainDistance[0],
                "An owned horde zombie did not move toward its Brain"
            ))
            .thenIdle(100)
            .thenExecute(() -> assertWithCleanup(
                helper,
                cleanup,
                level.getBlockState(turtleEggPos).is(Blocks.TURTLE_EGG),
                "An owned horde zombie broke a block on its route to the Brain"
            ))
            .thenExecute(() -> {
                BlockPos zombiePos = zombie[0].blockPosition();
                creative[0] = addTestPlayer(level, "target-creative", GameType.CREATIVE, zombiePos.offset(2, 0, 0));
                spectator[0] = addTestPlayer(level, "target-spectator", GameType.SPECTATOR, zombiePos.offset(3, 0, 0));
                nearest[0] = addTestPlayer(level, "target-nearest", GameType.SURVIVAL, zombiePos.offset(8, 0, 0));
                farther[0] = addTestPlayer(level, "target-farther", GameType.SURVIVAL, zombiePos.offset(12, 0, 0));
                zombie[0].getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
                assertWithCleanup(
                    helper,
                    cleanup,
                    level.getBlockState(brainPos).is(TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get()),
                    "An owned horde zombie broke its Brain"
                );
            })
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].getTarget() == nearest[0],
                "The owned horde zombie did not choose the nearest attackable player"
            ))
            .thenExecute(() -> farther[0].moveTo(
                zombie[0].getX() + 1.0, zombie[0].getY(), zombie[0].getZ(), farther[0].getYRot(), farther[0].getXRot()
            ))
            .thenExecuteFor(20, () -> assertWithCleanup(
                helper,
                cleanup,
                zombie[0].getTarget() == nearest[0],
                "The owned horde zombie switched to a closer player"
            ))
            .thenExecute(() -> nearest[0].moveTo(
                zombie[0].getX() + 25.0, zombie[0].getY(), zombie[0].getZ(), nearest[0].getYRot(), nearest[0].getXRot()
            ))
            .thenWaitUntil(() -> helper.assertTrue(zombie[0].getTarget() == null, "An out-of-range player remained targeted"))
            .thenExecuteFor(99, () -> assertWithCleanup(
                helper,
                cleanup,
                zombie[0].getTarget() == null,
                "The owned horde zombie reacquired a player before the 100-tick cooldown elapsed"
            ))
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].getTarget() == farther[0],
                "The owned horde zombie did not reacquire a player after the cooldown"
            ))
            .thenExecute(() -> setTestGameType(farther[0], GameType.CREATIVE))
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].getTarget() == null,
                "A player that became unattackable remained targeted"
            ))
            .thenExecute(() -> setTestGameType(farther[0], GameType.SURVIVAL))
            .thenIdle(100)
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].getTarget() == farther[0],
                "The owned horde zombie did not reacquire an attackable player"
            ))
            .thenExecute(() -> farther[0].kill())
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].getTarget() == null,
                "A dead player remained targeted"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyLostOwnershipAndRebinding(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty originalDifficulty = level.getDifficulty();
        BlockPos firstBrain = helper.absolutePos(TARGET_TEST_BRAIN);
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        BlockPos secondBrain = firstBrain.offset(96, 0, 0);
        AABB query = new AABB(firstBrain).inflate(160.0, 48.0, 160.0);
        HordeZombie[] zombie = {null};
        ServerPlayer[] player = {null};
        BlockPos[] shelterRoof = {null};

        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunk(firstBrain);
        level.getChunk(secondBrain);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(18000L);

        Runnable cleanup = () -> {
            removeTestPlayer(server, player[0]);
            hordeZombies(level, query).forEach(Entity::discard);
            level.removeBlock(firstBrain, false);
            level.removeBlock(secondBrain, false);
            clearTargetArena(level, firstBrain);
            if (shelterRoof[0] != null) {
                level.removeBlock(shelterRoof[0], false);
            }
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        };
        helper.runAtTickTime(1599, cleanup);

        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                prepareTargetArena(level, firstBrain);
                level.setBlockAndUpdate(firstBrain, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
            })
            .thenIdle(20)
            .thenExecute(() -> {
                zombie[0] = createOwnedHordeZombie(helper, level, firstBrain, firstBrain.offset(8, 0, 0));
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                player[0] = addTestPlayer(level, "unowned-target", GameType.SURVIVAL, firstBrain.offset(12, 1, 0));
                zombie[0].getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
                level.destroyBlock(firstBrain, false);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].isAlive() && savedBrainPos(zombie[0]) == null,
                "Destroying a Brain removed its horde zombie or left stale ownership"
            ))
            .thenWaitUntil(() -> helper.assertTrue(
                zombie[0].getTarget() == player[0],
                "An unowned horde zombie did not resume vanilla player targeting"
            ))
            .thenExecute(() -> {
                player[0].moveTo(zombie[0].getX() + 200.0, zombie[0].getY(), zombie[0].getZ(), 0.0F, 0.0F);
                zombie[0].checkDespawn();
                assertWithCleanup(helper, cleanup, zombie[0].isAlive(), "A horde zombie despawned beyond 128 blocks");
                removeTestPlayer(server, player[0]);
                player[0] = null;
                zombie[0].setNoAi(true);
                shelterRoof[0] = BlockPos.containing(zombie[0].getX(), zombie[0].getEyeY(), zombie[0].getZ()).above();
                level.setBlock(shelterRoof[0], Blocks.STONE.defaultBlockState(), 2);
                level.setDayTime(1000L);
                level.setBlockAndUpdate(secondBrain, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());
            })
            .thenIdle(120)
            .thenExecute(() -> assertWithCleanup(
                helper,
                cleanup,
                savedBrainPos(zombie[0]) == null,
                "An unowned horde zombie rebound during daytime"
            ))
            .thenExecute(() -> level.setDayTime(18000L))
            .thenWaitUntil(() -> helper.assertTrue(
                secondBrain.equals(savedBrainPos(zombie[0])),
                "An unowned horde zombie did not join the nearest nighttime Brain"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifySunriseCleanup(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var gameRules = level.getGameRules();
        boolean originalMobSpawning = gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty originalDifficulty = level.getDifficulty();
        int originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        long originalDayTime = level.getDayTime();
        float originalRainLevel = level.getRainLevel(1.0F);
        float originalThunderLevel = level.getThunderLevel(1.0F);
        BlockPos brainPos = helper.absolutePos(REMOTE_BRAIN);
        BlockPos probeOrigin = helper.absolutePos(TARGET_TEST_BRAIN.offset(0, 2, 8));
        AABB query = new AABB(brainPos).inflate(144.0, 48.0, 144.0);
        BlockPos[] protectedPositions = {
            probeOrigin,
            probeOrigin.offset(3, 0, 0),
            probeOrigin.offset(6, 0, 0),
            probeOrigin.offset(9, 0, 0),
            probeOrigin.offset(12, 0, 0),
            probeOrigin.offset(15, 0, 0)
        };
        BlockPos shelteredPos = probeOrigin.offset(0, 0, 3);
        BlockPos terminalPos = probeOrigin.offset(3, 0, 3);
        BlockPos currentTerminalPos = probeOrigin.offset(6, 0, 3);
        HordeZombie[] protectedZombies = new HordeZombie[protectedPositions.length];
        HordeZombie[] sheltered = {null};
        HordeZombie[] terminal = {null};
        HordeZombie[] currentTerminal = {null};

        var decayEffect = BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(TheyAreBillions.DECAY.getKey());
        BlockPos probeTicketPos = probeOrigin;
        var probeTicket = TicketType.create("they_are_billions:test_sunrise_probes", BlockPos::compareTo);
        server.getPlayerList().setSimulationDistance(TEST_SIMULATION_DISTANCE);
        level.getChunkSource().addRegionTicket(probeTicket, new ChunkPos(probeTicketPos), PROBE_TICKET_RADIUS, probeTicketPos);
        level.getChunk(brainPos);
        prepareSpawnArea(level, brainPos, false);
        server.setDifficulty(Difficulty.HARD, true);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
        hordeZombies(level, query).forEach(Entity::discard);
        level.setDayTime(18000L);
        level.updateSkyBrightness();
        level.setBlockAndUpdate(brainPos, TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get().defaultBlockState());

        Runnable cleanup = () -> {
            for (HordeZombie zombie : protectedZombies) {
                if (zombie != null) {
                    zombie.discard();
                }
            }
            if (sheltered[0] != null) {
                sheltered[0].discard();
            }
            if (terminal[0] != null) {
                terminal[0].discard();
            }
            if (currentTerminal[0] != null) {
                currentTerminal[0].discard();
            }
            hordeZombies(level, query).forEach(Entity::discard);
            level.getChunkSource().removeRegionTicket(
                probeTicket, new ChunkPos(probeTicketPos), PROBE_TICKET_RADIUS, probeTicketPos
            );
            level.removeBlock(brainPos, false);
            clearSpawnArea(level, brainPos, false);
            for (BlockPos position : protectedPositions) {
                level.removeBlock(position, false);
                level.removeBlock(position.below(), false);
            }
            level.removeBlock(shelteredPos.above(2), false);
            level.removeBlock(terminalPos.above(2), false);
            level.removeBlock(currentTerminalPos.above(2), false);
            level.removeBlock(shelteredPos.below(), false);
            level.removeBlock(terminalPos.below(), false);
            level.removeBlock(currentTerminalPos.below(), false);
            level.setRainLevel(originalRainLevel);
            level.setThunderLevel(originalThunderLevel);
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning, server);
            server.setDifficulty(originalDifficulty, true);
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
            level.setDayTime(originalDayTime);
            level.updateSkyBrightness();
        };
        helper.runAtTickTime(799, cleanup);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                !hordeZombies(level, query).isEmpty(),
                "A nighttime Brain did not create a horde zombie before sunrise"
            ))
            .thenExecute(() -> {
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                hordeZombies(level, query).forEach(Entity::discard);
                level.setDayTime(23460L);
                level.updateSkyBrightness();
                for (int index = 0; index < protectedPositions.length; index++) {
                    level.setBlock(protectedPositions[index].below(), Blocks.STONE.defaultBlockState(), 2);
                }
                level.setBlock(shelteredPos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(shelteredPos.above(2), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(terminalPos.above(2), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(terminalPos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(currentTerminalPos.above(2), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(currentTerminalPos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(protectedPositions[3], Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(protectedPositions[5], Blocks.POWDER_SNOW.defaultBlockState(), 2);
                level.setRainLevel(1.0F);
                level.updateSkyBrightness();
                level.setBlock(protectedPositions[4], Blocks.BUBBLE_COLUMN.defaultBlockState(), 2);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(level.isPositionEntityTicking(shelteredPos), "The daylight probe area is not entity ticking");
                for (BlockPos position : protectedPositions) {
                    helper.assertTrue(level.canSeeSky(position.above()), "An exposed daylight probe is sheltered");
                }
                helper.assertTrue(!level.canSeeSky(shelteredPos.above()), "The decay probe is not sheltered");
                helper.assertTrue(!level.canSeeSky(terminalPos.above()), "The terminal decay probe is not sheltered");
                helper.assertTrue(!level.canSeeSky(currentTerminalPos.above()), "The current-health decay probe is not sheltered");
                helper.assertTrue(level.isRainingAt(protectedPositions[2]), "The rain protection probe is not standing in rain");
                helper.assertTrue(level.isNight(), "The rain probe did not exercise weather-darkened daytime");
            })
            .thenExecute(() -> {
                level.setDayTime(23460L);
                for (int index = 0; index < protectedPositions.length; index++) {
                    protectedZombies[index] = createHordeZombie(helper, level, protectedPositions[index]);
                }
                protectedZombies[1].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                sheltered[0] = createHordeZombie(helper, level, shelteredPos);
                terminal[0] = createHordeZombie(helper, level, terminalPos);
                currentTerminal[0] = createHordeZombie(helper, level, currentTerminalPos);

                for (int index = 0; index < protectedZombies.length; index++) {
                    HordeZombie zombie = protectedZombies[index];
                    assertWithCleanup(
                        helper,
                        cleanup,
                        level.canSeeSky(BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ())),
                        "Exposed horde zombie probe has no sky access: " + index
                    );
                    level.tickNonPassenger(zombie);
                    assertWithCleanup(
                        helper,
                        cleanup,
                        zombie.isDeadOrDying(),
                        "Daylight did not kill exposed horde zombie probe " + index
                    );
                }
                level.tickNonPassenger(sheltered[0]);
                level.tickNonPassenger(terminal[0]);
                level.tickNonPassenger(currentTerminal[0]);

                var decay = sheltered[0].getEffect(decayEffect);
                var terminalDecay = terminal[0].getEffect(decayEffect);
                var currentTerminalDecay = currentTerminal[0].getEffect(decayEffect);
                assertWithCleanup(
                    helper,
                    cleanup,
                    decay != null
                        && terminalDecay != null
                        && currentTerminalDecay != null
                        && decay.isInfiniteDuration()
                        && !decay.isAmbient()
                        && !decay.isVisible()
                        && !decay.showIcon(),
                    "A sheltered horde zombie did not receive invisible, non-ambient infinite decay"
                );

                sheltered[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(5.0);
                sheltered[0].setHealth(5.0F);
                sheltered[0].tickCount = 18;
                level.tickNonPassenger(sheltered[0]);
                assertWithCleanup(
                    helper,
                    cleanup,
                    sheltered[0].getAttributeBaseValue(Attributes.MAX_HEALTH) == 5.0 && sheltered[0].getHealth() == 5.0F,
                    "Decay applied before its 20-tick interval"
                );
                sheltered[0].tickCount = 19;
                level.tickNonPassenger(sheltered[0]);
                assertWithCleanup(
                    helper,
                    cleanup,
                    sheltered[0].getAttributeBaseValue(Attributes.MAX_HEALTH) == 4.0 && sheltered[0].getHealth() == 4.0F,
                    "Decay did not reduce maximum and current health after 20 ticks"
                );

                terminal[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
                terminal[0].setHealth(1.0F);
                terminal[0].tickCount = 19;
                level.tickNonPassenger(terminal[0]);
                assertWithCleanup(helper, cleanup, terminal[0].isDeadOrDying(), "Decay did not kill at its minimum health");

                currentTerminal[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(5.0);
                currentTerminal[0].setHealth(1.0F);
                currentTerminal[0].tickCount = 19;
                level.tickNonPassenger(currentTerminal[0]);
                assertWithCleanup(
                    helper,
                    cleanup,
                    currentTerminal[0].isDeadOrDying(),
                    "Decay did not kill when current health reached its minimum"
                );

                level.setDayTime(18000L);
                level.updateSkyBrightness();
                sheltered[0].tickCount = 39;
                level.tickNonPassenger(sheltered[0]);
                assertWithCleanup(
                    helper,
                    cleanup,
                    sheltered[0].getAttributeBaseValue(Attributes.MAX_HEALTH) == 3.0
                        && sheltered[0].getHealth() == 3.0F
                        && sheltered[0].hasEffect(decayEffect),
                    "Nightfall removed decay or stopped its 20-tick health loss"
                );
                level.setDayTime(23460L);
                level.updateSkyBrightness();
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
                hordeZombies(level, query).forEach(Entity::discard);
            })
            .thenIdle(100)
            .thenExecute(() -> assertWithCleanup(
                helper,
                cleanup,
                hordeZombies(level, query).isEmpty(),
                "A Brain in a Jar refilled its horde after sunrise"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    private static void prepareSpawnArea(ServerLevel level, BlockPos brainPos, boolean lit) {
        setSpawnArea(level, brainPos, lit, Blocks.STONE.defaultBlockState(), Blocks.LIGHT.defaultBlockState());
    }

    private static void clearSpawnArea(ServerLevel level, BlockPos brainPos, boolean lit) {
        setSpawnArea(level, brainPos, lit, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
    }

    private static void setSpawnArea(
        ServerLevel level, BlockPos brainPos, boolean lit, BlockState floorState, BlockState lightState
    ) {
        for (int x = -136; x <= 136; x++) {
            for (int z = -136; z <= 136; z++) {
                double distance = Math.hypot(x, z);
                boolean spawnPosition = distance >= 64.0 && distance <= 128.0;
                boolean lightPosition = lit
                    && distance >= 56.0
                    && distance <= 136.0
                    && Math.floorMod(x, LIGHT_GRID_STEP) == 0
                    && Math.floorMod(z, LIGHT_GRID_STEP) == 0;
                if (!spawnPosition && !lightPosition) {
                    continue;
                }
                BlockPos spawn = brainPos.offset(x, 0, z);
                if (spawnPosition) {
                    level.setBlock(spawn.below(), floorState, 2);
                }
                if (lightPosition) {
                    level.setBlock(spawn.above(4), lightState, 2);
                }
            }
        }
    }

    private static void prepareTargetArena(ServerLevel level, BlockPos brainPos) {
        setTargetArena(level, brainPos, Blocks.STONE.defaultBlockState());
    }

    private static void clearTargetArena(ServerLevel level, BlockPos brainPos) {
        setTargetArena(level, brainPos, Blocks.AIR.defaultBlockState());
    }

    private static void setTargetArena(ServerLevel level, BlockPos brainPos, BlockState floor) {
        for (int x = -4; x <= 40; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlock(brainPos.offset(x, -1, z), floor, 2);
                level.setBlock(brainPos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(brainPos.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static HordeZombie createHordeZombie(
        GameTestHelper helper, ServerLevel level, BlockPos zombiePos
    ) {
        var zombie = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
        zombie.moveTo(zombiePos.getX() + 0.5, zombiePos.getY(), zombiePos.getZ() + 0.5, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        helper.assertTrue(zombie.validateOwnership(level), "The horde zombie probe has invalid ownership");
        helper.assertTrue(level.addFreshEntity(zombie), "Could not add a horde zombie probe");
        zombie.setDeltaMovement(0.0, 0.0, 0.0);
        return zombie;
    }

    private static HordeZombie createOwnedHordeZombie(
        GameTestHelper helper, ServerLevel level, BlockPos brainPos, BlockPos zombiePos
    ) {
        var zombie = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get().create(level);
        helper.assertTrue(
            level.getBlockEntity(brainPos) instanceof io.github.insulinocytus.theyarebillions.BrainInAJarBlockEntity,
            "The horde zombie probe has no Brain"
        );
        var brain = (io.github.insulinocytus.theyarebillions.BrainInAJarBlockEntity) level.getBlockEntity(brainPos);
        CompoundTag brainTag = brain.saveWithoutMetadata(level.registryAccess());
        ListTag owned = brainTag.getList("OwnedHordeZombies", Tag.TAG_INT_ARRAY);
        owned.add(NbtUtils.createUUID(zombie.getUUID()));
        brainTag.put("OwnedHordeZombies", owned);
        brain.loadCustomOnly(brainTag, level.registryAccess());

        CompoundTag zombieTag = zombie.saveWithoutId(new CompoundTag());
        zombieTag.putLong("BrainPos", brainPos.asLong());
        zombie.load(zombieTag);
        zombie.moveTo(zombiePos.getX() + 0.5, zombiePos.getY(), zombiePos.getZ() + 0.5, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(zombie), "Could not add a horde zombie probe");
        return zombie;
    }

    private static ServerPlayer addTestPlayer(ServerLevel level, String name, GameType gameType, BlockPos pos) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new TestServerPlayer(level, cookie, gameType);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        setTestGameType(player, gameType);
        player.setHealth(player.getMaxHealth());
        player.setNoGravity(true);
        return player;
    }

    private static void setTestGameType(ServerPlayer player, GameType gameType) {
        ((TestServerPlayer) player).gameType = gameType;
        player.getAbilities().invulnerable = gameType.isCreative();
    }

    private static void removeTestPlayer(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        if (player != null && server.getPlayerList().getPlayer(player.getUUID()) == player) {
            server.getPlayerList().remove(player);
        }
    }

    private static final class TestServerPlayer extends ServerPlayer {
        private GameType gameType;

        private TestServerPlayer(ServerLevel level, CommonListenerCookie cookie, GameType gameType) {
            super(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            this.gameType = gameType;
            this.getAbilities().invulnerable = gameType.isCreative();
        }

        @Override
        public boolean isSpectator() {
            return this.gameType == GameType.SPECTATOR;
        }

        @Override
        public boolean isCreative() {
            return this.gameType == GameType.CREATIVE;
        }
    }

    private static BlockPos savedBrainPos(HordeZombie zombie) {
        CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
        return tag.contains("BrainPos") ? BlockPos.of(tag.getLong("BrainPos")) : null;
    }

    private static List<HordeZombie> hordeZombies(ServerLevel level, AABB query) {
        return level.getEntities(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get(), query, Entity::isAlive);
    }

    private static void assertDirectionalPositions(
        GameTestHelper helper, ServerLevel level, BlockPos brainPos, Iterable<BlockPos> positions
    ) {
        var iterator = positions.iterator();
        BlockPos first = iterator.next();
        int firstX = first.getX() - brainPos.getX();
        int firstZ = first.getZ() - brainPos.getZ();
        boolean xAxis = Math.abs(firstX) >= Math.abs(firstZ);
        int directionSign = xAxis ? Integer.signum(firstX) : Integer.signum(firstZ);

        for (BlockPos position : positions) {
            int dx = position.getX() - brainPos.getX();
            int dz = position.getZ() - brainPos.getZ();
            double distance = Math.hypot(dx, dz);
            int forward = directionSign * (xAxis ? dx : dz);
            int side = xAxis ? dz : dx;
            helper.assertTrue(distance >= 64.0 && distance <= 128.0, "Horde spawn is outside the 64-128 block annulus: " + position);
            helper.assertTrue(Math.abs(position.getY() - brainPos.getY()) <= 32, "Horde spawn exceeds the vertical range: " + position);
            helper.assertTrue(forward > 0 && Math.abs(side) <= forward * SECTOR_TANGENT, "Horde spawns crossed cardinal sectors: " + position);
            helper.assertTrue(level.getBrightness(LightLayer.BLOCK, position) > 0, "Horde spawn incorrectly required darkness: " + position);
        }
    }

    private static void assertWithCleanup(GameTestHelper helper, Runnable cleanup, boolean condition, String message) {
        if (!condition) {
            cleanup.run();
            helper.fail(message);
        }
    }
}
