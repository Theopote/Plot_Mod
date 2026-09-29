package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.repair.RoadRepairDiagnosisCache;
import com.plot.ui.canvas.Canvas;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 道路插件画布选择：点击/框选道路走廊与认领候选路径，空白处取消选中。
 * <p>
 * 在路径拾取会话中，优先通过叠加层走廊命中候选路径（比细线几何更易点选），
 * 再交由 {@link com.plot.plugin.road.RoadPathPickSession} 合并累加选择。
 */
public final class RoadCanvasSelectionController {

    private static final double DRAG_THRESHOLD_PX = 4.0;

    private boolean pointerDown;
    private Vec2d pointerDownWorld;
    private Vec2d pointerDownScreen;

    public void resetPointer() {
        pointerDown = false;
        pointerDownWorld = null;
        pointerDownScreen = null;
    }

    public void tick(
            Canvas canvas,
            AppState appState,
            RoadNetworkManager networkManager,
            List<RoadOverlayEntry> overlayEntries,
            List<RoadJunctionOverlayEntry> junctionOverlayEntries,
            boolean pickSessionActive,
            BooleanSupplier overlayVisible) {
        if (canvas == null || appState == null || networkManager == null || overlayVisible == null) {
            return;
        }
        if (!overlayVisible.getAsBoolean()) {
            resetPointer();
            return;
        }
        Vec2d mouseScreen = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (!canvas.isScreenPosInsideCanvas(mouseScreen)) {
            resetPointer();
            return;
        }
        if (ImGui.getIO().getWantCaptureMouse()) {
            resetPointer();
            return;
        }
        BaseTool tool = appState.getCurrentTool();
        if (tool == null || !"select".equals(tool.getId())) {
            resetPointer();
            return;
        }

        Vec2d world = canvas.screenToWorld(mouseScreen);
        if (ImGui.isMouseClicked(0)) {
            pointerDown = true;
            pointerDownWorld = world;
            pointerDownScreen = mouseScreen;
            return;
        }
        if (!pointerDown || !ImGui.isMouseReleased(0)) {
            return;
        }

        pointerDown = false;
        boolean ctrl = ImGui.getIO().getKeyCtrl();
        double dragPx = pointerDownScreen != null ? pointerDownScreen.distance(mouseScreen) : 0.0;
        if (dragPx < DRAG_THRESHOLD_PX) {
            handlePointClick(
                appState,
                networkManager,
                overlayEntries,
                junctionOverlayEntries,
                canvas,
                world,
                ctrl,
                pickSessionActive);
        } else if (pointerDownWorld != null) {
            handleBoxSelect(
                appState,
                networkManager,
                overlayEntries,
                pointerDownWorld,
                world,
                ctrl,
                pickSessionActive);
        }
    }

    private void handlePointClick(
            AppState appState,
            RoadNetworkManager networkManager,
            List<RoadOverlayEntry> overlayEntries,
            List<RoadJunctionOverlayEntry> junctionOverlayEntries,
            Canvas canvas,
            Vec2d world,
            boolean ctrl,
            boolean pickSessionActive) {
        double junctionHitRadius = canvas.getCamera() != null
            ? canvas.getCamera().screenToWorldDistance(12.0)
            : 1.0;
        String nodeId = RoadJunctionOverlayController.hitTest(
            junctionOverlayEntries, world.x, world.y, junctionHitRadius);
        if (nodeId != null && !nodeId.isBlank()) {
            networkManager.handleNodeSelect(nodeId);
            if (!ctrl) {
                appState.setSelectedShapes(List.of());
            }
            invalidateOverlay();
            return;
        }

        RoadOverlayEntry hit = RoadOverlayController.hitTestEntry(overlayEntries, world.x, world.y);
        if (hit != null) {
            if (hit.roadId().startsWith("shape:")) {
                Shape shape = resolveShape(appState, hit.roadId().substring("shape:".length()));
                if (shape != null && RoadGeometryUtils.isAdoptablePath(shape)) {
                    selectCanvasShapes(appState, List.of(shape), ctrl);
                    if (!ctrl) {
                        networkManager.clearEdgeSelection();
                        networkManager.clearNodeSelection();
                    }
                    invalidateOverlay();
                }
            } else {
                networkManager.selectRoad(hit.roadId(), ctrl);
                if (!ctrl) {
                    appState.setSelectedShapes(List.of());
                }
                invalidateOverlay();
            }
            return;
        }

        if (!ctrl && !pickSessionActive) {
            networkManager.clearEdgeSelection();
            networkManager.clearNodeSelection();
            invalidateOverlay();
        }
    }

