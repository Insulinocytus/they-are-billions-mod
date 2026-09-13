package io.github.insulinocytus.theyarebillions.gametest;

import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;

public final class BrainInAJarGameTest {
    private static final BlockPos SUPPORT = new BlockPos(2, 1, 2);
    private static final BlockPos PLACED = SUPPORT.north();
    private static final BlockPos EXPLOSION_TARGET = new BlockPos(5, 1, 5);

    private BrainInAJarGameTest() {
    }

    public static void verify(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath(TheyAreBillions.MOD_ID, "brain_in_a_jar");
        var block = TheyAreBillions.BRAIN_IN_A_JAR_BLOCK.get();
        var item = TheyAreBillions.BRAIN_IN_A_JAR_ITEM.get();

        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(block).equals(id), "Brain in a Jar block was not registered");
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(item).equals(id), "Brain in a Jar item was not registered");
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == block, "Brain in a Jar item cannot place its block");

        var tab = TheyAreBillions.TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, false, helper.getLevel().registryAccess()));
        helper.assertTrue(tab.getIconItem().is(item), "Brain in a Jar is not the creative tab icon");
        helper.assertTrue(
            tab.getDisplayItems().size() == 1 && tab.getDisplayItems().iterator().next().is(item),
            "The They Are Billions creative tab must contain only Brain in a Jar"
        );

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        helper.setBlock(SUPPORT, Blocks.STONE);
        helper.useBlock(SUPPORT, player);
        helper.assertBlockPresent(block, PLACED);

        var state = block.defaultBlockState();
        helper.assertTrue(state.getLightEmission() == 0, "Brain in a Jar must not emit light");
        helper.assertTrue(!state.hasBlockEntity(), "Brain in a Jar must not have beacon behavior");
        helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "Pistons must not move Brain in a Jar");

        var miner = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(state.getDestroyProgress(miner, helper.getLevel(), helper.absolutePos(PLACED)) == 1.0F / 20.0F, "Brain in a Jar must take 20 ticks to mine by hand");
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        helper.assertTrue(state.getDestroyProgress(miner, helper.getLevel(), helper.absolutePos(PLACED)) == 1.0F / 20.0F, "Brain in a Jar must take 20 ticks to mine with tools");
        miner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        helper.getLevel().destroyBlock(helper.absolutePos(PLACED), true, miner);
        helper.assertBlockNotPresent(block, PLACED);
        helper.assertItemEntityCountIs(item, PLACED, 1.5, 1);

        helper.setBlock(EXPLOSION_TARGET, block);
        var explosion = helper.absolutePos(EXPLOSION_TARGET);
        helper.getLevel().explode(null, explosion.getX() + 0.5, explosion.getY() + 0.5, explosion.getZ() + 0.5, 4.0F, false, Level.ExplosionInteraction.BLOCK);
        helper.assertBlockPresent(block, EXPLOSION_TARGET);
        helper.succeed();
    }
}
