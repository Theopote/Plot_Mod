package com.plot;

import com.plot.core.context.ApplicationContext;
import com.plot.registry.ModItems;
import com.plot.utils.PlotI18n;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

    /** CI / 本地 dedicated-server smoke：{@code -Dplot.server.smoke=true} */
    public static final String SERVER_SMOKE_PROPERTY = "plot.server.smoke";
    public static final String SERVER_SMOKE_PASS_MARKER = "PLOT_SERVER_SMOKE_PASS";

    @Override
    public void onInitialize() {
        LOGGER.info("初始化 Master Planner Mod (通用逻辑)...");

        try {
            ApplicationContext context = ApplicationContext.getInstance();
            context.initialize();

            // ToolManager 已由 ApplicationContext 持有；此处仅确保可访问
            context.getToolManager();
            context.getCommandService();

            ModItems.registerItems();
            ModItems.registerItemGroups();

            registerDedicatedServerSmokeHook();

            LOGGER.info("Master Planner Mod (通用逻辑) 初始化成功!");
        } catch (Exception e) {
            LOGGER.error("Master Planner Mod (通用逻辑) 初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.mod_failed"), e);
        }
    }

    /**
     * Dedicated-server smoke：验证物理服务端 classloader 能完成 Plot 初始化，
     * 然后干净停服退出（避免 runServer 因非 daemon 线程挂起）。
     */
    private static void registerDedicatedServerSmokeHook() {
        if (!Boolean.getBoolean(SERVER_SMOKE_PROPERTY)) {
            return;
        }

        LOGGER.info("Dedicated-server smoke mode enabled (-D{}=true)", SERVER_SMOKE_PROPERTY);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("{}: Plot loaded on dedicated server; requesting stop", SERVER_SMOKE_PASS_MARKER);
            server.execute(() -> server.stop(false));
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOGGER.info("PLOT_SERVER_SMOKE_DONE: halting JVM");
            // System.exit 会卡在 Minecraft / Loom 非 daemon 线程的 shutdown hook 上；
            // smoke 只需验证初始化成功，用 halt 强制结束进程。
            Runtime.getRuntime().halt(0);
        });
    }
}
