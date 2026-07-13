package com.plot.plugin.road.manager;

import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.EllipticalArcShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadPathPickSession;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路绘制/拾取工具交互。
 */
public final class RoadToolManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadTool");

    private final RoadPathPickSession pathPickSession = new RoadPathPickSession();
    private final List<Shape> selectedPaths = new ArrayList<>();
    private final RoadProjectStatus status;

    public RoadToolManager(RoadProjectStatus status) {
        this.status = status;
    }

    public RoadPathPickSession getPathPickSession() {
        return pathPickSession;
    }

    public List<Shape> getSelectedPaths() {
        return selectedPaths;
    }

    public void cancel() {
        pathPickSession.cancel();
    }

    public void tick() {
        if (!pathPickSession.isActive()) {
            return;
        }
        RoadPathPickSession.Outcome outcome = pathPickSession.tick(AppState.getInstance());
        applyPathPickOutcome(outcome);

        if (pathPickSession.isActive()) {
            List<Shape> selected = AppState.getInstance().getSelectedShapes();
            String hintKey = pathPickSession.hintKeyForCurrentSelection(selected);
            if ("status.plot.road.pick_path_right_click_multi".equals(hintKey)) {
                status.set(PlotI18n.status(hintKey, pathPickSession.getAccumulatedCount()));
            } else {
                status.set(PlotI18n.status(hintKey));
            }
        }
    }

    public void updateSelectedPaths() {
        try {
            selectedPaths.clear();
            selectedPaths.addAll(
                RoadGeometryUtils.findAdoptablePaths(AppState.getInstance().getSelectedShapes())
            );
        } catch (Exception e) {
            LOGGER.error("更新选中路径失败: {}", e.getMessage(), e);
        }
    }

    public List<Shape> findAvailablePaths() {
        List<Shape> paths = new ArrayList<>();
        for (Shape shape : AppState.getInstance().getShapes()) {
            if (RoadGeometryUtils.isAdoptablePath(shape)) {
                paths.add(shape);
            }
        }
        return paths;
    }

    public static String getPathTypeName(Shape shape) {
        if (shape instanceof PolylineShape) {
            return PlotI18n.tr("path.plot.polyline");
        }
        if (shape instanceof FreeDrawPath) {
            return PlotI18n.tr("path.plot.freedraw");
        }
        if (shape instanceof BezierCurveShape) {
            return PlotI18n.tr("path.plot.bezier");
        }
        if (shape instanceof LineShape) {
            return PlotI18n.tr("path.plot.line");
        }
        if (shape instanceof CircleShape) {
            return PlotI18n.tr("path.plot.circle");
        }
        if (shape instanceof EllipseShape) {
            return PlotI18n.tr("path.plot.ellipse");
        }
        if (shape instanceof EllipticalArcShape) {
            return PlotI18n.tr("path.plot.elliptical_arc");
        }
        if (shape instanceof ArcShape) {
            return PlotI18n.tr("path.plot.arc");
        }
        if (shape instanceof RectangleShape) {
            return PlotI18n.tr("path.plot.rectangle");
        }
        if (shape instanceof Polygon) {
            return PlotI18n.tr("path.plot.polygon");
        }
        if (shape instanceof SpiralShape) {
            return PlotI18n.tr("path.plot.spiral");
        }
        if (shape instanceof SineCurveShape) {
            return PlotI18n.tr("path.plot.sine");
        }
        if (shape instanceof CableShape) {
            return PlotI18n.tr("path.plot.cable");
        }
        return PlotI18n.tr("path.plot.unknown");
    }

    public static double calculatePathLength(Shape path) {
        return RoadGeometryUtils.calculatePathLength(RoadGeometryUtils.extractShapePoints(path));
    }

    public void activatePathDrawingTool() {
        ToolManager toolManager = ToolManager.getInstance();
        if (toolManager != null) {
            var polylineTool = toolManager.getTool("polyline");
            if (polylineTool instanceof BaseTool baseTool) {
                AppState.getInstance().setCurrentTool(baseTool);
            }
        }
    }

    public void activatePathPickTool() {
        ToolManager toolManager = ToolManager.getInstance();
        if (toolManager == null) {
            return;
        }
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }

        selectedPaths.clear();
        pathPickSession.begin();
        toolManager.setActiveTool(selectTool);
        AppState.getInstance().setCurrentTool(baseTool);
        status.set(PlotI18n.tr("plugin.road.pick_path_hint"));
    }

    private void applyPathPickOutcome(RoadPathPickSession.Outcome outcome) {
        switch (outcome.getResult()) {
            case SUCCESS -> {
                selectedPaths.clear();
                selectedPaths.addAll(outcome.getPaths());
                if (selectedPaths.size() == 1) {
                    status.set(String.format(PlotI18n.tr("plugin.road.path_selected"),
                        calculatePathLength(selectedPaths.getFirst())));
                } else {
                    double totalLength = selectedPaths.stream()
                        .mapToDouble(RoadToolManager::calculatePathLength)
                        .sum();
                    status.set(String.format(
                        PlotI18n.tr("plugin.road.paths_selected"),
                        selectedPaths.size(),
                        totalLength));
                }
            }
            case NEED_SELECTION -> status.set(PlotI18n.status("status.plot.road.pick_path_need_selection"));
            case NO_VALID -> status.set(PlotI18n.status("status.plot.road.pick_path_no_valid"));
            case CANCELLED -> status.set(PlotI18n.status("status.plot.road.pick_path_cancelled"));
            default -> { }
        }
    }
}
