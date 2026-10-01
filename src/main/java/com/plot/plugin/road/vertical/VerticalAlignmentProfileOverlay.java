package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 设计纵断面叠加线：道路级 canonical station + double 高程。
 */
public record VerticalAlignmentProfileOverlay(List<Double> stations, List<Double> elevations) {

    private static final double MIN_SAMPLE_SPACING = 2.0;

    public VerticalAlignmentProfileOverlay(List<Double> stations, List<Double> elevations) {
        this.stations = List.copyOf(stations);
        this.elevations = List.copyOf(elevations);
    }

    /**
     * @deprecated 使用 {@link #stations()}；旧 edge-local 距离。
     */
    @Deprecated
    public List<Double> distances() {
        return stations;
    }

    /**
     * @deprecated 使用 {@link #elevations()}。
     */
    @Deprecated
    public List<Integer> heights() {
        List<Integer> legacy = new ArrayList<>(elevations.size());
        for (double elevation : elevations) {
            legacy.add((int) Math.round(elevation));
        }
        return legacy;
    }

    public boolean isEmpty() {
        return stations.size() < 2;
    }

    public static Optional<VerticalAlignmentProfileOverlay> forRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return Optional.empty();
        }
        if (!RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return Optional.empty();
        }
        double maxGrade = road.getMaxSlope() != null ? road.getMaxSlope() : 8.0;
        RoadVerticalAlignment alignment = RoadVerticalAlignmentResolver.resolveSynced(
                network, road, maxGrade);
        return forAlignment(network, road, alignment);
    }

    /** 从指定 alignment 采样设计纵断面叠加线（Draft 预览）。 */
    public static Optional<VerticalAlignmentProfileOverlay> forAlignment(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        if (network == null || road == null || alignment == null) {
            return Optional.empty();
        }
        if (!RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return Optional.empty();
        }
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return Optional.empty();
        }
        double roadLength = RoadStationing.canonicalLength(network, road);
        double spacing = Math.max(MIN_SAMPLE_SPACING, roadLength / 80.0);
        List<Double> sampleStations = new ArrayList<>();
        List<Double> sampleElevations = new ArrayList<>();
        for (VerticalAlignmentGeometry.ProfileSample sample
                : VerticalAlignmentGeometry.sample(alignment, spacing)) {
            if (sample.station() < -1e-6 || sample.station() > roadLength + 1e-6) {
                continue;
            }
            sampleStations.add(sample.station());
            sampleElevations.add(sample.elevation());
        }
        appendEndpointIfMissing(sampleStations, sampleElevations, 0.0, alignment);
        appendEndpointIfMissing(sampleStations, sampleElevations, roadLength, alignment);
        if (sampleStations.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new VerticalAlignmentProfileOverlay(sampleStations, sampleElevations));
    }

    /**
     * @deprecated 纵断面编辑器已升级为道路级。
     */
    @Deprecated
    public static Optional<VerticalAlignmentProfileOverlay> forEdge(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null) {
            return Optional.empty();
        }
        String roadId = edge.getRoadId();
        if (roadId == null) {
            return Optional.empty();
        }
        Road road = network.getRoad(roadId);
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return Optional.empty();
        }
        double maxGrade = road.getMaxSlope() != null ? road.getMaxSlope() : 8.0;
        RoadVerticalAlignment alignment = RoadVerticalAlignmentResolver.resolveSynced(
                network, road, maxGrade);
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return Optional.empty();
        }
        return RoadStationing.orientedSegment(network, road, edge.getId()).flatMap(oriented -> {
            double segmentStart = oriented.startStation();
            double edgeLength = oriented.length();
            double spacing = Math.max(MIN_SAMPLE_SPACING, edgeLength / 40.0);
            List<Double> localDistances = new ArrayList<>();
            List<Double> localElevations = new ArrayList<>();
            double segmentEnd = oriented.endStation();
            for (VerticalAlignmentGeometry.ProfileSample sample : VerticalAlignmentGeometry.sample(alignment, spacing)) {
                if (sample.station() < segmentStart - 1e-6 || sample.station() > segmentEnd + 1e-6) {
                    continue;
                }
                double chainLocal = sample.station() - segmentStart;
                localDistances.add(oriented.geometryLocalFromChainLocal(chainLocal));
                localElevations.add(sample.elevation());
            }
            appendEndpointIfMissingEdge(oriented, localDistances, localElevations, 0.0, alignment);
            appendEndpointIfMissingEdge(oriented, localDistances, localElevations, edgeLength, alignment);
            if (localDistances.size() < 2) {
                return Optional.empty();
            }
            return Optional.of(new VerticalAlignmentProfileOverlay(localDistances, localElevations));
        });
    }

    private static void appendEndpointIfMissing(
            List<Double> stations,
            List<Double> elevations,
            double station,
            RoadVerticalAlignment alignment) {
        if (stations.stream().anyMatch(value -> Math.abs(value - station) < 1e-6)) {
            return;
        }
        double height = VerticalAlignmentGeometry.elevationAt(alignment, station)
                .orElse(elevations.isEmpty() ? 64.0 : elevations.getLast());
        stations.add(station);
        elevations.add(height);
        sortByStation(stations, elevations);
    }

    private static void appendEndpointIfMissingEdge(
            OrientedRoadSegment oriented,
            List<Double> distances,
            List<Double> elevations,
            double geometryLocalDistance,
            RoadVerticalAlignment alignment) {
        if (distances.stream().anyMatch(distance -> Math.abs(distance - geometryLocalDistance) < 1e-6)) {
            return;
        }
        double chainage = oriented.roadStationAtGeometryLocal(geometryLocalDistance);
        double height = VerticalAlignmentGeometry.elevationAt(alignment, chainage)
                .orElse(elevations.isEmpty() ? 64.0 : elevations.getLast());
        distances.add(geometryLocalDistance);
        elevations.add(height);
        sortByStation(distances, elevations);
    }

    private static void sortByStation(List<Double> stations, List<Double> elevations) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            indices.add(i);
        }
        indices.sort(Comparator.comparingDouble(stations::get));
        List<Double> sortedStations = new ArrayList<>();
        List<Double> sortedElevations = new ArrayList<>();
        for (int index : indices) {
            sortedStations.add(stations.get(index));
            sortedElevations.add(elevations.get(index));
        }
        stations.clear();
        elevations.clear();
        stations.addAll(sortedStations);
        elevations.addAll(sortedElevations);
    }
}
