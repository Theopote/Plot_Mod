package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void chainedHeightsCarryForwardPreviousTarget() {
        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(10.0, 10.0),
            List.of(64, 64),
            List.of(80, 80),
            List.of(10.0f, 10.0f),
            64
        );

        assertEquals(65, targetEnds.get(0));
        assertEquals(66, targetEnds.get(1));
    }

    @Test
    void chainedHeightsHonorManualStartElevation() {
        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(10.0),
            List.of(64),
            List.of(80),
            List.of(10.0f),
            70
        );

        assertEquals(71, targetEnds.getFirst());
    }

    @Test
    void chainedHeightsApplySlopeOverrideByMileage() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setMaxSlope(10.0f);

        RoadEdge edge = new RoadEdge(
            "edge-1", "n1", "n2",
            List.of(),
            null, null, null, null, null, null,
            null,
            List.of(new RoadEdge.SlopeOverride(0, 10, 5.0f))
        );

        float steepSegmentSlope = edge.getEffectiveMaxSlope(0, config);
        float gentleSegmentSlope = edge.getEffectiveMaxSlope(10.0001, config);

        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(10.0, 10.0),
            List.of(64, 64),
            List.of(80, 80),
            List.of(steepSegmentSlope, gentleSegmentSlope),
            64
        );

        assertEquals(64, targetEnds.get(0), "5% slope over 10 blocks allows only 0.5 block rise");
        assertEquals(65, targetEnds.get(1), "10% slope over 10 blocks allows 1 block rise after steep segment");
    }

    @Test
    void averageJunctionHeightUsesMeanOfConnectedEdges() {
        assertEquals(66, RoadSlopeUtils.averageJunctionHeight(List.of(64, 66, 68)));
        assertEquals(64, RoadSlopeUtils.averageJunctionHeight(List.of()));
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

        assertEquals(3, profile.size());
        assertEquals(0, profile.getFirst()[0]);
        assertEquals(70, profile.getFirst()[1]);
        assertEquals(2, profile.get(1)[0]);
        assertEquals(69, profile.get(1)[1]);
        assertEquals(4, profile.get(2)[0]);
        assertEquals(68, profile.get(2)[1]);
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
    void shortClimbMatchesLegacyWithoutSlopeLengthLimit() {
        List<Integer> legacy = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(10.0, 10.0, 10.0),
            List.of(64, 64, 64),
            List.of(80, 80, 80),
            List.of(10.0f, 10.0f, 10.0f),
            64
        );
        List<Integer> limited = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(10.0, 10.0, 10.0),
            List.of(64, 64, 64),
            List.of(80, 80, 80),
            List.of(10.0f, 10.0f, 10.0f),
            64,
            30.0,
            5.0,
            1.0f
        );

        assertEquals(legacy, limited);
    }

    @Test
    void longContinuousClimbInsertsRelaxedSlopeSegment() {
        List<Double> distances = List.of(50.0);
        List<Integer> groundStarts = List.of(64);
        List<Integer> groundEnds = List.of(114);
        List<Float> slopes = List.of(10.0f);

        List<Integer> steepOnly = RoadSlopeUtils.computeChainedTargetHeights(
            distances, groundStarts, groundEnds, slopes, 64
        );
        List<Integer> withRelax = RoadSlopeUtils.computeChainedTargetHeights(
            distances, groundStarts, groundEnds, slopes, 64,
            30.0, 5.0, 1.0f
        );

        assertEquals(69, steepOnly.getFirst());
        assertTrue(withRelax.getFirst() < steepOnly.getFirst());
    }
}
