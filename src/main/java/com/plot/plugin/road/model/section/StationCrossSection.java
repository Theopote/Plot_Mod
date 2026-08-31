package com.plot.plugin.road.model.section;

/**
 * 沿桩号的横断面模板：从 {@code station} 起（含）使用该断面，直至下一桩号断面。
 * <p>
 * 桩号 0 之前使用 {@link com.plot.plugin.road.model.Road#getCrossSection()} 作为默认。
 */
public final class StationCrossSection {

    private final double station;
    private final RoadCrossSection crossSection;

    public StationCrossSection(double station, RoadCrossSection crossSection) {
        if (!Double.isFinite(station) || station < 0.0) {
            throw new IllegalArgumentException("station must be finite and non-negative");
        }
        if (crossSection == null) {
            throw new IllegalArgumentException("crossSection is required");
        }
        this.station = station;
        this.crossSection = crossSection;
    }

    public static StationCrossSection at(double station, RoadCrossSection crossSection) {
        return new StationCrossSection(station, crossSection);
    }

    public double getStation() {
        return station;
    }

    public RoadCrossSection getCrossSection() {
        return crossSection;
    }

    public StationCrossSection copy() {
        return new StationCrossSection(station, crossSection.copy());
    }
}
