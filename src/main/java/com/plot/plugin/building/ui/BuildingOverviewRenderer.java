package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** 建筑轮廓缩略图与项目总览图。 */
public final class BuildingOverviewRenderer {
    private static final float MIN_MAP_HEIGHT = 80f;
    private static final float PADDING = 8f;
    private static final float THUMBNAIL_WIDTH = 104f;
    private static final float THUMBNAIL_HEIGHT = 68f;
    private static final float PATH_THICKNESS = 1.8f;
    private static final float SELECTED_PATH_THICKNESS = 2.8f;

    private static final int[] FOOTPRINT_COLORS = {
        0xFF4DA6FF,
        0xFF66CC66,
        0xFFFFB060,
        0xFFCC66FF,
        0xFF66E0E0,
        0xFFFF8080,
    };

    private BuildingOverviewRenderer() {
    }

    public static void renderProjectMap(
            BuildingProject project,
            Collection<String> selectedBuildingIds,
            Consumer<String> onBuildingSelected) {
        float mapWidth = ImGui.getContentRegionAvail().x;
        float mapHeight = mapHeightForWidth(mapWidth);
        ImGui.beginChild("building_overview_map", 0, mapHeight, true);

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

        List<BuildingFootprint> buildings = new ArrayList<>(project.getBuildings().values());
        if (buildings.isEmpty()) {
            renderCenteredHint(drawList, origin.x, origin.y, width, height,
                PlotI18n.tr("plugin.building.no_buildings"));
            ImGui.dummy(width, height);
            ImGui.endChild();
            return;
        }

        Bounds bounds = computeBounds(buildings);
        MapViewport viewport = buildViewport(bounds, origin.x, origin.y, width, height);
        for (int i = 0; i < buildings.size(); i++) {
            BuildingFootprint building = buildings.get(i);
            boolean selected = selectedBuildingIds != null && selectedBuildingIds.contains(building.getId());
            int color = selected ? PluginUiColors.ACCENT_BLUE : footprintColor(i);
            drawFootprint(drawList, building.getOuterPoints(), viewport, color, selected);
        }

        ImGui.invisibleButton("##building_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.building.overview_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0) && onBuildingSelected != null) {
            ImVec2 mouse = ImGui.getMousePos();
            String hit = hitTestBuilding(
                buildings,
                toWorldX(mouse.x, viewport),
                toWorldY(mouse.y, viewport));
            if (hit != null) {
                onBuildingSelected.accept(hit);
            }
        }

        ImGui.endChild();
    }

    public static boolean renderFootprintThumbnail(
            List<Vec2d> points,
            boolean selected,
            int colorIndex) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x = origin.x;
        float y = origin.y;

        drawList.addRectFilled(x, y, x + THUMBNAIL_WIDTH, y + THUMBNAIL_HEIGHT, PluginUiColors.MAP_BG);
        int borderColor = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        drawList.addRect(x, y, x + THUMBNAIL_WIDTH, y + THUMBNAIL_HEIGHT, borderColor, 0f, 0, selected ? 2f : 1f);

        if (points != null && points.size() >= 3) {
            Bounds bounds = computeBoundsFromPoints(points);
            MapViewport viewport = buildViewport(bounds, x, y, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            int color = selected ? PluginUiColors.ACCENT_BLUE : footprintColor(colorIndex);
            drawFootprint(drawList, points, viewport, color, selected);
        }

        ImGui.invisibleButton("##thumb", THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.building.overview_thumbnail_hint"));
        }
        return ImGui.isItemClicked(0);
    }

