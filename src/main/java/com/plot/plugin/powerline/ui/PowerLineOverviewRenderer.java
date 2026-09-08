package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasAccess;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** 电力线路概览缩略图：项目总览图 + 单条线路预览。 */
public final class PowerLineOverviewRenderer {
    private static final float MIN_MAP_HEIGHT = 80f;
    private static final float DEFAULT_CANVAS_WIDTH = 800f;
    private static final float DEFAULT_CANVAS_HEIGHT = 600f;
    private static final float PADDING = 8f;
    private static final float THUMBNAIL_WIDTH = 104f;
    private static final float THUMBNAIL_HEIGHT = 68f;
    private static final float PATH_THICKNESS = 2f;
    private static final float SELECTED_PATH_THICKNESS = 3f;
    private static final float POLE_RADIUS = 3f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_SELECTED_RING = 0xFF4DA6FF;
    private static final int[] LINE_COLORS = {
        0xFF4DA6FF,
        0xFF66CC66,
        0xFFFFB060,
        0xFFCC66FF,
        0xFF66E0E0,
        0xFFFF8080,
    };

    private PowerLineOverviewRenderer() {
    }

    public static void renderProjectMap(
            PowerLineProject project,
            Collection<String> selectedLineIds,
            Consumer<String> onLineSelected) {
        ImGui.text(PlotI18n.tr("plugin.powerline.overview_map"));
        float mapWidth = ImGui.getContentRegionAvail().x;
        float mapHeight = mapHeightForWidth(mapWidth);
        ImGui.beginChild("powerline_overview_map", 0, mapHeight, true);

        float width = ImGui.getContentRegionAvail().x;
        float height = ImGui.getContentRegionAvail().y;
        if (width < 1f || height < 1f) {
            ImGui.endChild();
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, COLOR_BG);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + height, COLOR_BORDER);

        List<PowerLineFootprint> lines = new ArrayList<>(project.getLines().values());
        if (lines.isEmpty()) {
            renderCenteredHint(drawList, origin.x, origin.y, width, height,
                PlotI18n.tr("plugin.powerline.no_lines"));
            ImGui.dummy(width, height);
            ImGui.endChild();
            return;
        }

        Bounds bounds = computeBounds(lines);
        MapViewport viewport = buildViewport(bounds, origin.x, origin.y, width, height);
        for (int i = 0; i < lines.size(); i++) {
            PowerLineFootprint line = lines.get(i);
            boolean selected = selectedLineIds != null && selectedLineIds.contains(line.getId());
            int color = selected ? COLOR_SELECTED_RING : lineColor(i);
            drawLinePath(drawList, line, viewport, color, selected);
            drawPoles(drawList, line, viewport, selected);
        }

        ImGui.invisibleButton("##powerline_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.overview_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0) && onLineSelected != null) {
            ImVec2 mouse = ImGui.getMousePos();
            String hit = hitTestLine(lines, toWorldX(mouse.x, viewport), toWorldY(mouse.y, viewport), viewport.hitThreshold());
            if (hit != null) {
                onLineSelected.accept(hit);
            }
        }

