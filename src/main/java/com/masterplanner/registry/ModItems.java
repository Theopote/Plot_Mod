package com.masterplanner.registry;

import com.masterplanner.item.MasterPlannerItem;
import com.masterplanner.MasterPlannerMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
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

    public static void registerItemGroups() {
        LOGGER.info("Registering item groups...");
        try {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(MasterPlannerItem.getInstance()));
            LOGGER.info("Item groups registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register item groups", e);
            throw e;
        }
    }
} 