package com.plot.item;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.Hand;

import com.plot.PlotMod;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 模组主工具物品
 */
public class PlotItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/Item");
    public static final String ITEM_PATH = "plot";
    private static PlotItem INSTANCE;

    public PlotItem(Settings settings) {
        super(settings);
    }

    public static PlotItem getInstance() {
        if (INSTANCE == null) {
            INSTANCE = register(PlotItem::new, new Item.Settings().maxCount(1));
        }
        return INSTANCE;
    }

    private static PlotItem register(Function<Settings, Item> factory, Settings settings) {
        final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(PlotMod.MOD_ID, PlotItem.ITEM_PATH));
        return (PlotItem) Items.register(registryKey, factory, settings);
    }

    public static void register() {
        PlotMod.LOGGER.info("Registering Plot item...");
        try {
            getInstance();
            PlotMod.LOGGER.info("Plot item registered successfully");
        } catch (Exception e) {
            PlotMod.LOGGER.error("Failed to register Plot item", e);
            throw e;
        }
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        LOGGER.info("Plot item use triggered - world.isClient: {}", world.isClient());

        if (world.isClient()) {
            LOGGER.info("Attempting to open Plot screen...");
            try {
                PlotMod.getUIManager().openPlotScreen();
                LOGGER.info("Plot screen opened successfully");
                return ActionResult.SUCCESS;
            } catch (Exception e) {
                LOGGER.error("Failed to open Plot screen", e);
                return ActionResult.FAIL;
            }
        }
        
        LOGGER.info("Server-side use completed");
        return ActionResult.PASS;
    }
}
