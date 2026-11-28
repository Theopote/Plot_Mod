package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.FlatTerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkPreviewSessionTest {

    private RoadNetworkGenerator generator;
    private RoadNetwork network;

    @BeforeEach
    void setUp() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        generator = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        RoadNode c = network.createNode(new Vec2d(20, 20));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(20, 0), new Vec2d(20, 20)), road.getId());
    }

    @Test
    void zeroBudgetYieldsWithoutSkippingUnprocessedEdgesOrJunctions() {
        var session = generator.beginPreviewSession(network, new FlatTerrainSampler(64));
        generator.preparePreviewSession(session);
        int count = 0;
        while (session.hasMoreEdges()) {
            assertEquals(1, generator.tickPreviewSessionEdges(session, 100, 0));
            count++;
        }
        assertEquals(2, count);
        while (session.hasMoreJunctions()) {
            assertEquals(1, generator.tickPreviewSessionJunctions(session, 100, 0));
        }
        var result = generator.completePreviewSession(session);
        assertEquals(2, result.networkResult().successEdgeCount());
    }

    @Test
    void incrementalSessionMatchesSyncGenerateAll() {
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetworkGenerator.NetworkGenerationResult sync = generator.generateAll(network, terrain);

        RoadNetworkPreviewSession session = generator.beginPreviewSession(network, terrain);
        assertTrue(session.isValid());
        generator.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            generator.tickPreviewSessionEdges(session, 1);
        }
        generator.applyPreviewSessionTunnelConnectivity(session);
        while (session.hasMoreJunctions()) {
            generator.tickPreviewSessionJunctions(session, 1);
        }
        RoadNetworkGenerator.PreviewResult incremental = generator.completePreviewSession(session);

        assertEquals(sync.totalEdgeCount(), incremental.networkResult().totalEdgeCount());
        assertEquals(sync.successEdgeCount(), incremental.networkResult().successEdgeCount());
        assertFalse(incremental.aggregate().placementRecords.isEmpty());
    }

    /** VB01: preparePreviewSession fail-closes illegal EDITED without demoting source. */
    @Test
    void preparePreviewFailsClosedForIllegalEdited() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road road = localNetwork.createRoad("partial");
        road.setWidth(6);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(20.0, 80.0),
            PointOfVerticalIntersection.of(80.0, 90.0))));
        RoadNode n1 = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode n2 = localNetwork.createNode(new Vec2d(100, 0));
        localNetwork.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        ResolvedProfileAuthority.claimEditedWithoutValidation(localNetwork, road);

        var session = local.beginPreviewSession(localNetwork, new FlatTerrainSampler(64));
        local.preparePreviewSession(session);

        assertTrue(session.networkResult().hasPartialFailure());
        assertEquals(ProfileSource.EDITED, localNetwork.getRoad("partial").getEffectiveProfileSource());
        assertEquals(ProfileSource.EDITED,
            session.generationNetwork().getRoad("partial").getEffectiveProfileSource());
    }

    /** VB04: junction hard conflicts make preview not apply-able. */
    @Test
    void preparePreviewFailsClosedForEditedJunctionConflict() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road roadA = localNetwork.createRoad("a");
        roadA.setWidth(6);
        roadA.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadA.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road roadB = localNetwork.createRoad("b");
        roadB.setWidth(6);
        roadB.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 70.0))));
        RoadNode shared = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode endA = localNetwork.createNode(new Vec2d(40, 0));
        RoadNode endB = localNetwork.createNode(new Vec2d(0, 40));
        localNetwork.createEdge(
            shared.getId(), endA.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        localNetwork.createEdge(
            shared.getId(), endB.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadB.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, roadA);
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, roadB);

        var session = local.beginPreviewSession(localNetwork, new FlatTerrainSampler(64));
        local.preparePreviewSession(session);

        assertTrue(session.networkResult().hasPartialFailure());
    }

    /** VB07: steep EDITED grades are not rewritten during preparePreviewSession. */
    @Test
    void preparePreviewDoesNotSmoothSteepEditedPvis() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road road = localNetwork.createRoad("steep");
        road.setWidth(6);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(20.0, 84.0))));
        RoadNode n1 = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode n2 = localNetwork.createNode(new Vec2d(20, 0));
        localNetwork.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, road);

        var session = local.beginPreviewSession(localNetwork, new FlatTerrainSampler(64));
        local.preparePreviewSession(session);

        Road snapRoad = session.generationNetwork().getRoad("steep");
        assertEquals(64.0, snapRoad.getVerticalAlignment().getPvis().getFirst().getElevation(), 1e-9);
        assertEquals(84.0, snapRoad.getVerticalAlignment().getPvis().getLast().getElevation(), 1e-9);
        assertEquals(64.0,
            localNetwork.getRoad("steep").getVerticalAlignment().getPvis().getFirst().getElevation(), 1e-9);
        assertEquals(84.0,
            localNetwork.getRoad("steep").getVerticalAlignment().getPvis().getLast().getElevation(), 1e-9);
    }

    /** VC01: illegal EDITED full session — failed edges must not re-enter generation as SUCCESS. */
    @Test
    void illegalEditedFullSessionKeepsFailedEdgesOutOfSuccess() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road road = localNetwork.createRoad("partial");
        road.setWidth(6);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(20.0, 80.0),
            PointOfVerticalIntersection.of(80.0, 90.0))));
        RoadNode n1 = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode n2 = localNetwork.createNode(new Vec2d(100, 0));
        RoadEdge edge = localNetwork.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        ResolvedProfileAuthority.claimEditedWithoutValidation(localNetwork, road);

        var session = local.beginPreviewSession(localNetwork, new FlatTerrainSampler(64));
        local.preparePreviewSession(session);
        assertTrue(session.networkResult().getFailedEdgeIds().contains(edge.getId()));
        while (session.hasMoreEdges()) {
            local.tickPreviewSessionEdges(session, 1);
        }
        assertTrue(session.networkResult().getFailedEdgeIds().contains(edge.getId()));
        assertEquals(0, session.networkResult().successEdgeCount());
        assertFalse(session.networkResult().getEdgeResults().containsKey(edge.getId()));
    }

    /** VC03: hard-conflict edges are not both SUCCESS and FAILED. */
    @Test
    void hardConflictEdgesAreNotAlsoSuccessful() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road roadA = localNetwork.createRoad("a");
        roadA.setWidth(6);
        roadA.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadA.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road roadB = localNetwork.createRoad("b");
        roadB.setWidth(6);
        roadB.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 70.0))));
        RoadNode shared = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode endA = localNetwork.createNode(new Vec2d(40, 0));
        RoadNode endB = localNetwork.createNode(new Vec2d(0, 40));
        localNetwork.createEdge(
            shared.getId(), endA.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        localNetwork.createEdge(
            shared.getId(), endB.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadB.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, roadA);
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, roadB);

        RoadNetworkGenerator.NetworkGenerationResult sync =
            local.generateAll(localNetwork, new FlatTerrainSampler(64));
        assertTrue(sync.hasPartialFailure());
        assertFalse(sync.getFailedEdgeIds().isEmpty());
        for (String failedId : sync.getFailedEdgeIds()) {
            assertFalse(sync.getEdgeResults().containsKey(failedId),
                "failed edge must not publish SUCCESS geometry: " + failedId);
        }
        assertEquals(0, sync.successEdgeCount());
    }

    /** VC13: sync and incremental preview agree on failure closure. */
    @Test
    void syncAndIncrementalAgreeOnPreflightFailure() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road road = localNetwork.createRoad("partial");
        road.setWidth(6);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(20.0, 80.0),
            PointOfVerticalIntersection.of(80.0, 90.0))));
        RoadNode n1 = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode n2 = localNetwork.createNode(new Vec2d(100, 0));
        localNetwork.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        ResolvedProfileAuthority.claimEditedWithoutValidation(localNetwork, road);

        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        RoadNetworkGenerator.NetworkGenerationResult sync = local.generateAll(localNetwork, terrain);

        var session = local.beginPreviewSession(localNetwork, terrain);
        local.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            local.tickPreviewSessionEdges(session, 1);
        }
        local.applyPreviewSessionTunnelConnectivity(session);
        while (session.hasMoreJunctions()) {
            local.tickPreviewSessionJunctions(session, 1);
        }
        RoadNetworkGenerator.PreviewResult incremental = local.completePreviewSession(session);

        assertTrue(sync.hasPartialFailure());
        assertTrue(incremental.networkResult().hasPartialFailure());
        assertEquals(sync.getFailedEdgeIds(), incremental.networkResult().getFailedEdgeIds());
        assertEquals(sync.successEdgeCount(), incremental.networkResult().successEdgeCount());
        // VD-T11: blocked edges publish no SUCCESS placement geometry.
        for (String failedId : sync.getFailedEdgeIds()) {
            assertFalse(sync.getEdgeResults().containsKey(failedId));
            assertFalse(incremental.networkResult().getEdgeResults().containsKey(failedId));
        }
    }

    /** VD-T09 / VE-T06: EDITED + AUTO junction — seam columns share design Y. */
    @Test
    void editedAndAutoJunctionFinalPavementSharesDesignY() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();
        Road edited = localNetwork.createRoad("edited");
        edited.setWidth(6);
        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road auto = localNetwork.createRoad("auto");
        auto.setWidth(6);
        RoadNode shared = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode endEdited = localNetwork.createNode(new Vec2d(40, 0));
        RoadNode endAuto = localNetwork.createNode(new Vec2d(0, 40));
        localNetwork.createEdge(
            shared.getId(), endEdited.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), edited.getId());
        localNetwork.createEdge(
            shared.getId(), endAuto.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), auto.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, edited);
        shared.setManualElevation(70.0, com.plot.plugin.road.model.ManualElevationOrigin.PROFILE_PROPAGATED);

        RoadNetworkGenerator.PreviewResult preview =
            local.generatePreview(localNetwork, new FlatTerrainSampler(64));
        assertFalse(preview.networkResult().hasPartialFailure());
        Integer sharedY = preview.networkResult().getNodeElevations().get(shared.getId());
        assertEquals(80, sharedY);

        assertTrue(assertSeamPavementContinuous(preview.aggregate(), 80),
            "expected seam columns must keep continuous pavement at design Y");
    }

    /** VE-T06 negative: missing/wrong-Y seam column fails the continuity helper. */
    @Test
    void seamPavementHelperFailsOnMissingOrWrongY() {
        RoadGenerationResult empty = new RoadGenerationResult(0);
        assertFalse(assertSeamPavementContinuous(empty, 80));

        RoadGenerationResult wrongY = new RoadGenerationResult(0);
        wrongY.roadBlocks.add(new BlockPos(0, 70, 0));
        wrongY.roadBlocks.add(new BlockPos(1, 70, 0));
        wrongY.roadBlocks.add(new BlockPos(2, 70, 0));
        wrongY.roadBlocks.add(new BlockPos(0, 70, 1));
        wrongY.roadBlocks.add(new BlockPos(0, 70, 2));
        assertFalse(assertSeamPavementContinuous(wrongY, 80));
    }

    /**
     * VE-T07: mixed legal + illegal EDITED — sync and incremental agree on failures
     * and successful placement keys/block ids.
     */
    @Test
    void mixedLegalIllegalSyncAndIncrementalPlacementsMatch() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        RoadNetworkGenerator local = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetwork localNetwork = new RoadNetwork();

        Road legal = localNetwork.createRoad("legal");
        legal.setWidth(6);
        legal.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        legal.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 70.0))));
        RoadNode l1 = localNetwork.createNode(new Vec2d(0, 0));
        RoadNode l2 = localNetwork.createNode(new Vec2d(40, 0));
        localNetwork.createEdge(l1.getId(), l2.getId(), List.of(l1.getPosition(), l2.getPosition()), legal.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(localNetwork, legal);

        Road illegal = localNetwork.createRoad("illegal");
        illegal.setWidth(6);
        illegal.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        illegal.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(10.0, 90.0),
            PointOfVerticalIntersection.of(30.0, 95.0))));
        RoadNode i1 = localNetwork.createNode(new Vec2d(0, 40));
        RoadNode i2 = localNetwork.createNode(new Vec2d(40, 40));
        localNetwork.createEdge(i1.getId(), i2.getId(), List.of(i1.getPosition(), i2.getPosition()), illegal.getId());
        ResolvedProfileAuthority.claimEditedWithoutValidation(localNetwork, illegal);

        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        RoadNetworkGenerator.NetworkGenerationResult sync = local.generateAll(localNetwork, terrain);

        var session = local.beginPreviewSession(localNetwork, terrain);
        local.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            local.tickPreviewSessionEdges(session, 1);
        }
        local.applyPreviewSessionTunnelConnectivity(session);
        while (session.hasMoreJunctions()) {
            local.tickPreviewSessionJunctions(session, 1);
        }
        RoadNetworkGenerator.PreviewResult incremental = local.completePreviewSession(session);

        assertTrue(sync.hasPartialFailure());
        assertEquals(sync.getFailedEdgeIds(), incremental.networkResult().getFailedEdgeIds());
        assertEquals(sync.successEdgeCount(), incremental.networkResult().successEdgeCount());
        assertFalse(sync.getFailedEdgeIds().isEmpty());
        assertTrue(sync.successEdgeCount() > 0);

        assertEquals(
            placementFingerprint(sync.getEdgeResults()),
            placementFingerprint(incremental.networkResult().getEdgeResults()));
    }

    /** Expected near-junction seam columns on both arms (not the whole corridor). */
    static boolean assertSeamPavementContinuous(RoadGenerationResult aggregate, int expectedY) {
        if (aggregate == null) {
            return false;
        }
        int[][] columns = {
            {0, 0}, {1, 0}, {2, 0},
            {0, 1}, {0, 2}
        };
        for (int[] column : columns) {
            boolean found = false;
            for (var pos : aggregate.roadBlocks) {
                if (pos.getX() == column[0] && pos.getZ() == column[1] && pos.getY() == expectedY) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static String placementFingerprint(
            java.util.Map<String, RoadGenerationResult> edgeResults) {
        StringBuilder sb = new StringBuilder();
        edgeResults.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                sb.append(entry.getKey()).append(':');
                entry.getValue().placementRecords.entrySet().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().asLong()))
                    .forEach(e -> sb.append(e.getKey().asLong())
                        .append('=')
                        .append(e.getValue().newBlockId)
                        .append(';'));
                sb.append('|');
            });
        return sb.toString();
    }
}

