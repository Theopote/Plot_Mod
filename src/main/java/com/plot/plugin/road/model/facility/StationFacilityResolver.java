package com.plot.plugin.road.model.facility;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 沿桩号解析有效附属设施区间。
 */
public final class StationFacilityResolver {

    private static final double EPSILON = 1e-9;

    private StationFacilityResolver() {
    }

    public static boolean hasStationFacilities(Road road) {
        return road != null
            && road.getStationFacilities() != null
            && !road.getStationFacilities().isEmpty();
    }

    public static boolean isActive(StationFacilityRun run, double chainage, double roadEnd) {
        if (run == null || !Double.isFinite(chainage)) {
            return false;
        }
        if (chainage + EPSILON < run.getStartStation()) {
            return false;
        }
        double end = run.getEndStation() != null ? run.getEndStation() : roadEnd;
        return chainage <= end + EPSILON;
    }

    public static List<StationFacilityRun> activeAt(Road road, double chainage, double roadEnd) {
        if (road == null || road.getStationFacilities() == null || road.getStationFacilities().isEmpty()) {
            return List.of();
        }
        List<StationFacilityRun> active = new ArrayList<>();
        for (StationFacilityRun run : road.getStationFacilities().sortedRuns()) {
            if (isActive(run, chainage, roadEnd)) {
                active.add(run);
            }
        }
        return List.copyOf(active);
    }

    public static List<StationFacilityRun> activeAt(RoadNetwork network, Road road, double chainage) {
        if (network == null || road == null) {
            return List.of();
        }
        return activeAt(road, chainage, RoadStationing.totalLength(network, road));
    }

    public static List<StationFacilityRun> activeInRange(
            Road road,
            double rangeStart,
            double rangeEnd,
            double roadEnd) {
        if (road == null || road.getStationFacilities() == null || road.getStationFacilities().isEmpty()) {
            return List.of();
        }
        if (!Double.isFinite(rangeStart) || !Double.isFinite(rangeEnd) || rangeEnd + EPSILON < rangeStart) {
            return List.of();
        }
        List<StationFacilityRun> active = new ArrayList<>();
        for (StationFacilityRun run : road.getStationFacilities().sortedRuns()) {
            double runEnd = run.getEndStation() != null ? run.getEndStation() : roadEnd;
            if (runEnd + EPSILON < rangeStart || run.getStartStation() - EPSILON > rangeEnd) {
                continue;
            }
            active.add(run);
        }
        return List.copyOf(active);
    }

    public static List<StationFacilityRun> activeOnEdge(RoadNetwork network, Road road, RoadEdge edge) {
        if (network == null || road == null || edge == null) {
            return List.of();
        }
        if (!RoadStationing.isStationable(network, road)) {
            return List.of();
        }
        double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
        if (segmentStart < 0.0) {
            return List.of();
        }
        double segmentEnd = segmentStart + edge.getLength();
        return activeInRange(
            road,
            segmentStart,
            segmentEnd,
            RoadStationing.totalLength(network, road));
    }

    public static boolean hasActiveKind(
            RoadNetwork network,
            Road road,
            double chainage,
            RoadFacilityKind kind) {
        if (kind == null) {
            return false;
        }
        for (StationFacilityRun run : activeAt(network, road, chainage)) {
            if (run.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    public static EnumSet<RoadFacilityKind> activeKindsAt(RoadNetwork network, Road road, double chainage) {
        EnumSet<RoadFacilityKind> kinds = EnumSet.noneOf(RoadFacilityKind.class);
        for (StationFacilityRun run : activeAt(network, road, chainage)) {
            kinds.add(run.getKind());
        }
        return kinds;
    }

    public static boolean usesStationGatedDrainage(Road road) {
        if (!hasStationFacilities(road)) {
            return false;
        }
        for (StationFacilityRun run : road.getStationFacilities().sortedRuns()) {
            if (run.getKind() == RoadFacilityKind.DRAINAGE) {
                return true;
            }
        }
        return false;
    }

    public static String describe(StationFacilityRun run, RoadStationFormat format) {
        if (run == null) {
            return "";
        }
        String start = RoadStationing.format(run.getStartStation(), format);
        String end = run.getEndStation() != null
            ? RoadStationing.format(run.getEndStation(), format)
            : "END";
        StringBuilder line = new StringBuilder()
            .append(start)
            .append('-')
            .append(end)
            .append(' ')
            .append(run.getKind().name())
            .append(' ')
            .append(run.getSide().name());
        if (run.getHeight() != null) {
            line.append(" H=").append(formatHeight(run.getHeight()));
        }
        if (run.getMaterial() != null && !run.getMaterial().isBlank()) {
            line.append(" M=").append(run.getMaterial());
        }
        return line.toString();
    }

    private static String formatHeight(double height) {
        if (Math.abs(height - Math.rint(height)) < EPSILON) {
            return String.valueOf((int) Math.rint(height));
        }
        return String.format("%.1f", height);
    }
}
