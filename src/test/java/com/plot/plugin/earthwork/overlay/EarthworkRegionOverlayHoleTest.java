package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 带孔洞区域：命中测试与叠加条目一致性。 */
class EarthworkRegionOverlayHoleTest {

    @Test
    void hitTestExcludesHoleInterior() {
        GradingRegion region = new GradingRegion("pad", rectangleOutline(0, 10, 0, 10));
        region.setHoles(List.of(rectangleOutline(3, 7, 3, 7)));

        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);

        List<EarthworkRegionOverlayEntry> entries = EarthworkRegionOverlayController.snapshot(
            project, region.getId(), List.of(), false, true);
        assertEquals(1, entries.size());
        assertFalse(entries.getFirst().holes().isEmpty());

        // 环带内可命中
        assertEquals(region.getId(), EarthworkRegionOverlayController.hitTestAdopted(entries, 1.5, 1.5));
        // 孔洞内不可命中
        assertNull(EarthworkRegionOverlayController.hitTestAdopted(entries, 5.0, 5.0));
        assertFalse(entries.getFirst().containsWorldPoint(5.0, 5.0));
        assertTrue(entries.getFirst().containsWorldPoint(1.0, 1.0));
    }

    @Test
    void overlappingRegionsPreferSmallerAbsoluteArea() {
        GradingRegion outer = new GradingRegion("outer", rectangleOutline(0, 20, 0, 20));
        GradingRegion inner = new GradingRegion("inner", rectangleOutline(5, 10, 5, 10));
        EarthworkProject project = new EarthworkProject();
        project.addRegion(outer);
        project.addRegion(inner);

        List<EarthworkRegionOverlayEntry> entries = EarthworkRegionOverlayController.snapshot(
            project, "", List.of(), false, true);

        assertEquals("inner", EarthworkRegionOverlayController.hitTestAdopted(entries, 7.0, 7.0));
        assertEquals("outer", EarthworkRegionOverlayController.hitTestAdopted(entries, 1.0, 1.0));
    }

    @Test
    void triangulationSucceedsForSimpleOuter() {
        EarthworkRegionTriangulationCache.clear();
        GradingRegion region = new GradingRegion("pad", rectangleOutline(0, 8, 0, 8));
        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);

        List<EarthworkRegionOverlayEntry> entries = EarthworkRegionOverlayController.snapshot(
            project, region.getId(), List.of(), false, true);
        EarthworkRegionOverlayEntry entry = entries.getFirst();

        var triangles = EarthworkRegionTriangulationCache.triangulateGeometry(entry.toGeometry());
        assertFalse(triangles.isEmpty());
        EarthworkRegionTriangulationCache.invalidate(region.getId());
    }

    @Test
    void holeEntryCarriesHolesAndExcludesFromAbsoluteArea() {
        GradingRegion region = new GradingRegion("pad", rectangleOutline(0, 10, 0, 10));
        region.setHoles(List.of(rectangleOutline(3, 7, 3, 7)));
        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);

        EarthworkRegionOverlayEntry entry = EarthworkRegionOverlayController.snapshot(
            project, "", List.of(), false, true).getFirst();
        assertEquals(1, entry.holes().size());
        double solidArea = Math.abs(com.plot.core.geometry.PolygonRegionUtils.signedAreaOfRing(
            rectangleOutline(0, 10, 0, 10)));
        assertTrue(entry.absoluteArea() < solidArea);
    }
}