    private static void drawFootprint(
            ImDrawList drawList,
            List<Vec2d> points,
            MapViewport viewport,
            int color,
            boolean selected) {
        if (points == null || points.size() < 3) {
            return;
        }
        float thickness = selected ? SELECTED_PATH_THICKNESS : PATH_THICKNESS;
        int count = points.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            drawSegment(drawList, start, end, viewport, color, thickness);
        }
    }

    private static void drawSegment(
            ImDrawList drawList,
            Vec2d start,
            Vec2d end,
            MapViewport viewport,
            int color,
            float thickness) {
        drawList.addLine(
            toScreenX(start.x, viewport),
            toScreenY(start.y, viewport),
            toScreenX(end.x, viewport),
            toScreenY(end.y, viewport),
            color,
            thickness);
    }

    private static String hitTestBuilding(List<BuildingFootprint> buildings, double worldX, double worldY) {
        String best = null;
        double bestArea = Double.MAX_VALUE;
        for (BuildingFootprint building : buildings) {
            if (!containsPoint(building.getOuterPoints(), worldX, worldY)) {
                continue;
            }
            double area = Math.abs(building.computeArea());
            if (area < bestArea) {
                bestArea = area;
                best = building.getId();
            }
        }
        return best;
    }

    private static boolean containsPoint(List<Vec2d> polygon, double x, double y) {
        boolean inside = false;
        int count = polygon.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vec2d pi = polygon.get(i);
            Vec2d pj = polygon.get(j);
            boolean intersect = ((pi.y > y) != (pj.y > y))
                && (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y + 1e-12) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static Bounds computeBounds(List<BuildingFootprint> buildings) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (BuildingFootprint building : buildings) {
            Bounds pointBounds = computeBoundsFromPoints(building.getOuterPoints());
            minX = Math.min(minX, pointBounds.minX);
            minY = Math.min(minY, pointBounds.minY);
            maxX = Math.max(maxX, pointBounds.maxX);
            maxY = Math.max(maxY, pointBounds.maxY);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static Bounds computeBoundsFromPoints(List<Vec2d> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static MapViewport buildViewport(Bounds bounds, float x, float y, float width, float height) {
        double spanX = Math.max(bounds.maxX - bounds.minX, 1.0);
        double spanY = Math.max(bounds.maxY - bounds.minY, 1.0);
        float innerWidth = Math.max(1f, width - PADDING * 2f);
        float innerHeight = Math.max(1f, height - PADDING * 2f);
        double scale = Math.min(innerWidth / spanX, innerHeight / spanY);
        float offsetX = x + PADDING + (float) ((innerWidth - spanX * scale) * 0.5);
        float offsetY = y + PADDING + (float) ((innerHeight - spanY * scale) * 0.5);
        return new MapViewport(bounds.minX, bounds.minY, bounds.maxY, scale, offsetX, offsetY);
    }

    private static float mapHeightForWidth(float width) {
        return Math.max(MIN_MAP_HEIGHT, width * 0.35f);
    }

    private static float toScreenX(double worldX, MapViewport viewport) {
        return (float) (viewport.offsetX + (worldX - viewport.minX) * viewport.scale);
    }

    private static float toScreenY(double worldY, MapViewport viewport) {
        return (float) (viewport.offsetY + (viewport.maxY - worldY) * viewport.scale);
    }

    private static double toWorldX(float screenX, MapViewport viewport) {
        return viewport.minX + (screenX - viewport.offsetX) / viewport.scale;
    }

    private static double toWorldY(float screenY, MapViewport viewport) {
        return viewport.maxY - (screenY - viewport.offsetY) / viewport.scale;
    }

    private static int footprintColor(int index) {
        return FOOTPRINT_COLORS[Math.floorMod(index, FOOTPRINT_COLORS.length)];
    }

    private static void renderCenteredHint(
            ImDrawList drawList,
            float x,
            float y,
            float width,
            float height,
            String text) {
        ImVec2 size = ImGui.calcTextSize(text);
        drawList.addText(
            x + (width - size.x) * 0.5f,
            y + (height - size.y) * 0.5f,
            PluginUiColors.HINT_GRAY,
            text);
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
    }

    private record MapViewport(double minX, double minY, double maxY, double scale, float offsetX, float offsetY) {
    }
}
