package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 per-edge 预览采样拼接为整条道路的纵断面图数据。
 */
public final class RoadProfileChartAssembler {

    private static final double STATION_MERGE_TOLERANCE = 1e-3;

    private RoadProfileChartAssembler() {
    }

    /** 部分 segment 有预览采样、部分缺失时返回 true。 */
    public static boolean hasIncompleteProfileSampling(
            RoadNetwork network,
            Road road,
            Map<String, RoadGenerationResult> edgeResults) {
        if (road == null || edgeResults == null
                || !RoadStationing.isStationable(network, road)) {
            return false;
        }
        boolean any = false;
        boolean all = true;
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            RoadGenerationResult edgeResult = edgeResults.get(segment.edgeId());
            if (edgeResult != null && edgeResult.hasProfileData()) {
                any = true;
            } else {
                all = false;
            }
        }
        return any && !all;
    }

    public static Optional<RoadProfileChartData> assemble(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults) {
        if (network == null || road == null || config == null || edgeResults == null) {
            return Optional.empty();
        }
        if (!RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        double totalStation = RoadStationing.canonicalLength(network, road);
        if (totalStation <= 1e-6) {
            return Optional.empty();
        }

        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        for (OrientedRoadSegment segment : segments) {
            RoadGenerationResult edgeResult = edgeResults.get(segment.edgeId());
            if (edgeResult == null || !edgeResult.hasProfileData()) {
                return Optional.empty();
            }
        }

        List<Double> stations = new ArrayList<>();
        List<Double> groundElevations = new ArrayList<>();
        List<Double> designElevations = new ArrayList<>();
        List<Double> buildElevations = new ArrayList<>();
        List<BuildHeightSample> buildSamples = new ArrayList<>();
        List<Double> guideElevations = new ArrayList<>();
        List<Double> waterElevations = new ArrayList<>();
        List<WaterCrossingChartMarker> waterCrossings = new ArrayList<>();

        for (OrientedRoadSegment segment : segments) {
            RoadGenerationResult edgeResult = edgeResults.get(segment.edgeId());
            appendSegmentSamples(
                network,
                road,
                segment,
                edgeResult,
                stations,
                groundElevations,
                designElevations,
                buildElevations,
                guideElevations,
                waterElevations);
            appendWaterCrossings(network, road, segment, edgeResult, waterCrossings);
            appendBuildSamples(network, road, segment, edgeResult, buildSamples);
        }
        if (stations.size() < 2) {
            return Optional.empty();
        }

        List<ProfileControlPoint> controlPoints = VerticalProfileControlPoints.forRoad(network, road);
        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forRoad(
            network, road, config, edgeResults);
        boolean manualEndpointConstraintFeasible = true;
        boolean waterConstraintFeasible = true;
        for (OrientedRoadSegment segment : segments) {
            RoadGenerationResult edgeResult = edgeResults.get(segment.edgeId());
            if (edgeResult != null && !edgeResult.manualEndpointConstraintFeasible) {
                manualEndpointConstraintFeasible = false;
            }
            if (edgeResult != null && !edgeResult.waterConstraintFeasible) {
                waterConstraintFeasible = false;
            }
            if (!manualEndpointConstraintFeasible && !waterConstraintFeasible) {
                break;
            }
        }

        RoadProfileChartData chart = new RoadProfileChartData(
            road.getId(),
            totalStation,
            List.copyOf(stations),
            List.copyOf(groundElevations),
            List.copyOf(designElevations),
            List.copyOf(buildElevations),
            List.copyOf(buildSamples),
            List.copyOf(guideElevations),
            controlPoints,
            intersections,
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            new ArrayList<>(waterElevations),
            List.copyOf(waterCrossings));
        if (!chart.hasCompleteRoadProfile()) {
            return Optional.empty();
        }
        return Optional.of(chart);
    }

    private static void appendSegmentSamples(
            RoadNetwork network,
            Road road,
            OrientedRoadSegment segment,
            RoadGenerationResult edgeResult,
            List<Double> stations,
            List<Double> groundElevations,
            List<Double> designElevations,
            List<Double> buildElevations,
            List<Double> guideElevations,
            List<Double> waterElevations) {
        List<Double> profileDistances = edgeResult.profileDistances;
        double profileSpan = profileDistances.getLast() - profileDistances.getFirst();
        if (profileSpan <= 1e-9) {
            profileSpan = segment.length();
        }

        int startIndex = segment.forward() ? 0 : profileDistances.size() - 1;
        int endIndex = segment.forward() ? profileDistances.size() : -1;
        int step = segment.forward() ? 1 : -1;
        for (int i = startIndex; i != endIndex; i += step) {
            double geometryLocal = (profileDistances.get(i) - profileDistances.getFirst())
                * (segment.length() / profileSpan);
            double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
            double instanceStation = segment.startStation() + chainLocal;
            double roadStation = RoadStationing.toCanonicalChainage(network, road, instanceStation);
            double design = resolveDesignElevation(edgeResult, i);
            double build = resolveBuildElevation(edgeResult, i);
            Double water = resolveWaterElevation(edgeResult, i);
            if (!stations.isEmpty()
                    && Math.abs(roadStation - stations.getLast()) <= STATION_MERGE_TOLERANCE) {
                groundElevations.set(
                    groundElevations.size() - 1,
                    edgeResult.profileGroundHeights.get(i).doubleValue());
                designElevations.set(designElevations.size() - 1, design);
                buildElevations.set(buildElevations.size() - 1, build);
                if (!waterElevations.isEmpty()) {
                    waterElevations.set(waterElevations.size() - 1, water);
                }
                if (!edgeResult.profileGuideLine.isEmpty()
                        && edgeResult.profileGuideLine.size() == profileDistances.size()) {
                    guideElevations.set(
                        guideElevations.size() - 1,
                        edgeResult.profileGuideLine.get(i).doubleValue());
                }
                continue;
            }
            stations.add(roadStation);
            groundElevations.add(edgeResult.profileGroundHeights.get(i).doubleValue());
            designElevations.add(design);
            buildElevations.add(build);
            waterElevations.add(water);
            if (!edgeResult.profileGuideLine.isEmpty()
                    && edgeResult.profileGuideLine.size() == profileDistances.size()) {
                guideElevations.add(edgeResult.profileGuideLine.get(i).doubleValue());
            } else if (!guideElevations.isEmpty()) {
                guideElevations.add(guideElevations.getLast());
            } else {
                guideElevations.add(edgeResult.profileGroundHeights.get(i).doubleValue());
            }
        }
    }

    private static void appendWaterCrossings(
            RoadNetwork network,
            Road road,
            OrientedRoadSegment segment,
            RoadGenerationResult edgeResult,
            List<WaterCrossingChartMarker> waterCrossings) {
        if (edgeResult.profileWaterCrossingMarkers == null
                || edgeResult.profileWaterCrossingMarkers.isEmpty()) {
            return;
        }
        List<Double> profileDistances = edgeResult.profileDistances;
        double profileSpan = profileDistances.getLast() - profileDistances.getFirst();
        if (profileSpan <= 1e-9) {
            profileSpan = segment.length();
        }
        for (WaterCrossingChartMarker marker : edgeResult.profileWaterCrossingMarkers) {
            double startGeometry = marker.startStation();
            double endGeometry = marker.endStation();
            if (!segment.forward()) {
                double total = profileDistances.getLast() - profileDistances.getFirst();
                startGeometry = total - marker.endStation();
                endGeometry = total - marker.startStation();
            }
            double startChain = segment.chainLocalFromGeometryLocal(
                startGeometry * (segment.length() / profileSpan));
            double endChain = segment.chainLocalFromGeometryLocal(
                endGeometry * (segment.length() / profileSpan));
            double roadStart = RoadStationing.toCanonicalChainage(
                network, road, segment.startStation() + startChain);
            double roadEnd = RoadStationing.toCanonicalChainage(
                network, road, segment.startStation() + endChain);
            waterCrossings.add(new WaterCrossingChartMarker(roadStart, roadEnd, marker.strategy()));
        }
    }

    private static Double resolveWaterElevation(RoadGenerationResult edgeResult, int index) {
        if (edgeResult.profileWaterHeights == null
                || index < 0
                || index >= edgeResult.profileWaterHeights.size()) {
            return null;
        }
        Integer water = edgeResult.profileWaterHeights.get(index);
        return water == null ? null : water.doubleValue();
    }

    private static double resolveDesignElevation(RoadGenerationResult edgeResult, int index) {
        if (!edgeResult.profileDesignElevations.isEmpty()
                && index < edgeResult.profileDesignElevations.size()) {
            return edgeResult.profileDesignElevations.get(index);
        }
        if (!edgeResult.profileGuideLine.isEmpty() && index < edgeResult.profileGuideLine.size()) {
            return edgeResult.profileGuideLine.get(index).doubleValue();
        }
        return resolveBuildElevation(edgeResult, index);
    }

    private static double resolveBuildElevation(RoadGenerationResult edgeResult, int index) {
        if (!edgeResult.profileBuildHeights.isEmpty()
                && index < edgeResult.profileBuildHeights.size()) {
            return edgeResult.profileBuildHeights.get(index).doubleValue();
        }
        return edgeResult.profileGroundHeights.get(index).doubleValue();
    }

    private static void appendBuildSamples(
            RoadNetwork network,
            Road road,
            OrientedRoadSegment segment,
            RoadGenerationResult edgeResult,
            List<BuildHeightSample> buildSamples) {
        if (edgeResult.profileBuildSamples == null || edgeResult.profileBuildSamples.isEmpty()) {
            return;
        }
        if (segment.forward()) {
            for (BuildHeightSample sample : edgeResult.profileBuildSamples) {
                appendMappedBuildSample(network, road, segment, edgeResult, sample, buildSamples);
            }
        } else {
            for (int i = edgeResult.profileBuildSamples.size() - 1; i >= 0; i--) {
                appendMappedBuildSample(
                    network, road, segment, edgeResult, edgeResult.profileBuildSamples.get(i), buildSamples);
            }
        }
    }

    private static void appendMappedBuildSample(
            RoadNetwork network,
            Road road,
            OrientedRoadSegment segment,
            RoadGenerationResult edgeResult,
            BuildHeightSample sample,
            List<BuildHeightSample> buildSamples) {
        double roadStation = toRoadStation(network, road, segment, edgeResult, sample.station());
        if (!buildSamples.isEmpty()
                && Math.abs(roadStation - buildSamples.getLast().station()) <= STATION_MERGE_TOLERANCE) {
            buildSamples.set(
                buildSamples.size() - 1,
                new BuildHeightSample(roadStation, sample.designElevation(), sample.buildY()));
            return;
        }
        buildSamples.add(new BuildHeightSample(roadStation, sample.designElevation(), sample.buildY()));
    }

    private static double toRoadStation(
            RoadNetwork network,
            Road road,
            OrientedRoadSegment segment,
            RoadGenerationResult edgeResult,
            double edgeStation) {
        List<Double> profileDistances = edgeResult.profileDistances;
        double profileSpan = profileDistances.getLast() - profileDistances.getFirst();
        if (profileSpan <= 1e-9) {
            profileSpan = segment.length();
        }
        double geometryLocal = (edgeStation - profileDistances.getFirst())
            * (segment.length() / profileSpan);
        double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
        double instanceStation = segment.startStation() + chainLocal;
        return RoadStationing.toCanonicalChainage(network, road, instanceStation);
    }
}
