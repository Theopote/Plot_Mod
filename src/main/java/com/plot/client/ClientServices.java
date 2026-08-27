package com.plot.client;

import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.core.context.PluginContextFactory;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端组合根：持有依赖 MinecraftClient / Canvas 的世界服务，
 * 避免通用 {@link ApplicationContext} 反向依赖 client 类型。
 */
public final class ClientServices {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ClientServices");
    private static volatile ClientServices INSTANCE;

    private final CoordinateTransformer coordinateTransformer;
    private final GhostBlockManager ghostBlockManager;
    private final BlockPlacementScheduler placementScheduler;
    private final BlockProjectionHandler projectionHandler;

    private ClientServices() {
        this.coordinateTransformer = CoordinateTransformer.getInstance();
        this.ghostBlockManager = GhostBlockManager.getInstance();
        this.placementScheduler = BlockPlacementScheduler.getInstance();
        this.projectionHandler = BlockProjectionHandler.getInstance();
    }

    public static synchronized ClientServices initialize() {
        if (INSTANCE == null) {
            INSTANCE = new ClientServices();
            PluginContextFactory.setSupplier(() -> INSTANCE.createPluginContext());
            LOGGER.info("ClientServices 已初始化并接管 PluginContextFactory");
        }
        return INSTANCE;
    }

    public static ClientServices getInstance() {
        ClientServices local = INSTANCE;
        if (local == null) {
            throw new IllegalStateException("ClientServices 尚未初始化；仅应在 PlotClient 中访问");
        }
        return local;
    }

    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    public CoordinateTransformer getCoordinateTransformer() {
        return coordinateTransformer;
    }

    public GhostBlockManager getGhostBlockManager() {
        return ghostBlockManager;
    }

    public BlockPlacementScheduler getBlockPlacementScheduler() {
        return placementScheduler;
    }

    public BlockProjectionHandler getBlockProjectionHandler() {
        return projectionHandler;
    }

    public PluginContext createPluginContext() {
        ApplicationContext app = ApplicationContext.getInstance();
        return new PluginContext(
            app.getAppState(),
            app.getCommandService(),
            app.getEventBus(),
            app.getToolManager(),
            coordinateTransformer,
            ghostBlockManager,
            placementScheduler,
            projectionHandler
        );
    }
}
