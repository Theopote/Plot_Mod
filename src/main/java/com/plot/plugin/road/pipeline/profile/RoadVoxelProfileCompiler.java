package com.plot.plugin.road.pipeline.profile;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileChartAssembler;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiles the authoritative continuous design profile into Minecraft integer build heights
 * for editor grey-line preview and generation consistency.
 *
 * <p>Does not mutate the live {@link RoadNetwork} or Minecraft world. Edge solving reuses
 * {@link RoadGenerator#sampleEdgeProfile}; short-grade smoothing is applied via the same
 * post-process used by profile solvers, with hard constraints preserved.
 */
public final class RoadVoxelProfileCompiler {

    private RoadVoxelProfileCompiler() {
    }

    public static VoxelProfileCompileResult compile(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            RoadGenerator generator,
            TerrainSampler terrain,
            Map<String, Integer> nodeElevations,
            ProfileCompileOptions options) {
        return compile(
            network, road, config, generator, terrain, nodeElevations, options, null, null);
    }

    public static VoxelProfileCompileResult compile(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            RoadGenerator generator,
            TerrainSampler terrain,
            Map<String, Integer> nodeElevations,
            ProfileCompileOptions options,
            RoadElevationBounds elevationBounds,
            RoadVerticalAlignment alignmentOverride) {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(terrain, "terrain");
        if (!RoadStationing.isStationable(network, road)) {
            return VoxelProfileCompileResult.failed("road is not stationable");
        }
        ProfileCompileOptions effective = options != null ? options : ProfileCompileOptions.defaults();
        Map<String, Integer> elevations = nodeElevations != null ? nodeElevations : Map.of();
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            if (start == null || end == null) {
                continue;
            }
            RoadGenerationResult profile = generator.sampleEdgeProfile(
                network, edge, start, end, terrain, elevations);
            if (profile != null && profile.hasProfileData()) {
                edgeResults.put(edge.getId(), profile);
            }
        }
        if (edgeResults.isEmpty()) {
            return VoxelProfileCompileResult.failed("no edge profile samples");
        }
        RoadVerticalAlignment alignment = alignmentOverride != null
            ? alignmentOverride
            : road.getVerticalAlignment();
        return compileFromEdgeResults(
            network, road, config, edgeResults, effective, elevationBounds, alignment);
    }

    public static VoxelProfileCompileResult compileFromEdgeResults(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults,
            ProfileCompileOptions options) {
        return compileFromEdgeResults(
            network, road, config, edgeResults, options, null, road != null ? road.getVerticalAlignment() : null);
    }

    public static VoxelProfileCompileResult compileFromEdgeResults(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults,
            ProfileCompileOptions options,
            RoadElevationBounds elevationBounds,
            RoadVerticalAlignment alignment) {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(edgeResults, "edgeResults");
        ProfileCompileOptions effective = options != null ? options : ProfileCompileOptions.defaults();
        Optional<RoadProfileChartData> assembled = RoadProfileChartAssembler.assemble(
            network, road, config, edgeResults);
        if (assembled.isEmpty() || !assembled.get().hasBuildSamples()) {
            return VoxelProfileCompileResult.failed("could not assemble road build samples");
        }
        RoadProfileChartData base = assembled.get();
        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forRoad(
            network, road, config, edgeResults);
        VoxelProfileConstraints constraints = VoxelProfileConstraintResolver.resolve(
            network, road, alignment, base.buildSamples(), intersections, elevationBounds);
        BitSet locked = VoxelProfileConstraintResolver.toLockedSampleIndices(
            base.buildSamples(), constraints);

        BuildHeightSequenceSmoother.SmoothingResult smoothed =
            BuildHeightSequenceSmoother.smooth(base.buildSamples(), effective, locked);
        List<BuildHeightSample> samples = VoxelProfileConstraintResolver.enforce(
            smoothed.samples(), constraints);

        int lockViolations = VoxelProfileConstraintResolver.countLockViolations(samples, constraints);
        BuildHeightProfile buildProfile = BuildHeightProfile.fromSamples(samples);
        List<Double> buildElevations = summarizeBuildElevations(base, samples);
        RoadProfileChartData overlay = base.withBuildProfile(buildElevations, samples);
        boolean feasible = base.manualEndpointConstraintFeasible()
            && base.waterConstraintFeasible()
            && lockViolations == 0;
        String failure = null;
        if (!base.manualEndpointConstraintFeasible()) {
            failure = "endpoint constraints infeasible";
        } else if (!base.waterConstraintFeasible()) {
            failure = "water constraints infeasible";
        } else if (lockViolations > 0) {
            failure = "hard elevation locks violated after smoothing";
        }
        return new VoxelProfileCompileResult(
            feasible,
            samples,
            buildProfile,
            overlay,
            failure,
            smoothed.diagnostics());
    }

    private static List<Double> summarizeBuildElevations(
            RoadProfileChartData base,
            List<BuildHeightSample> samples) {
        if (base.stations() == null || base.stations().isEmpty()) {
            return List.of();
        }
        BuildHeightProfile profile = BuildHeightProfile.fromSamples(samples);
        return base.stations().stream()
            .map(station -> (double) profile.elevationAtWorldStation(station))
            .toList();
    }
}
