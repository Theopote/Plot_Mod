package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.test.scale.ScaleInvarianceProjections;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadCorridorPlanBuilderTest {

    @Test
    void spanRasterizationCoversStraightSegmentLength() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(RoadConstructionType.ROAD);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(64),
            1.0,
            "test-seed",
            CorridorTestFixtures.planHost(config));

        assertTrue(plan.surfaceMask().size() >= 20,
            "surface mask must cover at least one cell per block along a 20-block segment");
        assertTrue(plan.clearanceEntries().size() >= plan.surfaceMask().size(),
            "clearance envelope must cover at least the road surface columns");
    }

    @Test
    void surfaceMaskUsesWorldColumnKeysUnderScaledProjection() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);
        ICoordinateService coordinates = ScaleInvarianceProjections.FAR;
        RoadCorridorPlanBuilder.PlanHost host = CorridorTestFixtures.planHost(config, coordinates);

        Vec2d canvasStart = new Vec2d(1, 0);
        Vec2d canvasEnd = new Vec2d(6, 0);
        PathSegment segment = new PathSegment(canvasStart, canvasEnd);
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(RoadConstructionType.ROAD);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(64),
            ScaleInvarianceProjections.worldBlocksPerCanvasUnit(coordinates),
            "scaled-seed",
            host);

        int expectedWorldX = host.canvasToBlockPos(new Vec2d(2, 0)).getX();
        assertNotNull(plan.surfaceMask().get(new ColumnKey(expectedWorldX, 0)), "surface mask must key columns by projected world XZ, not canvas coordinates");
        assertNull(plan.surfaceMask().get(new ColumnKey(2, 0)), "canvas coordinate 2 must not be used as a world column key under 4:1 projection");
    }

    @Test
    void bridgeSpanProducesContinuousSurfaceMask() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment approach = new PathSegment(new Vec2d(0, 0), new Vec2d(8, 0));
        PathSegment bridge = new PathSegment(new Vec2d(8, 0), new Vec2d(22, 0));
        PathSegment exit = new PathSegment(new Vec2d(22, 0), new Vec2d(30, 0));
        List<PathSegment> segments = List.of(approach, bridge, exit);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(approach, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(bridge, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(exit, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.ROAD);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(50),
            1.0,
            "bridge-seed",
            CorridorTestFixtures.planHost(config));

        int bridgeSurfaceColumns = 0;
        for (int x = 8; x <= 22; x++) {
            SurfaceCell cell = plan.surfaceMask().get(new ColumnKey(x, 0));
            if (cell != null && cell.layer() == RoadSolidLayer.ROAD) {
                bridgeSurfaceColumns++;
            }
        }
        assertTrue(bridgeSurfaceColumns >= 14,
            "bridge span must keep continuous ROAD surface mask, found " + bridgeSurfaceColumns);
        ClearanceCell midBridge = plan.clearanceCell(new ColumnKey(15, 0));
        assertNotNull(midBridge, "bridge columns must still record clearance metadata");
        assertTrue(midBridge.clearTopY() > midBridge.designSurfaceY(),
            "bridge clearance envelope must have positive overhead height");
        assertEquals(RoadConstructionType.BRIDGE, midBridge.constructionType(),
            "mid-span clearance must stay BRIDGE after ROAD approach/exit spans merge");
        assertEquals(0, RoadGenerationIntegrityValidator.countStructureInteriorSlopeCells(plan),
            "bridge interior must not retain corridor slope cells");

        // Simulate later open-road envelope overlapping the deck column (higher clearTop).
        plan.putClearance(new ColumnKey(15, 0), new ClearanceCell(
            new Vec2d(15, 0), 64, midBridge.clearTopY() + 20,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        ClearanceCell afterOverlap = plan.clearanceCell(new ColumnKey(15, 0));
        assertEquals(RoadConstructionType.BRIDGE, afterOverlap.constructionType(),
            "open ROAD clearance must not overwrite structure interior type");
        assertEquals(midBridge.clearTopY() + 20, afterOverlap.clearTopY());
    }

    @Test
    void roadPortalTunnelPortalKeepsTransitionClearanceAndSlope() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setIncludeSlopeBatter(true);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        // One segment per type so stations land on portal columns (x≈4, 12, 20, 28 midpoints).
        PathSegment approach = new PathSegment(new Vec2d(0, 0), new Vec2d(8, 0));
        PathSegment portalIn = new PathSegment(new Vec2d(8, 0), new Vec2d(12, 0));
        PathSegment tunnel = new PathSegment(new Vec2d(12, 0), new Vec2d(20, 0));
        PathSegment portalOut = new PathSegment(new Vec2d(20, 0), new Vec2d(24, 0));
        PathSegment exit = new PathSegment(new Vec2d(24, 0), new Vec2d(32, 0));
        List<PathSegment> segments = List.of(approach, portalIn, tunnel, portalOut, exit);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(approach, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(portalIn, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(tunnel, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(portalOut, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(exit, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.TUNNEL_PORTAL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL_PORTAL,
            RoadConstructionType.ROAD);

        TerrainSampler mountain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return 72;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return 72;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 72;
            }
        };

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            mountain,
            1.0,
            "portal-seed",
            CorridorTestFixtures.planHost(config));

        ClearanceCell portal = plan.clearanceCell(new ColumnKey(10, 0));
        assertNotNull(portal, "portal station must write clearance");
        assertEquals(RoadConstructionType.TUNNEL_PORTAL, portal.constructionType(),
            "portal half of PORTAL→TUNNEL span must not be retagged as TUNNEL");
        assertEquals(RoadCorridorConstructionMode.PORTAL, portal.mode(),
            "portal clearance must use portal cavity mode, not open terrain-top");
        int portalRoofCap = RoadCorridorPlanBuilder.portalRoofTopY(
            CorridorTestFixtures.planHost(config), 64);
        assertEquals(portalRoofCap, portal.clearTopY(),
            "portal clearTop must match designed portal-frame roof");
        boolean portalHasLateralTag = plan.clearanceEntries().stream()
            .anyMatch(entry -> entry.getValue().constructionType() == RoadConstructionType.TUNNEL_PORTAL
                && entry.getValue().corridorLateral() != null);
        assertTrue(portalHasLateralTag, "portal envelope clearance must tag corridor lateral for mask");

        ClearanceCell interior = plan.clearanceCell(new ColumnKey(16, 0));
        assertNotNull(interior);
        assertEquals(RoadConstructionType.TUNNEL, interior.constructionType());
        assertEquals(RoadCorridorConstructionMode.TUNNEL, interior.mode());

        assertEquals(0, RoadGenerationIntegrityValidator.countStructureInteriorSlopeCells(plan),
            "tunnel interior must not retain corridor slope cells");
        assertTrue(plan.slopeEntries().stream().anyMatch(e -> {
            int x = e.getKey().worldX();
            return x >= 8 && x <= 12;
        }), "portal zone must keep transition batter / cut slope cells");
    }

    @Test
    void roadAbutmentBridgeAbutmentKeepsDeckStructureType() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setIncludeSlopeBatter(true);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment approach = new PathSegment(new Vec2d(0, 0), new Vec2d(8, 0));
        PathSegment abutmentIn = new PathSegment(new Vec2d(8, 0), new Vec2d(12, 0));
        PathSegment deck = new PathSegment(new Vec2d(12, 0), new Vec2d(20, 0));
        PathSegment abutmentOut = new PathSegment(new Vec2d(20, 0), new Vec2d(24, 0));
        PathSegment exit = new PathSegment(new Vec2d(24, 0), new Vec2d(32, 0));
        List<PathSegment> segments = List.of(approach, abutmentIn, deck, abutmentOut, exit);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(approach, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(abutmentIn, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(deck, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(abutmentOut, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(exit, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.BRIDGE_ABUTMENT,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.BRIDGE_ABUTMENT,
            RoadConstructionType.ROAD);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(50),
            1.0,
            "abutment-seed",
            CorridorTestFixtures.planHost(config));

        ClearanceCell abutment = plan.clearanceCell(new ColumnKey(10, 0));
        assertNotNull(abutment);
        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, abutment.constructionType(),
            "abutment half must not be overwritten as BRIDGE by deck-bound span");

        ClearanceCell midDeck = plan.clearanceCell(new ColumnKey(16, 0));
        assertNotNull(midDeck);
        assertEquals(RoadConstructionType.BRIDGE, midDeck.constructionType());
        assertEquals(0, RoadGenerationIntegrityValidator.countStructureInteriorSlopeCells(plan));
    }

    @Test
    void tunnelSpanKeepsStructureClearanceAgainstOpenRoadMerge() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment approach = new PathSegment(new Vec2d(0, 0), new Vec2d(8, 0));
        PathSegment tunnel = new PathSegment(new Vec2d(8, 0), new Vec2d(22, 0));
        PathSegment exit = new PathSegment(new Vec2d(22, 0), new Vec2d(30, 0));
        List<PathSegment> segments = List.of(approach, tunnel, exit);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(approach, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(tunnel, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(exit, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.ROAD);

        TerrainSampler mountain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return 72;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 72;
            }
        };

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            mountain,
            1.0,
            "tunnel-seed",
            CorridorTestFixtures.planHost(config));

        ClearanceCell midTunnel = plan.clearanceCell(new ColumnKey(15, 0));
        assertNotNull(midTunnel, "tunnel columns must record clearance metadata");
        assertEquals(RoadConstructionType.TUNNEL, midTunnel.constructionType(),
            "mid-tunnel clearance must stay TUNNEL after ROAD approaches merge");
        assertEquals(0, RoadGenerationIntegrityValidator.countStructureInteriorSlopeCells(plan));

        plan.putClearance(new ColumnKey(15, 0), new ClearanceCell(
            new Vec2d(15, 0), 64, midTunnel.clearTopY() + 30,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.CUT));
        assertEquals(RoadConstructionType.TUNNEL,
            plan.clearanceCell(new ColumnKey(15, 0)).constructionType());
    }
}
