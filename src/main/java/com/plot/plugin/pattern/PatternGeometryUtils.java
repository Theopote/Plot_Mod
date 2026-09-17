package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.core.geometry.shapes.AnnotationShape;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.EllipticalArcShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.TextShape;
import com.plot.core.model.Shape;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 铺装区域几何工具（认领与坐标转换）。
 */
public final class PatternGeometryUtils {
    /** 最小认领面积（画布 m²）。 */
    public static final double MIN_ADOPT_AREA_SQ = 0.25;
    private static final double CLOSE_EPSILON = 1e-6;

    private PatternGeometryUtils() {
    }

    public static List<Shape> findAdoptableRegions(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return List.of();
        }
        List<Shape> regions = new ArrayList<>();
        for (Shape shape : shapes) {
            if (isAdoptableRegion(shape)) {
                regions.add(shape);
            }
        }
        return regions;
    }

    public static boolean isAdoptableRegion(Shape shape) {
        List<Vec2d> points = extractRegionPoints(shape);
        return points.size() >= 3 && hasMeaningfulArea(points);
    }

    /**
     * 从画布图形提取铺装区域外轮廓；开放折线/自由路径会自动按首尾相连作为封闭区域。
     */
    public static List<Vec2d> extractRegionPoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        return PolygonRegionUtils.normalizeRegionOutline(extractRawBoundaryPoints(shape));
    }

    public static List<Vec2d> extractRawBoundaryPoints(Shape shape) {
        if (shape == null || isExcludedRegionShape(shape)) {
            return List.of();
        }
        switch (shape) {
            case PolylineShape polyline -> {
                return PolygonRegionUtils.copyPoints(polyline.getPoints());
            }
            case Polygon polygon -> {
                return PolygonRegionUtils.copyPoints(polygon.getPoints());
            }
            case FreeDrawPath freeDraw -> {
                return PolygonRegionUtils.copyPoints(freeDraw.getPoints());
            }
            case BezierCurveShape bezier -> {
                List<Vec2d> curvePoints = bezier.getCurvePoints();
                return curvePoints != null ? PolygonRegionUtils.copyPoints(curvePoints) : List.of();
            }
            default -> {
            }
        }
        if (shape instanceof RectangleShape
            || shape instanceof CircleShape
            || shape instanceof EllipseShape) {
            return PolygonRegionUtils.copyPoints(shape.getPoints());
        }
        if (shape instanceof ArcShape || shape instanceof EllipticalArcShape) {
            return List.of();
        }
        try {
            List<Vec2d> points = shape.getPoints();
            if (points != null && points.size() >= 3 && isClosedPointLoop(points)) {
                return PolygonRegionUtils.copyPoints(points);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    public static boolean overlapsExistingFootprint(List<Vec2d> candidatePoints, PatternProject project) {
        if (candidatePoints == null || candidatePoints.size() < 3 || project == null) {
            return false;
        }
        PolygonRegionUtils.RectBounds candidateBounds = PolygonRegionUtils.computeBounds(candidatePoints);
        for (PatternFootprint existing : project.getFootprints().values()) {
            List<Vec2d> existingPoints = existing.getOuterPoints();
            if (existingPoints.size() < 3) {
                continue;
            }
            List<List<Vec2d>> existingHoles = existing.getHoles();
            PolygonRegionUtils.RectBounds existingBounds =
                PolygonRegionUtils.computeBounds(existingPoints, existingHoles);
            if (!boundsOverlap(candidateBounds, existingBounds)) {
                continue;
            }
            if (ringsOverlap(candidatePoints, List.of(), existingPoints, existingHoles)) {
                return true;
            }
        }
        return false;
    }

    private static boolean boundsOverlap(
            PolygonRegionUtils.RectBounds a,
            PolygonRegionUtils.RectBounds b) {
        return a.minX() <= b.maxX()
            && a.maxX() >= b.minX()
            && a.minZ() <= b.maxZ()
            && a.maxZ() >= b.minZ();
    }

    private static boolean ringsOverlap(
            List<Vec2d> outerA,
            List<List<Vec2d>> holesA,
            List<Vec2d> outerB,
            List<List<Vec2d>> holesB) {
        for (Vec2d point : outerA) {
            if (PolygonRegionUtils.containsPoint(outerB, holesB, point)) {
                return true;
            }
        }
        for (Vec2d point : outerB) {
            if (PolygonRegionUtils.containsPoint(outerA, holesA, point)) {
                return true;
            }
        }
        for (Vec2d cell : PolygonRegionUtils.collectFootprintCellCenters(outerA, holesA)) {
            if (PolygonRegionUtils.containsPoint(outerB, holesB, cell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedRegionShape(Shape shape) {
        return shape instanceof TextShape
            || shape instanceof AnnotationShape
            || shape instanceof LineShape;
    }

    static boolean isClosedPointLoop(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return false;
        }
        Vec2d first = points.getFirst();
        Vec2d last = points.getLast();
        return first != null && last != null && first.distance(last) <= CLOSE_EPSILON;
    }

    private static boolean hasMeaningfulArea(List<Vec2d> points) {
        return Math.abs(PolygonRegionUtils.signedAreaOfRing(points)) >= MIN_ADOPT_AREA_SQ;
    }

    /** 孔洞添加校验结果。 */
    public enum HoleAddIssue {
        NONE(""),
        NO_VALID_SELECTION("plugin.pattern.geometry_no_valid_selection"),
        OUTSIDE_OUTER("plugin.pattern.geometry_hole_outside_outer"),
        TOO_SMALL("plugin.pattern.geometry_hole_too_small"),
        TOO_LARGE("plugin.pattern.geometry_hole_too_large");

        private final String statusKey;

        HoleAddIssue(String statusKey) {
            this.statusKey = statusKey;
        }

        public String statusKey() {
            return statusKey;
        }
    }

    public static HoleAddIssue validateHoleForFootprint(
            List<Vec2d> outerPoints,
            List<List<Vec2d>> existingHoles,
            List<Vec2d> holePoints) {
        if (outerPoints == null || outerPoints.size() < 3) {
            return HoleAddIssue.NO_VALID_SELECTION;
        }
        if (holePoints == null || holePoints.size() < 3) {
            return HoleAddIssue.NO_VALID_SELECTION;
        }
        if (!hasMeaningfulArea(holePoints)) {
            return HoleAddIssue.TOO_SMALL;
        }
        double outerArea = Math.abs(PolygonRegionUtils.signedAreaOfRing(outerPoints));
        double holeArea = Math.abs(PolygonRegionUtils.signedAreaOfRing(holePoints));
        if (holeArea >= outerArea * 0.95) {
            return HoleAddIssue.TOO_LARGE;
        }
        List<List<Vec2d>> holes = existingHoles != null ? existingHoles : List.of();
        for (Vec2d point : holePoints) {
            if (!PolygonRegionUtils.containsPoint(outerPoints, holes, point)) {
                return HoleAddIssue.OUTSIDE_OUTER;
            }
        }
        return HoleAddIssue.NONE;
    }

    /**
     * 认领结果：外环 + 自动识别的孔洞。
     */
    public record AdoptedRegionGroup(List<Vec2d> outerPoints, List<List<Vec2d>> holes) {
        public AdoptedRegionGroup {
            outerPoints = PolygonRegionUtils.copyPoints(outerPoints);
            List<List<Vec2d>> copiedHoles = new ArrayList<>();
            if (holes != null) {
                for (List<Vec2d> hole : holes) {
                    if (hole != null && hole.size() >= 3) {
                        copiedHoles.add(PolygonRegionUtils.copyPoints(hole));
                    }
                }
            }
            holes = copiedHoles;
        }
    }

    /**
     * 将多个闭合图形分组：完全落在外环内的较小区域作为孔洞，其余作为独立铺装外环。
     * 孔洞挂到面积最小的合法外环上。
     */
    public static List<AdoptedRegionGroup> groupAdoptableRegionsWithHoles(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return List.of();
        }
        List<RegionOutline> outlines = new ArrayList<>();
        for (Shape shape : shapes) {
            List<Vec2d> raw = extractRawBoundaryPoints(shape);
            if (raw.size() < 3) {
                continue;
            }
            List<Vec2d> points = extractRegionPoints(shape);
            if (points.size() < 3 || !hasMeaningfulArea(points)) {
                continue;
            }
            outlines.add(new RegionOutline(points));
        }
        if (outlines.isEmpty()) {
            return List.of();
        }
        outlines.sort((left, right) -> Double.compare(right.area(), left.area()));

        List<FootprintDraft> drafts = new ArrayList<>();
        for (RegionOutline outline : outlines) {
            FootprintDraft bestParent = null;
            double bestParentArea = Double.MAX_VALUE;
            for (FootprintDraft draft : drafts) {
                if (validateHoleForFootprint(draft.outerPoints, draft.holes, outline.points) != HoleAddIssue.NONE) {
                    continue;
                }
                double parentArea = draft.area();
                if (parentArea < bestParentArea) {
                    bestParent = draft;
                    bestParentArea = parentArea;
                }
            }
            if (bestParent != null) {
                bestParent.holes.add(outline.points);
            } else {
                drafts.add(new FootprintDraft(outline.points));
            }
        }

        List<AdoptedRegionGroup> groups = new ArrayList<>(drafts.size());
        for (FootprintDraft draft : drafts) {
            groups.add(new AdoptedRegionGroup(draft.outerPoints, draft.holes));
        }
        return groups;
    }

    public record AdoptSelectionSummary(int selectedShapeCount, int outerCount, int holeCount) {
    }

    public static AdoptSelectionSummary summarizeAdoptSelection(List<Shape> shapes) {
        List<AdoptedRegionGroup> groups = groupAdoptableRegionsWithHoles(shapes);
        int holeCount = 0;
        for (AdoptedRegionGroup group : groups) {
            holeCount += group.holes().size();
        }
        return new AdoptSelectionSummary(
            shapes != null ? shapes.size() : 0,
            groups.size(),
            holeCount);
    }

    public static List<Vec2d> extractFirstRegionFromShapes(List<Shape> shapes) {
        if (shapes == null) {
            return List.of();
        }
        for (Shape shape : shapes) {
            List<Vec2d> points = extractRegionPoints(shape);
            if (points.size() >= 3) {
                return points;
            }
        }
        return List.of();
    }

    public static double holeArea(List<Vec2d> holePoints) {
        if (holePoints == null || holePoints.size() < 3) {
            return 0.0;
        }
        return Math.abs(PolygonRegionUtils.signedAreaOfRing(holePoints));
    }

    /**
     * 统计区域投影到 Minecraft 平面上的方块列数（随当前视图投影变化）。
     */
    public static int countProjectedWorldBlocks(
            List<Vec2d> outerPoints,
            List<List<Vec2d>> holes,
            ICoordinateService coordinates) {
        if (outerPoints == null || outerPoints.size() < 3) {
            return 0;
        }
        if (coordinates == null) {
            return PolygonRegionUtils.countFootprintCells(outerPoints, holes);
        }
        try {
            double cellSize = canvasUnitsPerWorldBlock(outerPoints, coordinates);
            List<Vec2d> centers = PolygonRegionUtils.collectFootprintCellCenters(outerPoints, holes, cellSize);
            if (centers.isEmpty()) {
                return 0;
            }
            Set<Long> columns = new HashSet<>();
            for (Vec2d center : centers) {
                BlockPos column = canvasToBlockXZ(center, coordinates);
                columns.add(packColumn(column.getX(), column.getZ()));
            }
            return columns.size();
        } catch (RuntimeException ignored) {
            return PolygonRegionUtils.countFootprintCells(outerPoints, holes);
        }
    }

    public static int countProjectedWorldBlocksForShapes(List<Shape> shapes, ICoordinateService coordinates) {
        List<AdoptedRegionGroup> groups = groupAdoptableRegionsWithHoles(shapes);
        int count = 0;
        for (AdoptedRegionGroup group : groups) {
            count += countProjectedWorldBlocks(group.outerPoints(), group.holes(), coordinates);
        }
        return count;
    }

    private static double canvasUnitsPerWorldBlock(List<Vec2d> outerPoints, ICoordinateService coordinates) {
        Vec2d probe = PolygonRegionUtils.computeCentroid(outerPoints);
        double sum = 0.0;
        int count = outerPoints.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = outerPoints.get(i);
            Vec2d end = outerPoints.get((i + 1) % count);
            sum += WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, probe, end.subtract(start));
        }
        double average = sum / count;
        return Math.max(1e-6, Math.min(1.0, average));
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, ICoordinateService transformer) {
        return PolygonRegionUtils.canvasToBlockXZ(canvasPos, transformer);
    }

    private record RegionOutline(List<Vec2d> points) {
        double area() {
            return Math.abs(PolygonRegionUtils.signedAreaOfRing(points));
        }
    }

    private static final class FootprintDraft {
        final List<Vec2d> outerPoints;
        final List<List<Vec2d>> holes = new ArrayList<>();

        FootprintDraft(List<Vec2d> outerPoints) {
            this.outerPoints = PolygonRegionUtils.copyPoints(outerPoints);
        }

        double area() {
            return Math.abs(PolygonRegionUtils.signedAreaOfRing(outerPoints));
        }
    }
}
