package io.github.insulinocytus.theyarebillions.neoforge;

import io.github.insulinocytus.theyarebillions.HordeZombie;
import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import io.github.insulinocytus.theyarebillions.ZombieSpawnFilter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@Mod(TheyAreBillions.MOD_ID)
public final class TheyAreBillionsNeoForge {
    public TheyAreBillionsNeoForge() {
        NeoForge.EVENT_BUS.addListener(TheyAreBillionsNeoForge::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(TheyAreBillionsNeoForge::onEntityLeaveLevel);
        TheyAreBillions.init();
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (ZombieSpawnFilter.blocks(event.getEntity().getType(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
        }
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof HordeZombie zombie && event.getLevel() instanceof ServerLevel level) {
            zombie.onUnloaded(level);
        }
    }
}
