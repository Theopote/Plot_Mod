package com.plot.plugin.road.profile;

import com.plot.plugin.road.pipeline.profile.BuildHeightSample;

import java.util.List;

/**
 * 整条道路的纵断面图数据：X 轴为 canonical road station {@code 0…totalStation}。
 * <p>
 * {@link #buildElevations()} 为 segment-boundary 摘要，供概览与范围计算；
 * {@link #buildSamples()} 为完整方块阶梯序列，供 Editor 真实 stair-step 绘制。
 */
public record RoadProfileChartData(
        String roadId,
        double totalStation,
        List<Double> stations,
        List<Double> groundElevations,
        List<Double> previewElevations,
        List<Double> buildElevations,
        List<BuildHeightSample> buildSamples,
        List<Double> guideElevations,
        List<ProfileControlPoint> controlPoints,
        List<RoadProfileIntersection> intersections,
        boolean manualEndpointConstraintFeasible) {

    public RoadProfileChartData(
            String roadId,
            double totalStation,
            List<Double> stations,
            List<Double> groundElevations,
            List<Double> previewElevations,
            List<Double> guideElevations,
            List<ProfileControlPoint> controlPoints,
            List<RoadProfileIntersection> intersections) {
        this(
            roadId,
            totalStation,
            stations,
            groundElevations,
            previewElevations,
            previewElevations,
            List.of(),
            guideElevations,
            controlPoints,
            intersections,
            true);
    }

    public RoadProfileChartData(
            String roadId,
            double totalStation,
            List<Double> stations,
            List<Double> groundElevations,
            List<Double> previewElevations,
            List<Double> guideElevations,
            List<ProfileControlPoint> controlPoints,
            List<RoadProfileIntersection> intersections,
            boolean manualEndpointConstraintFeasible) {
        this(
            roadId,
            totalStation,
            stations,
            groundElevations,
            previewElevations,
            previewElevations,
            List.of(),
            guideElevations,
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible);
    }

    private static final double EPSILON = 1e-6;

    public boolean hasProfileData() {
        return stations != null
            && stations.size() >= 2
            && groundElevations != null
            && groundElevations.size() == stations.size()
            && previewElevations != null
            && previewElevations.size() == stations.size()
            && buildElevations != null
            && buildElevations.size() == stations.size();
    }

    public boolean hasBuildSamples() {
        return buildSamples != null && buildSamples.size() >= 2;
    }

    /** Road-level 图表契约：station 覆盖完整 canonical 范围且单调。 */
    public boolean hasCompleteRoadProfile() {
        if (!hasProfileData() || totalStation <= EPSILON) {
            return false;
        }
        if (Math.abs(stations.getFirst()) > 1e-2) {
            return false;
        }
        if (Math.abs(stations.getLast() - totalStation) > 1e-2) {
            return false;
        }
        for (int i = 1; i < stations.size(); i++) {
            if (stations.get(i) + EPSILON < stations.get(i - 1)) {
                return false;
            }
            if (stations.get(i) < -EPSILON || stations.get(i) > totalStation + 1e-2) {
                return false;
            }
        }
        return true;
    }

    public double previewElevationAt(double station) {
        return interpolate(stations, previewElevations, station);
    }

    public double buildElevationAt(double station) {
        if (hasBuildSamples()) {
            return buildElevationFromSamples(station);
        }
        return interpolate(stations, buildElevations, station);
    }

    public double groundElevationAt(double station) {
        return interpolate(stations, groundElevations, station);
    }

    private double buildElevationFromSamples(double station) {
        if (station <= buildSamples.getFirst().station() + EPSILON) {
            return buildSamples.getFirst().buildY();
        }
        BuildHeightSample match = buildSamples.getFirst();
        for (BuildHeightSample sample : buildSamples) {
            if (sample.station() > station + EPSILON) {
                break;
            }
            match = sample;
        }
        return match.buildY();
    }

    static double interpolate(List<Double> stations, List<Double> values, double station) {
        if (stations == null || values == null || stations.isEmpty() || values.size() != stations.size()) {
            return Double.NaN;
        }
        if (station <= stations.getFirst() + EPSILON) {
            return values.getFirst();
        }
        if (station >= stations.getLast() - EPSILON) {
            return values.getLast();
        }
        for (int i = 1; i < stations.size(); i++) {
            double start = stations.get(i - 1);
            double end = stations.get(i);
            if (station > end) {
                continue;
            }
            double span = end - start;
            if (span <= EPSILON) {
                return values.get(i);
            }
            double ratio = (station - start) / span;
            return values.get(i - 1) + ratio * (values.get(i) - values.get(i - 1));
        }
        return values.getLast();
    }
}