    private void handleBoxSelect(
            AppState appState,
            RoadNetworkManager networkManager,
            List<RoadOverlayEntry> overlayEntries,
            Vec2d startWorld,
            Vec2d endWorld,
            boolean ctrl,
            boolean pickSessionActive) {
        double minX = Math.min(startWorld.x, endWorld.x);
        double maxX = Math.max(startWorld.x, endWorld.x);
        double minY = Math.min(startWorld.y, endWorld.y);
        double maxY = Math.max(startWorld.y, endWorld.y);
        boolean leftToRight = endWorld.x >= startWorld.x;

        LinkedHashSet<String> roadIds = RoadOverlayController.collectRoadsInBox(
            overlayEntries, minX, minY, maxX, maxY, leftToRight);
        List<Shape> shapeHits = pickSessionActive
            ? resolveOverlayShapes(
                appState,
                RoadOverlayController.collectShapeIdsInBox(
                    overlayEntries, minX, minY, maxX, maxY, leftToRight))
            : List.of();

        if (roadIds.isEmpty() && shapeHits.isEmpty()) {
            if (!ctrl && !pickSessionActive) {
                networkManager.clearEdgeSelection();
                networkManager.clearNodeSelection();
                invalidateOverlay();
            }
            return;
        }

        if (!roadIds.isEmpty()) {
            applyRoadSelection(networkManager, roadIds, ctrl);
            if (!ctrl) {
                appState.setSelectedShapes(List.of());
            }
        }

        if (!shapeHits.isEmpty()) {
            selectCanvasShapes(appState, shapeHits, ctrl);
            if (!ctrl) {
                networkManager.clearEdgeSelection();
                networkManager.clearNodeSelection();
            }
        }

        invalidateOverlay();
    }

    private static void applyRoadSelection(
            RoadNetworkManager networkManager,
            LinkedHashSet<String> roadIds,
            boolean ctrl) {
        if (ctrl) {
            for (String roadId : roadIds) {
                networkManager.selectRoad(roadId, true);
            }
            return;
        }
        networkManager.clearEdgeSelection();
        boolean first = true;
        for (String roadId : roadIds) {
            networkManager.selectRoad(roadId, !first);
            first = false;
        }
    }

    private static void selectCanvasShapes(AppState appState, List<Shape> shapes, boolean ctrl) {
        if (shapes.isEmpty()) {
            return;
        }
        if (!ctrl) {
            appState.setSelectedShapes(new ArrayList<>(shapes));
            return;
        }
        LinkedHashMap<String, Shape> merged = new LinkedHashMap<>();
        for (Shape selected : appState.getSelectedShapes()) {
            merged.put(selected.getId(), selected);
        }
        for (Shape shape : shapes) {
            if (merged.containsKey(shape.getId())) {
                merged.remove(shape.getId());
            } else {
                merged.put(shape.getId(), shape);
            }
        }
        appState.setSelectedShapes(new ArrayList<>(merged.values()));
    }

    private static List<Shape> resolveOverlayShapes(AppState appState, LinkedHashSet<String> shapeIds) {
        if (shapeIds.isEmpty()) {
            return List.of();
        }
        List<Shape> resolved = new ArrayList<>();
        for (String shapeId : shapeIds) {
            Shape shape = resolveShape(appState, shapeId);
            if (shape != null && RoadGeometryUtils.isAdoptablePath(shape)) {
                resolved.add(shape);
            }
        }
        return resolved;
    }

    private static Shape resolveShape(AppState appState, String shapeId) {
        if (shapeId == null || shapeId.isBlank()) {
            return null;
        }
        for (Shape shape : appState.getShapes()) {
            if (shape != null && shapeId.equals(shape.getId())) {
                return shape;
            }
        }
        return null;
    }

    private static void invalidateOverlay() {
        RoadRepairDiagnosisCache.invalidate();
    }
}
