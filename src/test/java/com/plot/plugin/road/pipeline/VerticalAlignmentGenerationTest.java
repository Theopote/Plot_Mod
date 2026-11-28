package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.plugin.road.pipeline.profile.RoadGeneratorProfileContext;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentGenerationTest {

    @Test
    void generationUsesDesignProfileInsteadOfFlatTerrain() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(10.0);

        RoadGenerator generator = new RoadGenerator(
            config, com.plot.test.world.IdentityCoordinateService.INSTANCE, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork withAlignment = buildRoadWithVerticalAlignment(80.0, 100.0);
        RoadNetwork withoutAlignment = buildRoadWithoutVerticalAlignment();

        RoadGenerationResult design = generateSingleEdge(generator, withAlignment, terrain);
        RoadGenerationResult terrainDriven = generateSingleEdge(generator, withoutAlignment, terrain);

        assertFalse(design.roadBlocks.isEmpty());
        int designY = design.roadBlocks.getFirst().getY();
        int terrainY = terrainDriven.roadBlocks.getFirst().getY();
        assertEquals(80, designY);
        assertEquals(64, terrainY);
    }

    /** VA-A04: confirmed single-road FLAT holds its base elevation through generation. */
    @Test
    void flatRoadHoldsConfirmedElevation() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(10.0);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat");
        road.setWidth(6);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 80.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        assertEquals(ResolvedProfileAuthority.Kind.FLAT,
            ResolvedProfileAuthority.resolve(network, road).kind());
        assertTrue(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road));

        RoadGenerator generator = new RoadGenerator(
            config, com.plot.test.world.IdentityCoordinateService.INSTANCE,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        RoadGenerationResult result = generateSingleEdge(generator, network, new FlatTerrainSampler(64));
        assertFalse(result.roadBlocks.isEmpty());
        assertEquals(80, result.roadBlocks.getFirst().getY());
    }

    /** VA-A02/A12: incomplete EDITED claim fails closed with no apply-able geometry. */
    @Test
    void incompleteEditedClaimFailsClosedWithoutGeometry() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("partial");
        road.setWidth(6);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(20.0, 80.0),
            PointOfVerticalIntersection.of(80.0, 90.0))));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        assertTrue(ResolvedProfileAuthority.isIllegalEditedClaim(network, road));

        RoadGeneratorProfileContext context =
            new RoadGeneratorProfileContext(config, (pathPoints, segments) -> 1.0);
        assertThrows(IllegalStateException.class, () ->
            context.solveEdgeProfile(
                List.of(new com.plot.plugin.road.pipeline.geometry.PathSegment(
                    new Vec2d(0, 0), new Vec2d(100, 0))),
                new FlatTerrainSampler(64),
                network,
                edge,
                n1,
                n2,
                null,
                null));
    }

    private static RoadNetwork buildRoadWithVerticalAlignment(double startElevation, double endElevation) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setWidth(6);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, startElevation),
            PointOfVerticalIntersection.of(100.0, endElevation)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        return network;
    }

    private static RoadNetwork buildRoadWithoutVerticalAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain");
        road.setWidth(6);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }

    private static RoadGenerationResult generateSingleEdge(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain) {
        RoadEdge edge = network.getEdges().values().iterator().next();
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        return generator.generateEdge(network, edge, start, end, terrain, null);
    }
}
