package io.github.insulinocytus.theyarebillions.neoforge;

import io.github.insulinocytus.theyarebillions.TheyAreBillions;
import io.github.insulinocytus.theyarebillions.ZombieSpawnFilter;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.fml.common.Mod;

@Mod(TheyAreBillions.MOD_ID)
public final class TheyAreBillionsNeoForge {
    public TheyAreBillionsNeoForge() {
        NeoForge.EVENT_BUS.addListener(TheyAreBillionsNeoForge::onFinalizeSpawn);
        TheyAreBillions.init();
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (ZombieSpawnFilter.blocks(event.getEntity().getType(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
        }
    }
}
