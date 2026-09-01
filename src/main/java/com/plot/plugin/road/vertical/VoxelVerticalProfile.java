package com.plot.plugin.road.vertical;

import java.util.List;

/** Immutable, road-global voxel sequence sampled from one continuous vertical alignment. */
public final class VoxelVerticalProfile {
    public static final double DEFAULT_STATION_STEP = 1.0;
    private static final double EPSILON = 1e-9;
    private static final double STATION_SNAP_TOLERANCE = 1e-6;

    private final double stationStep;
    private final double endStation;
    private final int[] elevations;

    public VoxelVerticalProfile(double stationStep, double endStation, int[] elevations) {
        if (!Double.isFinite(stationStep) || stationStep <= 0.0) {
            throw new IllegalArgumentException("stationStep must be positive and finite");
        }
        if (!Double.isFinite(endStation) || endStation < 0.0) {
            throw new IllegalArgumentException("endStation must be non-negative and finite");
        }
        this.stationStep = stationStep;
        this.endStation = endStation;
        this.elevations = elevations != null ? elevations.clone() : new int[0];
    }

    public static VoxelVerticalProfile fromAlignment(RoadVerticalAlignment alignment) {
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) return inactive();
        double length = alignment.endStation();
        List<Integer> values = VoxelGradeDiscretizer.discretizeContinuousProfile(
            length,
            station -> VerticalAlignmentGeometry.elevationAt(alignment, station)
                .orElseGet(() -> endpointElevation(alignment, station)));
        return new VoxelVerticalProfile(
            DEFAULT_STATION_STEP,
            length,
            values.stream().mapToInt(Integer::intValue).toArray());
    }

    public static VoxelVerticalProfile inactive() {
        return new VoxelVerticalProfile(DEFAULT_STATION_STEP, 0.0, new int[0]);
    }

    public boolean isActive() { return elevations.length > 0; }
    public double stationStep() { return stationStep; }
    public double endStation() { return endStation; }
    public int sampleCount() { return elevations.length; }

    /** Reads the deterministic step active at the supplied global road station. */
    public int elevationAt(double station) {
        if (elevations.length == 0) return 0;
        double clamped = Double.isFinite(station)
            ? Math.max(0.0, Math.min(endStation, station))
            : 0.0;
        if (Math.abs(clamped - endStation) <= EPSILON) return elevations[elevations.length - 1];
        double scaledStation = clamped / stationStep;
        double nearestStation = Math.rint(scaledStation);
        if (Math.abs(scaledStation - nearestStation) <= STATION_SNAP_TOLERANCE) {
            scaledStation = nearestStation;
        }
        int index = (int) Math.floor(scaledStation + EPSILON);
        return elevations[Math.max(0, Math.min(elevations.length - 1, index))];
    }

    public int[] elevations() { return elevations.clone(); }

    private static double endpointElevation(RoadVerticalAlignment alignment, double station) {
        List<PointOfVerticalIntersection> pvis = alignment.sortedPvis();
        if (pvis.isEmpty()) return 0.0;
        return station <= pvis.getFirst().getStation()
            ? pvis.getFirst().getElevation()
            : pvis.getLast().getElevation();
    }
}
