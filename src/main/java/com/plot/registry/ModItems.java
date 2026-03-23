package com.plot.registry;

import com.plot.item.PlotItem;
import com.plot.PlotMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;

public class ModItems {
    private static final Logger LOGGER = PlotMod.LOGGER;

    public static void registerItems() {
        LOGGER.info("Registering items...");
        try {
            PlotItem.register();
            LOGGER.info("Items registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register items", e);
            throw e;
        }
    }

    public static void registerItemGroups() {
        LOGGER.info("Registering item groups...");
        try {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(PlotItem.getInstance()));
            LOGGER.info("Item groups registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register item groups", e);
            throw e;
        }
    }
} 