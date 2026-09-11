package com.insulinocytus.theyarebillions.horde.neoforge;

import com.insulinocytus.theyarebillions.horde.HordeBlockBreakingAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;

public final class HordeBlockBreakingAccessImpl {
    private HordeBlockBreakingAccessImpl() {
    }

    public static ServerPlayer player(ServerLevel level) {
        return FakePlayerFactory.get(level, HordeBlockBreakingAccess.PROFILE);
    }

    public static boolean start(
            ServerLevel level, Zombie zombie, ServerPlayer player, BlockPos pos, Direction face) {
        if (!CommonHooks.canEntityDestroy(level, pos, zombie)) {
            return false;
        }
        var event = CommonHooks.onLeftClickBlock(
                player, pos, face, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        if (event.isCanceled()) {
            return false;
        }
        if (event.getUseBlock() != TriState.FALSE) {
            level.getBlockState(pos).attack(level, pos, player);
        }
        return true;
    }
    public static SoundType sound(ServerLevel level, Zombie zombie, BlockPos pos, BlockState state) {
        return state.getSoundType(level, pos, player(level));
    }


    public static boolean destroy(ServerLevel level, Zombie zombie, BlockPos pos) {
        return CommonHooks.canEntityDestroy(level, pos, zombie) && player(level).gameMode.destroyBlock(pos);
    }
}
