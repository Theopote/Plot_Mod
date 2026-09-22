package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Generate Tab 体量鸟瞰预览：Footprint + 层数编码 + 问题高亮。 */
public final class BuildingMassingPreviewRenderer {
    private static final float MIN_MAP_HEIGHT = 140f;
    private static final float PADDING = 10f;
    private static final float EXTRUSION_SCALE = 1.4f;
    private static final int COLOR_FILL_LOW = 0x668CB4FF;
    private static final int COLOR_FILL_HIGH = 0xCC1A4F99;
    private static final int COLOR_SKIPPED_FILL = 0x55AAAAAA;
    private static final int COLOR_EXTRUSION = 0x88446699;

    private BuildingMassingPreviewRenderer() {
    }

    public record Model(
            List<BuildingFootprint> buildings,
            Set<String> warningBuildingIds,
            Set<String> skippedBuildingIds,
            Set<String> selectedBuildingIds,
            int minFloors,
            int maxFloors,
            String emptyHint) {
    }

    public static void render(String childId, Model model, Consumer<String> onBuildingClicked) {
        float mapWidth = Math.max(160f, ImGui.getContentRegionAvailX());
        float mapHeight = mapHeightForWidth(mapWidth);
        ImGui.beginChild(childId, 0, mapHeight, true, ImGuiWindowFlags.NoScrollbar);

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

        List<BuildingFootprint> buildings = model.buildings();
        if (buildings == null || buildings.isEmpty()) {
            drawCenteredHint(drawList, origin.x, origin.y, width, height, model.emptyHint());
            ImGui.dummy(width, height);
            ImGui.endChild();
            return;
        }

        Bounds bounds = computeBounds(buildings);
        MapViewport viewport = buildViewport(bounds, origin.x, origin.y, width, height);
        Set<String> warnings = model.warningBuildingIds() != null ? model.warningBuildingIds() : Set.of();
        Set<String> skipped = model.skippedBuildingIds() != null ? model.skippedBuildingIds() : Set.of();
        Set<String> selected = model.selectedBuildingIds() != null ? model.selectedBuildingIds() : Set.of();
        int floorSpan = Math.max(1, model.maxFloors() - model.minFloors());

        for (int i = 0; i < buildings.size(); i++) {
            BuildingFootprint building = buildings.get(i);
            if (building == null || building.getOuterPoints().size() < 3) {
                continue;
            }
            boolean isWarning = warnings.contains(building.getId());
            boolean isSkipped = skipped.contains(building.getId());
            boolean isSelected = selected.contains(building.getId());
            float extrusion = extrusionPixels(building.getFloors(), model.minFloors(), model.maxFloors());
            if (!isSkipped && extrusion > 0.5f) {
                drawExtrusion(drawList, building.getOuterPoints(), viewport, extrusion);
            }
            int fillColor = isSkipped
                ? COLOR_SKIPPED_FILL
                : heightFillColor(building.getFloors(), model.minFloors(), floorSpan);
            drawFilledFootprint(drawList, building.getOuterPoints(), viewport, fillColor);
            int borderColor = isWarning
                ? PluginUiColors.WARNING
                : isSelected
                    ? PluginUiColors.ACCENT_BLUE
                    : PluginUiColors.PANEL_BORDER;
            float borderThickness = isSelected || isWarning ? 2.4f : 1.4f;
            drawFootprintOutline(drawList, building.getOuterPoints(), viewport, borderColor, borderThickness);
            if (isWarning) {
                drawWarningBadge(drawList, building, viewport);
            }
            drawFloorLabel(drawList, building, viewport, viewport.scale);
        }

        ImGui.invisibleButton("##massing_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.building.generate.massing_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0) && onBuildingClicked != null) {
            ImVec2 mouse = ImGui.getMousePos();
            String hit = hitTestBuilding(
                buildings,
                toWorldX(mouse.x, viewport),
                toWorldY(mouse.y, viewport));
            if (hit != null) {
                onBuildingClicked.accept(hit);
            }
        }

        ImGui.endChild();
    }

    private static float extrusionPixels(int floors, int minFloors, int maxFloors) {
        int span = Math.max(1, maxFloors - minFloors);
        float normalized = (floors - minFloors) / (float) span;
        return normalized * 14f * EXTRUSION_SCALE;
    }

    private static int heightFillColor(int floors, int minFloors, int floorSpan) {
        float t = (floors - minFloors) / (float) Math.max(1, floorSpan);
        t = Math.clamp(t, 0f, 1f);
        return lerpColor(COLOR_FILL_LOW, COLOR_FILL_HIGH, t);
    }

