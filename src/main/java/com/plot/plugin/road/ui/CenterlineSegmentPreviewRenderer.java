package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 中心线设计器：选中分段的平面折线示意（PI、操作距离光标）。
 */
public final class CenterlineSegmentPreviewRenderer {

    private static final float PREVIEW_HEIGHT = 168f;
    private static final float PAD = 14f;
    private static final int COLOR_LINE = PluginUiColors.ACCENT_BLUE;
    private static final int COLOR_START = ImColor.rgba(90, 217, 115, 255);
    private static final int COLOR_END = ImColor.rgba(242, 140, 90, 255);
    private static final int COLOR_PI = ImColor.rgba(230, 230, 242, 255);
    private static final int COLOR_PI_SELECTED = PluginUiColors.WARNING;
    private static final int COLOR_CURSOR = PluginUiColors.STATUS_INFO;

    private CenterlineSegmentPreviewRenderer() {
    }

    /**
     * @param distanceBlocks 分段内操作距离（世界块 / m）
     * @param edgeBlocks     分段世界长度
     * @param selectedPi     圆角目标 PI 序号（1..n），0 表示无高亮
     * @return 若用户在预览上点击，返回新的分段内距离；否则 {@code null}
     */
    public static Float render(
            RoadEdge edge,
            float distanceBlocks,
            float edgeBlocks,
            int selectedPi) {
        if (edge == null) {
            return null;
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.size() < 2) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.centerline_preview_empty"));
            return null;
        }

        ImGui.text(PlotI18n.tr("plugin.road.centerline_preview_title"));

        float width = Math.max(120f, ImGui.getContentRegionAvailX());
        float height = PREVIEW_HEIGHT;
        ImVec2 origin = ImGui.getCursorScreenPos();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + height;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(x0, y0, x1, y1, PluginUiColors.MAP_BG, 4f);
        drawList.addRect(x0, y0, x1, y1, PluginUiColors.PANEL_BORDER, 4f, 0, 1f);

        Bounds bounds = boundsOf(points);
        float plotW = Math.max(1f, width - PAD * 2f);
        float plotH = Math.max(1f, height - PAD * 2f);
        float scale = Math.min(plotW / Math.max(1e-3f, bounds.spanX()), plotH / Math.max(1e-3f, bounds.spanY()));
        float midX = (float) ((bounds.minX + bounds.maxX) * 0.5);
        float midY = (float) ((bounds.minY + bounds.maxY) * 0.5);
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;

        List<ImVec2> screen = new ArrayList<>(points.size());
        for (Vec2d p : points) {
            screen.add(new ImVec2(
                cx + (float) ((p.x - midX) * scale),
                cy - (float) ((p.y - midY) * scale)));
        }

        for (int i = 0; i < screen.size() - 1; i++) {
            ImVec2 a = screen.get(i);
            ImVec2 b = screen.get(i + 1);
            drawList.addLine(a.x, a.y, b.x, b.y, COLOR_LINE, 2.5f);
        }

        for (int i = 0; i < screen.size(); i++) {
            ImVec2 p = screen.get(i);
            boolean isStart = i == 0;
            boolean isEnd = i == screen.size() - 1;
            boolean isSelectedPi = !isStart && !isEnd && i == selectedPi;
            int color = isStart ? COLOR_START
                : (isEnd ? COLOR_END : (isSelectedPi ? COLOR_PI_SELECTED : COLOR_PI));
            float radius = isSelectedPi ? 5.5f : (isStart || isEnd ? 5f : 3.5f);
            drawList.addCircleFilled(p.x, p.y, radius, color);
            if (!isStart && !isEnd) {
                String idx = String.valueOf(i);
                drawList.addText(p.x + 6f, p.y - 8f, COLOR_PI, idx);
            }
        }

        float edgeLen = Math.max(0.01f, edgeBlocks);
        float t = Math.clamp(distanceBlocks / edgeLen, 0f, 1f);
        Vec2d cursorCanvas = pointAtFraction(points, t);
        if (cursorCanvas != null) {
            float sx = cx + (float) ((cursorCanvas.x - midX) * scale);
            float sy = cy - (float) ((cursorCanvas.y - midY) * scale);
            drawList.addCircleFilled(sx, sy, 4.5f, COLOR_CURSOR);
            drawList.addCircle(sx, sy, 7f, COLOR_CURSOR, 0, 1.5f);
            drawList.addText(
                sx + 8f,
                sy - 10f,
                COLOR_CURSOR,
                PlotI18n.tr("plugin.road.centerline_preview_cursor"));
        }

