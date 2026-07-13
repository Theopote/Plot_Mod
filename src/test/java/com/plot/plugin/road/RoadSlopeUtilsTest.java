package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
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

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        road.setMaxSlope(10.0f);
        RoadEdge edge = new RoadEdge(
            "edge-1", "n1", "n2",
            List.of(),
            road.getId(),
            List.of(new RoadEdge.SlopeOverride(0, 10, 5.0f))
        );

        float steepSegmentSlope = RoadModelUtils.getEffectiveMaxSlope(network, edge, config, 0);
        float gentleSegmentSlope = RoadModelUtils.getEffectiveMaxSlope(network, edge, config, 10.0001);

        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(10.0, 10.0),
            List.of(64, 64),
            List.of(80, 80),
            List.of(steepSegmentSlope, gentleSegmentSlope),
            64
        );

        assertEquals(65, targetEnds.get(0), "5% slope accumulates fractional rise over 10m");
        assertEquals(66, targetEnds.get(1), "10% slope continues climbing on the second segment");
    }

    @Test
    void averageGroundHeightUsesArithmeticMean() {
        assertEquals(66, RoadSlopeUtils.averageGroundHeight(List.of(64, 66, 68)));
        assertEquals(64, RoadSlopeUtils.averageGroundHeight(List.of()));
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
        List<Double> distances = List.of(100.0);
        List<Integer> groundStarts = List.of(64);
        List<Integer> groundEnds = List.of(164);
        List<Float> slopes = List.of(10.0f);

        List<Integer> steepOnly = RoadSlopeUtils.computeChainedTargetHeights(
            distances, groundStarts, groundEnds, slopes, 64
        );
        List<Integer> withRelax = RoadSlopeUtils.computeChainedTargetHeights(
            distances, groundStarts, groundEnds, slopes, 64,
            30.0, 5.0, 1.0f
        );

        assertEquals(74, steepOnly.getFirst());
        assertTrue(withRelax.getFirst() < steepOnly.getFirst());
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

    @Test
    void chainedHeightsAccumulateFractionalRiseAcrossShortSegments() {
        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
            List.of(64, 64, 64, 64, 64, 64, 64, 64, 64, 64),
            List.of(80, 80, 80, 80, 80, 80, 80, 80, 80, 80),
            List.of(10.0f, 10.0f, 10.0f, 10.0f, 10.0f, 10.0f, 10.0f, 10.0f, 10.0f, 10.0f),
            64
        );

        assertEquals(66, targetEnds.getLast());
    }

    @Test
    void chainedHeightsHonorManualEndElevation() {
        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(50.0),
            List.of(64),
            List.of(80),
            List.of(45.0f),
            60,
            70,
            0.0,
            0.0,
            0.0f
        );

        assertEquals(70, targetEnds.getLast());
    }

    @Test
    void chainedHeightsMatchLegacyWhenManualEndIsNull() {
        List<Double> distances = List.of(10.0, 10.0);
        List<Integer> groundStarts = List.of(64, 64);
        List<Integer> groundEnds = List.of(80, 80);
        List<Float> slopes = List.of(10.0f, 10.0f);

        List<Integer> legacy = RoadSlopeUtils.computeChainedTargetHeights(
            distances, groundStarts, groundEnds, slopes, 64, 30.0, 5.0, 1.0f);
        List<Integer> withNullEnd = RoadSlopeUtils.computeChainedTargetHeights(
            distances, groundStarts, groundEnds, slopes, 64, null, 30.0, 5.0, 1.0f);

        assertEquals(legacy, withNullEnd);
    }

    @Test
    void chainedHeightsHonorBothManualStartAndEnd() {
        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            List.of(100.0),
            List.of(64),
            List.of(80),
            List.of(45.0f),
            60,
            70,
            0.0,
            0.0,
            0.0f
        );

        assertEquals(70, targetEnds.getLast());
    }
}
