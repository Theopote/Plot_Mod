package com.plot.plugin.road.profile;

import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.profile.terrain.RoadTerrainProfileData;

import java.util.ArrayList;
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
        boolean manualEndpointConstraintFeasible,
        boolean waterConstraintFeasible,
        List<Double> waterElevations,
        List<WaterCrossingChartMarker> waterCrossings,
        List<ConstructionRunChartMarker> constructionRuns) {

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
            true,
            true,
            List.of(),
            List.of(),
            List.of());
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
            boolean manualEndpointConstraintFeasible,
            boolean waterConstraintFeasible) {
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
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            List.of(),
            List.of(),
            List.of());
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
            manualEndpointConstraintFeasible,
            true,
            List.of(),
            List.of(),
            List.of());
    }

    public RoadProfileChartData(
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
            boolean manualEndpointConstraintFeasible,
            boolean waterConstraintFeasible) {
        this(
            roadId,
            totalStation,
            stations,
            groundElevations,
            previewElevations,
            buildElevations,
            buildSamples,
            guideElevations,
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            List.of(),
            List.of(),
            List.of());
    }

    public RoadProfileChartData(
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
        this(
            roadId,
            totalStation,
            stations,
            groundElevations,
            previewElevations,
            buildElevations,
            buildSamples,
            guideElevations,
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible,
            true,
            List.of(),
            List.of(),
            List.of());
    }

    public RoadProfileChartData(
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
            boolean manualEndpointConstraintFeasible,
            boolean waterConstraintFeasible,
            List<Double> waterElevations,
            List<WaterCrossingChartMarker> waterCrossings) {
        this(
            roadId,
            totalStation,
            stations,
            groundElevations,
            previewElevations,
            buildElevations,
            buildSamples,
            guideElevations,
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            waterElevations,
            waterCrossings,
            List.of());
    }

    private static final double EPSILON = 1e-6;

    /** 仅地形序列有效即可绘制灰线（Level-1）；不要求设计/建造高程。 */
    public boolean hasTerrainSeries() {
        if (stations == null || stations.size() < 2
                || groundElevations == null
                || groundElevations.size() != stations.size()) {
            return false;
        }
        int finite = 0;
        for (Double y : groundElevations) {
            if (y != null && Double.isFinite(y)) {
                finite++;
            }
        }
        return finite >= 2;
    }

    /** 概览/编辑器图表门禁：有地形即可渲染；完整设计预览仍用 {@link #hasProfileData()}。 */
    public boolean canRenderChart() {
        return hasTerrainSeries();
    }

    public boolean hasProfileData() {
        return hasTerrainSeries()
            && previewElevations != null
            && previewElevations.size() == stations.size()
            && buildElevations != null
            && buildElevations.size() == stations.size()
            && finiteCount(previewElevations) >= 2
            && finiteCount(buildElevations) >= 2;
    }

    private static int finiteCount(List<Double> values) {
        if (values == null) {
            return 0;
        }
        int count = 0;
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                count++;
            }
        }
        return count;
    }

    /** 由 Level-1 地形数据构造可渲染图表（无设计线、无建造阶梯）。 */
    public static RoadProfileChartData fromTerrain(RoadTerrainProfileData terrain) {
        if (terrain == null || !terrain.hasRenderableTerrain()) {
            return null;
        }
        List<Double> ground = new ArrayList<>(terrain.terrainY());
        List<Double> empty = new ArrayList<>(ground.size());
        for (int i = 0; i < ground.size(); i++) {
            empty.add(Double.NaN);
        }
        return new RoadProfileChartData(
            terrain.roadId(),
            terrain.totalStation(),
            terrain.stations(),
            ground,
            empty,
            empty,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            true,
            true,
            terrain.waterY(),
            List.of(),
            List.of());
    }

    /**
     * 用 Level-1 地形替换灰线/水面，保留既有设计/建造序列的桩号域时尽量插值对齐；
     * 桩号网格不同则以地形站为准并清空建造阶梯（避免旧预览冒充新地形）。
     */
    public RoadProfileChartData withTerrainGround(RoadTerrainProfileData terrain) {
        if (terrain == null || !terrain.hasRenderableTerrain()) {
            return this;
        }
        List<Double> preview = new ArrayList<>(terrain.stations().size());
        List<Double> build = new ArrayList<>(terrain.stations().size());
        for (double station : terrain.stations()) {
            preview.add(previewElevationAt(station));
            build.add(buildElevationAt(station));
        }
        return new RoadProfileChartData(
            terrain.roadId(),
            terrain.totalStation(),
            terrain.stations(),
            terrain.terrainY(),
            preview,
            build,
            List.of(),
            guideElevations,
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            terrain.waterY(),
            waterCrossings,
            constructionRuns);
    }

    public double nearestFiniteGroundElevation(double station) {
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < stations.size(); i++) {
            Double y = groundElevations.get(i);
            if (y == null || !Double.isFinite(y)) {
                continue;
            }
            double dist = Math.abs(stations.get(i) - station);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best >= 0 ? groundElevations.get(best) : Double.NaN;
    }

    public boolean hasBuildSamples() {
        return buildSamples != null && buildSamples.size() >= 2;
    }

    /** Replace voxel build series while retaining terrain / design / water chart series. */
    public RoadProfileChartData withBuildProfile(
            List<Double> newBuildElevations,
            List<BuildHeightSample> newBuildSamples) {
        return new RoadProfileChartData(
            roadId,
            totalStation,
            stations,
            groundElevations,
            previewElevations,
            newBuildElevations != null ? List.copyOf(newBuildElevations) : List.of(),
            newBuildSamples != null ? List.copyOf(newBuildSamples) : List.of(),
            guideElevations,
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            waterElevations,
            waterCrossings,
            constructionRuns);
    }

    public boolean hasWaterElevations() {
        if (waterElevations == null || waterElevations.isEmpty()) {
            return false;
        }
        for (Double water : waterElevations) {
            if (water != null && Double.isFinite(water)) {
                return true;
            }
        }
        return false;
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
