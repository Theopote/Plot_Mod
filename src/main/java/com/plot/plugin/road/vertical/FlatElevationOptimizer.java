package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadUniformElevationUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Finds flat-road baseline elevations that minimize construction modification along terrain.
 * Stage A scans integer Y candidates with {@link RoadConstructionEvaluator}; Stage B
 * re-scores Top-K with compiled flat profile + {@link FlatElevationRefinementEvaluator}.
 */
public final class FlatElevationOptimizer {
    private static final double DEFAULT_SAMPLE_SPACING = 1.0;
    private static final int SEARCH_MARGIN = 4;
    private static final int MEDIAN_RADIUS = 8;
    private static final int REFINE_TOP_K = 5;
    private static final int DISPLAY_TOP_K = 3;
    private static final double EPSILON = 1e-6;

    record TerrainSegmentSample(double segmentLength, int groundY) { }

    private FlatElevationOptimizer() {
    }

    public static FlatElevationRecommendation evaluate(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        if (road == null || terrain == null || config == null
                || !RoadStationing.isStationable(network, road)) {
            return FlatElevationRecommendation.empty();
        }

        List<TerrainSegmentSample> samples = sampleRoadTerrainSegments(network, road, terrain, config);
        if (samples.isEmpty()) {
            return FlatElevationRecommendation.empty();
        }

        double maxGrade = road.getEffectiveMaxSlope(config);
        double roadLength = RoadStationing.canonicalLength(network, road);
        FlatVerticalIntent intentTemplate = copyIntentTemplate(FlatVerticalIntentSupport.resolveIntent(network, road));
        int median = medianGround(samples);

        int minGround = samples.stream().mapToInt(TerrainSegmentSample::groundY).min().orElse(median);
        int maxGround = samples.stream().mapToInt(TerrainSegmentSample::groundY).max().orElse(median);
        int searchMin = Math.min(minGround - SEARCH_MARGIN, median - MEDIAN_RADIUS);
        int searchMax = Math.max(maxGround + SEARCH_MARGIN, median + MEDIAN_RADIUS);
        int[] constraintBounds = constraintElevationBounds(network, road, intentTemplate);
        if (constraintBounds != null) {
            searchMin = Math.min(searchMin, constraintBounds[0] - SEARCH_MARGIN);
            searchMax = Math.max(searchMax, constraintBounds[1] + SEARCH_MARGIN);
        }

        RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig =
            RoadConstructionEvaluator.RoadConstructionScoreConfig.from(config);
        List<FlatElevationCandidate> ranked = new ArrayList<>();
        for (int candidateY = searchMin; candidateY <= searchMax; candidateY++) {
            FlatElevationCandidate candidate = evaluateCandidate(
                network,
                road,
                samples,
                candidateY,
                maxGrade,
                intentTemplate,
                scoreConfig,
                RoadConstructionHeuristics.MIN_STRUCTURE_RUN,
                roadLength);
            if (candidate.feasible()) {
                ranked.add(candidate);
            }
        }

        ranked.sort(Comparator
            .comparingDouble(FlatElevationCandidate::score)
            .thenComparingInt(candidate -> Math.abs(candidate.elevation() - median))
            .thenComparingInt(FlatElevationCandidate::elevation));
        if (ranked.isEmpty()) {
            FlatElevationCandidate fallback = evaluateCandidate(
                network,
                road,
                samples,
                median,
                maxGrade,
                intentTemplate,
                scoreConfig,
                RoadConstructionHeuristics.MIN_STRUCTURE_RUN,
                RoadStationing.canonicalLength(network, road));
            FlatElevationCandidate best = fallback.feasible()
                ? fallback
                : medianFallbackCandidate(samples, median, scoreConfig, RoadConstructionHeuristics.MIN_STRUCTURE_RUN);
            return new FlatElevationRecommendation(best, List.of(best), samples.size());
        }

        List<FlatElevationCandidate> stageA = ranked.stream().limit(REFINE_TOP_K).toList();
        List<FlatElevationCandidate> refined = refineTopCandidates(
            network,
            road,
            terrain,
            config,
            stageA,
            maxGrade,
            intentTemplate,
            scoreConfig,
            roadLength,
            median);
        List<FlatElevationCandidate> displayed = refined.stream().limit(DISPLAY_TOP_K).toList();
        return new FlatElevationRecommendation(displayed.getFirst(), displayed, samples.size());
    }

