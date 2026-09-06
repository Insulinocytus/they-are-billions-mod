package com.insulinocytus.theyarebillions.horde;

import java.util.List;

public record HordeTickInput(
        long dayTime,
        boolean overworld,
        boolean peaceful,
        List<HordePlayer> validPlayers,
        int ordinaryZombieCount,
        int hordeTarget,
        long worldSeed,
        HordeNightState nightState) {
}
