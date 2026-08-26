package com.plot.core.state;

import com.plot.api.graphics.IShapeStyle;
import com.plot.api.model.ILayer;
import com.plot.api.model.IShape;
import com.plot.api.state.IAppState;
import com.plot.core.command.CommandService;
import com.plot.core.command.commands.DeleteShapesCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.layer.LayerManager;
import com.plot.core.layer.LayerService;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.selection.Selection;
import com.plot.core.spatial.SpatialIndex;
import com.plot.core.spatial.SpatialIndexService;
import com.plot.core.tool.BaseTool;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.command.CommandExecutedEvent;
import com.plot.infrastructure.event.shapes.ShapesRemovedEvent;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * 应用状态兼容门面：委托到 {@link ApplicationContext} 及其聚焦服务。
 * <p>
 * 新代码优先通过 {@link ApplicationContext} 访问各服务；本类保留为兼容门面。
 * {@link #getInstance()} 已标记废弃。Canvas 不在此持有，见
 * {@code com.plot.ui.canvas.CanvasAccess}。
 */
public class AppState implements IAppState {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/AppState");

    private final ApplicationContext context;

    /**
     * 由 {@link ApplicationContext} 构造；外部请使用 {@link ApplicationContext#getAppState()}。
     */
    public AppState(ApplicationContext context) {
        this.context = context;
    }

    /**
     * @deprecated 使用 {@link ApplicationContext#getAppState()}。
     */
    @Deprecated
    public static AppState getInstance() {
        return ApplicationContext.getInstance().getAppState();
    }

    private LayerService layers() {
        return context.getLayerService();
    }

    private SelectionState selection() {
        return context.getSelectionState();
    }

    private SpatialIndexService spatial() {
        return context.getSpatialIndexService();
    }

    public void initializeLayerSystem() {
        layers().initialize();
    }

    public void ensureDefaultLayer() {
        layers().ensureDefaultLayer();
    }

    public LayerManager getLayerManager() {
        return layers().getLayerManager();
    }

    @Override
    public void addShape(IShape shape) {
        if (!(shape instanceof Shape concrete)) {
            throw new IllegalArgumentException("IShape must be core Shape implementation");
        }
        layers().addShape(concrete);
        context.bumpStateVersion();
    }

    public void addShape(Shape shape) {
        addShape((IShape) shape);
    }

    public void removeShape(Shape shape) {
        layers().removeShape(shape);
        context.bumpStateVersion();
    }

    public List<ILayer> getLayers() {
        return layers().getLayers();
    }

    public void setCurrentTool(BaseTool tool) {
        context.getActiveToolState().setCurrentTool(tool);
    }

    public BaseTool getCurrentTool() {
        return context.getActiveToolState().getCurrentTool();
    }

    public float getZoom() {
        return 100.0f;
    }

    public void setOpacity(float opacity) {
        context.getViewportState().setOpacity(opacity);
    }

    public float getOpacity() {
        return context.getViewportState().getOpacity();
    }

    public CommandService getCommandService() {
        return context.getCommandService();
    }

    public Project getCurrentProject() {
        return context.getCurrentProject();
    }

    public void setCurrentProject(Project project) {
        context.setCurrentProject(project);
    }

    public void setActiveLayer(ILayer layer) {
        layers().setActiveLayer(layer);
    }

    public void syncActiveLayerFromManager(ILayer layer) {
        layers().syncActiveLayerFromManager(layer);
    }

    @Override
    public ILayer getActiveLayer() {
        return layers().getActiveLayer();
    }

    public String getActiveLayerName() {
        return layers().getActiveLayerName();
    }

    public Selection getSelection() {
        return selection().getSelection();
    }

    public List<Shape> getSelectedShapes() {
        return selection().getSelectedShapes();
    }

    public void setSelectedShapes(List<Shape> shapes) {
        selection().setSelectedShapes(shapes);
        context.bumpStateVersion();
    }

    public void addSelectedShape(Shape shape) {
        selection().addSelectedShape(shape);
        context.bumpStateVersion();
    }

    public void removeSelectedShape(Shape shape) {
        selection().removeSelectedShape(shape);
        context.bumpStateVersion();
    }

    public void clearSelection() {
        selection().clearSelection();
        context.bumpStateVersion();
    }

    public List<Shape> getShapes() {
        return layers().getShapes();
    }

    public void clear() {
        context.clear();
    }

    public void undo() {
        context.getCommandService().undo();
    }

    public void redo() {
        context.getCommandService().redo();
    }

    public void deleteSelectedShapes() {
        List<Shape> shapesToDelete = new ArrayList<>(selection().getSelectedShapes());
        if (shapesToDelete.isEmpty()) {
            LOGGER.debug("没有选中的图形需要删除");
            return;
        }

        DeleteShapesCommand deleteCommand = new DeleteShapesCommand(shapesToDelete);
        context.getCommandService().execute(deleteCommand);
        clearSelection();
        context.getEventBus().publish(new ShapesRemovedEvent(shapesToDelete));
        context.getEventBus().publish(
            new CommandExecutedEvent("删除图形", CommandExecutedEvent.CommandType.EXECUTE));
    }

    public void dispose() {
        context.dispose();
    }

    @Override
    public IShapeStyle getCurrentShapeStyle() {
        return context.getCurrentShapeStyle();
    }

    public ScheduledFuture<?> scheduleDelayedTask(Runnable task, int delayMs) {
        if (task == null || delayMs < 0) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.task_null_negative_delay"));
        }
        return DebouncedTasks.scheduleDelayed(task, delayMs);
    }

    @Override
    public ScheduledFuture<?> scheduleDelayedTask(Runnable task, long delayMs) {
        return scheduleDelayedTask(task, (int) delayMs);
    }

    @Override
    public boolean isValid() {
        return context.isValid();
    }

    @Override
    public long getStateVersion() {
        return context.getStateVersion();
    }

    public SpatialIndex getSpatialIndex() {
        return spatial().getSpatialIndex();
    }

    public void rebuildShapeToLayerMap() {
        layers().rebuildShapeToLayerMap();
    }

    public void rebuildSpatialIndex() {
        spatial().rebuild();
    }

    public void updateSpatialIndex(Shape shape) {
        spatial().update(shape);
    }

    public void removeFromSpatialIndex(Shape shape) {
        spatial().remove(shape);
    }
}
