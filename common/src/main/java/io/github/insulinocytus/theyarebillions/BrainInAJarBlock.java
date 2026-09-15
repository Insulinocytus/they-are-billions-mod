package io.github.insulinocytus.theyarebillions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

public final class BrainInAJarBlock extends Block implements EntityBlock {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrainInAJarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != TheyAreBillions.BRAIN_IN_A_JAR_BLOCK_ENTITY_TYPE.get()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> BrainInAJarBlockEntity.serverTick(
            tickerLevel, pos, tickerState, (BrainInAJarBlockEntity) blockEntity
        );
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean movedByPiston) {
        if (!state.is(previous.getBlock())
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof BrainInAJarBlockEntity brain) {
            brain.updateTickets(serverLevel, pos);
        }
        super.onPlace(state, level, pos, previous, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof BrainInAJarBlockEntity brain) {
            brain.onBlockRemoved(serverLevel);
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 1.0F / 20.0F;
    }
}
