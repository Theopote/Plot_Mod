package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
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

        List<Double> stations = new ArrayList<>();
        List<Double> groundElevations = new ArrayList<>();
        List<Double> previewElevations = new ArrayList<>();
        List<Double> guideElevations = new ArrayList<>();

        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            RoadGenerationResult edgeResult = edgeResults.get(segment.edgeId());
            if (edgeResult == null || !edgeResult.hasProfileData()) {
                continue;
            }
            appendSegmentSamples(
                segment,
                edgeResult,
                stations,
                groundElevations,
                previewElevations,
                guideElevations);
        }
        if (stations.size() < 2) {
            return Optional.empty();
        }

        List<ProfileControlPoint> controlPoints = VerticalProfileControlPoints.forRoad(network, road);
        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forRoad(
            network, road, config, edgeResults);

        return Optional.of(new RoadProfileChartData(
            road.getId(),
            totalStation,
            List.copyOf(stations),
            List.copyOf(groundElevations),
            List.copyOf(previewElevations),
            List.copyOf(guideElevations),
            controlPoints,
            intersections));
    }

    private static void appendSegmentSamples(
            OrientedRoadSegment segment,
            RoadGenerationResult edgeResult,
            List<Double> stations,
            List<Double> groundElevations,
            List<Double> previewElevations,
            List<Double> guideElevations) {
        List<Double> profileDistances = edgeResult.profileDistances;
        double profileSpan = profileDistances.getLast() - profileDistances.getFirst();
        if (profileSpan <= 1e-9) {
            profileSpan = segment.length();
        }
        double segmentStart = segment.startStation();
        double segmentLength = segment.length();

        for (int i = 0; i < profileDistances.size(); i++) {
            double localProfile = profileDistances.get(i) - profileDistances.getFirst();
            double roadStation = segmentStart + (localProfile / profileSpan) * segmentLength;
            if (!stations.isEmpty()
                    && Math.abs(roadStation - stations.getLast()) <= STATION_MERGE_TOLERANCE) {
                groundElevations.set(
                    groundElevations.size() - 1,
                    edgeResult.profileGroundHeights.get(i).doubleValue());
                previewElevations.set(
                    previewElevations.size() - 1,
                    edgeResult.profileTargetHeights.get(i).doubleValue());
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
            previewElevations.add(edgeResult.profileTargetHeights.get(i).doubleValue());
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
}
