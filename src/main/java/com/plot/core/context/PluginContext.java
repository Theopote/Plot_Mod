package com.plot.core.context;

import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.CommandService;
import com.plot.core.state.AppState;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.event.EventBus;

import java.util.Objects;

/**
 * 宿主注入给插件的服务句柄。插件应通过本对象访问 Core/infra，而不是 {@code *.getInstance()}。
 * <p>
 * 逻辑侧服务始终可用；世界服务以 api 接口注入，由客户端 {@code ClientServices} 组装，
 * 不携带 MinecraftClient 具体类型，dedicated server 加载链路保持干净。
 *
 * @see ApplicationContext#createPluginContext()
 * @see PluginContextFactory
 */
public final class PluginContext {
    private final AppState appState;
    private final CommandService commandService;
    private final EventBus eventBus;
    private final ToolManager toolManager;
    private final ICoordinateService coordinateService;
    private final IGhostBlockService ghostBlockService;
    private final IBlockPlacementService placementService;
    private final IBlockProjectionService projectionService;

    public PluginContext(
            AppState appState,
            CommandService commandService,
            EventBus eventBus,
            ToolManager toolManager,
            ICoordinateService coordinateService,
            IGhostBlockService ghostBlockService,
            IBlockPlacementService placementService,
            IBlockProjectionService projectionService) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.toolManager = Objects.requireNonNull(toolManager, "toolManager");
        this.coordinateService = coordinateService;
        this.ghostBlockService = ghostBlockService;
        this.placementService = placementService;
        this.projectionService = projectionService;
    }

    /**
     * 仅逻辑侧服务（无 client 世界服务）。完整上下文由客户端组合根组装。
     */
    public static PluginContext from(ApplicationContext applicationContext) {
        Objects.requireNonNull(applicationContext, "applicationContext");
        return new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            null,
            null,
            null,
            null
        );
    }

    public AppState appState() {
        return appState;
    }

    public CommandService commands() {
        return commandService;
    }

    public EventBus events() {
        return eventBus;
    }

    public ToolManager tools() {
        return toolManager;
    }

    public ICoordinateService coordinates() {
        return requireWorld(coordinateService, "ICoordinateService");
    }

    /** 幽灵方块预览。 */
    public IGhostBlockService ghosts() {
        return requireWorld(ghostBlockService, "IGhostBlockService");
    }

    /** 分帧世界落地调度。 */
    public IBlockPlacementService placement() {
        return requireWorld(placementService, "IBlockPlacementService");
    }

    /** 世界方块读写与就绪检查。 */
    public IBlockProjectionService projection() {
        return requireWorld(projectionService, "IBlockProjectionService");
    }

    public boolean hasWorldServices() {
        return coordinateService != null
            && ghostBlockService != null
            && placementService != null
            && projectionService != null;
    }

    private static <T> T requireWorld(T value, String name) {
        if (value == null) {
            throw new IllegalStateException(
                name + " 仅在客户端可用；请确认 ClientServices 已初始化并接管 PluginContextFactory");
        }
        return value;
    }
}
