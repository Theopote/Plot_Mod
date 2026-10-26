package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSlopeUtilsTest {

    @Test
    void slopeWithinLimitUsesGroundEnd() {
        int targetEnd = RoadSlopeUtils.computeTargetEndHeight(
            64, 64, 65, 20.0, 10.0f);

        assertEquals(65, targetEnd);
    }

    @Test
    void slopeExceedingLimitClampsRise() {
        // 10% over 10 blocks allows at most 1 block rise
        int targetEnd = RoadSlopeUtils.computeTargetEndHeight(
            64, 64, 80, 10.0, 10.0f);

        assertEquals(65, targetEnd);
    }

    @Test
    void slopeExceedingLimitClampsFall() {
        int targetEnd = RoadSlopeUtils.computeTargetEndHeight(
            64, 64, 50, 10.0, 10.0f);

        assertEquals(63, targetEnd);
    }

    @Test
    void averageJunctionHeightUsesMeanOfConnectedEdges() {
        assertEquals(66, RoadSlopeUtils.averageJunctionHeight(List.of(64, 66, 68)));
        assertEquals(64, RoadSlopeUtils.averageJunctionHeight(List.of()));
    }

    @Test
    void resolveJunctionHeightReportsSpreadAndMean() {
        RoadSlopeUtils.JunctionHeightResolution resolution =
            RoadSlopeUtils.resolveJunctionHeight(List.of(77, 77, 73, 73));
        assertEquals(75, resolution.height());
        assertEquals(73, resolution.min());
        assertEquals(77, resolution.max());
        assertEquals(4, resolution.spread());
        assertTrue(resolution.isSignificantMismatch());

        RoadSlopeUtils.JunctionHeightResolution tight =
            RoadSlopeUtils.resolveJunctionHeight(List.of(70, 71, 70));
        assertEquals(70, tight.height());
        assertFalse(tight.isSignificantMismatch());
    }

    @Test
    void actualSlopePercentMatchesTargetDelta() {
        double slope = RoadSlopeUtils.computeActualSlopePercent(64, 66, 10.0);
        assertEquals(20.0, slope, 1e-6);
    }

    @Test
    void slopeProfileFindsFillIntersectionWithSlopedGround() {
        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            70,
            -1,
            offset -> 64 + offset,
            2.0f,
            32
        );

        assertEquals(5, profile.size());
        assertEquals(0, profile.getFirst()[0]);
        assertEquals(70, profile.getFirst()[1]);
        assertEquals(1, profile.get(1)[0]);
        assertEquals(69, profile.get(1)[1]);
        assertEquals(2, profile.get(2)[0]);
        assertEquals(69, profile.get(2)[1]);
        assertEquals(3, profile.get(3)[0]);
        assertEquals(68, profile.get(3)[1]);
        assertEquals(4, profile.get(4)[0]);
        assertEquals(68, profile.get(4)[1]);
    }

    @Test
    void slopeProfileReturnsOnlyStartOnFlatGround() {
        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            70,
            -1,
            offset -> 70,
            2.0f,
            32
        );

        assertEquals(1, profile.size());
        assertEquals(70, profile.getFirst()[1]);
    }

    @Test
    void elevationAccumulatorClimbsAfterFractionalRemainderBuildsUp() {
        RoadSlopeUtils.ElevationAccumulator accumulator = new RoadSlopeUtils.ElevationAccumulator();
        int height = 64;
        for (int i = 0; i < 10; i++) {
            height = RoadSlopeUtils.computeTargetEndHeight(
                height, 64, 80, 1.0, 10.0f, accumulator);
        }
        assertEquals(65, height);
    }

}
