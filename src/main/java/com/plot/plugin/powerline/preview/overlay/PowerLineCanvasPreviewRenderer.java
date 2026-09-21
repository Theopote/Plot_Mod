package com.plot.plugin.powerline.preview.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;

/** 电力线路画布 Build Preview 叠加层渲染。 */
public final class PowerLineCanvasPreviewRenderer {
    private static final float MARKER_RADIUS = 5f;
    private static final float MARKER_RING = 1.4f;
    private static final float ORIENTATION_LENGTH = 9f;
    private static final float HIT_RADIUS = 8f;
    private static final float SPAN_PATH_THICKNESS = 1.5f;
    private static final float DASH_ON = 6f;
    private static final float DASH_OFF = 4f;

    private static final int SPAN_PATH_COLOR = ImColor.rgba(77, 166, 255, 150);
    private static final int AUTO_LAYOUT_FILL = ImColor.rgba(200, 200, 200, 210);
    private static final int AUTO_LAYOUT_RING = ImColor.rgba(40, 40, 40, 220);
    private static final int CORNER_FILL = ImColor.rgba(255, 200, 96, 230);
    private static final int ENDPOINT_FILL = ImColor.rgba(180, 180, 180, 230);
    private static final int TERRAIN_FILL = ImColor.rgba(255, 128, 64, 235);
    private static final int USER_FILL = PluginUiColors.ACCENT_BLUE;
    private static final int HOVER_RING = ImColor.rgba(255, 255, 255, 240);

    public record RenderResult(TowerPreviewMarker hoveredMarker, String clickedPoleSiteId) {
        public static RenderResult empty() {
            return new RenderResult(null, null);
        }
    }

    private PowerLineCanvasPreviewRenderer() {
    }

    public static RenderResult render(
            ImDrawList drawList,
            CanvasCamera camera,
            PowerLineCanvasPreviewOverlay overlay,
            String selectedPoleSiteId) {
        if (drawList == null || camera == null || overlay == null || overlay.markers().isEmpty()) {
            return RenderResult.empty();
        }

        renderSpanPath(drawList, camera, overlay.spanPathPoints(), overlay.closedLoop());

        float zoom = camera.getZoom();
        boolean showOrientation = zoom >= 0.55f;
        boolean fullDetail = zoom >= 0.85f;

        ImVec2 mouse = ImGui.getMousePos();
        TowerPreviewMarker hovered = null;
        double bestDist = HIT_RADIUS;
        String clickedId = null;

        for (TowerPreviewMarker marker : overlay.markers()) {
            if (!shouldRenderMarker(marker, zoom)) {
                continue;
            }
            Vec2d screen = camera.worldToScreen(marker.position());
            float x = (float) screen.x;
            float y = (float) screen.y;
            boolean selected = marker.poleSiteId() != null && marker.poleSiteId().equals(selectedPoleSiteId);
            drawMarker(drawList, marker, x, y, selected, showOrientation, fullDetail);
            double dist = Math.hypot(mouse.x - x, mouse.y - y);
            if (dist <= bestDist) {
                bestDist = dist;
                hovered = marker;
            }
        }

        if (hovered != null) {
            ImGui.beginTooltip();
            ImGui.textUnformatted(TowerPreviewMarkerTooltip.format(hovered));
            ImGui.endTooltip();
            if (ImGui.isMouseClicked(0)) {
                clickedId = hovered.poleSiteId();
            }
        }

        return new RenderResult(hovered, clickedId);
    }

    private static boolean shouldRenderMarker(TowerPreviewMarker marker, float zoom) {
        if (zoom >= 0.35f) {
            return true;
        }
        return marker.source() == MarkerSource.CORNER
            || marker.source() == MarkerSource.ENDPOINT
            || marker.source() == MarkerSource.TERRAIN_AUTO_INSERT
            || marker.source() == MarkerSource.USER_OVERRIDE
            || marker.source() == MarkerSource.STANDALONE;
    }

    private static void renderSpanPath(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            boolean closedLoop) {
        if (points == null || points.size() < 2) {
            return;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            drawDashedSegment(drawList, camera, points.get(i), points.get(i + 1));
        }
        if (closedLoop && points.size() >= 3) {
            drawDashedSegment(drawList, camera, points.getLast(), points.getFirst());
        }
    }

