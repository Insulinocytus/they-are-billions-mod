package io.github.insulinocytus.theyarebillions.gametest;

import java.util.Arrays;
import java.util.List;

import io.github.insulinocytus.theyarebillions.HordeZombie;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

/**
 * Verifies every player-observable restriction of the summonable horde zombie on a live server:
 * it is summoned by command as its own entity type, it is an unequipped vanilla adult zombie in
 * size and base attributes, and it never rides, picks items up, breaks doors, summons
 * reinforcements, converts itself or a villager, or leaves drops behind.
 */
public final class HordeZombieGameTest {
    private static final BlockPos FLOOR_MIN = new BlockPos(1, 0, 1);
    private static final BlockPos FLOOR_MAX = new BlockPos(6, 0, 6);
    private static final List<BlockPos> SUMMON_POSITIONS = List.of(
        new BlockPos(2, 1, 2),
        new BlockPos(3, 1, 2),
        new BlockPos(4, 1, 2),
        new BlockPos(5, 1, 2),
        new BlockPos(2, 1, 4),
        new BlockPos(3, 1, 4),
        new BlockPos(4, 1, 4),
        new BlockPos(5, 1, 4)
    );
    private static final BlockPos PICKUP_ITEM = new BlockPos(2, 1, 3);
    private static final BlockPos MOUNT = new BlockPos(3, 1, 3);
    private static final BlockPos VILLAGER = new BlockPos(4, 1, 3);
    private static final int SETTLE_TICKS = 20;
    private static final BlockPos SUNLIGHT_PLAIN = new BlockPos(2, 1, 2);
    private static final BlockPos SUNLIGHT_HELMET = new BlockPos(4, 1, 2);
    private static final BlockPos SUNLIGHT_WATER = new BlockPos(6, 1, 2);
    private static final BlockPos SUNLIGHT_BUBBLE_COLUMN = new BlockPos(8, 1, 2);
    private static final BlockPos SUNLIGHT_POWDER_SNOW = new BlockPos(10, 1, 2);
    private static final BlockPos DECAY_POS = new BlockPos(2, 1, 2);

    private HordeZombieGameTest() {
    }

    public static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        server.setDifficulty(Difficulty.HARD, true);
        BlockPos.betweenClosed(FLOOR_MIN, FLOOR_MAX).forEach(pos -> helper.setBlock(pos, Blocks.STONE));

        var commands = server.getCommands();
        var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        for (BlockPos position : SUMMON_POSITIONS) {
            commands.performPrefixedCommand(
                source.withPosition(helper.absoluteVec(Vec3.atBottomCenterOf(position))),
                "summon they_are_billions:horde_zombie ~ ~ ~"
            );
        }

        var hordeZombies = helper.getEntities(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get());
        helper.assertTrue(
            hordeZombies.size() == SUMMON_POSITIONS.size(),
            "Every /summon they_are_billions:horde_zombie must produce a horde zombie, got " + hordeZombies.size()
        );

