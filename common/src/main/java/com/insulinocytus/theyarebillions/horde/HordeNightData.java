package com.insulinocytus.theyarebillions.horde;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class HordeNightData extends SavedData {
    private static final String NAME = "theyarebillions_horde_night";
    private static final Factory<HordeNightData> FACTORY =
            new Factory<>(HordeNightData::new, HordeNightData::load, null);

    private HordePlanner.NightState state;

    private HordeNightData() {
        this(HordePlanner.NightState.none());
    }

    HordeNightData(HordePlanner.NightState state) {
        this.state = state;
    }

    static HordeNightData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    static HordeNightData load(CompoundTag tag, HolderLookup.Provider provider) {
        Map<HordePlanner.GroupIdentity, Double> directions = new LinkedHashMap<>();
        CompoundTag savedDirections = tag.getCompound("directions");
        for (String members : savedDirections.getAllKeys()) {
            if (!members.isEmpty()) {
                directions.put(HordePlanner.GroupIdentity.of(members.split(",")), savedDirections.getDouble(members));
            }
        }
        return new HordeNightData(new HordePlanner.NightState(
                tag.getLong("observedDayTime"),
                directions,
                tag.getInt("rotation"),
                tag.getLong("initializedNight")));
    }

    HordePlanner.NightState state() {
        return state;
    }

    void setState(HordePlanner.NightState state) {
        if (!state.equals(this.state)) {
            this.state = state;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("observedDayTime", state.observedDayTime());
        tag.putInt("rotation", state.rotation());
        tag.putLong("initializedNight", state.initializedNight());
        CompoundTag directions = new CompoundTag();
        state.directions().forEach(
                (group, direction) -> directions.putDouble(String.join(",", group.memberIds()), direction));
        tag.put("directions", directions);
        return tag;
    }
}
