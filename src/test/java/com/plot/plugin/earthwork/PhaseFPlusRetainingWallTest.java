package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.RetainingEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseFPlusRetainingWallTest {

    @Test
    void collectsRetainingWallBoundarySegments() {
        GradingZone zone = createFlatZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        zone.getEdgeSettings().setDefaultTreatment(EdgeTreatment.VERTICAL);
        zone.getEdgeSettings().setEdgeOverrides(List.of(
            new com.plot.plugin.earthwork.model.BoundaryEdgeOverride(1, EdgeTreatment.RETAINING_WALL)));

        List<ZoneBoundaryRetainingEdgeAdapter.BoundarySegment> segments =
            ZoneBoundaryRetainingEdgeAdapter.collectRetainingWallSegments(zone);

        assertEquals(1, segments.size());
        assertEquals(1, segments.getFirst().edgeIndex());
    }

    @Test
    void derivesVirtualRetainingEdgesFromDesignGrid() {
        EarthworkSite site = createSite();
        GradingZone pad = createFlatZone("pad", List.of(
            new Vec2d(2, 2), new Vec2d(8, 2), new Vec2d(8, 8), new Vec2d(2, 8)));
        pad.getDesignSurface().setManualTargetElevation(64);
        pad.syncDesignSurfaceToRegion();
        pad.getEdgeSettings().setDefaultTreatment(EdgeTreatment.RETAINING_WALL);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 1.5), 5, 1, 70)));

        var composed = DesignTerrainComposer.compose(site, terrain, null);
        List<RetainingEdge> virtualEdges = ZoneBoundaryRetainingEdgeAdapter.deriveVirtualEdges(
            site, composed.grid(), composed.zoneEvaluators());

        assertFalse(virtualEdges.isEmpty());
        assertTrue(virtualEdges.getFirst().getTopElevation() > virtualEdges.getFirst().getBottomElevation());
        assertEquals("pad", virtualEdges.getFirst().getLinkedZoneId());
    }

    @Test
    void syncZoneWritesManagedRetainingEdges() {
        EarthworkSite site = createSite();
        GradingZone pad = createFlatZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.getEdgeSettings().setDefaultTreatment(EdgeTreatment.RETAINING_WALL);
        site.addZone(pad);

        int synced = ZoneBoundaryRetainingEdgeAdapter.syncZoneToSite(site, pad);
        assertEquals(4, synced);
        assertEquals(4, site.getRetainingEdges().size());
        assertTrue(site.getRetainingEdges().stream()
            .allMatch(edge -> edge.getId().startsWith(ZoneBoundaryRetainingEdgeAdapter.VIRTUAL_ID_PREFIX)));

        pad.getEdgeSettings().setDefaultTreatment(EdgeTreatment.VERTICAL);
        synced = ZoneBoundaryRetainingEdgeAdapter.syncZoneToSite(site, pad);
        assertEquals(0, synced);
        assertTrue(site.getRetainingEdges().isEmpty());
    }

    @Test
    void resolveWallBoundsUsesTargetAndExistingGap() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        grid.put(5, 5, new DesignTerrainCell(5, 5, new Vec2d(5, 5), 70));
        grid.get(5, 5).setTargetY(64);

        int[] bounds = ZoneBoundaryRetainingEdgeAdapter.resolveWallBounds(new Vec2d(5, 5), grid, null);
        assertEquals(64, bounds[0]);
        assertEquals(70, bounds[1]);
    }

    private static EarthworkSite createSite() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(12, 0), new Vec2d(12, 12), new Vec2d(0, 12)));
        return site;
    }

    private static GradingZone createFlatZone(String id, List<Vec2d> points) {
        GradingZone zone = new GradingZone(id, points);
        zone.setName(id);
        DesignSurface surface = zone.getDesignSurface();
        surface.setAutoBalance(false);
        zone.syncDesignSurfaceToRegion();
        return zone;
    }
}
