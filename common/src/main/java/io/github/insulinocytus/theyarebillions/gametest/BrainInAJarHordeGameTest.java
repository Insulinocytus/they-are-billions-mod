package io.github.insulinocytus.theyarebillions.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.insulinocytus.theyarebillions.HordeZombie;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class BrainInAJarHordeGameTest {
    private static final BlockPos REMOTE_BRAIN = new BlockPos(96, 2, 0);
    private static final int TEST_SIMULATION_DISTANCE = 6;
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
            .thenIdle(1)
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
