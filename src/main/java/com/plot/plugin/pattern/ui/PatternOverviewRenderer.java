package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
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

/** 铺装区域概览缩略图：项目总览图 + 单个区域预览。 */
public final class PatternOverviewRenderer {
    private static final float MIN_MAP_HEIGHT = 80f;
    private static final float DEFAULT_CANVAS_WIDTH = 800f;
    private static final float DEFAULT_CANVAS_HEIGHT = 600f;
    private static final float PADDING = 8f;
    private static final float THUMBNAIL_WIDTH = 104f;
    private static final float THUMBNAIL_HEIGHT = 68f;
    private static final float OUTLINE_THICKNESS = 2f;
    private static final float SELECTED_OUTLINE_THICKNESS = 3f;

    private static final int[] REGION_COLORS = {
        0xFF4DA6FF,
        0xFF66CC66,
        0xFFFFB060,
        0xFFCC66FF,
        0xFF66E0E0,
        0xFFFF8080,
    };

    private PatternOverviewRenderer() {
    }

    public static void renderProjectMap(
            PatternProject project,
            Collection<String> selectedFootprintIds,
            Consumer<String> onFootprintSelected) {
        float mapWidth = ImGui.getContentRegionAvail().x;
        float mapHeight = mapHeightForWidth(mapWidth);
        ImGui.beginChild("pattern_overview_map", 0, mapHeight, true);

        float width = ImGui.getContentRegionAvail().x;
        float height = ImGui.getContentRegionAvail().y;
        if (width < 1f || height < 1f) {
            ImGui.endChild();
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, PluginUiColors.MAP_BG);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + height, PluginUiColors.PANEL_BORDER);

        List<PatternFootprint> footprints = new ArrayList<>(project.getFootprints().values());
        if (footprints.isEmpty()) {
            renderCenteredHint(drawList, origin.x, origin.y, width, height,
                PlotI18n.tr("plugin.pattern.no_footprints"));
            ImGui.dummy(width, height);
            ImGui.endChild();
            return;
        }

        Bounds bounds = computeBounds(footprints);
        MapViewport viewport = buildViewport(bounds, origin.x, origin.y, width, height);
        for (int i = 0; i < footprints.size(); i++) {
            PatternFootprint footprint = footprints.get(i);
            boolean selected = selectedFootprintIds != null && selectedFootprintIds.contains(footprint.getId());
            int color = selected ? PluginUiColors.ACCENT_BLUE : regionColor(i);
            drawFootprint(drawList, footprint, viewport, color, selected);
        }

        ImGui.invisibleButton("##pattern_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.pattern.overview_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0) && onFootprintSelected != null) {
            ImVec2 mouse = ImGui.getMousePos();
            String hit = hitTestFootprint(
                footprints,
                toWorldX(mouse.x, viewport),
                toWorldY(mouse.y, viewport));
            if (hit != null) {
                onFootprintSelected.accept(hit);
            }
        }

        ImGui.endChild();
    }

    /**
     * 绘制单个区域缩略图；点击返回 true。
     */
    public static boolean renderFootprintThumbnail(PatternFootprint footprint, boolean selected) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x = origin.x;
        float y = origin.y;

        drawList.addRectFilled(x, y, x + THUMBNAIL_WIDTH, y + THUMBNAIL_HEIGHT, PluginUiColors.MAP_BG);
        int borderColor = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        drawList.addRect(x, y, x + THUMBNAIL_WIDTH, y + THUMBNAIL_HEIGHT, borderColor, 0f, 0, selected ? 2f : 1f);

        Bounds bounds = computeBounds(List.of(footprint));
        MapViewport viewport = buildViewport(bounds, x, y, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        int color = selected ? PluginUiColors.ACCENT_BLUE : 0xFF9EC9FF;
        drawFootprint(drawList, footprint, viewport, color, selected);

        ImGui.invisibleButton("##pattern_thumb_" + footprint.getId(), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.pattern.overview_thumbnail_hint"));
        }
        return ImGui.isItemClicked(0);
    }

    private static void drawFootprint(
            ImDrawList drawList,
            PatternFootprint footprint,
            MapViewport viewport,
            int color,
            boolean selected) {
        drawRing(drawList, PatternOverviewLayoutCache.outerPoints(footprint), viewport, color, selected);
        for (List<Vec2d> hole : PatternOverviewLayoutCache.holes(footprint)) {
            drawRing(drawList, hole, viewport, PluginUiColors.RING_DARK, false);
        }
    }

    private static void drawRing(
            ImDrawList drawList,
            List<Vec2d> points,
            MapViewport viewport,
            int color,
            boolean selected) {
        if (points == null || points.size() < 2) {
            return;
        }
        float thickness = selected ? SELECTED_OUTLINE_THICKNESS : OUTLINE_THICKNESS;
        for (int i = 0; i < points.size(); i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % points.size());
            drawList.addLine(
                toScreenX(a.x, viewport),
                toScreenY(a.y, viewport),
                toScreenX(b.x, viewport),
                toScreenY(b.y, viewport),
                color,
                thickness);
        }
    }

    private static int regionColor(int index) {
        return REGION_COLORS[Math.floorMod(index, REGION_COLORS.length)];
    }

    private static Bounds computeBounds(List<PatternFootprint> footprints) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (PatternFootprint footprint : footprints) {
            PolygonRegionUtils.RectBounds bounds = PatternOverviewLayoutCache.bounds(footprint);
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minZ());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxZ());
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

        return new Bounds(minX, minY, maxX, maxY, spanX, spanY);
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
        return new MapViewport(bounds, scale, offsetX, offsetY, originX, originY);
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

    static String hitTestFootprint(List<PatternFootprint> footprints, double wx, double wy) {
        Vec2d point = new Vec2d(wx, wy);
        String hit = null;
        double smallestArea = Double.MAX_VALUE;
        for (PatternFootprint footprint : footprints) {
            List<Vec2d> outer = PatternOverviewLayoutCache.outerPoints(footprint);
            if (!PolygonRegionUtils.containsPoint(outer, PatternOverviewLayoutCache.holes(footprint), point)) {
                continue;
            }
            double area = Math.abs(PolygonRegionUtils.signedAreaOfRing(outer));
            if (area < smallestArea) {
                smallestArea = area;
                hit = footprint.getId();
            }
        }
        return hit;
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
            float originY) {
    }

    private record Bounds(
            double minX,
            double minY,
            double maxX,
            double maxY,
            double spanX,
            double spanY) {
    }
}
