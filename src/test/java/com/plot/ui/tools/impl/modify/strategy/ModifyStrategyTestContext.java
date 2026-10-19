package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ICanvas;
import com.plot.api.state.IAppState;
import com.plot.api.tool.ToolState;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.tools.impl.drawing.helper.ISnapHandler;
import com.plot.ui.tools.impl.drawing.helper.IStyleHandler;
import com.plot.ui.tools.impl.modify.ControlPointEditTool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * 修改策略单元测试用的最小 {@link IModifyStrategy.ModifyToolContext} 实现。
 */
final class ModifyStrategyTestContext implements IModifyStrategy.ModifyToolContext {

    private final AppState appState;
    private final List<Shape> selectedShapes = new ArrayList<>();
    private final List<ModifyCommand> executedCommands = new ArrayList<>();
    private final ISnapHandler snapHandler = new ISnapHandler() {
        @Override
        public Vec2d getSnappedWorldPoint(Vec2d screenPoint, CanvasCamera camera) {
            return screenPoint;
        }

        @Override
        public void clearCache() {
        }

        @Override
        public boolean isSnapEnabled() {
            return false;
        }

        @Override
        public void setSnapEnabled(boolean enabled) {
        }
    };
    private final CanvasCamera camera = new CanvasCamera();

    private String lastStatusMessage;
    private boolean previewEnabled;
    private BiFunction<Vec2d, Double, Shape> shapeFinder = (pos, tolerance) -> null;
    private BiPredicate<Shape, Vec2d[]> areaPredicate = (shape, bounds) -> false;

    ModifyStrategyTestContext(AppState appState) {
        this.appState = appState;
    }

    void setShapeFinder(BiFunction<Vec2d, Double, Shape> shapeFinder) {
        this.shapeFinder = shapeFinder;
    }

    void setAreaPredicate(BiPredicate<Shape, Vec2d[]> areaPredicate) {
        this.areaPredicate = areaPredicate;
    }

    List<ModifyCommand> getExecutedCommands() {
        return executedCommands;
    }

    String getLastStatusMessage() {
        return lastStatusMessage;
    }

    @Override
    public void executeModifyCommand(ModifyCommand command) {
        executedCommands.add(command);
    }

    @Override
    public void resetModification(String reason) {
    }

    @Override
    public void setPreviewEnabled(boolean enabled) {
        previewEnabled = enabled;
    }

    boolean isPreviewEnabled() {
        return previewEnabled;
    }

    @Override
    public void setToolState(ToolState state) {
    }

    @Override
    public void setStatusMessage(String message) {
        lastStatusMessage = message;
    }

    @Override
    public List<Shape> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    @Override
    public void setSelectedShapes(List<Shape> shapes) {
        selectedShapes.clear();
        if (shapes != null) {
            selectedShapes.addAll(shapes);
        }
    }

    @Override
    public void addSelectedShape(Shape shape) {
        if (shape != null && !selectedShapes.contains(shape)) {
            selectedShapes.add(shape);
        }
    }

    @Override
    public void removeSelectedShape(Shape shape) {
        selectedShapes.remove(shape);
    }

    @Override
    public void clearSelection() {
        selectedShapes.clear();
    }

    @Override
    public boolean isShiftKeyDown() {
        return false;
    }

    @Override
    public boolean isAltKeyDown() {
        return false;
    }

    @Override
    public boolean isCtrlKeyDown() {
        return false;
    }

    @Override
    public Shape findShapeAt(Vec2d pos, double tolerance) {
        return shapeFinder.apply(pos, tolerance);
    }

    @Override
    public List<Shape> findShapesInArea(Vec2d startPos, Vec2d endPos) {
        Vec2d[] bounds = new Vec2d[] {startPos, endPos};
        List<Shape> result = new ArrayList<>();
        for (Shape shape : appState.getShapes()) {
            if (areaPredicate.test(shape, bounds)) {
                result.add(shape);
            }
        }
        return result;
    }

    @Override
    public ISnapHandler getSnapHandler() {
        return snapHandler;
    }

    @Override
    public IStyleHandler getStyleHandler() {
        return new IStyleHandler() {
            @Override
            public void applyPreviewStyle(Shape shape) {
            }

            @Override
            public void applyFinalStyle(Shape shape) {
            }

            @Override
            public com.plot.core.graphics.style.ShapeStyle getPreviewStyle() {
                return null;
            }

            @Override
            public com.plot.core.graphics.style.ShapeStyle getFinalStyle() {
                return null;
            }

            @Override
            public void invalidateCache() {
            }
        };
    }

    @Override
    public CanvasCamera getCamera() {
        return camera;
    }

    @Override
    public ICanvas getCanvas() {
        return null;
    }

    @Override
    public IAppState getAppState() {
        return appState;
    }

    @Override
    public ControlPointEditTool getControlPointEditTool() {
        return null;
    }

    @Override
    public List<Shape> getAllShapesInActiveLayer() {
        return appState.getShapes();
    }

    @Override
    public List<Shape> getAllShapesInLayer(String layerId) {
        return appState.getShapes();
    }
}
