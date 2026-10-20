package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.manager.EarthworkPreviewIdentity;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.ui.EarthworkOverviewRenderer;

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
        int colorIndex = 0;
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
                points,
                region.getHoles(),
                selected ? EarthworkRegionOverlayState.SELECTED : EarthworkRegionOverlayState.REGISTERED,
                geometryVersion(region),
                colorIndex++));
        }
        appendCanvasEntries(entries, canvasShapes, pickSessionActive, colorIndex);
        entries.sort(Comparator.comparingInt(entry -> entry.state().renderPriority()));
        return entries;
    }

    private static void appendCanvasEntries(
            List<EarthworkRegionOverlayEntry> entries,
            List<Shape> canvasShapes,
            boolean pickSessionActive,
            int colorIndexStart) {
        if (canvasShapes == null || canvasShapes.isEmpty()) {
            return;
        }
        List<Shape> adoptable = EarthworkGeometryUtils.findAdoptableRegions(canvasShapes);
        int colorIndex = colorIndexStart;
        for (Shape shape : adoptable) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() < 3) {
                continue;
            }
            entries.add(new EarthworkRegionOverlayEntry(
                "canvas:" + shape.getId(),
                shape.getId(),
                points,
                List.of(),
                pickSessionActive
                    ? EarthworkRegionOverlayState.PICK_ACTIVE
                    : EarthworkRegionOverlayState.CANDIDATE,
                EarthworkPreviewIdentity.normalizedOutlineFingerprint(points),
                colorIndex++));
        }
    }

    /**
     * 命中测试：在外轮廓内且不在孔洞内；重叠时选净面积最小的已认领区域。
     */
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
            if (!entry.containsWorldPoint(worldX, worldY)) {
                continue;
            }
            double area = entry.absoluteArea();
            if (area < bestArea) {
                bestArea = area;
                best = entry.regionId();
            }
        }
        return best;
    }

    /** 与总览缩略图共用的稳定色板索引颜色。 */
    public static int stableColor(int colorIndex) {
        return EarthworkOverviewRenderer.regionColor(colorIndex);
    }

    private static long geometryVersion(GradingRegion region) {
        long hash = EarthworkPreviewIdentity.normalizedOutlineFingerprint(region.getOuterPoints());
        for (List<Vec2d> hole : region.getHoles()) {
            hash = 31L * hash + EarthworkPreviewIdentity.normalizedOutlineFingerprint(hole);
        }
        return hash;
    }
}
