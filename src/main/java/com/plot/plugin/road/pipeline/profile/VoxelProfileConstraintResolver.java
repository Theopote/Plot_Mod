package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Maps design / intersection / world constraints onto voxel sample indices.
 */
public final class VoxelProfileConstraintResolver {

    private static final double STATION_MATCH_TOLERANCE = 0.75;

    private VoxelProfileConstraintResolver() {
    }

    public static VoxelProfileConstraints resolve(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment,
            List<BuildHeightSample> samples,
            List<RoadProfileIntersection> intersections,
            RoadElevationBounds elevationBounds) {
        VoxelProfileConstraints.Builder builder = VoxelProfileConstraints.builder();
        if (samples == null || samples.isEmpty()) {
            return builder.build();
        }
        freezeSample(builder, samples, 0);
        freezeSample(builder, samples, samples.size() - 1);

        if (alignment != null) {
            for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
                if (pvi.getConstraint() == null || pvi.getConstraint().isAutomaticallyMovable()) {
                    continue;
                }
                int index = nearestSampleIndex(samples, pvi.getStation());
                if (index >= 0) {
                    // Freeze pre-smooth voxel height so smoother cannot move fixed control points.
                    freezeSample(builder, samples, index);
                }
            }
        }

        if (intersections != null) {
            for (RoadProfileIntersection intersection : intersections) {
                if (intersection == null) {
                    continue;
                }
                int index = nearestSampleIndex(samples, intersection.roadStation());
                if (index >= 0) {
                    freezeSample(builder, samples, index);
                }
            }
        }

        if (elevationBounds != null) {
            builder.worldYBounds(
                (int) Math.floor(elevationBounds.minY()),
                (int) Math.ceil(elevationBounds.maxY()));
        }
        return builder.build();
    }

    public static BitSet toLockedSampleIndices(
            List<BuildHeightSample> samples,
            VoxelProfileConstraints constraints) {
        BitSet locked = new BitSet();
        if (samples == null || samples.isEmpty()) {
            return locked;
        }
        locked.set(0);
        locked.set(samples.size() - 1);
        if (constraints == null || !constraints.hasLocks()) {
            return locked;
        }
        for (var entry : constraints.lockedStations().entrySet()) {
            int index = nearestSampleIndex(samples, entry.getKey());
            if (index >= 0) {
                locked.set(index);
            }
        }
        return locked;
    }

    /**
     * After smoothing, restore locked stations to their required block Y.
     */
    public static List<BuildHeightSample> enforce(
            List<BuildHeightSample> samples,
            VoxelProfileConstraints constraints) {
        if (samples == null || samples.isEmpty() || constraints == null) {
            return samples != null ? List.copyOf(samples) : List.of();
        }
        List<BuildHeightSample> out = new ArrayList<>(samples);
        for (var entry : constraints.lockedStations().entrySet()) {
            int index = nearestSampleIndex(samples, entry.getKey());
            if (index < 0) {
                continue;
            }
            BuildHeightSample sample = out.get(index);
            int required = entry.getValue();
            if (sample.buildY() != required) {
                out.set(index, new BuildHeightSample(sample.station(), sample.designElevation(), required));
            }
        }
        if (constraints.minBlockY() != null || constraints.maxBlockY() != null) {
            for (int i = 0; i < out.size(); i++) {
                int y = out.get(i).buildY();
                if (constraints.minBlockY() != null) {
                    y = Math.max(y, constraints.minBlockY());
                }
                if (constraints.maxBlockY() != null) {
                    y = Math.min(y, constraints.maxBlockY());
                }
                if (y != out.get(i).buildY()) {
                    BuildHeightSample sample = out.get(i);
                    out.set(i, new BuildHeightSample(sample.station(), sample.designElevation(), y));
                }
            }
        }
        return List.copyOf(out);
    }

    public static int countLockViolations(
            List<BuildHeightSample> samples,
            VoxelProfileConstraints constraints) {
        if (samples == null || constraints == null || !constraints.hasLocks()) {
            return 0;
        }
        int violations = 0;
        for (var entry : constraints.lockedStations().entrySet()) {
            int index = nearestSampleIndex(samples, entry.getKey());
            if (index >= 0 && samples.get(index).buildY() != entry.getValue()) {
                violations++;
            }
        }
        return violations;
    }

    static int nearestSampleIndex(List<BuildHeightSample> samples, double station) {
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < samples.size(); i++) {
            double distance = Math.abs(samples.get(i).station() - station);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return bestDistance <= STATION_MATCH_TOLERANCE ? best : -1;
    }

    private static void freezeSample(
            VoxelProfileConstraints.Builder builder,
            List<BuildHeightSample> samples,
            int index) {
        BuildHeightSample sample = samples.get(index);
        builder.lockStation(sample.station(), sample.buildY());
    }
}
