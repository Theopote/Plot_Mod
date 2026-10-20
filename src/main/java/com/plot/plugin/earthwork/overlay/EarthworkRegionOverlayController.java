package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从项目与选中状态生成画布叠加层条目。 */
public final class EarthworkRegionOverlayController {

    private EarthworkRegionOverlayController() {
    }

    public static List<EarthworkRegionOverlayEntry> snapshot(
            EarthworkProject project,
            String selectedRegionId,
            List<Shape> canvasShapes,
            boolean pickSessionActive,
            boolean overlayEnabled) {
        if (!overlayEnabled || project == null) {
            return List.of();
        }
        List<EarthworkRegionOverlayEntry> entries = new ArrayList<>();
        for (GradingRegion region : project.getRegions().values()) {
            if (region == null) {
                continue;
            }
            List<Vec2d> points = region.getOuterPoints();
            if (points.size() < 3) {
                continue;
            }
            boolean selected = selectedRegionId != null && selectedRegionId.equals(region.getId());
            entries.add(new EarthworkRegionOverlayEntry(
                region.getId(),
                region.getName(),
                List.copyOf(points),
                selected ? EarthworkRegionOverlayState.SELECTED : EarthworkRegionOverlayState.REGISTERED));
        }
        appendCanvasEntries(entries, canvasShapes, pickSessionActive);
        entries.sort(Comparator.comparingInt(entry -> entry.state().renderPriority()));
        return entries;
    }

    private static void appendCanvasEntries(
            List<EarthworkRegionOverlayEntry> entries,
            List<Shape> canvasShapes,
            boolean pickSessionActive) {
        if (canvasShapes == null || canvasShapes.isEmpty()) {
            return;
        }
        List<Shape> adoptable = EarthworkGeometryUtils.findAdoptableRegions(canvasShapes);
        for (Shape shape : adoptable) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() < 3) {
                continue;
            }
            entries.add(new EarthworkRegionOverlayEntry(
                "canvas:" + shape.getId(),
                shape.getId(),
                List.copyOf(points),
                pickSessionActive
                    ? EarthworkRegionOverlayState.PICK_ACTIVE
                    : EarthworkRegionOverlayState.CANDIDATE));
        }
    }

    /** 命中测试：返回面积最小的已认领区域 id。 */
    public static String hitTestAdopted(List<EarthworkRegionOverlayEntry> entries, double worldX, double worldY) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        String best = null;
        double bestArea = Double.MAX_VALUE;
        for (EarthworkRegionOverlayEntry entry : entries) {
            if (entry.state() != EarthworkRegionOverlayState.REGISTERED
                && entry.state() != EarthworkRegionOverlayState.SELECTED) {
                continue;
            }
            if (!containsPoint(entry.outerPoints(), worldX, worldY)) {
                continue;
            }
            double area = Math.abs(GradingRegion.signedArea(entry.outerPoints()));
            if (area < bestArea) {
                bestArea = area;
                best = entry.regionId();
            }
        }
        return best;
    }

    private static boolean containsPoint(List<Vec2d> polygon, double x, double y) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
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
}
