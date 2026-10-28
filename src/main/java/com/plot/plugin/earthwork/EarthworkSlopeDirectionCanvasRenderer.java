package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.grading.GradingPlane;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/**
 * 在画布上绘制坡向指示箭头：
 * 「方向与坡度」跟随显式坡向；「三点定面」由控制点平面梯度推导。
 */
public final class EarthworkSlopeDirectionCanvasRenderer {

    private static final int ARROW_COLOR = 0xFF00C8FF; // amber (ABGR)
    private static final float SHAFT_THICKNESS = 3.0f;
    private static final float OUTLINE_THICKNESS = 1.4f;

    private EarthworkSlopeDirectionCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkProject project,
            String selectedRegionId) {
        if (drawList == null || camera == null || project == null) {
            return;
        }
        if (selectedRegionId == null || selectedRegionId.isBlank()) {
            return;
        }
        GradingRegion region = project.getRegion(selectedRegionId);
        Double directionDegrees = resolveArrowDirectionDegrees(region);
        if (directionDegrees == null) {
            return;
        }
        List<Vec2d> outer = region.getOuterPoints();
        if (outer == null || outer.size() < 3) {
            return;
        }

        Vec2d center = resolveArrowCenter(region, outer);
        if (center == null) {
            return;
        }

        PolygonRegionUtils.RectBounds bounds = EarthworkGeometryUtils.computeBounds(outer);
        double span = Math.min(bounds.width(), bounds.depth());
        double arrowLen = Math.max(span * 0.35, 1.5);

        double radians = Math.toRadians(directionDegrees);
        Vec2d direction = new Vec2d(Math.cos(radians), Math.sin(radians));
        Vec2d tip = center.add(direction.multiply(arrowLen));

        Vec2d screenCenter = camera.worldToScreen(center);
        Vec2d screenTip = camera.worldToScreen(tip);
        float cx = (float) screenCenter.x;
        float cy = (float) screenCenter.y;
        float tx = (float) screenTip.x;
        float ty = (float) screenTip.y;

        drawList.addLine(cx, cy, tx, ty, ARROW_COLOR, SHAFT_THICKNESS);

        double headAlong = arrowLen * 0.28;
        double headSpread = arrowLen * 0.42;
        Vec2d headBase = tip.subtract(direction.multiply(headAlong));
        Vec2d wing = new Vec2d(-direction.y, direction.x);
        Vec2d left = headBase.add(wing.multiply(headSpread));
        Vec2d right = headBase.subtract(wing.multiply(headSpread));

        Vec2d screenLeft = camera.worldToScreen(left);
        Vec2d screenRight = camera.worldToScreen(right);
        drawList.addTriangleFilled(
            tx, ty,
            (float) screenLeft.x, (float) screenLeft.y,
            (float) screenRight.x, (float) screenRight.y,
            ARROW_COLOR);
        drawList.addTriangle(
            tx, ty,
            (float) screenLeft.x, (float) screenLeft.y,
            (float) screenRight.x, (float) screenRight.y,
            PluginUiColors.RING_DARK,
            OUTLINE_THICKNESS);

        float anchorRadius = 4.5f;
        drawList.addCircleFilled(cx, cy, anchorRadius, ARROW_COLOR);
        drawList.addCircle(cx, cy, anchorRadius, PluginUiColors.RING_DARK, 0, OUTLINE_THICKNESS);
    }

    /** 供测试：解析当前区域应显示的上坡方向（度）；无需绘制时返回 null。 */
    static Double resolveArrowDirectionDegrees(GradingRegion region) {
        if (region == null) {
            return null;
        }
        if (region.getSurfaceMode() == GradingSurfaceMode.SINGLE_SLOPE_PLANE) {
            return region.getSlopeDirectionDegrees();
        }
        if (region.getSurfaceMode() != GradingSurfaceMode.THREE_POINT_PLANE) {
            return null;
        }
        GradingPlane plane = GradingSurfaceResolver.solveThreePointPlane(
            region.getThreePointCanvasX(0), region.getThreePointCanvasY(0), region.getThreePointElevation(0),
            region.getThreePointCanvasX(1), region.getThreePointCanvasY(1), region.getThreePointElevation(1),
            region.getThreePointCanvasX(2), region.getThreePointCanvasY(2), region.getThreePointElevation(2));
        if (plane == null) {
            return null;
        }
        return plane.uphillDirectionDegreesOrNull();
    }

    private static Vec2d resolveArrowCenter(GradingRegion region, List<Vec2d> outer) {
        if (region.getSurfaceMode() == GradingSurfaceMode.SINGLE_SLOPE_PLANE) {
            Vec2d center = region.getSlopeAnchorCanvas();
            if (center != null && region.containsCanvasPoint(center)) {
                return center;
            }
        }
        Vec2d centroid = EarthworkGeometryUtils.computeCentroid(outer);
        if (region.containsCanvasPoint(centroid)) {
            return centroid;
        }
        return outer.getFirst();
    }
}
