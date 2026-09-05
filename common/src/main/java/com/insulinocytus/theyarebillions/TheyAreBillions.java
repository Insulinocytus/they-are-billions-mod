package com.insulinocytus.theyarebillions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TheyAreBillions {
    public static final String MOD_ID = "theyarebillions";
    public static final String MOD_NAME = "They Are Billions";
    public static final String VERSION = BuildConstants.VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private TheyAreBillions() {
    }

    public static void initialize() {
        LOGGER.info("{} {} loaded", MOD_NAME, VERSION);
    }
}
