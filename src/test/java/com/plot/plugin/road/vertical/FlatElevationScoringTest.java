package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatElevationScoringTest {
    private static final RoadSystemConfig CONFIG = new RoadSystemConfig("test");

    @Test
    void bridgeCandidateDoesNotAlsoPayFillCost() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        SegmentHeightInfo info = new SegmentHeightInfo(segment, 60, 60, 80, 80, 0.0);
        ConstructionDetection detection = new ConstructionDetection(
            List.of(),
            List.of(),
            List.of(RoadConstructionType.BRIDGE),
            List.of(20.0));

        FlatElevationConstructionMetrics.Metrics metrics =
            FlatElevationConstructionMetrics.aggregate(detection, List.of(info));
        assertEquals(0, metrics.fillVolume());
        assertEquals(0, metrics.cutVolume());
        assertEquals(20.0, metrics.bridgeLength(), 1e-6);
        assertEquals(1, metrics.bridgeRunCount());

        var costConfig = RoadConstructionEvaluator.RoadConstructionScoreConfig.from(CONFIG);
        double score = FlatElevationConstructionMetrics.score(metrics, costConfig, 0.0);
        double expected = costConfig.bridgeBasePenalty() + costConfig.bridgeLengthPenalty() * 20.0;
        assertEquals(expected, score, 1e-6);
    }

    @Test
    void twoBridgeRunsPayTwoBaseCosts() {
        List<RoadConstructionType> types = List.of(
            RoadConstructionType.BRIDGE,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.ROAD,
            RoadConstructionType.ROAD,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.BRIDGE);
        List<Double> distances = List.of(10.0, 10.0, 25.0, 25.0, 10.0, 10.0);
        List<SegmentHeightInfo> infos = List.of(
            segmentInfo(distances.get(0), 60, 80),
            segmentInfo(distances.get(1), 60, 80),
            segmentInfo(distances.get(2), 80, 80),
            segmentInfo(distances.get(3), 80, 80),
            segmentInfo(distances.get(4), 60, 80),
            segmentInfo(distances.get(5), 60, 80));
        ConstructionDetection detection = new ConstructionDetection(
            List.of(), List.of(), types, distances);

        FlatElevationConstructionMetrics.Metrics metrics =
            FlatElevationConstructionMetrics.aggregate(detection, infos);
        assertEquals(0, metrics.fillVolume());
        assertEquals(2, metrics.bridgeRunCount());
        assertEquals(40.0, metrics.bridgeLength(), 1e-6);

        var costConfig = RoadConstructionEvaluator.RoadConstructionScoreConfig.from(CONFIG);
        double score = FlatElevationConstructionMetrics.score(metrics, costConfig, 0.0);
        double expected = 2 * costConfig.bridgeBasePenalty() + costConfig.bridgeLengthPenalty() * 40.0;
        assertEquals(expected, score, 1e-6);
    }

    @Test
    void twoTunnelRunsPayTwoBaseCosts() {
        List<RoadConstructionType> types = List.of(
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.ROAD,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL);
        List<Double> distances = List.of(8.0, 8.0, 30.0, 12.0, 12.0);
        List<SegmentHeightInfo> infos = List.of(
            segmentInfo(distances.get(0), 80, 60),
            segmentInfo(distances.get(1), 80, 60),
            segmentInfo(distances.get(2), 60, 60),
            segmentInfo(distances.get(3), 80, 60),
            segmentInfo(distances.get(4), 80, 60));
        ConstructionDetection detection = new ConstructionDetection(
            List.of(), List.of(), types, distances);

        FlatElevationConstructionMetrics.Metrics metrics =
            FlatElevationConstructionMetrics.aggregate(detection, infos);
        assertEquals(0, metrics.cutVolume());
        assertEquals(2, metrics.tunnelRunCount());
        assertEquals(40.0, metrics.tunnelLength(), 1e-6);

        var costConfig = RoadConstructionEvaluator.RoadConstructionScoreConfig.from(CONFIG);
        double score = FlatElevationConstructionMetrics.score(metrics, costConfig, 0.0);
        double expected = 2 * costConfig.tunnelBasePenalty() + costConfig.tunnelLengthPenalty() * 40.0;
        assertEquals(expected, score, 1e-6);
    }

    @Test
    void tunnelCandidateDoesNotAlsoPayCutCost() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(15, 0));
        SegmentHeightInfo info = new SegmentHeightInfo(segment, 80, 80, 60, 60, 0.0);
        ConstructionDetection detection = new ConstructionDetection(
            List.of(),
            List.of(),
            List.of(RoadConstructionType.TUNNEL),
            List.of(15.0));

        FlatElevationConstructionMetrics.Metrics metrics =
            FlatElevationConstructionMetrics.aggregate(detection, List.of(info));
        assertEquals(0, metrics.fillVolume());
        assertEquals(0, metrics.cutVolume());
        assertEquals(15.0, metrics.tunnelLength(), 1e-6);
        assertEquals(1, metrics.tunnelRunCount());

        var costConfig = RoadConstructionEvaluator.RoadConstructionScoreConfig.from(CONFIG);
        double score = FlatElevationConstructionMetrics.score(metrics, costConfig, 0.0);
        double expected = costConfig.tunnelBasePenalty() + costConfig.tunnelLengthPenalty() * 15.0;
        assertEquals(expected, score, 1e-6);
    }

    @Test
    void recommendationInvalidatesWhenJunctionElevationChanges() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoads().values().iterator().next();
        String endNodeId = network.getEdges().values().iterator().next().getEndNodeId();
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0, Map.of(endNodeId, 70.0)));
        road.setVerticalMode(RoadVerticalMode.FLAT);

        int before = FlatElevationRecommendationSignature.contextSignature(network, road, CONFIG);
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0, Map.of(endNodeId, 74.0)));
        int after = FlatElevationRecommendationSignature.contextSignature(network, road, CONFIG);
        assertNotEquals(before, after);
    }

    @Test
    void stageBMayReorderStageACandidates() {
        RoadNetwork network = straightRoad(120.0);
        Road road = network.getRoads().values().iterator().next();
        String endNodeId = network.getEdges().values().iterator().next().getEndNodeId();
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0, Map.of(endNodeId, 72.0)));
        road.setVerticalMode(RoadVerticalMode.FLAT);

        TerrainSampler ramp = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return (int) Math.round(58.0 + (planPoint.x / 120.0) * 12.0);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= sampleSurfaceY(new Vec2d(worldX, worldZ));
            }
        };

        double maxGrade = road.getEffectiveMaxSlope(CONFIG);
        FlatVerticalIntent intent = road.getFlatVerticalIntent().copy();
        var costConfig = RoadConstructionEvaluator.RoadConstructionScoreConfig.from(CONFIG);
        double roadLength = RoadStationing.canonicalLength(network, road);

        boolean reorderObserved = false;
        for (int low = 62; low <= 66 && !reorderObserved; low++) {
            for (int high = low + 1; high <= 70; high++) {
                FlatElevationCandidate stageALow = evaluateStageA(network, road, ramp, low, maxGrade, intent, costConfig, roadLength);
                FlatElevationCandidate stageAHigh = evaluateStageA(network, road, ramp, high, maxGrade, intent, costConfig, roadLength);
                if (!stageALow.feasible() || !stageAHigh.feasible()) {
                    continue;
                }
                boolean stageAPrefersLow = stageALow.score() < stageAHigh.score();
                FlatElevationCandidate stageBLow = FlatElevationRefinementEvaluator.refine(
                    network, road, ramp, CONFIG, low, maxGrade, intent, costConfig, roadLength, 0.0);
                FlatElevationCandidate stageBHigh = FlatElevationRefinementEvaluator.refine(
                    network, road, ramp, CONFIG, high, maxGrade, intent, costConfig, roadLength, 0.0);
                if (!stageBLow.feasible() || !stageBHigh.feasible()) {
                    continue;
                }
                boolean stageBPrefersLow = stageBLow.score() < stageBHigh.score();
                if (stageAPrefersLow != stageBPrefersLow) {
                    reorderObserved = true;
                    break;
                }
            }
        }
        assertTrue(reorderObserved);
    }

    private static FlatElevationCandidate evaluateStageA(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            int candidateY,
            double maxGrade,
            FlatVerticalIntent intent,
            RoadConstructionEvaluator.RoadConstructionScoreConfig costConfig,
            double roadLength) {
        if (!FlatElevationOptimizer.isJunctionFeasible(
                network, road, candidateY, maxGrade, intent, roadLength)) {
            return FlatElevationCandidate.infeasible(candidateY);
        }
        var samples = FlatElevationOptimizer.sampleRoadTerrainSegments(network, road, terrain, CONFIG);
        var types = RoadConstructionEvaluator.evaluatePath(
            samples.stream().map(FlatElevationOptimizer.TerrainSegmentSample::segmentLength).toList(),
            samples.stream().map(FlatElevationOptimizer.TerrainSegmentSample::groundY).toList(),
            samples.stream().map(sample -> candidateY).toList(),
            costConfig,
            RoadConstructionHeuristics.MIN_STRUCTURE_RUN);
        int cutVolume = 0;
        int fillVolume = 0;
        double bridgeLength = 0.0;
        double tunnelLength = 0.0;
        for (int i = 0; i < types.size(); i++) {
            double distance = samples.get(i).segmentLength();
            int diff = candidateY - samples.get(i).groundY();
            var totals = FlatElevationConstructionMetrics.accumulateSegment(types.get(i), diff, distance);
            cutVolume += totals.cutVolume();
            fillVolume += totals.fillVolume();
            bridgeLength += totals.bridgeLength();
            tunnelLength += totals.tunnelLength();
        }
        double score = FlatElevationConstructionMetrics.score(
            FlatElevationConstructionMetrics.fromStageA(
                types, cutVolume, fillVolume, bridgeLength, tunnelLength),
            costConfig,
            0.0);
        return new FlatElevationCandidate(
            candidateY, score, cutVolume, fillVolume, bridgeLength, tunnelLength,
            cutVolume + fillVolume, true);
    }

    private static SegmentHeightInfo segmentInfo(double distance, int ground, int target) {
        return new SegmentHeightInfo(
            new PathSegment(new Vec2d(0, 0), new Vec2d(distance, 0)),
            ground,
            ground,
            target,
            target,
            0.0);
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }
}
