package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ValidPlayers {
    private ValidPlayers() {
    }

    public static List<HordePlayer> list(ServerLevel level) {
        List<HordePlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (isValid(player)) {
                players.add(new HordePlayer(player.getUUID(), player.getX(), player.getY(), player.getZ()));
            }
        }
        return List.copyOf(players);
    }

    public static boolean isValid(ServerPlayer player) {
        if (!player.gameMode.getGameModeForPlayer().isSurvival()) {
            return false;
        }
        for (Class<?> type = player.getClass(); type != null && type != ServerPlayer.class; type = type.getSuperclass()) {
            if (type.getSimpleName().contains("FakePlayer")) {
                return false;
            }
        }
        return true;
    }
}
