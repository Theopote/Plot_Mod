package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.ProfileSolveSupport;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSolver;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Stage B refinement: re-scores Top-K flat elevation candidates using compiled flat
 * profile targets and {@link RoadConstructionClassifier} (same semantics as generation).
 */
final class FlatElevationRefinementEvaluator {
    private static final RoadConstructionClassifier.CanvasBlockPosResolver CANVAS_RESOLVER =
        pos -> new BlockPos((int) Math.round(pos.x), 0, (int) Math.round(pos.y));

    private FlatElevationRefinementEvaluator() {
    }

    static FlatElevationCandidate refine(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config,
            int candidateY,
            double maxGrade,
            FlatVerticalIntent intentTemplate,
            RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig,
            double roadLength,
            double junctionPenalty) {
        if (!FlatElevationOptimizer.isJunctionFeasible(
                network, road, candidateY, maxGrade, intentTemplate, roadLength)) {
            return FlatElevationCandidate.infeasible(candidateY);
        }

        FlatVerticalIntent probe = intentTemplate != null
            ? new FlatVerticalIntent(candidateY, intentTemplate.getIntersectionOverrides())
            : new FlatVerticalIntent(candidateY);
        RoadVerticalAlignment alignment = FlatProfileCompiler.compile(network, road, probe, maxGrade);
        if (alignment == null) {
            return FlatElevationCandidate.infeasible(candidateY);
        }

        ProfileSolveSupport profileSupport = ProfileSolveSupport.fromConfig(config, segments -> 1.0);
        FlatElevationConstructionMetrics.Metrics totals = FlatElevationConstructionMetrics.Metrics.empty();
        for (String edgeId : road.getSegmentIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            FlatElevationConstructionMetrics.Metrics edgeMetrics = evaluateEdge(
                network,
                road,
                edge,
                alignment,
                terrain,
                config,
                profileSupport);
            totals = totals.add(edgeMetrics);
        }

        double score = FlatElevationConstructionMetrics.score(totals, scoreConfig, junctionPenalty);
        return new FlatElevationCandidate(
            candidateY,
            score,
            totals.cutVolume(),
            totals.fillVolume(),
            totals.bridgeLength(),
            totals.tunnelLength(),
            totals.earthworkBlocks(),
            true);
    }

    private static FlatElevationConstructionMetrics.Metrics evaluateEdge(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadVerticalAlignment alignment,
            TerrainSampler terrain,
            RoadSystemConfig config,
            ProfileSolveSupport profileSupport) {
        java.util.Optional<OrientedRoadSegment> oriented =
            RoadStationing.orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty()) {
            return FlatElevationConstructionMetrics.Metrics.empty();
        }

        List<Vec2d> pathPoints = RoadPlanGeometry.resolveEdgeCenterline(
            network, edge, config.getPathSampleDistance());
        if (pathPoints.size() < 2) {
            return FlatElevationConstructionMetrics.Metrics.empty();
        }

        List<PathSegment> segments = RoadGeometrySampler.sample(
            pathPoints,
            config.getPathSampleDistance(),
            (points, unused) -> 1.0);
        if (segments.isEmpty()) {
            return FlatElevationConstructionMetrics.Metrics.empty();
        }

        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.getEffectiveWidth(network, edge, config));
        ProfileSolveResult solved = VerticalAlignmentProfileSolver.solveForEdge(
            alignment,
            oriented.get(),
            segments,
            terrain,
            halfWidth,
            null,
            null,
            profileSupport,
            config,
            RoadVerticalMode.FLAT,
            road.getEffectiveTerrainFollowPreset(config));
        if (solved.heightInfos().isEmpty()) {
            return FlatElevationConstructionMetrics.Metrics.empty();
        }

        ConstructionDetection detection = RoadConstructionClassifier.classify(
            segments,
            solved.heightInfos(),
            terrain,
            config,
            road.getEffectiveTerrainStyle(config),
            CANVAS_RESOLVER);
        return FlatElevationConstructionMetrics.aggregate(detection, solved.heightInfos());
    }
}
