package com.insulinocytus.theyarebillions.horde.fabric;

import com.insulinocytus.theyarebillions.horde.HordeBlockBreakingAccess;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public final class HordeBlockBreakingAccessImpl {
    private HordeBlockBreakingAccessImpl() {
    }

    public static ServerPlayer player(ServerLevel level) {
        return FakePlayer.get(level, HordeBlockBreakingAccess.PROFILE);
    }

    public static boolean start(
            ServerLevel level, Zombie zombie, ServerPlayer player, BlockPos pos, Direction face) {
        if (AttackBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pos, face)
                != InteractionResult.PASS) {
            return false;
        }
        level.getBlockState(pos).attack(level, pos, player);
        return true;
    }
    public static SoundType sound(ServerLevel level, Zombie zombie, BlockPos pos, BlockState state) {
        return state.getSoundType();
    }


    public static boolean destroy(ServerLevel level, Zombie zombie, BlockPos pos) {
        return player(level).gameMode.destroyBlock(pos);
    }
}
