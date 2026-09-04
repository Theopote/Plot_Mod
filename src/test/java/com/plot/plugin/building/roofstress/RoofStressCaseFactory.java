package com.plot.plugin.building.roofstress;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * Roof Stress Suite 用例：R01–R12。
 */
public final class RoofStressCaseFactory {
    private RoofStressCaseFactory() {
    }

    public enum SymmetryKind {
        /** 不做对称断言（非对称 footprint）。 */
        NONE,
        /** 关于质心竖直线镜像（左右对称）。 */
        BILATERAL_X,
        /** 关于质心竖直线与水平线均镜像。 */
        BILATERAL_XY,
        /** 关于质心 180° 旋转对称。 */
        ROTATIONAL_180,
        /** 与 {@link RoofStressCase#referencePolygon()} 高度场一致（绕序/平移）。 */
        MATCH_REFERENCE
    }

    public record RoofStressCase(
            String id,
            String description,
            List<Vec2d> polygon,
            int pitch,
            SymmetryKind symmetry,
            List<Vec2d> referencePolygon) {
        public RoofStressCase {
            polygon = List.copyOf(polygon);
            referencePolygon = referencePolygon == null ? null : List.copyOf(referencePolygon);
        }

        @Override
        public String toString() {
            return id + " — " + description;
        }
    }

    public static List<RoofStressCase> all() {
        return List.of(
            r01Rectangle(),
            r02RotatedRectangle(),
            r03Square(),
            r04LongRectangle(),
            r05LShape(),
            r06TShape(),
            r07UShape(),
            r08ConvexPentagon(),
            r09ConcavePolygon(),
            r10NarrowWing(),
            r11ReversedWinding(),
            r12TranslatedGeometry()
        );
    }

    public static RoofStressCase r01Rectangle() {
        return new RoofStressCase(
            "R01", "rectangle 16x10",
            rect(0, 0, 16, 10), 2, SymmetryKind.BILATERAL_XY, null);
    }

    public static RoofStressCase r02RotatedRectangle() {
        return new RoofStressCase(
            "R02", "rotated rectangle / diamond",
            List.of(
                new Vec2d(10, 4),
                new Vec2d(16, 10),
                new Vec2d(10, 16),
                new Vec2d(4, 10)
            ),
            2, SymmetryKind.ROTATIONAL_180, null);
    }

    public static RoofStressCase r03Square() {
        return new RoofStressCase(
            "R03", "square 12x12",
            rect(0, 0, 12, 12), 2, SymmetryKind.BILATERAL_XY, null);
    }

    public static RoofStressCase r04LongRectangle() {
        return new RoofStressCase(
            "R04", "long rectangle 28x8",
            rect(0, 0, 28, 8), 2, SymmetryKind.BILATERAL_XY, null);
    }

    public static RoofStressCase r05LShape() {
        return new RoofStressCase(
            "R05", "L shape",
            List.of(
                new Vec2d(0, 0),
                new Vec2d(14, 0),
                new Vec2d(14, 5),
                new Vec2d(5, 5),
                new Vec2d(5, 14),
                new Vec2d(0, 14)
            ),
            2, SymmetryKind.NONE, null);
    }

    public static RoofStressCase r06TShape() {
        return new RoofStressCase(
            "R06", "T shape",
            List.of(
                new Vec2d(0, 0),
                new Vec2d(16, 0),
                new Vec2d(16, 4),
                new Vec2d(10, 4),
                new Vec2d(10, 12),
                new Vec2d(6, 12),
                new Vec2d(6, 4),
                new Vec2d(0, 4)
            ),
            2, SymmetryKind.BILATERAL_X, null);
    }

    public static RoofStressCase r07UShape() {
        return new RoofStressCase(
            "R07", "U shape",
            List.of(
                new Vec2d(0, 0),
                new Vec2d(14, 0),
                new Vec2d(14, 10),
                new Vec2d(11, 10),
                new Vec2d(11, 3),
                new Vec2d(3, 3),
                new Vec2d(3, 10),
                new Vec2d(0, 10)
            ),
            2, SymmetryKind.BILATERAL_X, null);
    }

    public static RoofStressCase r08ConvexPentagon() {
        return new RoofStressCase(
            "R08", "convex pentagon",
            List.of(
                new Vec2d(6, 0),
                new Vec2d(14, 3),
                new Vec2d(12, 12),
                new Vec2d(2, 12),
                new Vec2d(0, 3)
            ),
            2, SymmetryKind.NONE, null);
    }

    public static RoofStressCase r09ConcavePolygon() {
        return new RoofStressCase(
            "R09", "concave polygon",
            List.of(
                new Vec2d(0, 0),
                new Vec2d(12, 0),
                new Vec2d(12, 8),
                new Vec2d(7, 8),
                new Vec2d(7, 3),
                new Vec2d(4, 3),
                new Vec2d(4, 8),
                new Vec2d(0, 8)
            ),
            2, SymmetryKind.NONE, null);
    }

    public static RoofStressCase r10NarrowWing() {
        return new RoofStressCase(
            "R10", "narrow wing",
            List.of(
                new Vec2d(0, 0),
                new Vec2d(16, 0),
                new Vec2d(16, 2),
                new Vec2d(0, 2)
            ),
            2, SymmetryKind.BILATERAL_XY, null);
    }

    public static RoofStressCase r11ReversedWinding() {
        List<Vec2d> base = rect(0, 0, 16, 10);
        List<Vec2d> reversed = List.of(
            base.get(0), base.get(3), base.get(2), base.get(1)
        );
        return new RoofStressCase(
            "R11", "reversed winding of R01",
            reversed, 2, SymmetryKind.MATCH_REFERENCE, base);
    }

    public static RoofStressCase r12TranslatedGeometry() {
        List<Vec2d> base = rect(0, 0, 16, 10);
        double dx = 100;
        double dy = 50;
        List<Vec2d> translated = List.of(
            new Vec2d(0 + dx, 0 + dy),
            new Vec2d(16 + dx, 0 + dy),
            new Vec2d(16 + dx, 10 + dy),
            new Vec2d(0 + dx, 10 + dy)
        );
        return new RoofStressCase(
            "R12", "translated R01 by (100,50)",
            translated, 2, SymmetryKind.MATCH_REFERENCE, base);
    }

    private static List<Vec2d> rect(double x0, double y0, double w, double h) {
        return List.of(
            new Vec2d(x0, y0),
            new Vec2d(x0 + w, y0),
            new Vec2d(x0 + w, y0 + h),
            new Vec2d(x0, y0 + h)
        );
    }
}
