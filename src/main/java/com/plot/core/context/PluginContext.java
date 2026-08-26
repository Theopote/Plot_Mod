package com.plot.core.context;

import com.plot.core.command.CommandService;
import com.plot.core.state.AppState;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;

import java.util.Objects;

/**
 * 宿主注入给插件的服务句柄。插件应通过本对象访问 Core/infra，而不是 {@code *.getInstance()}。
 *
 * @see ApplicationContext#createPluginContext()
 */
public final class PluginContext {
    private final AppState appState;
    private final CommandService commandService;
    private final EventBus eventBus;
    private final ToolManager toolManager;
    private final CoordinateTransformer coordinateTransformer;
    private final GhostBlockManager ghostBlockManager;
    private final BlockPlacementScheduler placementScheduler;
    private final BlockProjectionHandler projectionHandler;

    public PluginContext(
            AppState appState,
            CommandService commandService,
            EventBus eventBus,
            ToolManager toolManager,
            CoordinateTransformer coordinateTransformer,
            GhostBlockManager ghostBlockManager,
            BlockPlacementScheduler placementScheduler,
            BlockProjectionHandler projectionHandler) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.toolManager = Objects.requireNonNull(toolManager, "toolManager");
        this.coordinateTransformer = Objects.requireNonNull(
            coordinateTransformer, "coordinateTransformer");
        this.ghostBlockManager = Objects.requireNonNull(ghostBlockManager, "ghostBlockManager");
        this.placementScheduler = Objects.requireNonNull(placementScheduler, "placementScheduler");
        this.projectionHandler = Objects.requireNonNull(projectionHandler, "projectionHandler");
    }

    public static PluginContext from(ApplicationContext applicationContext) {
        Objects.requireNonNull(applicationContext, "applicationContext");
        return new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            applicationContext.getCoordinateTransformer(),
            applicationContext.getGhostBlockManager(),
            applicationContext.getBlockPlacementScheduler(),
            applicationContext.getBlockProjectionHandler()
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

    public CoordinateTransformer coordinates() {
        return coordinateTransformer;
    }

    /** 幽灵方块预览。 */
    public GhostBlockManager ghosts() {
        return ghostBlockManager;
    }

    /** 分帧世界落地调度。 */
    public BlockPlacementScheduler placement() {
        return placementScheduler;
    }

    /** 世界方块读写与就绪检查。 */
    public BlockProjectionHandler projection() {
        return projectionHandler;
    }
}
