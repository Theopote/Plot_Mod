package com.masterplanner.registry;

import com.masterplanner.item.MasterPlannerItem;
import com.masterplanner.MasterPlannerMod;
import org.slf4j.Logger;

public class ModItems {
    private static final Logger LOGGER = MasterPlannerMod.LOGGER;

    public static void registerItems() {
        LOGGER.info("Registering items...");
        try {
            MasterPlannerItem.register();
            LOGGER.info("Items registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register items", e);
            throw e;
        }
    }
} 