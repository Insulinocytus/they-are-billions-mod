package com.insulinocytus.theyarebillions.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ServerHordeSpawnWorld implements HordeSpawnWorld {
    private final ServerLevel level;

    public ServerHordeSpawnWorld(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean trySpawn(HordeSpawnAttempt attempt) {
        int x = Mth.floor(attempt.x());
        int z = Mth.floor(attempt.z());
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            return false;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return HordeSpawns.spawnHordeMember(level, new BlockPos(x, y, z));
    }
}
