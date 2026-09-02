package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.BuildingFootprintResolver;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingFootprintResolverTest {

    @Test
    void resolvePitBottomSubtractsBasementDepthFromBuildingBase() {
        GradingZone zone = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        zone.setBuildingFootprintRef("b1");
        DesignSurface surface = zone.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        surface.setBasementDepthBlocks(4);

        BuildingFootprint footprint = new BuildingFootprint("b1", zone.getOuterPoints(), false);
        footprint.setManualBaseElevation(72);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 66)));

        int bottom = BuildingFootprintResolver.resolvePitBottomElevation(
            zone, surface, terrain, id -> "b1".equals(id) ? footprint : null, 64);
        assertEquals(68, bottom);
    }

    @Test
    void basementDepthRoundTripsThroughProjectJson() throws Exception {
        EarthworkProject project = new EarthworkProject();
        GradingZone pit = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        pit.getDesignSurface().setBasementDepthBlocks(7);
        project.getActiveSite().addZone(pit);

        var temp = Files.createTempFile("earthwork-pit-depth", ".json");
        project.saveTo(temp);
        EarthworkProject loaded = EarthworkProject.loadFrom(temp);
        assertEquals(7, loaded.getZone("pit").getDesignSurface().getBasementDepthBlocks());
        Files.deleteIfExists(temp);
    }
}