    private static int lerpColor(int from, int to, float t) {
        int a0 = (from >> 24) & 0xFF;
        int r0 = (from >> 16) & 0xFF;
        int g0 = (from >> 8) & 0xFF;
        int b0 = from & 0xFF;
        int a1 = (to >> 24) & 0xFF;
        int r1 = (to >> 16) & 0xFF;
        int g1 = (to >> 8) & 0xFF;
        int b1 = to & 0xFF;
        int a = (int) (a0 + (a1 - a0) * t);
        int r = (int) (r0 + (r1 - r0) * t);
        int g = (int) (g0 + (g1 - g0) * t);
        int b = (int) (b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void drawExtrusion(
            ImDrawList drawList,
            List<Vec2d> points,
            MapViewport viewport,
            float pixels) {
        ImVec2[] screenPoints = toScreenPoints(points, viewport);
        ImVec2[] extruded = new ImVec2[screenPoints.length];
        for (int i = 0; i < screenPoints.length; i++) {
            extruded[i] = new ImVec2(screenPoints[i].x - pixels * 0.55f, screenPoints[i].y - pixels);
        }
        drawList.addConvexPolyFilled(extruded, extruded.length, COLOR_EXTRUSION);
    }

    private static void drawFilledFootprint(
            ImDrawList drawList,
            List<Vec2d> points,
            MapViewport viewport,
            int color) {
        ImVec2[] screenPoints = toScreenPoints(points, viewport);
        if (screenPoints.length < 3) {
            return;
        }
        drawList.addConvexPolyFilled(screenPoints, screenPoints.length, color);
    }

    private static void drawFootprintOutline(
            ImDrawList drawList,
            List<Vec2d> points,
            MapViewport viewport,
            int color,
            float thickness) {
        int count = points.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            drawList.addLine(
                toScreenX(start.x, viewport),
                toScreenY(start.y, viewport),
                toScreenX(end.x, viewport),
                toScreenY(end.y, viewport),
                color,
                thickness);
        }
    }

    private static void drawFloorLabel(
            ImDrawList drawList,
            BuildingFootprint building,
            MapViewport viewport,
            double scale) {
        if (scale < 2.5) {
            return;
        }
        Vec2d center = centroid(building.getOuterPoints());
        String label = PlotI18n.tr("plugin.building.generate.map_floors", building.getFloors());
        ImVec2 size = ImGui.calcTextSize(label);
        float x = toScreenX(center.x, viewport) - size.x * 0.5f;
        float y = toScreenY(center.y, viewport) - size.y * 0.5f;
        drawList.addText(x, y, ImGui.getColorU32(ImGuiCol.Text), label);
    }

    private static void drawWarningBadge(
            ImDrawList drawList,
            BuildingFootprint building,
            MapViewport viewport) {
        Vec2d center = centroid(building.getOuterPoints());
        float x = toScreenX(center.x, viewport);
        float y = toScreenY(center.y, viewport) - 14f;
        drawList.addText(x - 4f, y - 10f, PluginUiColors.WARNING, "!");
    }

    private static Vec2d centroid(List<Vec2d> points) {
        double x = 0;
        double y = 0;
        for (Vec2d point : points) {
            x += point.x;
            y += point.y;
        }
        int count = Math.max(1, points.size());
        return new Vec2d(x / count, y / count);
    }

    private static ImVec2[] toScreenPoints(List<Vec2d> points, MapViewport viewport) {
        ImVec2[] screenPoints = new ImVec2[points.size()];
        for (int i = 0; i < points.size(); i++) {
            screenPoints[i] = new ImVec2(
                toScreenX(points.get(i).x, viewport),
                toScreenY(points.get(i).y, viewport));
        }
        return screenPoints;
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
            for (Vec2d point : building.getOuterPoints()) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
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
        return new MapViewport(bounds.minX, bounds.minY, scale, offsetX, offsetY);
    }

    private static float mapHeightForWidth(float width) {
        return Math.max(MIN_MAP_HEIGHT, width * 0.55f);
    }

    private static float toScreenX(double worldX, MapViewport viewport) {
        return (float) (viewport.offsetX + (worldX - viewport.minX) * viewport.scale);
    }

    private static float toScreenY(double worldY, MapViewport viewport) {
        return (float) (viewport.offsetY + (worldY - viewport.minY) * viewport.scale);
    }

    private static double toWorldX(float screenX, MapViewport viewport) {
        return viewport.minX + (screenX - viewport.offsetX) / viewport.scale;
    }

    private static double toWorldY(float screenY, MapViewport viewport) {
        return viewport.minY + (screenY - viewport.offsetY) / viewport.scale;
    }

    private static void drawCenteredHint(
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

    private record MapViewport(double minX, double minY, double scale, float offsetX, float offsetY) {
    }
}
