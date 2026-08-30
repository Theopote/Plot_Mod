package com.plot.plugin.road.station;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.model.RoadTopologyViolationKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Road-local 里程（chainage）计算：沿有序分段链累计弧长。
 * <p>
 * 单位与 {@link RoadEdge#getLength()} 一致（canvas 平面距离，与纵断面 profile 里程对齐）。
 * 仅对拓扑可维护的道路（无分叉、无断开）保证语义；分叉/断开时 {@link #isStationable} 为 false。
 *
 * @see docs/development/task-assignments/RoadSystemPlugin_Phase2_Stationing_v1.md
 */
public final class RoadStationing {

    private static final double STATION_EPSILON = 1e-6;

    private RoadStationing() {
    }

    public static List<String> orderedSegments(RoadNetwork network, Road road) {
        return RoadSegmentOrdering.orderedSegmentIds(network, road);
    }

    public static double totalLength(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return 0.0;
        }
        double total = 0.0;
        for (String segmentId : orderedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                total += edge.getLength();
            }
        }
        return total;
    }

    /**
     * 道路是否具备稳定桩号语义（连通、无分叉；LINEAR 或 LOOP）。
     */
    public static boolean isStationable(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        if (network.getRoad(road.getId()) == null) {
            return false;
        }
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validateRoad(network, road)) {
            if (violation.kind() == RoadTopologyViolationKind.ROAD_DISCONNECTED
                    || violation.kind() == RoadTopologyViolationKind.ROAD_BRANCHING) {
                return false;
            }
            if (violation.kind() == RoadTopologyViolationKind.ROAD_CYCLE
                    && road.getTopologyMode() != RoadTopologyMode.LOOP) {
                return false;
            }
        }
        return orderedSegments(network, road).size() > 0;
    }

    public static boolean isValid(RoadNetwork network, RoadStation station) {
        if (station == null) {
            return false;
        }
        Road road = network != null ? network.getRoad(station.roadId()) : null;
        if (!isStationable(network, road)) {
            return false;
        }
        double total = totalLength(network, road);
        return station.chainageMeters() >= -STATION_EPSILON
            && station.chainageMeters() <= total + STATION_EPSILON;
    }

    public static double segmentStartStation(RoadNetwork network, Road road, String segmentId) {
        if (network == null || road == null || segmentId == null || segmentId.isBlank()) {
            return 0.0;
        }
        double station = 0.0;
        for (String orderedId : orderedSegments(network, road)) {
            if (orderedId.equals(segmentId)) {
                return station;
            }
            RoadEdge edge = network.getEdge(orderedId);
            if (edge != null) {
                station += edge.getLength();
            }
        }
        return -1.0;
    }

    public static Optional<RoadStation> stationAt(
            RoadNetwork network,
            Road road,
            String segmentId,
            double localDistance) {
        if (!isStationable(network, road) || segmentId == null || segmentId.isBlank()) {
            return Optional.empty();
        }
        if (!Double.isFinite(localDistance)) {
            return Optional.empty();
        }
        double segmentStart = segmentStartStation(network, road, segmentId);
        if (segmentStart < 0.0) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(segmentId);
        if (edge == null) {
            return Optional.empty();
        }
        if (localDistance < -STATION_EPSILON || localDistance > edge.getLength() + STATION_EPSILON) {
            return Optional.empty();
        }
        double chainage = segmentStart + Math.max(0.0, localDistance);
        if (!isValid(network, new RoadStation(road.getId(), chainage))) {
            return Optional.empty();
        }
        return Optional.of(new RoadStation(road.getId(), chainage));
    }

    public static Optional<SegmentStation> resolve(RoadNetwork network, Road road, double chainageMeters) {
        if (!isStationable(network, road) || !Double.isFinite(chainageMeters)) {
            return Optional.empty();
        }
        if (!isValid(network, new RoadStation(road.getId(), chainageMeters))) {
            return Optional.empty();
        }

        double remaining = chainageMeters;
        List<String> segments = orderedSegments(network, road);
        for (int i = 0; i < segments.size(); i++) {
            String segmentId = segments.get(i);
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                return Optional.empty();
            }
            double length = edge.getLength();
            boolean isLast = i == segments.size() - 1;
            if (remaining < length - STATION_EPSILON || isLast) {
                double local = Math.min(Math.max(0.0, remaining), length);
                return Optional.of(new SegmentStation(segmentId, local));
            }
            remaining -= length;
        }
        return Optional.empty();
    }

    public static String format(double chainageMeters, RoadStationFormat format) {
        if (!Double.isFinite(chainageMeters)) {
            return "-";
        }
        int kilometers = (int) Math.floor(chainageMeters / 1000.0);
        double meters = chainageMeters - kilometers * 1000.0;
        if (Math.abs(meters - Math.rint(meters)) < 0.05) {
            meters = Math.rint(meters);
            if (format == RoadStationFormat.KILOMETER_PLUS) {
                return String.format(Locale.ROOT, "K%d+%03.0f", kilometers, meters);
            }
            return String.format(Locale.ROOT, "%d+%03.0f", kilometers, meters);
        }
        if (format == RoadStationFormat.KILOMETER_PLUS) {
            return String.format(Locale.ROOT, "K%d+%06.2f", kilometers, meters);
        }
        return String.format(Locale.ROOT, "%d+%06.2f", kilometers, meters);
    }

    public static String format(RoadStation station, RoadStationFormat format) {
        if (station == null) {
            return "-";
        }
        return format(station.chainageMeters(), format);
    }

    public static String formatRange(double startMeters, double endMeters, RoadStationFormat format) {
        return format(startMeters, format) + " – " + format(endMeters, format);
    }

    public static List<Double> segmentStartStations(RoadNetwork network, Road road) {
        List<Double> stations = new ArrayList<>();
        if (network == null || road == null) {
            return stations;
        }
        double station = 0.0;
        for (String segmentId : orderedSegments(network, road)) {
            stations.add(station);
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                station += edge.getLength();
            }
        }
        return stations;
    }
}
