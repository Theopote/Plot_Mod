package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.RetainingEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseDPlusCompositionTest {

    @Test
    void roadCorridorZoneSamplesLinkedRoadElevation() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone corridor = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        corridor.setType(GradingZoneType.ROAD_CORRIDOR);
        corridor.setRoadEdgeRef("edge-main");
        site.addZone(corridor);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65)
        ));

        RoadSurfaceLookup lookup = (edgeId, planPoint) -> {
            if ("edge-main".equals(edgeId) && planPoint.x == 5.0 && planPoint.y == 5.0) {
                return 68;
            }
            return null;
        };

        DesignTerrainCell cell = DesignTerrainComposer.compose(
            site, terrain, null, BuildingFootprintLookup.NONE, lookup).grid().get(5, 5);
        assertEquals(68, cell.targetY());
    }

    @Test
    void retainingEdgeSuppressesBoundaryBlend() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone yard = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        yard.setPriority(50);
        yard.getRegion().setAutoBalance(false);
        yard.getRegion().setManualTargetElevation(60);

        GradingZone pad = new GradingZone(List.of(
            new Vec2d(2, 2),
            new Vec2d(8, 2),
            new Vec2d(8, 8),
            new Vec2d(2, 8)
        ));
        pad.setPriority(100);
        pad.getRegion().setAutoBalance(false);
        pad.getRegion().setManualTargetElevation(75);

        site.addZone(yard);
        site.addZone(pad);
        site.getCompositionPolicy().setBlendWidthBlocks(3);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

        RetainingEdge retainingEdge = new RetainingEdge("ret-1");
        retainingEdge.setPolyline(List.of(new Vec2d(2, 2), new Vec2d(2, 8)));
        site.addRetainingEdge(retainingEdge);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 65)
        ));

        DesignTerrainCell cell = DesignTerrainComposer.compose(site, terrain, null).grid().get(2, 5);
        assertEquals(75, cell.targetY());
    }
}
