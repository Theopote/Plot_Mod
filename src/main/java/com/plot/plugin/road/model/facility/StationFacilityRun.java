package com.plot.plugin.road.model.facility;

/**
 * 沿桩号区间的附属设施布置：从 {@code startStation}（含）起生效，至 {@code endStation}（含）或道路终点。
 */
public final class StationFacilityRun {

    private final double startStation;
    private final Double endStation;
    private final RoadFacilityKind kind;
    private final RoadFacilitySide side;
    private final String material;
    private final Double height;

    public StationFacilityRun(
            double startStation,
            Double endStation,
            RoadFacilityKind kind,
            RoadFacilitySide side,
            String material,
            Double height) {
        if (!Double.isFinite(startStation) || startStation < 0.0) {
            throw new IllegalArgumentException("startStation must be finite and non-negative");
        }
        if (endStation != null && (!Double.isFinite(endStation) || endStation <= startStation)) {
            throw new IllegalArgumentException("endStation must be finite and greater than startStation");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (side == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (height != null && (!Double.isFinite(height) || height <= 0.0)) {
            throw new IllegalArgumentException("height must be finite and positive");
        }
        this.startStation = startStation;
        this.endStation = endStation;
        this.kind = kind;
        this.side = side;
        this.material = material;
        this.height = height;
    }

    public static StationFacilityRun of(
            double startStation,
            Double endStation,
            RoadFacilityKind kind,
            RoadFacilitySide side) {
        return new StationFacilityRun(startStation, endStation, kind, side, null, null);
    }

    public double getStartStation() {
        return startStation;
    }

    public Double getEndStation() {
        return endStation;
    }

    public RoadFacilityKind getKind() {
        return kind;
    }

    public RoadFacilitySide getSide() {
        return side;
    }

    public String getMaterial() {
        return material;
    }

    public Double getHeight() {
        return height;
    }

    public StationFacilityRun copy() {
        return new StationFacilityRun(startStation, endStation, kind, side, material, height);
    }
}
