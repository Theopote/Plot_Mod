package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.BuildingFootprintResolver;
import com.plot.plugin.earthwork.design.ResolutionResult;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.ExcavationPitParameters;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFootprintResolverTest {

    @Test
    void resolvePitBottomSubtractsFloorFoundationAndAllowance() {
        GradingZone zone = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        zone.setBuildingFootprintRef("b1");
        DesignSurface surface = zone.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        surface.setExcavationPit(new ExcavationPitParameters(4, 1, 1));

        BuildingFootprint footprint = new BuildingFootprint("b1", zone.getOuterPoints(), false);
        footprint.setManualBaseElevation(72);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 66)));

        ResolutionResult<Integer> bottom = BuildingFootprintResolver.resolvePitBottomElevation(
            zone, surface, terrain, id -> "b1".equals(id) ? footprint : null, 64);
        assertEquals(ResolutionResult.Status.RESOLVED, bottom.status());
        // 72 - 4 - 1 - 1 = 66
        assertEquals(66, bottom.value());
    }

    @Test
    void legacyBasementDepthAloneStillMeansFloorDepthOnly() {
        GradingZone zone = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        zone.setBuildingFootprintRef("b1");
        DesignSurface surface = zone.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        surface.setBasementFloorDepth(4);

        BuildingFootprint footprint = new BuildingFootprint("b1", zone.getOuterPoints(), false);
        footprint.setManualBaseElevation(72);

        ResolutionResult<Integer> bottom = BuildingFootprintResolver.resolvePitBottomElevation(
            zone, surface, TerrainSnapshot.empty(), id -> footprint, 64);
        assertTrue(bottom.isResolved());
        assertEquals(68, bottom.value());
    }

    @Test
    void excavationPitParametersRoundTripThroughProjectJson() throws Exception {
        EarthworkProject project = new EarthworkProject();
        GradingZone pit = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        pit.getDesignSurface().setExcavationPit(new ExcavationPitParameters(5, 2, 1));
        project.getActiveSite().addZone(pit);

        var temp = Files.createTempFile("earthwork-pit-depth", ".json");
        project.saveTo(temp);
        EarthworkProject loaded = EarthworkProject.loadFrom(temp);
        DesignSurface surface = loaded.getZone("pit").getDesignSurface();
        assertEquals(5, surface.getBasementFloorDepth());
        assertEquals(2, surface.getFoundationDepth());
        assertEquals(1, surface.getPitWorkingAllowance());
        Files.deleteIfExists(temp);
    }

    @Test
    void buildingLinkedPitBottomMarksInvalidReferenceWithoutQuietFallback() {
        GradingZone zone = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        zone.setBuildingFootprintRef("missing");
        DesignSurface surface = zone.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        surface.setBasementFloorDepth(4);

        ResolutionResult<Integer> bottom = BuildingFootprintResolver.resolvePitBottomElevation(
            zone, surface, TerrainSnapshot.empty(), BuildingFootprintLookup.NONE, 64);

        assertEquals(ResolutionResult.Status.INVALID_REFERENCE, bottom.status());
        assertEquals(64, bottom.value()); // diagnostic only — not a design value
        assertFalse(bottom.isResolved());
        assertThrows(
            BuildingFootprintResolver.UnresolvedBuildingReferenceException.class,
            () -> bottom.requireResolved("pit bottom"));
    }

    @Test
    void buildingLinkedPitBottomMarksMissingReference() {
        GradingZone zone = new GradingZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        DesignSurface surface = zone.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);

        ResolutionResult<Integer> bottom = BuildingFootprintResolver.resolvePitBottomElevation(
            zone, surface, TerrainSnapshot.empty(), BuildingFootprintLookup.NONE, 64);

        assertEquals(ResolutionResult.Status.MISSING_REFERENCE, bottom.status());
        assertTrue(bottom.isReferenceFailure());
    }

    @Test
    void buildingPadAllowsRecommendedFallbackWhenReferenceMissing() {
        GradingZone zone = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        DesignSurface surface = zone.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);

        ResolutionResult<Integer> elevation = BuildingFootprintResolver.resolveConstantElevation(
            zone, surface, TerrainSnapshot.empty(), BuildingFootprintLookup.NONE, 64);

        assertEquals(ResolutionResult.Status.MISSING_REFERENCE, elevation.status());
        assertEquals(64, elevation.valueOrFallback(0));
    }

    @Test
    void legacyBasementDepthBlocksJsonMigratesToFloorDepth() {
        String json = """
            {
              "schemaVersion": 3,
              "sites": [{
                "id": "site-1",
                "name": "Site",
                "gradingZones": [{
                  "id": "pit",
                  "name": "pit",
                  "type": "EXCAVATION_PIT",
                  "outerPoints": [
                    {"x": 0, "y": 0}, {"x": 10, "y": 0}, {"x": 10, "y": 10}, {"x": 0, "y": 10}
                  ],
                  "designSurface": {
                    "kind": "EXCAVATION_PIT",
                    "elevationSource": "BUILDING_BASE_ELEVATION",
                    "basementDepthBlocks": 7
                  }
                }]
              }],
              "activeSiteId": "site-1"
            }
            """;
        EarthworkProject loaded = EarthworkProject.fromJson(json);
        DesignSurface surface = loaded.getActiveSite().getZone("pit").getDesignSurface();
        assertEquals(7, surface.getBasementFloorDepth());
        assertEquals(0, surface.getFoundationDepth());
        assertEquals(0, surface.getPitWorkingAllowance());
    }
}