    private static void drawDashedSegment(
            ImDrawList drawList,
            CanvasCamera camera,
            Vec2d a,
            Vec2d b) {
        Vec2d screenA = camera.worldToScreen(a);
        Vec2d screenB = camera.worldToScreen(b);
        float x1 = (float) screenA.x;
        float y1 = (float) screenA.y;
        float x2 = (float) screenB.x;
        float y2 = (float) screenB.y;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float cursor = 0f;
        while (cursor < length) {
            float end = Math.min(cursor + DASH_ON, length);
            drawList.addLine(
                x1 + ux * cursor,
                y1 + uy * cursor,
                x1 + ux * end,
                y1 + uy * end,
                SPAN_PATH_COLOR,
                SPAN_PATH_THICKNESS);
            cursor += DASH_ON + DASH_OFF;
        }
    }

    private static void drawMarker(
            ImDrawList drawList,
            TowerPreviewMarker marker,
            float x,
            float y,
            boolean selected,
            boolean showOrientation,
            boolean fullDetail) {
        int fill = markerFill(marker.source());
        int ring = selected ? HOVER_RING : AUTO_LAYOUT_RING;
        switch (marker.source()) {
            case CORNER -> drawDiamond(drawList, x, y, MARKER_RADIUS + 0.5f, fill, ring);
            case ENDPOINT -> drawSquare(drawList, x, y, MARKER_RADIUS + 1f, fill, ring);
            case TERRAIN_AUTO_INSERT -> drawPlus(drawList, x, y, MARKER_RADIUS + 1.5f, TERRAIN_FILL, ring);
            case USER_OVERRIDE -> {
                drawList.addCircleFilled(x, y, MARKER_RADIUS + 1f, USER_FILL);
                drawList.addCircle(x, y, MARKER_RADIUS + 1.8f, ring, 12, MARKER_RING);
            }
            default -> {
                drawList.addCircleFilled(x, y, MARKER_RADIUS, fill);
                drawList.addCircle(x, y, MARKER_RADIUS + 0.8f, ring, 12, MARKER_RING);
            }
        }
        if (showOrientation && fullDetail) {
            drawOrientationTick(drawList, marker.crossarmAxis(), x, y);
        }
    }

    private static int markerFill(MarkerSource source) {
        return switch (source) {
            case CORNER -> CORNER_FILL;
            case ENDPOINT -> ENDPOINT_FILL;
            case TERRAIN_AUTO_INSERT -> TERRAIN_FILL;
            case USER_OVERRIDE -> USER_FILL;
            default -> AUTO_LAYOUT_FILL;
        };
    }

    private static void drawOrientationTick(
            ImDrawList drawList,
            Vec2d axis,
            float x,
            float y) {
        if (axis == null || axis.lengthSquared() < 1e-12) {
            return;
        }
        Vec2d normal = axis.normalize();
        float dx = (float) normal.x * ORIENTATION_LENGTH;
        float dy = (float) normal.y * ORIENTATION_LENGTH;
        drawList.addLine(x - dx, y - dy, x + dx, y + dy, AUTO_LAYOUT_RING, 1.2f);
    }

    private static void drawDiamond(
            ImDrawList drawList,
            float x,
            float y,
            float radius,
            int fill,
            int ring) {
        drawList.addQuadFilled(
            x, y - radius,
            x + radius, y,
            x, y + radius,
            x - radius, y,
            fill);
        drawList.addQuad(
            x, y - radius,
            x + radius, y,
            x, y + radius,
            x - radius, y,
            ring,
            MARKER_RING);
    }

    private static void drawSquare(
            ImDrawList drawList,
            float x,
            float y,
            float half,
            int fill,
            int ring) {
        drawList.addRectFilled(x - half, y - half, x + half, y + half, fill, 1f);
        drawList.addRect(x - half, y - half, x + half, y + half, ring, 1f, 0, MARKER_RING);
    }

    private static void drawPlus(
            ImDrawList drawList,
            float x,
            float y,
            float radius,
            int color,
            int ring) {
        drawList.addLine(x - radius, y, x + radius, y, color, 1.6f);
        drawList.addLine(x, y - radius, x, y + radius, color, 1.6f);
        drawList.addCircle(x, y, radius + 0.5f, ring, 12, 1f);
    }
}
