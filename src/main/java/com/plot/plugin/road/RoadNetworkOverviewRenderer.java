package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * 路网概览缩略图：在 ImGui 面板内绘制节点/边的俯视图。
 */
public final class RoadNetworkOverviewRenderer {
    private static final float MAP_HEIGHT = 180f;
    private static final float PADDING = 10f;
    private static final float NODE_RADIUS = 4f;
    private static final float SELECTED_NODE_RADIUS = 6f;
    private static final float EDGE_THICKNESS = 1.5f;
    private static final float SELECTED_EDGE_THICKNESS = 2.5f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_EDGE = 0xFF909090;
    private static final int COLOR_EDGE_SELECTED = 0xFF4DA6FF;
    private static final int COLOR_ENDPOINT = 0xFF888888;
    private static final int COLOR_THROUGH = 0xFF66CC66;
    private static final int COLOR_T_JUNCTION = 0xFFFFCC44;
    private static final int COLOR_CROSSROAD = 0xFFFF6666;
    private static final int COLOR_COMPLEX = 0xFFCC66FF;
    private static final int COLOR_NODE_SELECTED_RING = 0xFFFFFFFF;
    private static final int COLOR_JUNCTION_PREVIEW_FILL = 0x334DA6FF;
    private static final int COLOR_JUNCTION_PREVIEW_BORDER = 0xCC4DA6FF;

    private RoadNetworkOverviewRenderer() {
    }

    public static void render(
            RoadNetwork network,
            RoadNetworkBuilder networkBuilder,
            RoadSystemConfig config,
            Set<String> selectedEdgeIds,
            String selectedNodeId,
            Consumer<String> onEdgeSelected,
            Consumer<String> onNodeSelected) {
        ImGui.text(PlotI18n.tr("plugin.road.network_map"));
        ImGui.beginChild("road_network_map", 0, MAP_HEIGHT, true);

        float width = ImGui.getContentRegionAvail().x;
        float height = ImGui.getContentRegionAvail().y;
        if (width < 1f || height < 1f) {
            ImGui.endChild();
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        float originX = origin.x;
        float originY = origin.y;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(originX, originY, originX + width, originY + height, COLOR_BG);
        drawList.addRect(originX, originY, originX + width, originY + height, COLOR_BORDER);

        if (network.getEdges().isEmpty()) {
            renderCenteredHint(drawList, originX, originY, width, height,
                PlotI18n.tr("plugin.road.network_map_empty"));
            ImGui.dummy(width, height);
            ImGui.endChild();
            return;
        }

        Bounds bounds = computeBounds(network);
        drawEdges(drawList, network, bounds, originX, originY, width, height, selectedEdgeIds);
        drawSelectedJunctionPreview(
            drawList, network, config, bounds, originX, originY, width, height, selectedNodeId);
        drawNodes(
            drawList, network, networkBuilder, bounds, originX, originY, width, height, selectedNodeId);

        ImGui.invisibleButton("##road_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.network_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0)) {
            ImVec2 mouse = ImGui.getMousePos();
            double worldX = toWorldX(mouse.x, bounds, originX, width);
            double worldY = toWorldY(mouse.y, bounds, originY, height);

            String nodeHit = hitTestNode(network, worldX, worldY, bounds.hitThreshold() * 0.55);
            if (nodeHit != null && onNodeSelected != null) {
                onNodeSelected.accept(nodeHit);
            } else if (onEdgeSelected != null) {
                String edgeHit = hitTestEdge(network, worldX, worldY, bounds.hitThreshold());
                if (edgeHit != null) {
                    onEdgeSelected.accept(edgeHit);
                }
            }
        }

        ImGui.endChild();
        renderLegend();
    }

    private static void drawSelectedJunctionPreview(
            ImDrawList drawList,
            RoadNetwork network,
            RoadSystemConfig config,
            Bounds bounds,
            float originX,
            float originY,
            float width,
            float height,
            String selectedNodeId) {
        if (selectedNodeId == null || selectedNodeId.isBlank() || config == null) {
            return;
        }
        RoadNode node = network.getNode(selectedNodeId);
        if (node == null || !node.isJunction()) {
            return;
        }

        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        if (edges.isEmpty()) {
            return;
        }

        ToDoubleFunction<RoadEdge> halfWidthResolver = edge -> edge.getEffectiveWidth(config) / 2.0;
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            halfWidthResolver,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );
        double cornerRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        List<Vec2d> polygon = RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            edges,
            halfWidthResolver,
            junctionRadius,
            cornerRadius
        );
        if (polygon.size() < 3) {
            return;
        }