        var id = ResourceLocation.fromNamespaceAndPath(TheyAreBillions.MOD_ID, "horde_zombie");
        var adultZombieSize = EntityType.ZOMBIE.getDimensions();
        var vanillaZombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(vanillaZombie != null, "A vanilla zombie could not be created for comparison");
        var hordeZombieType = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get();
        var egg = BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof SpawnEggItem)
            .filter(item -> ((SpawnEggItem) item).spawnsEntity(item.getDefaultInstance(), hordeZombieType))
            .findFirst();
        helper.assertTrue(egg.isEmpty(), "No spawn egg may spawn the horde zombie, but found " + egg.map(Item::toString).orElse(""));
        for (var hordeZombie : hordeZombies) {
            helper.assertTrue(
                BuiltInRegistries.ENTITY_TYPE.getKey(hordeZombie.getType()).equals(id), "The summoned entity must be " + id
            );
            helper.assertTrue(!hordeZombie.isBaby(), "The horde zombie must always be an adult");
            helper.assertTrue(
                hordeZombie instanceof Zombie,
                "The horde zombie must stay a vanilla zombie subclass so it keeps the zombie model, sounds and animations"
            );
            var size = hordeZombie.getDimensions(Pose.STANDING);
            helper.assertTrue(
                Float.compare(size.width(), adultZombieSize.width()) == 0
                    && Float.compare(size.height(), adultZombieSize.height()) == 0
                    && Float.compare(size.eyeHeight(), adultZombieSize.eyeHeight()) == 0
                    && size.attachments()
                        .get(EntityAttachment.PASSENGER, 0, 0.0F)
                        .equals(adultZombieSize.attachments().get(EntityAttachment.PASSENGER, 0, 0.0F)),
                "The horde zombie must use the vanilla adult zombie size and rider attachment"
            );
            assertSameBaseValue(helper, hordeZombie, vanillaZombie, Attributes.MAX_HEALTH);
            assertSameBaseValue(helper, hordeZombie, vanillaZombie, Attributes.FOLLOW_RANGE);
            assertSameBaseValue(helper, hordeZombie, vanillaZombie, Attributes.MOVEMENT_SPEED);
            assertSameBaseValue(helper, hordeZombie, vanillaZombie, Attributes.ATTACK_DAMAGE);
            assertSameBaseValue(helper, hordeZombie, vanillaZombie, Attributes.ARMOR);
            helper.assertTrue(
                hordeZombie.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) == 0.0,
                "The horde zombie must never roll a reinforcement chance"
            );
            assertNoEquipment(helper, hordeZombie);
        }

        var hordeZombie = hordeZombies.getFirst();
        commands.performPrefixedCommand(
            source,
            "data merge entity "
                + hordeZombie.getStringUUID()
                + " {IsBaby:1b,CanPickUpLoot:1b,CanBreakDoors:1b,DrownedConversionTime:0}"
        );
        helper.assertTrue(!hordeZombie.isBaby(), "The horde zombie must stay an adult even when told otherwise");
        helper.assertTrue(!hordeZombie.canPickUpLoot(), "The horde zombie must refuse to pick up loot");
        helper.assertTrue(!hordeZombie.canBreakDoors(), "The horde zombie must not break doors");

        var mount = helper.spawn(EntityType.CHICKEN, MOUNT);
        commands.performPrefixedCommand(source, "ride " + hordeZombie.getStringUUID() + " mount " + mount.getStringUUID());
        helper.assertTrue(!hordeZombie.isPassenger(), "The horde zombie must reject riding");

        helper.spawnItem(Items.DIAMOND, PICKUP_ITEM);

        var villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
        commands.performPrefixedCommand(
            source,
            "damage " + villager.getStringUUID() + " 100 minecraft:mob_attack by " + hordeZombie.getStringUUID()
        );
        helper.assertTrue(!villager.isAlive(), "The horde zombie must be able to kill a villager in the test");
        helper.assertEntityNotPresent(EntityType.ZOMBIE_VILLAGER);

        var lootZombie = hordeZombies.getLast();
        lootZombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        lootZombie.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        lootZombie.hurt(lootZombie.damageSources().playerAttack(player), Float.MAX_VALUE);

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertEntityNotPresent(EntityType.DROWNED);
            helper.assertEntityNotPresent(EntityType.ZOMBIE);
            helper.assertEntityNotPresent(EntityType.ZOMBIE_VILLAGER);
            helper.assertEntityNotPresent(EntityType.EXPERIENCE_ORB);
            helper.assertEntitiesPresent(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get(), SUMMON_POSITIONS.size() - 1);
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
            helper.assertTrue(
                drops.size() == 1 && drops.getFirst().getItem().is(Items.DIAMOND),
                "The only item on the ground must be the untouched diamond, but found " + drops.size()
            );
            assertNoEquipment(helper, hordeZombie);
            helper.succeed();
        });
    }

    public static void verifySunriseDeath(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerLevelData levelData = level.getServer().getWorldData().overworldData();
        long originalDayTime = level.getDayTime();
        Difficulty originalDifficulty = level.getDifficulty();
        int originalClearWeatherTime = levelData.getClearWeatherTime();
        int originalRainTime = levelData.getRainTime();
        int originalThunderTime = levelData.getThunderTime();
        boolean originalRaining = levelData.isRaining();
        boolean originalThundering = levelData.isThundering();
        level.getServer().setDifficulty(Difficulty.HARD, true);
        level.setDayTime(18000L);
        level.setWeatherParameters(0, 6000, true, false);

        List<BlockPos> positions = List.of(
            SUNLIGHT_PLAIN,
            SUNLIGHT_HELMET,
            SUNLIGHT_WATER,
            SUNLIGHT_BUBBLE_COLUMN,
            SUNLIGHT_POWDER_SNOW
        );
        positions.forEach(pos -> helper.setBlock(pos.below(), Blocks.STONE));
        helper.setBlock(SUNLIGHT_WATER, Blocks.WATER);
        helper.setBlock(SUNLIGHT_BUBBLE_COLUMN, Blocks.BUBBLE_COLUMN);
        helper.setBlock(SUNLIGHT_POWDER_SNOW, Blocks.POWDER_SNOW);

        List<HordeZombie> zombies = positions.stream()
            .map(pos -> helper.spawn(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get(), pos))
            .toList();
        zombies.forEach(zombie -> {
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
        });
        zombies.get(1).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));

        Runnable cleanup = () -> {
            zombies.forEach(Entity::discard);
            level.setDayTime(originalDayTime);
            levelData.setClearWeatherTime(originalClearWeatherTime);
            levelData.setRainTime(originalRainTime);
            levelData.setThunderTime(originalThunderTime);
            levelData.setRaining(originalRaining);
            levelData.setThundering(originalThundering);
            level.getServer().setDifficulty(originalDifficulty, true);
        };
        helper.runAtTickTime(99, () -> {
            cleanup.run();
            helper.fail("Daylight-visible horde zombies did not die before the timeout");
        });
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                level.isPositionEntityTicking(helper.absolutePos(SUNLIGHT_PLAIN)),
                "The daylight horde zombie chunk did not become entity ticking"
            ))
            .thenExecute(() -> {
                level.getServer().setDifficulty(Difficulty.HARD, true);
                level.setDayTime(1000L);
            })
            .thenIdle(1)
            .thenWaitUntil(() -> helper.assertTrue(
                zombies.stream().noneMatch(Entity::isAlive),
                "Daylight-visible horde zombies must die immediately through helmets, rain, water, bubble columns and powder snow"
            ))
            .thenExecute(cleanup)
            .thenSucceed();
    }

    public static void verifyCoveredDecay(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty originalDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.HARD, true);
        level.setDayTime(18000L);
        helper.setBlock(DECAY_POS.below(), Blocks.STONE);
        helper.setBlock(DECAY_POS.above(2), Blocks.STONE);

        HordeZombie zombie = helper.spawn(TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get(), DECAY_POS);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setHealth(10.0F);
        double initialMaxHealth = zombie.getAttributeBaseValue(Attributes.MAX_HEALTH);
        var decayChunk = new net.minecraft.world.level.ChunkPos(zombie.blockPosition());
        level.setChunkForced(decayChunk.x, decayChunk.z, true);
        int[] firstDecayTick = {0};

        Runnable cleanup = () -> {
            level.setChunkForced(decayChunk.x, decayChunk.z, false);
            zombie.discard();
            level.getServer().setDifficulty(originalDifficulty, true);
        };
        helper.runAtTickTime(799, cleanup);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                level.isPositionEntityTicking(zombie.blockPosition()),
                "The covered horde zombie chunk did not become entity ticking"
            ))
            .thenExecute(() -> {
                level.getServer().setDifficulty(Difficulty.HARD, true);
                level.setDayTime(1000L);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                zombie.hasEffect(TheyAreBillions.decayEffect()),
                "A covered daytime horde zombie must receive decay"
            ))
            .thenExecute(() -> {
                MobEffectInstance decay = zombie.getEffect(TheyAreBillions.decayEffect());
                helper.assertTrue(
                    decay.getDuration() == MobEffectInstance.INFINITE_DURATION,
                    "Decay must be permanent"
                );
                helper.assertTrue(!decay.isVisible() && !decay.showIcon(), "Decay must hide particles and the HUD icon");
                level.setDayTime(18000L);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                zombie.getAttributeBaseValue(Attributes.MAX_HEALTH) <= initialMaxHealth - 1.0,
                "Decay did not reduce maximum health"
            ))
            .thenExecute(() -> {
                helper.assertTrue(
                    Float.compare(zombie.getHealth(), 9.0F) == 0,
                    "Decay must reduce maximum and current health together"
                );
                firstDecayTick[0] = zombie.tickCount;
            })
            .thenWaitUntil(() -> helper.assertTrue(
                zombie.getAttributeBaseValue(Attributes.MAX_HEALTH) <= initialMaxHealth - 2.0,
                "Decay did not repeat after another 20 ticks"
            ))
            .thenExecute(() -> {
                helper.assertTrue(
                    zombie.tickCount - firstDecayTick[0] == 20 && Float.compare(zombie.getHealth(), 8.0F) == 0,
                    "Decay must reduce both health values every 20 ticks at night"
                );
                zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
                zombie.setHealth(1.0F);
            })
            .thenIdle(20)
            .thenExecute(() -> {
                helper.assertTrue(!zombie.isAlive(), "Decay must kill before maximum or current health reaches zero");
                cleanup.run();
            })
            .thenSucceed();
    }

    private static void assertNoEquipment(GameTestHelper helper, Zombie zombie) {
        List<EquipmentSlot> equipped = Arrays.stream(EquipmentSlot.values())
            .filter(slot -> !zombie.getItemBySlot(slot).isEmpty())
            .toList();
        helper.assertTrue(equipped.isEmpty(), "The horde zombie must not hold or wear anything, but has " + equipped);
    }

    private static void assertSameBaseValue(
        GameTestHelper helper, Zombie hordeZombie, Zombie vanillaZombie, Holder<Attribute> attribute
    ) {
        double expected = vanillaZombie.getAttributeValue(attribute);
        double actual = hordeZombie.getAttributeValue(attribute);
        helper.assertTrue(
            Double.compare(actual, expected) == 0,
            "The horde zombie must reuse the vanilla zombie " + attribute.getRegisteredName() + " of " + expected + ", but was " + actual
        );
    }
}
