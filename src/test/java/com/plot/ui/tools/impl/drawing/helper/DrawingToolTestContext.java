package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.tools.impl.drawing.DrawingTool;
import com.plot.ui.tools.impl.drawing.strategy.IShapeFactory;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 绘图策略/模式处理器单元测试用的最小上下文实现。
 */
public final class DrawingToolTestContext implements IInteractionStrategy.DrawingToolContext {

    private final AppState appState;
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
    private final IStyleHandler styleHandler;
    private final IShapeFactory shapeFactory;
    private final CanvasCamera camera = new CanvasCamera();
    private final List<Shape> committedShapes = new ArrayList<>();

    private Shape previewShape;
    private DrawingTool.ToolState toolState;

    DrawingToolTestContext(AppState appState, IShapeFactory shapeFactory) {
        this.appState = appState;
        this.shapeFactory = shapeFactory;
        this.styleHandler = new StyleHandler(appState, "test");
    }

    public static ModeHandlerContext forModeHandler(AppState appState) {
        return new ModeHandlerContext(appState);
    }

    public List<Shape> getCommittedShapes() {
        return committedShapes;
    }

    public Shape getPreviewShape() {
        return previewShape;
    }

    public DrawingTool.ToolState getToolState() {
        return toolState;
    }

    @Override
    public void commitShape(Shape shape) {
        if (shape != null) {
            committedShapes.add(shape);
            appState.addShape(shape);
        }
    }

    @Override
    public void resetDrawing(String reason) {
        previewShape = null;
    }

    @Override
    public void setPreviewShape(Shape shape) {
        previewShape = shape;
    }

    @Override
    public void setToolState(DrawingTool.ToolState state) {
        toolState = state;
    }

    @Override
    public ISnapHandler getSnapHandler() {
        return snapHandler;
    }

    @Override
    public IStyleHandler getStyleHandler() {
        return styleHandler;
    }

    @Override
    public IShapeFactory getShapeFactory() {
        return shapeFactory;
    }

    @Override
    public CanvasCamera getCamera() {
        return camera;
    }

    public static DrawingToolTestContext lineToolContext() {
        AppState appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        return new DrawingToolTestContext(appState, new IShapeFactory() {
            @Override
            public Shape createShape(Vec2d startPoint, Vec2d endPoint) {
                return new com.plot.core.geometry.shapes.LineShape(startPoint, endPoint);
            }

            @Override
            public Shape createShapeFromPoints(List<Vec2d> points) {
                if (points == null || points.size() < 2) {
                    return null;
                }
                return new com.plot.core.geometry.shapes.LineShape(points.get(0), points.get(1));
            }
        });
    }

    public static final class ModeHandlerContext implements IModeHandler.DrawingToolContext {
        private final AppState appState;
        private final IModeHandler.StyleHandler styleHandler;
        private final IModeHandler.SnapHandler snapHandler = (screenPos, camera) -> screenPos;
        private final CanvasCamera camera = new CanvasCamera();
        private boolean closeEnabled = false;

        ModeHandlerContext(AppState appState) {
            this.appState = appState;
            StyleHandler delegate = new StyleHandler(appState, "polyline-test");
            this.styleHandler = delegate::getFinalStyle;
        }

        public void setCloseEnabled(boolean closeEnabled) {
            this.closeEnabled = closeEnabled;
        }

        @Override
        public IModeHandler.StyleHandler getStyleHandler() {
            return styleHandler;
        }

        @Override
        public IModeHandler.SnapHandler getSnapHandler() {
            return snapHandler;
        }

        @Override
        public void updateStatusMessage(String message) {
        }

        @Override
        public void resetDrawing(String reason) {
        }

        @Override
        public void setToolState(Object state) {
        }

        @Override
        public Object getCamera() {
            return camera;
        }

        @Override
        public void commitShape(Shape shape) {
            if (shape != null) {
                appState.addShape(shape);
            }
        }

        @Override
        public boolean isMultipleMode() {
            return false;
        }

        @Override
        public boolean isCloseEnabled() {
            return closeEnabled;
        }
    }
}