        ImGui.endChild();
    }

    /**
     * 绘制单条线路缩略图；点击返回 true。
     */
    public static boolean renderLineThumbnail(PowerLineFootprint line, boolean selected) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x = origin.x;
        float y = origin.y;

        drawList.addRectFilled(x, y, x + THUMBNAIL_WIDTH, y + THUMBNAIL_HEIGHT, COLOR_BG);
        int borderColor = selected ? COLOR_SELECTED_RING : COLOR_BORDER;
        drawList.addRect(x, y, x + THUMBNAIL_WIDTH, y + THUMBNAIL_HEIGHT, borderColor, 0f, 0, selected ? 2f : 1f);

        Bounds bounds = computeBounds(List.of(line));
        MapViewport viewport = buildViewport(bounds, x, y, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        int color = selected ? COLOR_SELECTED_RING : 0xFF9EC9FF;
        drawLinePath(drawList, line, viewport, color, selected);
        drawPoles(drawList, line, viewport, selected);

        ImGui.invisibleButton("##thumb", THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.overview_thumbnail_hint"));
        }
        return ImGui.isItemClicked(0);
    }

    public static float thumbnailWidth() {
        return THUMBNAIL_WIDTH;
    }

    public static float thumbnailHeight() {
        return THUMBNAIL_HEIGHT;
    }

    private static void drawLinePath(
            ImDrawList drawList,
            PowerLineFootprint line,
            MapViewport viewport,
            int color,
            boolean selected) {
        List<Vec2d> points = line.getPathPoints();
        if (points.size() < 2) {
            return;
        }
        float thickness = selected ? SELECTED_PATH_THICKNESS : PATH_THICKNESS;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            drawList.addLine(
                toScreenX(a.x, viewport),
                toScreenY(a.y, viewport),
                toScreenX(b.x, viewport),
                toScreenY(b.y, viewport),
                color,
                thickness);
        }
    }

    private static void drawPoles(
            ImDrawList drawList,
            PowerLineFootprint line,
            MapViewport viewport,
            boolean selected) {
        int color = selected ? COLOR_SELECTED_RING : 0xFFE0E0E0;
        for (PowerPoleSite site : PowerPoleLayoutUtils.computePoleSites(line)) {
            Vec2d pole = site.getPlanPosition();
            float sx = toScreenX(pole.x, viewport);
            float sy = toScreenY(pole.y, viewport);
            drawList.addCircleFilled(sx, sy, POLE_RADIUS, color);
            drawList.addCircle(sx, sy, POLE_RADIUS + 0.5f, PluginUiColors.RING_DARK, 10, 1f);
        }
    }

    private static int lineColor(int index) {
        return LINE_COLORS[Math.floorMod(index, LINE_COLORS.length)];
    }

    private static Bounds computeBounds(List<PowerLineFootprint> lines) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (PowerLineFootprint line : lines) {
            for (Vec2d point : line.getPathPoints()) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
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

    static float mapHeightForWidth(float width) {
        if (width < 1f) {
            return MIN_MAP_HEIGHT;
        }
        return Math.max(MIN_MAP_HEIGHT, width / canvasAspectRatio());
    }

    private static float canvasAspectRatio() {
        Canvas canvas = CanvasAccess.get();
        if (canvas != null) {
            int canvasWidth = canvas.getWidth();
            int canvasHeight = canvas.getHeight();
            if (canvasWidth > 0 && canvasHeight > 0) {
                return (float) canvasWidth / canvasHeight;
            }
        }
        return DEFAULT_CANVAS_WIDTH / DEFAULT_CANVAS_HEIGHT;
    }

    private static MapViewport buildViewport(
            Bounds bounds,
            float originX,
            float originY,
            float width,
            float height) {
        float innerWidth = width - PADDING * 2f;
        float innerHeight = height - PADDING * 2f;
        float scale = (float) Math.min(innerWidth / bounds.spanX, innerHeight / bounds.spanY);
        float contentWidth = (float) (bounds.spanX * scale);
        float contentHeight = (float) (bounds.spanY * scale);
        float offsetX = (innerWidth - contentWidth) * 0.5f;
        float offsetY = (innerHeight - contentHeight) * 0.5f;
        return new MapViewport(bounds, scale, offsetX, offsetY, originX, originY, bounds.hitThreshold());
    }

    private static float toScreenX(double worldX, MapViewport viewport) {
        return viewport.originX()
            + PADDING
            + viewport.offsetX()
            + (float) ((worldX - viewport.bounds().minX) * viewport.scale());
    }

    private static float toScreenY(double worldY, MapViewport viewport) {
        return viewport.originY()
            + PADDING
            + viewport.offsetY()
            + (float) ((worldY - viewport.bounds().minY) * viewport.scale());
    }

    private static double toWorldX(float screenX, MapViewport viewport) {
        if (viewport.scale() <= 0f) {
            return viewport.bounds().minX;
        }
        float inner = screenX - viewport.originX() - PADDING - viewport.offsetX();
        return viewport.bounds().minX + inner / viewport.scale();
    }

    private static double toWorldY(float screenY, MapViewport viewport) {
        if (viewport.scale() <= 0f) {
            return viewport.bounds().minY;
        }
        float inner = screenY - viewport.originY() - PADDING - viewport.offsetY();
        return viewport.bounds().minY + inner / viewport.scale();
    }

    static String hitTestLine(List<PowerLineFootprint> lines, double wx, double wy, double threshold) {
        String closestId = null;
        double closestDist = threshold;
        for (PowerLineFootprint line : lines) {
            List<Vec2d> points = line.getPathPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                double dist = distancePointToSegment(wx, wy, points.get(i), points.get(i + 1));
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = line.getId();
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

    private static void renderCenteredHint(
            ImDrawList drawList, float x, float y, float w, float h, String text) {
        float textW = ImGui.calcTextSize(text).x;
        float textX = x + (w - textW) * 0.5f;
        float textY = y + (h - ImGui.getTextLineHeight()) * 0.5f;
        drawList.addText(textX, textY, PluginUiColors.LEGEND_DARK, text);
    }

    private record MapViewport(
            Bounds bounds,
            float scale,
            float offsetX,
            float offsetY,
            float originX,
            float originY,
            double hitThreshold) {
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
