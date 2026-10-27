package com.plot.plugin.road.pipeline.profile;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.RoadStationingReadCache;
import com.plot.plugin.road.vertical.EditedProfileGeometry;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Main-thread profile compilation, yielding between physical edges. The caller owns the
 * disposable network and must cancel when its configuration or terrain revision changes.
 * A single edge solve and final assembly remain indivisible operations.
 */
public final class RoadVoxelProfileCompileJob {
    public enum Phase { PREPARING, SAMPLING, ASSEMBLING, COMPLETED, CANCELLED, FAILED }

    private static final int MAX_EDGES_PER_TICK = 4;
    private final RoadNetwork network;
    private final Road road;
    private final RoadSystemConfig config;
    private final RoadGenerator generator;
    private final TerrainSampler terrain;
    private final Map<String, Integer> elevations;
    private final ProfileCompileOptions options;
    private final RoadElevationBounds bounds;
    private final RoadVerticalAlignment alignment;
    private final LongSupplier clock;
    private final Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
    private List<OrientedRoadSegment> segments = List.of();
    private RoadStationingReadCache stationingCache;
    private int completedEdges;
    private Phase phase = Phase.PREPARING;
    private VoxelProfileCompileResult result;

    public RoadVoxelProfileCompileJob(RoadNetwork network, Road road, RoadSystemConfig config,
            RoadGenerator generator, TerrainSampler terrain, Map<String, Integer> elevations,
            ProfileCompileOptions options, RoadElevationBounds bounds, RoadVerticalAlignment alignment) {
        this(network, road, config, generator, terrain, elevations, options, bounds, alignment, System::nanoTime);
    }

    RoadVoxelProfileCompileJob(RoadNetwork network, Road road, RoadSystemConfig config,
            RoadGenerator generator, TerrainSampler terrain, Map<String, Integer> elevations,
            ProfileCompileOptions options, RoadElevationBounds bounds, RoadVerticalAlignment alignment,
            LongSupplier clock) {
        this.network = Objects.requireNonNull(network);
        this.road = Objects.requireNonNull(road);
        this.config = Objects.requireNonNull(config);
        this.generator = Objects.requireNonNull(generator);
        this.terrain = Objects.requireNonNull(terrain);
        this.elevations = elevations != null ? Map.copyOf(elevations) : Map.of();
        this.options = options != null ? options : ProfileCompileOptions.defaults();
        this.bounds = bounds;
        this.alignment = alignment != null ? alignment.copy() : null;
        this.clock = Objects.requireNonNull(clock);
    }

    public Phase phase() { return phase; }
    public int completedEdges() { return completedEdges; }
    public int totalEdges() { return segments.size(); }
    public boolean isDone() {
        return phase == Phase.COMPLETED || phase == Phase.CANCELLED || phase == Phase.FAILED;
    }

    public VoxelProfileCompileResult result() {
        if (result == null) throw new IllegalStateException("profile compilation has no result");
        return result;
    }

    public void cancel() {
        if (isDone()) return;
        phase = Phase.CANCELLED;
        edgeResults.clear();
    }

    public void advance(long budgetNanos) {
        if (budgetNanos <= 0) throw new IllegalArgumentException("positive tick budget required");
        if (isDone()) return;
        try {
            switch (phase) {
                case PREPARING -> prepare();
                case SAMPLING -> sample(budgetNanos);
                case ASSEMBLING -> assemble();
                default -> { }
            }
        } catch (RuntimeException ex) {
            fail(ex.getMessage() != null ? ex.getMessage() : "profile compilation failed");
        }
    }

    private void prepare() {
        if (!RoadStationing.isStationable(network, road)) {
            fail("road is not stationable");
            return;
        }
        boolean bound = ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road);
        DerivedCenterlineSynchronizer.synchronizeRoad(network, road, config.getPathSampleDistance());
        if (alignment != null) {
            // The explicit editor override is authoritative only in this disposable snapshot.
            road.setVerticalAlignment(alignment.copy());
            ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        } else if (bound) {
            road.setEditedGeometrySignature(EditedProfileGeometry.signature(network, road));
        }
        stationingCache = new RoadStationingReadCache(network);
        try (var ignored = stationingCache.activate()) {
            segments = List.copyOf(RoadStationing.orientedSegments(network, road));
        }
        if (segments.isEmpty()) {
            fail("no road segments");
            return;
        }
        phase = Phase.SAMPLING;
    }

    private void sample(long budgetNanos) {
        long started = clock.getAsLong();
        int sampled = 0;
        try (var ignored = stationingCache.activate()) {
            do {
                var segment = segments.get(completedEdges);
                var edge = network.getEdge(segment.edgeId());
                var start = edge != null ? network.getNode(edge.getStartNodeId()) : null;
                var end = edge != null ? network.getNode(edge.getEndNodeId()) : null;
                if (edge == null || start == null || end == null) {
                    fail("road segment is incomplete");
                    return;
                }
                var profile = generator.sampleEdgeProfile(network, edge, start, end, terrain, elevations);
                if (profile == null || !profile.hasProfileData()) {
                    fail("road segment has no profile samples");
                    return;
                }
                edgeResults.put(edge.getId(), profile);
                completedEdges++;
                sampled++;
            } while (completedEdges < segments.size() && sampled < MAX_EDGES_PER_TICK
                && clock.getAsLong() - started < budgetNanos);
        }
        // Final assembly gets its own tick rather than extending the last sampling tick.
        if (completedEdges == segments.size()) phase = Phase.ASSEMBLING;
    }

    private void assemble() {
        try (var ignored = stationingCache.activate()) {
            result = RoadVoxelProfileCompiler.compileFromEdgeResults(
                network, road, config, edgeResults, options, bounds,
                alignment != null ? alignment : road.getVerticalAlignment());
        }
        phase = result.feasible() ? Phase.COMPLETED : Phase.FAILED;
        edgeResults.clear();
    }

    private void fail(String reason) {
        result = VoxelProfileCompileResult.failed(reason);
        phase = Phase.FAILED;
        edgeResults.clear();
    }
}
