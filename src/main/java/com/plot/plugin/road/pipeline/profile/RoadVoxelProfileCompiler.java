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
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

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
 * post-process used by profile solvers.
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
        return compileFromEdgeResults(network, road, config, edgeResults, effective);
    }

    public static VoxelProfileCompileResult compileFromEdgeResults(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults,
            ProfileCompileOptions options) {
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
        BuildHeightSequenceSmoother.SmoothingResult smoothed =
            BuildHeightSequenceSmoother.smooth(base.buildSamples(), effective, lockedEndpoints(base.buildSamples()));
        List<BuildHeightSample> samples = smoothed.samples();
        BuildHeightProfile buildProfile = BuildHeightProfile.fromSamples(samples);
        List<Double> buildElevations = summarizeBuildElevations(base, samples);
        RoadProfileChartData overlay = base.withBuildProfile(buildElevations, samples);
        boolean feasible = base.manualEndpointConstraintFeasible() && base.waterConstraintFeasible();
        String failure = null;
        if (!base.manualEndpointConstraintFeasible()) {
            failure = "endpoint constraints infeasible";
        } else if (!base.waterConstraintFeasible()) {
            failure = "water constraints infeasible";
        }
        return new VoxelProfileCompileResult(
            feasible,
            samples,
            buildProfile,
            overlay,
            failure,
            smoothed.diagnostics());
    }

    private static BitSet lockedEndpoints(List<BuildHeightSample> samples) {
        BitSet locked = new BitSet();
        if (samples != null && !samples.isEmpty()) {
            locked.set(0);
            locked.set(samples.size() - 1);
        }
        return locked;
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
