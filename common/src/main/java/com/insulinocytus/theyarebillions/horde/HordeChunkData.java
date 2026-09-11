package com.insulinocytus.theyarebillions.horde;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class HordeChunkData extends SavedData {
    private static final String NAME = "theyarebillions_horde_chunks";
    private static final Factory<HordeChunkData> FACTORY =
            new Factory<>(HordeChunkData::new, HordeChunkData::load, null);

    private Map<HordePlanner.ChunkRef, Integer> occupancy;

    private HordeChunkData() {
        this(Map.of());
    }

    HordeChunkData(Map<HordePlanner.ChunkRef, Integer> occupancy) {
        this.occupancy = Map.copyOf(occupancy);
    }

    static HordeChunkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    static HordeChunkData load(CompoundTag tag, HolderLookup.Provider provider) {
        Map<HordePlanner.ChunkRef, Integer> occupancy = new LinkedHashMap<>();
        ListTag chunks = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunk = chunks.getCompound(i);
            int count = chunk.getInt("count");
            if (count > 0) {
                occupancy.put(new HordePlanner.ChunkRef(chunk.getInt("x"), chunk.getInt("z")), count);
            }
        }
        return new HordeChunkData(occupancy);
    }

    Map<HordePlanner.ChunkRef, Integer> occupancy() {
        return occupancy;
    }

    void setOccupancy(Map<HordePlanner.ChunkRef, Integer> occupancy) {
        Map<HordePlanner.ChunkRef, Integer> copy = Map.copyOf(occupancy);
        if (!copy.equals(this.occupancy)) {
            this.occupancy = copy;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag chunks = new ListTag();
        occupancy.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag chunk = new CompoundTag();
            chunk.putInt("x", entry.getKey().x());
            chunk.putInt("z", entry.getKey().z());
            chunk.putInt("count", entry.getValue());
            chunks.add(chunk);
        });
        tag.put("chunks", chunks);
        return tag;
    }
}
