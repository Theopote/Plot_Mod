package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
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
 * 中心线设计器：选中分段的平面折线示意（PI、操作距离光标、圆角预览）。
 */
public final class CenterlineSegmentPreviewRenderer {

    private static final float PREVIEW_HEIGHT = 168f;
    private static final float PAD = 14f;
    private static final float LEGEND_RESERVE = 22f;
    private static final float PI_HIT_RADIUS_PX = 8f;
    private static final float LEGEND_GAP = 12f;
    private static final int COLOR_LINE = PluginUiColors.ACCENT_BLUE;
    private static final int COLOR_START = ImColor.rgba(90, 217, 115, 255);
    private static final int COLOR_END = ImColor.rgba(242, 140, 90, 255);
    private static final int COLOR_PI = ImColor.rgba(230, 230, 242, 255);
    private static final int COLOR_PI_SELECTED = PluginUiColors.WARNING;
    private static final int COLOR_CURSOR = PluginUiColors.STATUS_INFO;
    private static final int COLOR_FILLET = ImColor.rgba(255, 200, 96, 180);
    private static final int COLOR_FILLET_TRIM = ImColor.rgba(255, 200, 96, 120);

    private CenterlineSegmentPreviewRenderer() {
    }

    /**
     * 预览点击结果：优先 PI 命中；否则为折线上的操作距离。
     *
     * @param distanceBlocks  折线命中时的分段内距离；PI 命中时为 null
     * @param selectedPiIndex 命中的内部 PI 序号（1..n）；未命中为 0
     */
    public record PreviewInteraction(Float distanceBlocks, int selectedPiIndex) {
        public boolean hitPi() {
            return selectedPiIndex > 0;
        }

        public boolean hitDistance() {
            return distanceBlocks != null;
        }
    }

    /**
     * @param distanceBlocks       分段内操作距离（世界块 / m）
     * @param edgeBlocks           分段世界长度
     * @param selectedPi           圆角目标 PI 序号（1..n），0 表示无高亮
     * @param filletRadiusCanvas   圆角半径（画布域）；≤0 或不显示时不画示意
     * @param showFilletPreview    是否绘制圆角示意
     */
    public static PreviewInteraction render(
            RoadEdge edge,
            float distanceBlocks,
            float edgeBlocks,
            int selectedPi,
            float filletRadiusCanvas,
            boolean showFilletPreview) {
        if (edge == null) {
            return new PreviewInteraction(null, 0);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.size() < 2) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.centerline_preview_empty"));
            return new PreviewInteraction(null, 0);
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
        float plotH = Math.max(1f, height - PAD * 2f - LEGEND_RESERVE);
        float scale = Math.min(plotW / Math.max(1e-3f, bounds.spanX()), plotH / Math.max(1e-3f, bounds.spanY()));
        float midX = (float) ((bounds.minX + bounds.maxX) * 0.5);
        float midY = (float) ((bounds.minY + bounds.maxY) * 0.5);
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1 - LEGEND_RESERVE) * 0.5f;

        List<ImVec2> screen = new ArrayList<>(points.size());
        for (Vec2d p : points) {
            screen.add(toScreen(p, cx, cy, midX, midY, scale));
        }

        drawList.pushClipRect(x0, y0, x1, y1, true);
        try {
            for (int i = 0; i < screen.size() - 1; i++) {
                ImVec2 a = screen.get(i);
                ImVec2 b = screen.get(i + 1);
                drawList.addLine(a.x, a.y, b.x, b.y, COLOR_LINE, 2.5f);
            }

            if (showFilletPreview
                    && selectedPi > 0
                    && selectedPi < points.size() - 1
                    && filletRadiusCanvas > 1e-4f) {
                drawFilletPreview(
                    drawList, points, selectedPi, filletRadiusCanvas, cx, cy, midX, midY, scale);
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
                    drawList.addText(p.x + 6f, p.y - 8f, COLOR_PI, String.valueOf(i));
                }
            }

            float edgeLen = Math.max(0.01f, edgeBlocks);
            float t = Math.clamp(distanceBlocks / edgeLen, 0f, 1f);
            Vec2d cursorCanvas = pointAtFraction(points, t);
            if (cursorCanvas != null) {
                ImVec2 cursor = toScreen(cursorCanvas, cx, cy, midX, midY, scale);
                drawList.addCircleFilled(cursor.x, cursor.y, 4.5f, COLOR_CURSOR);
                drawList.addCircle(cursor.x, cursor.y, 7f, COLOR_CURSOR, 0, 1.5f);
                drawList.addText(
                    cursor.x + 8f,
                    cursor.y - 10f,
                    COLOR_CURSOR,
                    PlotI18n.tr("plugin.road.centerline_preview_cursor"));
            }

