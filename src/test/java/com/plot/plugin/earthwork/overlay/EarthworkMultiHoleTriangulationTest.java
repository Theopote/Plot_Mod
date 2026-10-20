package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.polygon.RegionTriangulator;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.ui.EarthworkOverviewRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多孔洞几何回归：三角形不得实质进入孔洞，总面积应接近净面积。
 */
class EarthworkMultiHoleTriangulationTest {
    private static final double AREA_TOLERANCE = 0.5;

    @BeforeEach
    void clearCache() {
        EarthworkRegionTriangulationCache.clear();
    }

    @Test
    void singleLargeHole_meshAreaMatchesNetAndAvoidsHole() {
        GradingRegion region = regionWithHoles(
            "single",
            rectangleOutline(0, 20, 0, 20),
            List.of(rectangleOutline(5, 15, 5, 15)));
        assertMeshValid(region);
    }

    @Test
    void twoDisjointHoles_meshAreaMatchesNetAndAvoidsBothHoles() {
        GradingRegion region = regionWithHoles(
            "two",
            rectangleOutline(0, 30, 0, 20),
            List.of(
                rectangleOutline(2, 8, 4, 14),
                rectangleOutline(18, 26, 4, 14)));
        assertMeshValid(region);
    }

    @Test
    void concaveOuterWithTwoHoles_meshAreaMatchesNet() {
        // 凹多边形：C 形外环
        List<Vec2d> concave = List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 6),
            new Vec2d(8, 6),
            new Vec2d(8, 14),
            new Vec2d(20, 14),
            new Vec2d(20, 20),
            new Vec2d(0, 20));
        List<Vec2d> holeA = rectangleOutline(2, 5, 2, 5);
        List<Vec2d> holeB = rectangleOutline(2, 5, 15, 18);
        GradingRegion region = regionWithHoles("concave", concave, List.of(holeA, holeB));
        assertMeshValid(region);
    }

    @Test
    void bridgeAllHolesProducesTriangulablePolygon() {
        RegionGeometry geometry = RegionGeometry.of(
            rectangleOutline(0, 20, 0, 20),
            List.of(
                rectangleOutline(2, 6, 2, 6),
                rectangleOutline(12, 16, 12, 16)));
        RegionTriangulator.BridgedPolygon bridged =
            RegionTriangulator.bridgeAllHoles(geometry.outerRing(), geometry.holes());
        assertNotNull(bridged);
        assertTrue(bridged.vertices().size() >= 3);
        var result = com.plot.core.geometry.polygon.PolygonTriangulator.triangulate(bridged.vertices());
        assertTrue(result.success(), () -> "issue=" + result.issue() + " verts=" + bridged.vertices().size());
    }

    @Test
    void stableColorIndexIndependentOfListOrder() {
        String idA = "region-alpha-stable";
        String idB = "region-beta-stable";
        int colorA = EarthworkOverviewRenderer.stableColorIndex(idA);
        int colorB = EarthworkOverviewRenderer.stableColorIndex(idB);
        assertEquals(colorA, EarthworkOverviewRenderer.stableColorIndex(idA));
        assertEquals(colorB, EarthworkOverviewRenderer.stableColorIndex(idB));
        // 不同 ID 在常见情况下应有机会不同色（不强制，但至少索引稳定）
        assertTrue(colorA >= 0 && colorA < 6);
        assertTrue(colorB >= 0 && colorB < 6);
    }

    private static GradingRegion regionWithHoles(String id, List<Vec2d> outer, List<List<Vec2d>> holes) {
        GradingRegion region = new GradingRegion(id, outer);
        region.setHoles(holes);
        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);
        return region;
    }

    private static void assertMeshValid(GradingRegion region) {
        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);
        EarthworkRegionOverlayEntry entry = EarthworkRegionOverlayController.snapshot(
            project, region.getId(), List.of(), false, true).getFirst();

        List<EarthworkRegionTriangulationCache.WorldTriangle> triangles =
            EarthworkRegionTriangulationCache.resolveWorldTriangles(entry);
        assertFalse(triangles.isEmpty(), "triangulation must succeed for multi-hole region");

        double netArea = Math.abs(PolygonRegionUtils.computeSignedArea(
            region.getOuterPoints(), region.getHoles()));
        double meshAreaSum = 0.0;
        for (EarthworkRegionTriangulationCache.WorldTriangle triangle : triangles) {
            meshAreaSum += triangle.absoluteArea();
            assertTriangleDoesNotEnterHoles(triangle, region.getHoles());
        }
        final double meshArea = meshAreaSum;
        assertEquals(netArea, meshArea, AREA_TOLERANCE,
            () -> "meshArea=" + meshArea + " netArea=" + netArea);
    }

    /**
     * 用三角形重心 + 三等分点采样：任一点落入孔洞则失败。
     * （完整桥接网格下这些采样点都应在净区域内。）
     */
    private static void assertTriangleDoesNotEnterHoles(
            EarthworkRegionTriangulationCache.WorldTriangle triangle,
            List<List<Vec2d>> holes) {
        Vec2d[] samples = {
            centroid(triangle),
            midpoint(triangle.a(), triangle.b()),
            midpoint(triangle.b(), triangle.c()),
            midpoint(triangle.c(), triangle.a()),
            // 靠近重心的内点
            lerp(triangle.a(), centroid(triangle), 0.5),
            lerp(triangle.b(), centroid(triangle), 0.5),
            lerp(triangle.c(), centroid(triangle), 0.5)
        };
        for (Vec2d sample : samples) {
            for (List<Vec2d> hole : holes) {
                assertFalse(
                    PolygonRegionUtils.containsPoint(hole, List.of(), sample),
                    () -> "sample " + sample + " inside hole");
            }
        }
    }

    private static Vec2d centroid(EarthworkRegionTriangulationCache.WorldTriangle t) {
        return new Vec2d(
            (t.a().x + t.b().x + t.c().x) / 3.0,
            (t.a().y + t.b().y + t.c().y) / 3.0);
    }

    private static Vec2d midpoint(Vec2d a, Vec2d b) {
        return new Vec2d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5);
    }

    private static Vec2d lerp(Vec2d a, Vec2d b, double t) {
        return new Vec2d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }
}
