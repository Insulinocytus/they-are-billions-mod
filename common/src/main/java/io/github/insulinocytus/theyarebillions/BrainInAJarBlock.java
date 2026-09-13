package io.github.insulinocytus.theyarebillions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class BrainInAJarBlock extends Block {
    public BrainInAJarBlock() {
        super(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIAMOND)
                .instrument(NoteBlockInstrument.HAT)
                .strength(3.0F, 3_600_000.0F)
                .noOcclusion()
                .isRedstoneConductor((state, level, pos) -> false)
                .pushReaction(PushReaction.BLOCK)
        );
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 1.0F / 20.0F;
    }
}