            drawLegend(drawList, x0, x1, y1);
        } finally {
            drawList.popClipRect();
        }

        ImGui.invisibleButton("##centerline_segment_preview_hit", width, height);
        if (!ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            return new PreviewInteraction(null, 0);
        }
        ImVec2 mouse = ImGui.getMousePos();
        int hitPi = hitInteriorPi(screen, mouse.x, mouse.y);
        if (hitPi > 0) {
            return new PreviewInteraction(null, hitPi);
        }
        float frac = nearestFractionOnPolyline(screen, mouse.x, mouse.y);
        if (Float.isFinite(frac)) {
            float edgeLen = Math.max(0.01f, edgeBlocks);
            return new PreviewInteraction(Math.clamp(frac, 0f, 1f) * edgeLen, 0);
        }
        return new PreviewInteraction(null, 0);
    }

    /** 兼容旧调用：无圆角示意。 */
    public static Float render(
            RoadEdge edge,
            float distanceBlocks,
            float edgeBlocks,
            int selectedPi) {
        PreviewInteraction hit = render(edge, distanceBlocks, edgeBlocks, selectedPi, 0f, false);
        return hit.distanceBlocks();
    }

    private static void drawFilletPreview(
            ImDrawList drawList,
            List<Vec2d> points,
            int selectedPi,
            float filletRadiusCanvas,
            float cx,
            float cy,
            float midX,
            float midY,
            float scale) {
        Vec2d prev = points.get(selectedPi - 1);
        Vec2d corner = points.get(selectedPi);
        Vec2d next = points.get(selectedPi + 1);
        ImVec2 prevScreen = toScreen(prev, cx, cy, midX, midY, scale);
        ImVec2 cornerScreen = toScreen(corner, cx, cy, midX, midY, scale);
        ImVec2 nextScreen = toScreen(next, cx, cy, midX, midY, scale);

        List<Vec2d> localArc = RoadCenterlineEditor.previewFilletArc(
            points, selectedPi, filletRadiusCanvas);
        if (localArc == null || localArc.size() < 2) {
            // 半径不可行：仅虚线标出当前折角，不叠画整条路。
            drawDashedLine(drawList, prevScreen, cornerScreen, COLOR_FILLET_TRIM, 1.5f);
            drawDashedLine(drawList, cornerScreen, nextScreen, COLOR_FILLET_TRIM, 1.5f);
            return;
        }

        // 将被削掉的折角用淡虚线标出，圆角弧用实线示意（避免整折线虚线叠画乱线）。
        ImVec2 trim1 = toScreen(localArc.getFirst(), cx, cy, midX, midY, scale);
        ImVec2 trim2 = toScreen(localArc.getLast(), cx, cy, midX, midY, scale);
        drawDashedLine(drawList, trim1, cornerScreen, COLOR_FILLET_TRIM, 1.4f);
        drawDashedLine(drawList, cornerScreen, trim2, COLOR_FILLET_TRIM, 1.4f);

        for (int i = 0; i < localArc.size() - 1; i++) {
            ImVec2 a = toScreen(localArc.get(i), cx, cy, midX, midY, scale);
            ImVec2 b = toScreen(localArc.get(i + 1), cx, cy, midX, midY, scale);
            drawList.addLine(a.x, a.y, b.x, b.y, COLOR_FILLET, 2.4f);
        }
    }

    private static void drawLegend(ImDrawList drawList, float x0, float x1, float y1) {
        float legendY = y1 - 12f;
        float cursorX = x0 + 10f;
        float maxX = x1 - 6f;
        cursorX = legendItem(
            drawList, cursorX, legendY, maxX, COLOR_START,
            PlotI18n.tr("plugin.road.centerline_preview_legend_start"));
        cursorX = legendItem(
            drawList, cursorX, legendY, maxX, COLOR_END,
            PlotI18n.tr("plugin.road.centerline_preview_legend_end"));
        cursorX = legendItem(
            drawList, cursorX, legendY, maxX, COLOR_PI,
            PlotI18n.tr("plugin.road.centerline_preview_legend_pi"));
        legendItem(
            drawList, cursorX, legendY, maxX, COLOR_CURSOR,
            PlotI18n.tr("plugin.road.centerline_preview_legend_cursor"));
    }

    private static float legendItem(
            ImDrawList drawList,
            float cursorX,
            float legendY,
            float maxX,
            int color,
            String label) {
        float textW = ImGui.calcTextSize(label).x;
        float need = 4f + 3.5f + 4f + textW + LEGEND_GAP;
        if (cursorX + need - LEGEND_GAP > maxX) {
            return cursorX;
        }
        drawList.addCircleFilled(cursorX + 3.5f, legendY, 3.5f, color);
        drawList.addText(cursorX + 10f, legendY - 7f, PluginUiColors.LEGEND, label);
        return cursorX + need;
    }

    private static void drawDashedLine(
            ImDrawList drawList, ImVec2 a, ImVec2 b, int color, float thickness) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            return;
        }
        float dash = 6f;
        float gap = 4f;
        float ux = dx / len;
        float uy = dy / len;
        float t = 0f;
        boolean draw = true;
        while (t < len) {
            float seg = draw ? dash : gap;
            float t1 = Math.min(len, t + seg);
            if (draw) {
                drawList.addLine(
                    a.x + ux * t,
                    a.y + uy * t,
                    a.x + ux * t1,
                    a.y + uy * t1,
                    color,
                    thickness);
            }
            t = t1;
            draw = !draw;
        }
    }

    /** 命中内部 PI 时返回其序号（1..n-2 对应索引）；未命中返回 0。 */
    static int hitInteriorPi(List<ImVec2> screen, float mx, float my) {
        if (screen == null || screen.size() < 3) {
            return 0;
        }
        double bestDist2 = PI_HIT_RADIUS_PX * PI_HIT_RADIUS_PX;
        int best = 0;
        for (int i = 1; i < screen.size() - 1; i++) {
            ImVec2 p = screen.get(i);
            double dx = mx - p.x;
            double dy = my - p.y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = i;
            }
        }
        return best;
    }

    private static ImVec2 toScreen(
            Vec2d p, float cx, float cy, float midX, float midY, float scale) {
        return new ImVec2(
            cx + (float) ((p.x - midX) * scale),
            cy - (float) ((p.y - midY) * scale));
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
