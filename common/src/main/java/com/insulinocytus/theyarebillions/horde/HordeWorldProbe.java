package com.insulinocytus.theyarebillions.horde;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

public final class HordeWorldProbe {
    private HordeWorldProbe() {
    }

    public static HordeTickInput capture(ServerLevel level, HordeNightState previous) {
        return new HordeTickInput(
                level.getDayTime(),
                level.dimension() == Level.OVERWORLD,
                level.getDifficulty() == Difficulty.PEACEFUL,
                ValidPlayers.list(level),
                OrdinaryZombies.countLoaded(level),
                HordeGameRules.target(level),
                level.getSeed(),
                previous);
    }
}
