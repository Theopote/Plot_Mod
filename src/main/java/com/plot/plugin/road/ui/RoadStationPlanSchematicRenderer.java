package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
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
 * 道路级平面示意：整路折线 + 播放头 + 桩号/区间标记；点击折线可设置画布域桩号。
 */
public final class RoadStationPlanSchematicRenderer {

    private static final float PREVIEW_HEIGHT = 148f;
    private static final float PAD = 14f;
    private static final int COLOR_LINE = PluginUiColors.ACCENT_BLUE;
    private static final int COLOR_START = ImColor.rgba(90, 217, 115, 255);
    private static final int COLOR_END = ImColor.rgba(242, 140, 90, 255);
    /** 与纵断面播放头同色：琥珀黄，避免与地形亮蓝混淆。 */
    private static final int COLOR_CURSOR = ImColor.rgba(255, 200, 64, 255);
    private static final int COLOR_MARKER = ImColor.rgba(230, 230, 242, 255);
    private static final int COLOR_MARKER_SELECTED = PluginUiColors.WARNING;
    private static final int COLOR_INTERVAL = ImColor.rgba(77, 166, 255, 160);
    private static final int COLOR_INTERVAL_SELECTED = ImColor.rgba(255, 176, 64, 200);

    /** 点状桩号标记（变截面等）。 */
    public record StationMarker(float canvasStation, boolean selected) {
    }

    /** 区间标记（设施等）；{@code endCanvas} 可用道路终点。 */
    public record StationInterval(float startCanvas, float endCanvas, boolean selected) {
    }

    private RoadStationPlanSchematicRenderer() {
    }

    /**
     * @param playheadCanvas 当前播放头（画布域）
     * @param canvasLength   道路画布域总长
     * @return 点击折线后的新播放头（画布域）；未点击则 {@code null}
     */
    public static Float render(
            RoadNetwork network,
            Road road,
            float playheadCanvas,
            float canvasLength,
            List<StationMarker> markers,
            List<StationInterval> intervals) {
        return render(
            network, road, playheadCanvas, canvasLength, markers, intervals, PREVIEW_HEIGHT);
    }

    public static Float render(
            RoadNetwork network,
            Road road,
            float playheadCanvas,
            float canvasLength,
            List<StationMarker> markers,
            List<StationInterval> intervals,
            float previewHeight) {
        if (network == null || road == null || canvasLength <= 1e-6f) {
            return null;
        }
        List<Vec2d> points = buildRoadPolyline(network, road);
        if (points.size() < 2) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.plan_schematic_empty"));
            return null;
        }

        ImGui.text(PlotI18n.tr("plugin.road.plan_schematic_title"));
        float width = Math.max(120f, ImGui.getContentRegionAvailX());
        float height = Math.max(72f, previewHeight);
        ImVec2 origin = ImGui.getCursorScreenPos();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + height;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(x0, y0, x1, y1, PluginUiColors.MAP_BG, 0f);
        drawList.addRect(x0, y0, x1, y1, PluginUiColors.PANEL_BORDER, 0f, 0, 1f);