        // 图例
        float legendY = y1 - 16f;
        drawList.addCircleFilled(x0 + 12f, legendY, 3.5f, COLOR_START);
        drawList.addText(x0 + 18f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.centerline_preview_legend_start"));
        drawList.addCircleFilled(x0 + 70f, legendY, 3.5f, COLOR_END);
        drawList.addText(x0 + 76f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.centerline_preview_legend_end"));
        drawList.addCircleFilled(x0 + 120f, legendY, 3.5f, COLOR_PI);
        drawList.addText(x0 + 126f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.centerline_preview_legend_pi"));
        drawList.addCircleFilled(x0 + 170f, legendY, 3.5f, COLOR_CURSOR);
        drawList.addText(x0 + 176f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.centerline_preview_legend_cursor"));

        ImGui.invisibleButton("##centerline_segment_preview_hit", width, height);
        Float clicked = null;
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            ImVec2 mouse = ImGui.getMousePos();
            float frac = nearestFractionOnPolyline(screen, mouse.x, mouse.y);
            if (Float.isFinite(frac)) {
                clicked = Math.clamp(frac, 0f, 1f) * edgeLen;
            }
        }
        return clicked;
    }

    private static Bounds boundsOf(List<Vec2d> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Vec2d p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        if (!Double.isFinite(minX)) {
            return new Bounds(0, 0, 1, 1);
        }
        if (maxX - minX < 1e-3) {
            maxX = minX + 1;
        }
        if (maxY - minY < 1e-3) {
            maxY = minY + 1;
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    /** 沿折线弧长比例取点（0=起点，1=终点）。 */
    static Vec2d pointAtFraction(List<Vec2d> points, float fraction) {
        if (points == null || points.size() < 2) {
            return null;
        }
        double total = polylineLength(points);
        if (total <= 1e-9) {
            return points.getFirst();
        }
        double target = Math.clamp(fraction, 0f, 1f) * total;
        double acc = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            double seg = Math.hypot(b.x - a.x, b.y - a.y);
            if (acc + seg >= target - 1e-9) {
                double u = seg <= 1e-9 ? 0.0 : (target - acc) / seg;
                return new Vec2d(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u);
            }
            acc += seg;
        }
        return points.getLast();
    }

    private static double polylineLength(List<Vec2d> points) {
        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            total += Math.hypot(b.x - a.x, b.y - a.y);
        }
        return total;
    }

    /** 屏幕折线上最近点对应的弧长比例。 */
    static float nearestFractionOnPolyline(List<ImVec2> screen, float mx, float my) {
        if (screen == null || screen.size() < 2) {
            return Float.NaN;
        }
        double bestDist2 = Double.POSITIVE_INFINITY;
        double bestAlong = 0.0;
        double total = 0.0;
        double[] segLens = new double[screen.size() - 1];
        for (int i = 0; i < screen.size() - 1; i++) {
            ImVec2 a = screen.get(i);
            ImVec2 b = screen.get(i + 1);
            segLens[i] = Math.hypot(b.x - a.x, b.y - a.y);
            total += segLens[i];
        }
        if (total <= 1e-9) {
            return 0f;
        }
        double acc = 0.0;
        for (int i = 0; i < screen.size() - 1; i++) {
            ImVec2 a = screen.get(i);
            ImVec2 b = screen.get(i + 1);
            double seg = segLens[i];
            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double u = seg <= 1e-9 ? 0.0 : ((mx - a.x) * dx + (my - a.y) * dy) / (seg * seg);
            u = Math.clamp(u, 0.0, 1.0);
            double px = a.x + dx * u;
            double py = a.y + dy * u;
            double d2 = (mx - px) * (mx - px) + (my - py) * (my - py);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                bestAlong = acc + seg * u;
            }
            acc += seg;
        }
        return (float) (bestAlong / total);
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
        float spanX() {
            return (float) (maxX - minX);
        }

        float spanY() {
            return (float) (maxY - minY);
        }
    }
}
