package com.masterplanner.item;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.Hand;

import com.masterplanner.MasterPlannerMod;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MasterPlanner工具物品
 */
public class MasterPlannerItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/Item");
    public static final String ITEM_PATH = "master_planner";
    private static MasterPlannerItem INSTANCE;

    public MasterPlannerItem(Settings settings) {
        super(settings);
    }

    public static MasterPlannerItem getInstance() {
        if (INSTANCE == null) {
            INSTANCE = register(MasterPlannerItem::new, new Item.Settings().maxCount(1));
        }
        return INSTANCE;
    }

    private static MasterPlannerItem register(Function<Settings, Item> factory, Settings settings) {
        final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MasterPlannerMod.MOD_ID, MasterPlannerItem.ITEM_PATH));
        return (MasterPlannerItem) Items.register(registryKey, factory, settings);
    }

    public static void register() {
        MasterPlannerMod.LOGGER.info("Registering Master Planner Item...");
        try {
            getInstance();
            MasterPlannerMod.LOGGER.info("Master Planner Item registered successfully");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("Failed to register Master Planner Item", e);
            throw e;
        }
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        LOGGER.info("MasterPlanner use triggered - world.isClient: {}", world.isClient());

        if (world.isClient()) {
            LOGGER.info("Attempting to open MasterPlanner screen...");
            try {
                // 使用 UIManager 替代已删除的静态方法
                MasterPlannerMod.getUIManager().openMasterPlannerScreen();
                LOGGER.info("MasterPlanner screen opened successfully");
                return ActionResult.SUCCESS;
            } catch (Exception e) {
                LOGGER.error("Failed to open MasterPlanner screen", e);
                e.printStackTrace();
                return ActionResult.FAIL;
            }
        }
        
        LOGGER.info("Server-side use completed");
        return ActionResult.PASS;
    }
}
