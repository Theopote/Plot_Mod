package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampPresets;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.3 closing integration regressions — real generation call chain (IG01–IG03).
 */
class Phase243LoopSeamGenerationIT {

    private static final double EDGE = 60.0;

    @Test
    void ig01_dualSliceAsymmetricPavementWidth() {
        RoadSystemConfig config = flatConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = createSquareLoop(network, "loop", EDGE);
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(EDGE * 0.5, 0), edgeId, 0.5));
        RoadDesignDirectionAuthority.ensureBound(network, road);

        road.setCrossSection(sectionWithTravelWidth(8));
        double narrowStation = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, edge.getLength() * 0.25).orElseThrow();
        double wideStation = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, edge.getLength() * 0.75).orElseThrow();
        assertNotEquals(narrowStation, wideStation, 1.0);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(Math.min(narrowStation, wideStation), sectionWithTravelWidth(8)),
            StationCrossSection.at(Math.max(narrowStation, wideStation), sectionWithTravelWidth(16))),
            VariableCrossSectionTransitionMode.STEP));

        CrossSectionBuildContext ctx = CrossSectionBuildContext.forEdge(network, edge, config);
        int widthAt025 = ctx.resolve(ctx.chainageAtGeometryLocal(edge.getLength() * 0.25)).carriagewayWidth;
        int widthAt075 = ctx.resolve(ctx.chainageAtGeometryLocal(edge.getLength() * 0.75)).carriagewayWidth;
        assertEquals(8, Math.min(widthAt025, widthAt075));
        assertEquals(16, Math.max(widthAt025, widthAt075));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        EdgeGenerationResult outcome = generator.generateEdgeOutcome(
            network, edge, start, end, new FlatTerrainSampler(64), null);
        assertTrue(outcome.isSuccess(), "dual-slice edge must generate");
        RoadGenerationResult result = outcome.geometry();
        assertFalse(result.roadBlocks.isEmpty(), "dual-slice edge must place pavement");

        // Interior windows away from nodes / seam: compare the half that maps to 8 vs 16.
        Set<Integer> zAtLow = lateralExtents(result, 10, 20);
        Set<Integer> zAtHigh = lateralExtents(result, 40, 50);
        assertFalse(zAtLow.isEmpty(), "narrow-side pavement missing");
        assertFalse(zAtHigh.isEmpty(), "wide-side pavement missing");
        int halfLow = zAtLow.stream().mapToInt(Math::abs).max().orElse(0);
        int halfHigh = zAtHigh.stream().mapToInt(Math::abs).max().orElse(0);
        int wideHalf = Math.max(halfLow, halfHigh);
        int narrowHalf = Math.min(halfLow, halfHigh);
        assertTrue(wideHalf > narrowHalf,
            "wide slice half=" + wideHalf + " must exceed narrow half=" + narrowHalf);
        assertTrue(wideHalf >= narrowHalf + 3,
            "expected clearly wider pavement; wide=" + wideHalf + " narrow=" + narrowHalf);
    }

    @Test
    void ig02_seamMoveGenerationEquivalence() {
        RoadSystemConfig config = flatConfig();
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(true);
        config.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        config.setLaneDividers(true);
        RoadNetworkManager manager = new RoadNetworkManager(config, new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop", EDGE);
        double L = RoadStationing.canonicalLength(network, road);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(L, 64.0))));
        road.getCrossSection().getCarriageway().setLaneCount(2);
        road.getCrossSection().getCarriageway().applyUniformDefaultLaneWidth(4);
        road.getCrossSection().getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        road.getCrossSection().getMarkings().setLaneDividers(true);
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(
            new RoadLampPlacement(
                RoadLampPresets.ALL.getFirst().style(),
                RoadLampPlacement.Sides.BOTH, 8, 0, 2, true, false));
        RoadStationFacilities facilities = new RoadStationFacilities();
        facilities.addRun(StationFacilityRun.of(
            5.0, 25.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT));
        road.setStationFacilities(facilities);

        RoadLoopSeam oldSeam = road.getLoopSeam();
        assertNotNull(oldSeam);

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        assertAllEdgesSuccess(generator, network, terrain);
        Set<BlockPos> beforeRoad = generateLayerBlocks(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> beforeMarkings = generateLayerBlocks(
            generator, network, terrain, RoadSolidLayer.MARKING);
        Set<BlockPos> beforeGuardrails = generateLayerBlocks(
            generator, network, terrain, RoadSolidLayer.GUARDRAIL);
        Set<BlockPos> beforeLamps = generateAllLampSupports(generator, network, terrain);
        assertFalse(beforeRoad.isEmpty());
        assertFalse(beforeMarkings.isEmpty(), "markings must generate for IG02");
        assertFalse(beforeGuardrails.isEmpty(), "guardrails must generate for IG02");
        assertFalse(beforeLamps.isEmpty(), "lamp supports must generate for IG02");

        String edgeId = road.getOrderedSegmentIds().getFirst();
        manager.setPrimarySelectedEdge(edgeId);
        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(EDGE * 0.5, 0)));
        assertTrue(manager.isLoopSeamRemapConfirmPending());
        manager.confirmLoopSeamRemap();

        Road afterRoad = manager.getNetwork().getRoad("loop");
        assertNotNull(afterRoad);
        assertNotEquals(oldSeam, afterRoad.getLoopSeam(), "seam must move");
        assertAllEdgesSuccess(generator, manager.getNetwork(), terrain);

        Set<BlockPos> afterRoadBlocks = generateLayerBlocks(
            generator, manager.getNetwork(), terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> afterMarkings = generateLayerBlocks(
            generator, manager.getNetwork(), terrain, RoadSolidLayer.MARKING);
        Set<BlockPos> afterGuardrails = generateLayerBlocks(
            generator, manager.getNetwork(), terrain, RoadSolidLayer.GUARDRAIL);
        Set<BlockPos> afterLamps = generateAllLampSupports(
            generator, manager.getNetwork(), terrain);
        assertEqualsSet(beforeRoad, afterRoadBlocks);
        assertEqualsSet(beforeMarkings, afterMarkings);
        assertEqualsSet(beforeGuardrails, afterGuardrails);
        assertEqualsSet(beforeLamps, afterLamps);
    }

    @Test
    void ig03_nanStationFailsEdgeGeneration() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        assertThrows(IllegalStateException.class, () -> RoadPathStationSampler.forEach(
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0)),
            true,
            geometryLocal -> Double.NaN,
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            null,
            null,
            true,
            (c, n, y, chainage, i) -> {
            }));

        RoadSystemConfig config = flatConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = createSquareLoop(network, "loop", EDGE);
        road.setCrossSection(sectionWithTravelWidth(8));
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(10.0, sectionWithTravelWidth(16))),
            VariableCrossSectionTransitionMode.STEP));
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(1000, 1000),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(40.0))));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        EdgeGenerationResult outcome = generator.generateEdgeOutcome(
            network,
            edge,
            network.getNode(edge.getStartNodeId()),
            network.getNode(edge.getEndNodeId()),
            new FlatTerrainSampler(64),
            null);
        assertTrue(outcome.isFailed(), "unresolvable station map must fail the edge");
        assertTrue(outcome.geometry().roadBlocks.isEmpty(),
            "failed edge must not produce applyable pavement");
    }

    @Test
    void virtualSeamOriginRoundTrip() {
        RoadNetwork network = new RoadNetwork();
        Road road = createSquareLoop(network, "loop", EDGE);
        road.setLoopSeam(null);
        RoadDesignDirectionAuthority.ensureBound(network, road);
        double origin = RoadStationing.loopPhysicalOriginStation(network, road);
        assertTrue(Double.isFinite(origin), "virtual seam origin must resolve");
        double L = RoadStationing.canonicalLength(network, road);
        assertTrue(L > 0.0);
        double instance0 = RoadStationing.toInstanceChainage(network, road, 0.0);
        double instanceL = RoadStationing.toInstanceChainage(network, road, L);
        assertEquals(0.0, instance0, 1e-6);
        double instanceTotal = com.plot.plugin.road.alignment.RoadPlanGeometry.instanceLength(network, road);
        assertEquals(instanceTotal, instanceL, 1e-6);
        assertEquals(0.0, RoadStationing.toCanonicalChainage(network, road, 0.0), 1e-6);
        assertEquals(L, RoadStationing.toCanonicalChainage(network, road, instanceTotal), 1e-6);
    }

    @Test
    void uniformLoopMissingSliceReturnsNan() {
        // Open LOOP is not stationable → forEdge binds no slices → require binding → NaN.
        RoadSystemConfig config = flatConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("open-loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(EDGE, 0));
        RoadNode c = network.createNode(new Vec2d(EDGE, EDGE));
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(EDGE, 0)), road.getId());
        network.createEdge(
            b.getId(), c.getId(), List.of(new Vec2d(EDGE, 0), new Vec2d(EDGE, EDGE)), road.getId());
        assertFalse(RoadStationing.isStationable(network, road));
        CrossSectionBuildContext ctx = CrossSectionBuildContext.forEdge(network, edge, config);
        assertTrue(Double.isNaN(ctx.chainageAtGeometryLocal(edge.getLength() * 0.5)));
        assertTrue(Double.isNaN(ctx.physicalStationAtGeometryLocal(edge.getLength() * 0.5)));
    }

    @Test
    void smoothDifferentMaterialsRefuseMigrate() {
        RoadSystemConfig config = flatConfig();
        RoadCrossSection asphalt = sectionWithTravelWidth(8);
        asphalt.getCarriageway().setMaterial("minecraft:black_concrete");
        RoadCrossSection stone = sectionWithTravelWidth(8);
        stone.getCarriageway().setMaterial("minecraft:stone");
        Road road = new Road("loop");
        road.setCrossSection(asphalt);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, asphalt),
            StationCrossSection.at(60.0, stone)), VariableCrossSectionTransitionMode.SMOOTH));
        assertFalse(RoadStationDataTransforms.rotateLoopStations(road, 30.0, 100.0, config));
    }

    private static void assertAllEdgesSuccess(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain) {
        for (Road road : network.getRoads().values()) {
            for (String edgeId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                EdgeGenerationResult outcome = generator.generateEdgeOutcome(
                    network,
                    edge,
                    network.getNode(edge.getStartNodeId()),
                    network.getNode(edge.getEndNodeId()),
                    terrain,
                    null);
                assertTrue(outcome.isSuccess(), "edge " + edgeId + " must succeed: " + outcome.message());
            }
        }
    }

    private static void assertEqualsSet(Set<BlockPos> before, Set<BlockPos> after) {
        assertTrue(before.equals(after),
            "generation sets differ; before=" + before.size() + " after=" + after.size());
    }

    private static Set<BlockPos> generateLayerBlocks(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain,
            RoadSolidLayer layer) {
        Set<BlockPos> blocks = new HashSet<>();
        for (Road road : network.getRoads().values()) {
            for (String edgeId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                RoadGenerationResult result = generator.generateEdge(
                    network,
                    edge,
                    network.getNode(edge.getStartNodeId()),
                    network.getNode(edge.getEndNodeId()),
                    terrain,
                    null);
                for (var entry : result.placementLayers.entrySet()) {
                    if (entry.getValue() == layer) {
                        blocks.add(entry.getKey());
                    }
                }
                if (layer == RoadSolidLayer.ROAD) {
                    blocks.addAll(result.roadBlocks);
                }
            }
        }
        return blocks;
    }

    private static Set<BlockPos> generateAllLampSupports(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain) {
        Set<BlockPos> supports = new HashSet<>();
        for (Road road : network.getRoads().values()) {
            for (String edgeId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                RoadGenerationResult result = generator.generateEdge(
                    network,
                    edge,
                    network.getNode(edge.getStartNodeId()),
                    network.getNode(edge.getEndNodeId()),
                    terrain,
                    null);
                result.roadLampCandidates.forEach(c -> supports.add(c.support()));
            }
        }
        return supports;
    }

    private static Set<Integer> lateralExtents(RoadGenerationResult result, int minX, int maxX) {
        Set<Integer> zs = new HashSet<>();
        for (BlockPos pos : result.roadBlocks) {
            if (pos.getX() >= minX && pos.getX() <= maxX) {
                zs.add(pos.getZ());
            }
        }
        if (zs.isEmpty()) {
            for (var entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getX() >= minX && pos.getX() <= maxX
                        && result.placementLayers.get(pos) == RoadSolidLayer.ROAD) {
                    zs.add(pos.getZ());
                }
            }
        }
        return zs;
    }

    private static RoadSystemConfig flatConfig() {
        RoadSystemConfig config = new RoadSystemConfig("phase243-ig");
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setLaneDividers(false);
        config.setIncludeSlopeBatter(false);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(2.0);
        return config;
    }

    private static RoadCrossSection sectionWithTravelWidth(int travelWidth) {
        RoadCrossSection section = new RoadCrossSection();
        section.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        section.getMarkings().setLaneDividers(false);
        int lanes = travelWidth <= 6 ? 1 : (travelWidth <= 12 ? 2 : 4);
        int laneW = Math.max(1, travelWidth / lanes);
        section.getCarriageway().setLaneCount(lanes);
        section.getCarriageway().applyUniformDefaultLaneWidth(laneW);
        return section;
    }

    private static Road createSquareLoop(RoadNetwork network, String id, double side) {
        Road road = network.createRoad(id);
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(side, 0));
        RoadNode c = network.createNode(new Vec2d(side, side));
        RoadNode d = network.createNode(new Vec2d(0, side));
        RoadEdge e0 = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(side, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(),
            List.of(new Vec2d(side, 0), new Vec2d(side, side)), road.getId());
        network.createEdge(c.getId(), d.getId(),
            List.of(new Vec2d(side, side), new Vec2d(0, side)), road.getId());
        network.createEdge(d.getId(), a.getId(),
            List.of(new Vec2d(0, side), new Vec2d(0, 0)), road.getId());
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(0, 0), e0.getId(), 0.0));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        return road;
    }
}
