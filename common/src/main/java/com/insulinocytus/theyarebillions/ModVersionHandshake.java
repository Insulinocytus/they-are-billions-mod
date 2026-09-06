package com.insulinocytus.theyarebillions;

import java.util.Objects;

public final class ModVersionHandshake {
    private ModVersionHandshake() {
    }

    public static boolean accepts(String serverVersion, String clientVersion) {
        return Objects.equals(serverVersion, clientVersion);
    }
}