    private static List<FlatElevationCandidate> refineTopCandidates(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config,
            List<FlatElevationCandidate> stageA,
            double maxGrade,
            FlatVerticalIntent intentTemplate,
            RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig,
            double roadLength,
            int median) {
        List<FlatElevationCandidate> refined = new ArrayList<>(stageA.size());
        for (FlatElevationCandidate candidate : stageA) {
            double junctionPenalty = junctionAlignmentPenalty(
                network, road, candidate.elevation(), intentTemplate, maxGrade, scoreConfig, roadLength);
            FlatElevationCandidate stageB = FlatElevationRefinementEvaluator.refine(
                network,
                road,
                terrain,
                config,
                candidate.elevation(),
                maxGrade,
                intentTemplate,
                scoreConfig,
                roadLength,
                junctionPenalty);
            refined.add(stageB.feasible() ? stageB : candidate);
        }
        refined.sort(Comparator
            .comparingDouble(FlatElevationCandidate::score)
            .thenComparingInt(candidate -> Math.abs(candidate.elevation() - median))
            .thenComparingInt(FlatElevationCandidate::elevation));
        return List.copyOf(refined);
    }

    /** Fast median guess retained for initial dialogs and search centering. */
    public static int medianGuess(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        RoadUniformElevationUtils.FlatRoadRecommendation median =
            RoadUniformElevationUtils.recommendMedianForRoad(network, road, terrain, config);
        return median.sampleCount() > 0 ? median.elevation() : TerrainSampler.DEFAULT_SEA_LEVEL;
    }

    private static FlatElevationCandidate medianFallbackCandidate(
            List<TerrainSegmentSample> samples,
            int median,
            RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig,
            double minimumRunLength) {
        FlatElevationCandidate candidate = scoreSamples(
            samples, median, scoreConfig, minimumRunLength, 0.0);
        return new FlatElevationCandidate(
            median,
            candidate.score(),
            candidate.estimatedCutVolume(),
            candidate.estimatedFillVolume(),
            candidate.estimatedBridgeLength(),
            candidate.estimatedTunnelLength(),
            candidate.estimatedEarthworkBlocks(),
            true);
    }

    private static FlatElevationCandidate evaluateCandidate(
            RoadNetwork network,
            Road road,
            List<TerrainSegmentSample> samples,
            int candidateY,
            double maxGrade,
            FlatVerticalIntent intentTemplate,
            RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig,
            double minimumRunLength,
            double roadLength) {
        if (!isJunctionFeasible(network, road, candidateY, maxGrade, intentTemplate, roadLength)) {
            return FlatElevationCandidate.infeasible(candidateY);
        }
        double junctionPenalty = junctionAlignmentPenalty(
            network, road, candidateY, intentTemplate, maxGrade, scoreConfig, roadLength);
        FlatElevationCandidate scored = scoreSamples(
            samples, candidateY, scoreConfig, minimumRunLength, junctionPenalty);
        return new FlatElevationCandidate(
            candidateY,
            scored.score(),
            scored.estimatedCutVolume(),
            scored.estimatedFillVolume(),
            scored.estimatedBridgeLength(),
            scored.estimatedTunnelLength(),
            scored.estimatedEarthworkBlocks(),
            true);
    }

    private static FlatElevationCandidate scoreSamples(
            List<TerrainSegmentSample> samples,
            int candidateY,
            RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig,
            double minimumRunLength,
            double junctionPenalty) {
        List<Double> distances = new ArrayList<>(samples.size());
        List<Integer> groundHeights = new ArrayList<>(samples.size());
        List<Integer> targetHeights = new ArrayList<>(samples.size());
        for (TerrainSegmentSample sample : samples) {
            distances.add(sample.segmentLength());
            groundHeights.add(sample.groundY());
            targetHeights.add(candidateY);
        }

        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            distances,
            groundHeights,
            targetHeights,
            scoreConfig,
            minimumRunLength);

