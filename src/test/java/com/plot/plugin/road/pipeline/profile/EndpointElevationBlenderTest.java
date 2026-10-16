package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointElevationBlenderTest {

    @Test
    void blendsTowardSnapAtCenter() {
        EndpointElevationSnap snap = new EndpointElevationSnap(new Vec2d(0, 0), 70, 10.0);

        assertEquals(70, EndpointElevationBlender.blend(new Vec2d(0, 0), snap, 64));
    }

    @Test
    void leavesElevationUnchangedOutsideBlendRadius() {
        EndpointElevationSnap snap = new EndpointElevationSnap(new Vec2d(0, 0), 70, 5.0);

        assertEquals(64, EndpointElevationBlender.blend(new Vec2d(10, 0), snap, 64));
    }

    @Test
    void coreRadiusHoldsSnapElevationAcrossPlazaFootprint() {
        EndpointElevationSnap snap = new EndpointElevationSnap(new Vec2d(0, 0), 68, 20.0, 12.0);

        assertEquals(68, EndpointElevationBlender.blend(new Vec2d(0, 0), snap, 80));
        assertEquals(68, EndpointElevationBlender.blend(new Vec2d(12, 0), snap, 80));
        int midTransition = EndpointElevationBlender.blend(new Vec2d(16, 0), snap, 80);
        assertTrue(midTransition > 68 && midTransition < 80, "got " + midTransition);
        assertEquals(80, EndpointElevationBlender.blend(new Vec2d(20, 0), snap, 80));
    }
}

class EndpointElevationSnapResolverTest {

    @Test
    void returnsNullWhenNoNetworkElevations() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));

        assertNull(EndpointElevationSnapResolver.resolve(node, node, null, 5.0));
        assertNull(EndpointElevationSnapResolver.resolve(node, node, Map.of(), 5.0));
    }

    @Test
    void buildsSnapsForResolvedNodeElevations() {
        RoadNode start = new RoadNode(new Vec2d(0, 0));
        RoadNode end = new RoadNode(new Vec2d(10, 0));

        EndpointElevationSnaps snaps = EndpointElevationSnapResolver.resolve(
            start,
            end,
            Map.of(start.getId(), 68, end.getId(), 72),
            6.0);

        assertEquals(68, snaps.start().elevation());
        assertEquals(72, snaps.end().elevation());
        assertEquals(6.0, snaps.start().blendRadius(), 1e-9);
    }

    @Test
    void resolveForEdgeUsesBuildElevationsInsteadOfUnderpassNodeMap() {
        RoadNode start = new RoadNode(new Vec2d(0, 0));
        RoadNode end = new RoadNode(new Vec2d(20, 0));
        int underpassY = 70;
        int overpassY = 73;

        EndpointElevationSnaps fromNodeMap = EndpointElevationSnapResolver.resolve(
            start,
            end,
            Map.of(start.getId(), underpassY, end.getId(), 64),
            6.0);
        assertEquals(underpassY, fromNodeMap.start().elevation());

        EndpointElevationSnaps fromBuild = EndpointElevationSnapResolver.resolveForEdge(
            start, end, overpassY, 64, 6.0);
        assertEquals(overpassY, fromBuild.start().elevation());
        assertEquals(64, fromBuild.end().elevation());
        assertEquals(
            overpassY,
            EndpointElevationBlender.blend(start.getPosition(), fromBuild.start(), overpassY));
    }

    @Test
    void resolveForEdgeReturnsNullWhenBothBuildElevationsMissing() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));

        assertNull(EndpointElevationSnapResolver.resolveForEdge(node, node, null, null, 5.0));
    }

    @Test
    void blendRadiusScalesWithUnitsPerBlock() {
        double narrow = EndpointElevationSnapResolver.blendRadius(4.0, 1.0);
        double wide = EndpointElevationSnapResolver.blendRadius(4.0, 8.0);
        assertTrue(wide >= 8.0 * 8.0, "transition band must span at least 8 blocks in canvas units");
        assertTrue(wide > narrow);
    }
}
