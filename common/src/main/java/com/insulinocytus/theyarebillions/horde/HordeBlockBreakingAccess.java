package com.insulinocytus.theyarebillions.horde;

import com.mojang.authlib.GameProfile;
import dev.architectury.injectables.annotations.ExpectPlatform;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public final class HordeBlockBreakingAccess {
    public static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("d1e68630-a813-56dd-b0fc-89ec1474bca5"), "[TheyAreBillions]");

    private HordeBlockBreakingAccess() {
    }

    @ExpectPlatform
    public static ServerPlayer player(ServerLevel level) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean start(
            ServerLevel level, Zombie zombie, ServerPlayer player, BlockPos pos, Direction face) {
        throw new AssertionError();
    }
    @ExpectPlatform
    public static SoundType sound(ServerLevel level, Zombie zombie, BlockPos pos, BlockState state) {
        throw new AssertionError();
    }


    @ExpectPlatform
    public static boolean destroy(ServerLevel level, Zombie zombie, BlockPos pos) {
        throw new AssertionError();
    }
}
