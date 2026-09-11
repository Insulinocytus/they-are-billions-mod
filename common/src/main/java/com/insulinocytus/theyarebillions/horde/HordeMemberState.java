package com.insulinocytus.theyarebillions.horde;

public interface HordeMemberState {
    boolean theyarebillions$isSyncedHordeMember();

    void theyarebillions$setSyncedHordeMember(boolean hordeMember);

    void theyarebillions$disableVanillaDoorBreaking();

    void theyarebillions$restoreVanillaDoorBreaking();
}
