package com.plot.plugin.road.vertical;

import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.station.OrientedRoadSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelVerticalProfileTest {

    @Test void arbitraryQueriesReadOnePrecomputedStepSequence() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(30, 72)));
        VoxelVerticalProfile profile = VoxelVerticalProfile.fromAlignment(alignment);

        assertEquals(70, profile.elevationAt(7.99));
        assertEquals(71, profile.elevationAt(8.0));
        assertEquals(profile.elevationAt(8.2), profile.elevationAt(8.99));
        for (int i = 1; i < profile.sampleCount(); i++) {
            int[] values = profile.elevations();
            assertTrue(Math.abs(values[i] - values[i - 1]) <= 1);
        }
    }

    @Test void adjacentEdgesShareRoadGlobalVoxelPhaseInBothDirections() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(40, 74)));
        VoxelVerticalProfile profile = VoxelVerticalProfile.fromAlignment(alignment);
        DesignElevationSource first = new DesignElevationSource(
            alignment,
            new OrientedRoadSegment("a", true, "n0", "n1", 0, 20),
            20,
            profile);
        DesignElevationSource secondReversed = new DesignElevationSource(
            alignment,
            new OrientedRoadSegment("b", false, "n2", "n1", 20, 20),
            20,
            profile);

        assertSame(first.voxelProfile(), secondReversed.voxelProfile());
        assertEquals(first.elevationAtChainage(20), secondReversed.elevationAtLocalDistance(20));
        assertEquals(profile.elevationAt(40), secondReversed.elevationAtLocalDistance(0));
    }
}
