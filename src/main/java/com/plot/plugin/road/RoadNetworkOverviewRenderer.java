package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 路网概览缩略图：在 ImGui 面板内绘制节点/边的俯视图。
 */
public final class RoadNetworkOverviewRenderer {
    private static final float MAP_HEIGHT = 180f;
    private static final float PADDING = 10f;
    private static final float NODE_RADIUS = 4f;
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

    private RoadNetworkOverviewRenderer() {
    }

    public static void render(
            RoadNetwork network,
            RoadNetworkBuilder networkBuilder,
            Set<String> selectedEdgeIds,
            Consumer<String> onEdgeSelected) {
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
        drawNodes(drawList, network, networkBuilder, bounds, originX, originY, width, height);

        ImGui.invisibleButton("##road_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.network_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0) && onEdgeSelected != null) {
            ImVec2 mouse = ImGui.getMousePos();
            double worldX = toWorldX(mouse.x, bounds, originX, width);
            double worldY = toWorldY(mouse.y, bounds, originY, height);
            String hit = hitTestEdge(network, worldX, worldY, bounds.hitThreshold());
            if (hit != null) {
                onEdgeSelected.accept(hit);
            }
        }

        ImGui.endChild();
        renderLegend();
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
            float height) {
        for (RoadNode node : network.getNodes().values()) {
            Vec2d pos = node.getPosition();
            int color = junctionColor(networkBuilder.classify(node));
            float sx = toScreenX(pos.x, bounds, originX, width);
            float sy = toScreenY(pos.y, bounds, originY, height);
            drawList.addCircleFilled(sx, sy, NODE_RADIUS, color);
            drawList.addCircle(sx, sy, NODE_RADIUS + 0.5f, (int) 0xFF202020FFL, 12, 1f);
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
