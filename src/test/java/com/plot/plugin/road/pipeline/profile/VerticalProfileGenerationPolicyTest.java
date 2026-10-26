package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalProfileGenerationPolicyTest {

    @Test
    void stripsNonAuthoritativeDesignProfileFromAutoSource() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.AUTO);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(network, road, 40.0);

        int stripped = VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertEquals(1, stripped);
        assertTrue(road.getVerticalAlignment() == null || road.getVerticalAlignment().isEmpty());
    }

    @Test
    void keepsEditedProfileOnGenerationSnapshot() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("confirmed");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(network, road, 40.0);
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        int stripped = VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertEquals(0, stripped);
        assertTrue(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road));
    }

    @Test
    void clearsStaleJunctionManualElevationWhenNotEditedAuthority() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("auto");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.AUTO);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(40, 0));
        // 无来源的便捷 API 现视为 USER；传播残留应被清除
        junction.setManualElevation(88.0, ManualElevationOrigin.PROFILE_PROPAGATED);
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertTrue(junction.getManualElevation() == null);
    }

    @Test
    void preservesJunctionManualElevationForEditedAuthority() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("confirmed");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(40, 0));
        junction.setManualElevation(88.0, ManualElevationOrigin.USER);
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertEquals(88.0, junction.getManualElevation());
    }

    @Test
    void clearsPropagatedJunctionElevationButPreservesUserLock() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("auto");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.AUTO);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode propagated = network.createNode(new Vec2d(40, 0));
        RoadNode userLocked = network.createNode(new Vec2d(80, 0));
        propagated.setManualElevation(88.0, ManualElevationOrigin.PROFILE_PROPAGATED);
        userLocked.setManualElevation(72.0, ManualElevationOrigin.USER);
        network.createEdge(
            start.getId(),
            userLocked.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertTrue(propagated.getManualElevation() == null);
        assertEquals(72.0, userLocked.getManualElevation());
        assertEquals(1, VerticalProfileGenerationPolicy.lastClearedPropagatedNodeLocks());
    }

    @Test
    void explicitAutoSelectionClearsPropagatedLocksWithoutDemotingEditedSource() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("stale-edited");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        end.setManualElevation(88.0, ManualElevationOrigin.PROFILE_PROPAGATED);
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyExplicitAutoProfileSelection(network);

        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        assertTrue(end.getManualElevation() == null);
        assertTrue(road.getVerticalAlignment() != null && !road.getVerticalAlignment().isEmpty());
    }

    @Test
    void generationSnapshotDemotesStaleEditedWithoutMutatingLiveCopy() {
        RoadNetwork live = new RoadNetwork();
        Road road = live.createRoad("stale-edited");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(live, road, 40.0);
        road.setProfileSource(ProfileSource.EDITED);

        RoadNetwork snapshot = live.snapshot();
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(snapshot);

        assertEquals(ProfileSource.AUTO, snapshot.getRoad("stale-edited").getEffectiveProfileSource());
        assertEquals(ProfileSource.EDITED, live.getRoad("stale-edited").getEffectiveProfileSource());
    }

    @Test
    void clearsStaleCrossingSharedElevationWhenRoadsAreAuto() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        roadA.setProfileSource(ProfileSource.AUTO);
        roadB.setProfileSource(ProfileSource.AUTO);
        roadA.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadB.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);

        Vec2d crossingPos = new Vec2d(50, 0);
        network.registerCrossing(new RoadCrossing(
            "x1",
            roadA.getId(),
            50.0,
            roadB.getId(),
            30.0,
            crossingPos,
            null,
            null,
            null,
            88.0));
        RoadNode junction = network.createNode(crossingPos);
        junction.setManualElevation(88.0, ManualElevationOrigin.CROSSING);

        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertTrue(network.getCrossing("x1").sharedElevation() == null);
        assertTrue(junction.getManualElevation() == null);
    }

    @Test
    void explicitAutoSelectionClearsDormantDesignProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("auto");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.AUTO);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        addStraightEdge(network, road, 40.0);

        VerticalProfileGenerationPolicy.applyExplicitAutoProfileSelection(network);

        assertTrue(road.getVerticalAlignment() == null || road.getVerticalAlignment().isEmpty());
    }

    @Test
    void preservesJunctionManualElevationForFlatRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(40, 0));
        junction.setManualElevation(72.0);
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());

        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);

        assertEquals(RoadVerticalMode.FLAT, road.getVerticalMode());
        assertEquals(72.0, junction.getManualElevation());
    }

    private static void addStraightEdge(RoadNetwork network, Road road, double length) {
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
    }
}