        ImVec2[] screenPoints = new ImVec2[polygon.size()];
        for (int i = 0; i < polygon.size(); i++) {
            screenPoints[i] = new ImVec2(
                toScreenX(polygon.get(i).x, bounds, originX, width),
                toScreenY(polygon.get(i).y, bounds, originY, height)
            );
        }
        drawList.addConvexPolyFilled(screenPoints, polygon.size(), COLOR_JUNCTION_PREVIEW_FILL);
        for (int i = 0; i < screenPoints.length; i++) {
            ImVec2 a = screenPoints[i];
            ImVec2 b = screenPoints[(i + 1) % screenPoints.length];
            drawList.addLine(a.x, a.y, b.x, b.y, COLOR_JUNCTION_PREVIEW_BORDER, 1.5f);
        }
    }

    private static void renderLegend() {
        ImGui.spacing();
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.network_map_legend"));
        ImGui.sameLine();
        legendItem(COLOR_ENDPOINT, PlotI18n.tr("plugin.road.legend.endpoint"));
        ImGui.sameLine();
        legendItem(COLOR_THROUGH, PlotI18n.tr("plugin.road.legend.through"));
        ImGui.sameLine();
        legendItem(COLOR_T_JUNCTION, PlotI18n.tr("plugin.road.legend.t_junction"));
        ImGui.sameLine();
        legendItem(COLOR_CROSSROAD, PlotI18n.tr("plugin.road.legend.crossroad"));
        ImGui.sameLine();
        legendItem(COLOR_COMPLEX, PlotI18n.tr("plugin.road.legend.complex"));
    }

    private static void legendItem(int color, String label) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        ImVec2 pos = ImGui.getCursorScreenPos();
        float y = pos.y + ImGui.getTextLineHeight() * 0.5f;
        drawList.addCircleFilled(pos.x + 4f, y, 3f, color);
        ImGui.dummy(10f, 0);
        ImGui.sameLine();
        ImGui.textColored((int) 0xFFAAAAAAFFL, label);
        ImGui.sameLine();
    }

    private static void renderCenteredHint(
            ImDrawList drawList, float x, float y, float w, float h, String text) {
        float textW = ImGui.calcTextSize(text).x;
        float textX = x + (w - textW) * 0.5f;
        float textY = y + (h - ImGui.getTextLineHeight()) * 0.5f;
        drawList.addText(textX, textY, (int) 0xFF707070FFL, text);
    }

    private static void drawEdges(
            ImDrawList drawList,
            RoadNetwork network,
            Bounds bounds,
            float originX,
            float originY,
            float width,
            float height,
            Set<String> selectedEdgeIds) {
        for (RoadEdge edge : network.getEdges().values()) {
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points.size() < 2) {
                continue;
            }
            boolean selected = selectedEdgeIds != null && selectedEdgeIds.contains(edge.getId());
            int color = selected ? COLOR_EDGE_SELECTED : COLOR_EDGE;
            float thickness = selected ? SELECTED_EDGE_THICKNESS : EDGE_THICKNESS;
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d a = points.get(i);
                Vec2d b = points.get(i + 1);
                drawList.addLine(
                    toScreenX(a.x, bounds, originX, width),
                    toScreenY(a.y, bounds, originY, height),
                    toScreenX(b.x, bounds, originX, width),
                    toScreenY(b.y, bounds, originY, height),
                    color,
                    thickness
                );
            }
        }
    }

    private static void drawNodes(
            ImDrawList drawList,
            RoadNetwork network,
            RoadNetworkBuilder networkBuilder,
            Bounds bounds,
            float originX,
            float originY,
            float width,
            float height,
            String selectedNodeId) {
        for (RoadNode node : network.getNodes().values()) {
            Vec2d pos = node.getPosition();
            boolean selected = node.getId().equals(selectedNodeId);
            int color = junctionColor(networkBuilder.classify(node));
            float sx = toScreenX(pos.x, bounds, originX, width);
            float sy = toScreenY(pos.y, bounds, originY, height);
            float radius = selected ? SELECTED_NODE_RADIUS : NODE_RADIUS;
            drawList.addCircleFilled(sx, sy, radius, color);
            drawList.addCircle(sx, sy, radius + 0.5f, (int) 0xFF202020FFL, 12, 1f);
            if (selected) {
                drawList.addCircle(sx, sy, radius + 2.5f, COLOR_NODE_SELECTED_RING, 16, 1.5f);
            }
        }
    }

    private static int junctionColor(RoadNetworkBuilder.JunctionType type) {
        return switch (type) {
            case ENDPOINT -> COLOR_ENDPOINT;
            case THROUGH -> COLOR_THROUGH;
            case T_JUNCTION -> COLOR_T_JUNCTION;
            case CROSSROAD -> COLOR_CROSSROAD;
            case COMPLEX -> COLOR_COMPLEX;
        };
    }

    private static Bounds computeBounds(RoadNetwork network) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (RoadEdge edge : network.getEdges().values()) {
            for (Vec2d point : edge.getCenterlinePoints()) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
        }
        for (RoadNode node : network.getNodes().values()) {
            Vec2d pos = node.getPosition();
            minX = Math.min(minX, pos.x);
            minY = Math.min(minY, pos.y);
            maxX = Math.max(maxX, pos.x);
            maxY = Math.max(maxY, pos.y);
        }

        double spanX = maxX - minX;
        double spanY = maxY - minY;
        if (spanX < 1e-6) {
            minX -= 0.5;
            maxX += 0.5;
            spanX = 1.0;
        }
        if (spanY < 1e-6) {
            minY -= 0.5;
            maxY += 0.5;
            spanY = 1.0;
        }

        double hitThreshold = Math.max(spanX, spanY) * 0.08;
        return new Bounds(minX, minY, maxX, maxY, spanX, spanY, hitThreshold);
    }

    private static float toScreenX(double worldX, Bounds bounds, float originX, float width) {
        float inner = width - PADDING * 2f;
        return originX + PADDING + (float) ((worldX - bounds.minX) / bounds.spanX) * inner;
    }

    private static float toScreenY(double worldY, Bounds bounds, float originY, float height) {
        float inner = height - PADDING * 2f;
        return originY + PADDING + (float) ((bounds.maxY - worldY) / bounds.spanY) * inner;
    }

    private static double toWorldX(float screenX, Bounds bounds, float originX, float width) {
        float inner = width - PADDING * 2f;
        if (inner <= 0f) {
            return bounds.minX;
        }
        return bounds.minX + (screenX - originX - PADDING) / inner * bounds.spanX;
    }

    private static double toWorldY(float screenY, Bounds bounds, float originY, float height) {
        float inner = height - PADDING * 2f;
        if (inner <= 0f) {
            return bounds.maxY;
        }
        return bounds.maxY - (screenY - originY - PADDING) / inner * bounds.spanY;
    }

    static String hitTestEdge(RoadNetwork network, double wx, double wy, double threshold) {
        String closestId = null;
        double closestDist = threshold;
        for (RoadEdge edge : network.getEdges().values()) {
            List<Vec2d> points = edge.getCenterlinePoints();
            for (int i = 0; i < points.size() - 1; i++) {
                double dist = distancePointToSegment(wx, wy, points.get(i), points.get(i + 1));
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = edge.getId();
                }
            }
        }
        return closestId;
    }

    static String hitTestNode(RoadNetwork network, double wx, double wy, double threshold) {
        String closestId = null;
        double closestDist = threshold;
        for (RoadNode node : network.getNodes().values()) {
            Vec2d pos = node.getPosition();
            double dist = Math.hypot(wx - pos.x, wy - pos.y);
            if (dist < closestDist) {
                closestDist = dist;
                closestId = node.getId();
            }
        }
        return closestId;
    }

    static double distancePointToSegment(double px, double py, Vec2d a, Vec2d b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double abLenSq = abx * abx + aby * aby;
        if (abLenSq < 1e-12) {
            return Math.hypot(px - a.x, py - a.y);
        }
        double t = ((px - a.x) * abx + (py - a.y) * aby) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = a.x + t * abx;
        double cy = a.y + t * aby;
        return Math.hypot(px - cx, py - cy);
    }

    private record Bounds(
            double minX,
            double minY,
            double maxX,
            double maxY,
            double spanX,
            double spanY,
            double hitThreshold) {
    }
}