        Bounds bounds = boundsOf(points);
        float plotW = Math.max(1f, width - PAD * 2f);
        float plotH = Math.max(1f, height - PAD * 2f);
        float scale = Math.min(
            plotW / Math.max(1e-3f, bounds.spanX()),
            plotH / Math.max(1e-3f, bounds.spanY()));
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
            drawList.addLine(a.x, a.y, b.x, b.y, COLOR_LINE, 2.2f);
        }

        if (intervals != null) {
            for (StationInterval interval : intervals) {
                drawIntervalAlongPolyline(
                    drawList,
                    screen,
                    canvasLength,
                    interval.startCanvas(),
                    interval.endCanvas(),
                    interval.selected() ? COLOR_INTERVAL_SELECTED : COLOR_INTERVAL,
                    interval.selected() ? 4.5f : 3.2f);
            }
        }

        ImVec2 start = screen.getFirst();
        ImVec2 end = screen.getLast();
        drawList.addCircleFilled(start.x, start.y, 4.5f, COLOR_START);
        drawList.addCircleFilled(end.x, end.y, 4.5f, COLOR_END);

        if (markers != null) {
            for (StationMarker marker : markers) {
                ImVec2 p = screenPointAtStation(screen, canvasLength, marker.canvasStation());
                if (p == null) {
                    continue;
                }
                int color = marker.selected() ? COLOR_MARKER_SELECTED : COLOR_MARKER;
                float r = marker.selected() ? 5f : 3.5f;
                drawList.addCircleFilled(p.x, p.y, r, color);
            }
        }

        ImVec2 cursor = screenPointAtStation(screen, canvasLength, playheadCanvas);
        if (cursor != null) {
            // 只画当前位置圆点；跟随文字与图例重复，不再绘制。
            drawList.addCircleFilled(cursor.x, cursor.y, 4.5f, COLOR_CURSOR);
            drawList.addCircle(cursor.x, cursor.y, 7f, COLOR_CURSOR, 0, 1.5f);
        }

        float legendY = y1 - 16f;
        drawList.addCircleFilled(x0 + 12f, legendY, 3.5f, COLOR_START);
        drawList.addText(x0 + 18f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.centerline_preview_legend_start"));
        drawList.addCircleFilled(x0 + 70f, legendY, 3.5f, COLOR_END);
        drawList.addText(x0 + 76f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.centerline_preview_legend_end"));
        drawList.addCircleFilled(x0 + 120f, legendY, 3.5f, COLOR_MARKER);
        drawList.addText(x0 + 126f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.plan_schematic_legend_marker"));
        drawList.addCircleFilled(x0 + 180f, legendY, 3.5f, COLOR_CURSOR);
        drawList.addText(x0 + 186f, legendY - 7f, PluginUiColors.LEGEND,
            PlotI18n.tr("plugin.road.plan_schematic_cursor"));

        ImGui.invisibleButton("##road_plan_schematic_hit", width, height);
        if (!ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            return null;
        }
        ImVec2 mouse = ImGui.getMousePos();
        float frac = nearestFractionOnPolyline(screen, mouse.x, mouse.y);
        if (!Float.isFinite(frac)) {
            return null;
        }
        return Math.clamp(frac, 0f, 1f) * Math.max(0.001f, canvasLength);
    }

    static List<Vec2d> buildRoadPolyline(RoadNetwork network, Road road) {
        List<Vec2d> out = new ArrayList<>();
        for (String id : RoadEdgeListHelper.orderedSegmentIds(network, road)) {
            RoadEdge edge = network.getEdge(id);
            if (edge == null) {
                continue;
            }
            List<Vec2d> pts = edge.getCenterlinePoints();
            if (pts == null || pts.isEmpty()) {
                continue;
            }
            int start = 0;
            if (!out.isEmpty() && near(out.getLast(), pts.getFirst())) {
                start = 1;
            }
            for (int i = start; i < pts.size(); i++) {
                out.add(pts.get(i));
            }
        }
        return out;
    }

    private static void drawIntervalAlongPolyline(
            ImDrawList drawList,
            List<ImVec2> screen,
            float canvasLength,
            float startCanvas,
            float endCanvas,
            int color,
            float thickness) {
        float a = Math.min(startCanvas, endCanvas);
        float b = Math.max(startCanvas, endCanvas);
        a = Math.clamp(a, 0f, canvasLength);
        b = Math.clamp(b, 0f, canvasLength);
        if (b - a < 1e-4f) {
            return;
        }
        // 沿弧长按比例取样，保证区间在示意上连续可见。
        int samples = Math.max(4, (int) Math.ceil((b - a) / Math.max(1f, canvasLength) * 48f));
        ImVec2 prev = null;
        for (int i = 0; i <= samples; i++) {
            float t = a + (b - a) * (i / (float) samples);
            ImVec2 p = screenPointAtStation(screen, canvasLength, t);
            if (p == null) {
                continue;
            }
            if (prev != null) {
                drawList.addLine(prev.x, prev.y, p.x, p.y, color, thickness);
            }
            prev = p;
        }
    }

    private static ImVec2 screenPointAtStation(
            List<ImVec2> screen,
            float canvasLength,
            float canvasStation) {
        float clamped = Math.clamp(canvasStation, 0f, Math.max(0.001f, canvasLength));
        // 示意坐标按折线屏上弧长比例映射，与点击回写一致。
        float frac = clamped / Math.max(0.001f, canvasLength);
        return pointAtFractionScreen(screen, frac);
    }

    private static ImVec2 pointAtFractionScreen(List<ImVec2> screen, float fraction) {
        if (screen == null || screen.size() < 2) {
            return null;
        }
        double total = 0.0;
        for (int i = 0; i < screen.size() - 1; i++) {
            ImVec2 a = screen.get(i);
            ImVec2 b = screen.get(i + 1);
            total += Math.hypot(b.x - a.x, b.y - a.y);
        }
        if (total <= 1e-9) {
            return screen.getFirst();
        }
        double target = Math.clamp(fraction, 0f, 1f) * total;
        double acc = 0.0;
        for (int i = 0; i < screen.size() - 1; i++) {
            ImVec2 a = screen.get(i);
            ImVec2 b = screen.get(i + 1);
            double seg = Math.hypot(b.x - a.x, b.y - a.y);
            if (acc + seg >= target - 1e-9) {
                double u = seg <= 1e-9 ? 0.0 : (target - acc) / seg;
                return new ImVec2(
                    (float) (a.x + (b.x - a.x) * u),
                    (float) (a.y + (b.y - a.y) * u));
            }
            acc += seg;
        }
        return screen.getLast();
    }

    static float nearestFractionOnPolyline(List<ImVec2> screen, float mx, float my) {
        return CenterlineSegmentPreviewRenderer.nearestFractionOnPolyline(screen, mx, my);
    }

    private static boolean near(Vec2d a, Vec2d b) {
        return Math.hypot(a.x - b.x, a.y - b.y) < 1e-4;
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

    private record Bounds(double minX, double minY, double maxX, double maxY) {
        float spanX() {
            return (float) (maxX - minX);
        }

        float spanY() {
            return (float) (maxY - minY);
        }
    }
}
