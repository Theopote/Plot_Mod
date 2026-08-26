package com.plot;

import com.plot.core.command.CommandService;
import com.plot.core.context.ApplicationContext;
import com.plot.core.tool.ToolManager;
import com.plot.registry.ModItems;
import com.plot.utils.PlotI18n;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 模组通用入口（服务端与客户端共享）。
 * <p>
 * 仅注册可在逻辑服务端运行的核心系统；UI、渲染、键位等客户端逻辑见 {@link com.plot.client.PlotClient}。
 */
public final class PlotMod implements ModInitializer {
    public static final String MOD_ID = "plot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("初始化 Master Planner Mod (通用逻辑)...");

        try {
            ApplicationContext context = ApplicationContext.getInstance();
            context.initialize();

            ToolManager.initialize(context.getAppState());
            ApplicationContext.getInstance().getCommandService();

            ModItems.registerItems();
            ModItems.registerItemGroups();

            LOGGER.info("Master Planner Mod (通用逻辑) 初始化成功!");
        } catch (Exception e) {
            LOGGER.error("Master Planner Mod (通用逻辑) 初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.mod_failed"), e);
        }
    }
}