        int cutVolume = 0;
        int fillVolume = 0;
        double bridgeLength = 0.0;
        double tunnelLength = 0.0;
        for (int i = 0; i < types.size(); i++) {
            double distance = distances.get(i);
            int diff = candidateY - groundHeights.get(i);
            FlatElevationConstructionMetrics.EarthworkTotals totals =
                FlatElevationConstructionMetrics.accumulateSegment(types.get(i), diff, distance);
            cutVolume += totals.cutVolume();
            fillVolume += totals.fillVolume();
            bridgeLength += totals.bridgeLength();
            tunnelLength += totals.tunnelLength();
        }

        FlatElevationConstructionMetrics.Metrics metrics = FlatElevationConstructionMetrics.fromStageA(
            types, cutVolume, fillVolume, bridgeLength, tunnelLength);
        double score = FlatElevationConstructionMetrics.score(metrics, scoreConfig, junctionPenalty);
        return new FlatElevationCandidate(
            candidateY,
            score,
            cutVolume,
            fillVolume,
            bridgeLength,
            tunnelLength,
            metrics.earthworkBlocks(),
            true);
    }

    static boolean isJunctionFeasible(
            RoadNetwork network,
            Road road,
            int candidateY,
            double maxGrade,
            FlatVerticalIntent intentTemplate,
            double roadLength) {
        FlatVerticalIntent probe = intentTemplate != null
            ? new FlatVerticalIntent(candidateY, intentTemplate.getIntersectionOverrides())
            : new FlatVerticalIntent(candidateY);
        for (Map.Entry<String, Double> entry
                : collectConstraintStations(network, road, intentTemplate).entrySet()) {
            double station = entry.getValue();
            double junctionElev = FlatProfileCompiler.resolveJunctionElevation(
                entry.getKey(), candidateY, probe, network);
            if (Math.abs(junctionElev - candidateY) <= EPSILON) {
                continue;
            }
            double required = VerticalProfileDesignRules.requiredRunLength(
                Math.abs(junctionElev - candidateY), maxGrade);
            double runAfter = roadLength - station;
            if (required > station + EPSILON && required > runAfter + EPSILON) {
                return false;
            }
        }
        RoadVerticalAlignment compiled = FlatProfileCompiler.compile(network, road, probe, maxGrade);
        if (compiled == null) {
            return false;
        }
        return VerticalProfileDesignRules.assess(compiled, roadLength, maxGrade).stream()
            .noneMatch(issue -> issue.kind() == VerticalProfileDesignRules.IssueKind.GRADE_EXCEEDS_LIMIT
                || issue.kind() == VerticalProfileDesignRules.IssueKind.GRADE_RUN_TOO_SHORT);
    }

    private static double junctionAlignmentPenalty(
            RoadNetwork network,
            Road road,
            int candidateY,
            FlatVerticalIntent intentTemplate,
            double maxGrade,
            RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig,
            double roadLength) {
        double penalty = 0.0;
        for (Map.Entry<String, Double> entry
                : collectConstraintStations(network, road, intentTemplate).entrySet()) {
            RoadNode node = network.getNode(entry.getKey());
            if (node == null) {
                continue;
            }
            FlatVerticalIntent probe = intentTemplate != null
                ? new FlatVerticalIntent(candidateY, intentTemplate.getIntersectionOverrides())
                : new FlatVerticalIntent(candidateY);
            Double override = intentTemplate != null
                ? intentTemplate.getIntersectionOverrides().get(entry.getKey())
                : null;
            double target = override != null
                ? override
                : node.getManualElevation() != null ? node.getManualElevation() : candidateY;
            double delta = Math.abs(candidateY - target);
            if (delta <= EPSILON) {
                continue;
            }
            double station = entry.getValue();
            double runAfter = roadLength - station;
            double available = Math.max(station, runAfter);
            double required = VerticalProfileDesignRules.requiredRunLength(delta, maxGrade);
            double transitionLength = Math.min(required, available);
            double volume = transitionLength * delta;
            penalty += volume * (candidateY >= target
                ? scoreConfig.cutWeight()
                : scoreConfig.fillWeight());
        }
        return penalty;
    }

    private static int[] constraintElevationBounds(
            RoadNetwork network,
            Road road,
            FlatVerticalIntent intentTemplate) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        FlatVerticalIntent probe = intentTemplate != null
            ? new FlatVerticalIntent(64.0, intentTemplate.getIntersectionOverrides())
            : new FlatVerticalIntent(64.0);
        for (Map.Entry<String, Double> entry
                : collectConstraintStations(network, road, intentTemplate).entrySet()) {
            double elevation = FlatProfileCompiler.resolveJunctionElevation(
                entry.getKey(), probe.getBaseElevation(), probe, network);
            int rounded = (int) Math.round(elevation);
            min = Math.min(min, rounded);
            max = Math.max(max, rounded);
        }
        if (min == Integer.MAX_VALUE) {
            return null;
        }
        return new int[] {min, max};
    }

    private static Map<String, Double> collectConstraintStations(
            RoadNetwork network,
            Road road,
            FlatVerticalIntent intentTemplate) {
        Map<String, Double> stations = new LinkedHashMap<>(
            VerticalAlignmentJunctionSynchronizer.junctionStations(network, road));
        if (intentTemplate != null) {
            for (String nodeId : intentTemplate.getIntersectionOverrides().keySet()) {
                stationAtNode(network, road, nodeId)
                    .ifPresent(station -> stations.putIfAbsent(nodeId, station));
            }
        }
        return stations;
    }

    private static OptionalDouble stationAtNode(RoadNetwork network, Road road, String nodeId) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            OptionalDouble station = segment.roadStationAtNode(nodeId);
            if (station.isPresent()) {
                return station;
            }
        }
        return OptionalDouble.empty();
    }

    private static FlatVerticalIntent copyIntentTemplate(FlatVerticalIntent intent) {
        return intent != null ? intent.copy() : null;
    }

    private static int medianGround(List<TerrainSegmentSample> samples) {
        List<Integer> heights = samples.stream().map(TerrainSegmentSample::groundY).sorted().toList();
        int middle = heights.size() / 2;
        return heights.size() % 2 == 1
            ? heights.get(middle)
            : (int) Math.round((heights.get(middle - 1) + heights.get(middle)) / 2.0);
    }

    static List<TerrainSegmentSample> sampleRoadTerrainSegments(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        double spacing = Math.max(0.5, config.getPathSampleDistance());
        List<TerrainSegmentSample> samples = new ArrayList<>();
        Map<Long, Integer> seenCells = new LinkedHashMap<>();
        for (String edgeId : road.getSegmentIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            appendEdgeSamples(network, edge, terrain, config, spacing, seenCells, samples);
        }
        return List.copyOf(samples);
    }

    private static void appendEdgeSamples(
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            RoadSystemConfig config,
            double spacing,
            Map<Long, Integer> seenCells,
            List<TerrainSegmentSample> samples) {
        List<Vec2d> centerline = edge.getCenterlinePoints();
        if (centerline == null || centerline.size() < 2) {
            return;
        }
        int width = RoadModelUtils.getEffectiveWidth(network, edge, config);
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(width);

        appendSample(centerline.getFirst(), centerline.get(1), halfWidth, terrain, seenCells, samples, spacing);
        double accumulated = 0.0;
        double nextSampleAt = spacing;
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d a = centerline.get(i);
            Vec2d b = centerline.get(i + 1);
            double segLen = a.distance(b);
            if (segLen < EPSILON) {
                continue;
            }
            while (nextSampleAt <= accumulated + segLen + EPSILON) {
                double t = (nextSampleAt - accumulated) / segLen;
                t = Math.clamp(t, 0.0, 1.0);
                Vec2d point = a.lerp(b, t);
                appendSample(point, b.subtract(a), halfWidth, terrain, seenCells, samples, spacing);
                nextSampleAt += spacing;
            }
            accumulated += segLen;
        }
        Vec2d last = centerline.getLast();
        Vec2d prev = centerline.get(centerline.size() - 2);
        appendSample(last, last.subtract(prev), halfWidth, terrain, seenCells, samples, spacing);
    }

    private static void appendSample(
            Vec2d point,
            Vec2d tangent,
            double halfWidth,
            TerrainSampler terrain,
            Map<Long, Integer> seenCells,
            List<TerrainSegmentSample> samples,
            double spacing) {
        long key = cellKey(point);
        if (seenCells.containsKey(key)) {
            return;
        }
        int groundY = terrain.sampleCrossSectionGroundY(point, tangent, halfWidth);
        seenCells.put(key, groundY);
        samples.add(new TerrainSegmentSample(spacing, groundY));
    }

    private static long cellKey(Vec2d point) {
        int x = (int) Math.round(point.x);
        int z = (int) Math.round(point.y);
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
