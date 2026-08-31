package com.plot.plugin.road.station;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 道路反向时镜像沿桩号存储的工程数据，使桩号语义与 {@link RoadStationing} 新链方向一致。
 */
public final class RoadStationMirroring {

    private static final double EPSILON = 1e-6;

    private RoadStationMirroring() {
    }

    public static void mirrorRoadStationData(Road road, double totalLength) {
        mirrorRoadStationDataInRange(road, 0.0, totalLength, totalLength);
    }

    public static void mirrorRoadStationDataInRange(
            Road road,
            double rangeStart,
            double rangeEnd,
            double totalLength) {
        if (road == null || totalLength <= EPSILON || rangeEnd <= rangeStart + EPSILON) {
            return;
        }
        if (road.getVariableCrossSections() != null && !road.getVariableCrossSections().isEmpty()) {
            road.setVariableCrossSections(mirrorVariableCrossSectionsInRange(
                road.getVariableCrossSections(),
                road.getCrossSection(),
                totalLength,
                rangeStart,
                rangeEnd));
        }
        if (road.getStationFacilities() != null && !road.getStationFacilities().isEmpty()) {
            road.setStationFacilities(mirrorStationFacilitiesInRange(
                road.getStationFacilities(),
                totalLength,
                rangeStart,
                rangeEnd));
        }
    }

    /**
     * 单段反向后的沿桩号数据联动：单分段道路整路镜像，多分段仅镜像该段桩号区间。
     */
    public static void mirrorStationDataForReversedEdge(RoadNetwork network, String edgeId) {
        if (network == null || edgeId == null || edgeId.isBlank()) {
            return;
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || edge.getRoadId() == null) {
            return;
        }
        Road road = network.getRoad(edge.getRoadId());
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return;
        }
        double segmentStart = RoadStationing.segmentStartStation(network, road, edgeId);
        if (segmentStart < 0.0) {
            return;
        }
        double totalLength = RoadStationing.totalLength(network, road);
        List<String> ordered = com.plot.plugin.road.model.RoadSegmentOrdering.orderedSegmentIds(network, road);
        if (ordered.size() == 1) {
            mirrorRoadStationData(road, totalLength);
            return;
        }
        mirrorRoadStationDataInRange(
            road,
            segmentStart,
            segmentStart + edge.getLength(),
            totalLength);
    }

    public static RoadVariableCrossSections mirrorVariableCrossSectionsInRange(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLength,
            double rangeStart,
            double rangeEnd) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        if (Math.abs(rangeStart) <= EPSILON && rangeEnd >= totalLength - EPSILON) {
            return mirrorVariableCrossSections(source, roadDefault, totalLength);
        }
        List<StationCrossSection> sorted = source.sortedStations();
        if (sorted.isEmpty()) {
            return source.copy();
        }

        RoadCrossSection defaultSection = roadDefault != null ? roadDefault : new RoadCrossSection();
        List<Interval> intervals = buildIntervals(sorted, defaultSection, totalLength);
        List<Interval> mirrored = new ArrayList<>();
        for (Interval interval : intervals) {
            mirrored.addAll(mirrorIntervalInRange(interval, rangeStart, rangeEnd));
        }
        mirrored = mergeAdjacentIntervals(mirrored);
        mirrored.sort(Comparator.comparingDouble(interval -> interval.start));
        return intervalsToVariableCrossSections(mirrored, defaultSection);
    }

    public static RoadStationFacilities mirrorStationFacilitiesInRange(
            RoadStationFacilities source,
            double totalLength,
            double rangeStart,
            double rangeEnd) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        if (Math.abs(rangeStart) <= EPSILON && rangeEnd >= totalLength - EPSILON) {
            return mirrorStationFacilities(source, totalLength);
        }
        List<StationFacilityRun> mirrored = new ArrayList<>();
        for (StationFacilityRun run : source.getRuns()) {
            mirrored.addAll(mirrorFacilityRunInRange(run, totalLength, rangeStart, rangeEnd));
        }
        mirrored.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return mirrored.isEmpty() ? null : new RoadStationFacilities(mirrored);
    }

    public static RoadVariableCrossSections mirrorVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLength) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<StationCrossSection> sorted = source.sortedStations();
        if (sorted.isEmpty()) {
            return source.copy();
        }

        RoadCrossSection defaultSection = roadDefault != null ? roadDefault : new RoadCrossSection();
        List<Interval> intervals = buildIntervals(sorted, defaultSection, totalLength);
        List<Interval> mirrored = new ArrayList<>();
        for (Interval interval : intervals) {
            mirrored.add(new Interval(
                totalLength - interval.end,
                totalLength - interval.start,
                interval.template));
        }
        mirrored.sort(Comparator.comparingDouble(interval -> interval.start));
        return intervalsToVariableCrossSections(mirrored, defaultSection);
    }

    public static RoadStationFacilities mirrorStationFacilities(
            RoadStationFacilities source,
            double totalLength) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<StationFacilityRun> mirrored = new ArrayList<>();
        for (StationFacilityRun run : source.getRuns()) {
            StationFacilityRun reversed = mirrorFacilityRun(run, totalLength);
            if (reversed != null) {
                mirrored.add(reversed);
            }
        }
        mirrored.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return mirrored.isEmpty() ? null : new RoadStationFacilities(mirrored);
    }

    static StationFacilityRun mirrorFacilityRun(StationFacilityRun run, double totalLength) {
        List<StationFacilityRun> mirrored = mirrorFacilityRunInRange(run, totalLength, 0.0, totalLength);
        return mirrored.isEmpty() ? null : mirrored.getFirst();
    }

    static List<StationFacilityRun> mirrorFacilityRunInRange(
            StationFacilityRun run,
            double totalLength,
            double rangeStart,
            double rangeEnd) {
        if (run == null || totalLength <= EPSILON) {
            return List.of();
        }
        double runStart = run.getStartStation();
        double runEnd = run.getEndStation() != null ? run.getEndStation() : totalLength;
        List<StationFacilityRun> parts = new ArrayList<>();

        if (runStart < rangeStart - EPSILON) {
            double end = Math.min(runEnd, rangeStart);
            if (end > runStart + EPSILON) {
                StationFacilityRun part = copyFacilityRun(run, runStart, end, totalLength, run.getSide());
                if (part != null) {
                    parts.add(part);
                }
            }
        }

        double midStart = Math.max(runStart, rangeStart);
        double midEnd = Math.min(runEnd, rangeEnd);
        if (midEnd > midStart + EPSILON) {
            double newStart = rangeStart + rangeEnd - midEnd;
            double newEnd = rangeStart + rangeEnd - midStart;
            Double endStation = newEnd >= totalLength - EPSILON ? null : newEnd;
            parts.add(new StationFacilityRun(
                Math.max(0.0, newStart),
                endStation,
                run.getKind(),
                mirrorSide(run.getSide()),
                run.getMaterial(),
                run.getHeight()));
        }

        if (runEnd > rangeEnd + EPSILON) {
            double start = Math.max(runStart, rangeEnd);
            if (runEnd > start + EPSILON) {
                StationFacilityRun part = copyFacilityRun(run, start, runEnd, totalLength, run.getSide());
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        return parts;
    }

    private static StationFacilityRun copyFacilityRun(
            StationFacilityRun run,
            double start,
            double end,
            double totalLength,
            RoadFacilitySide side) {
        if (end <= start + EPSILON) {
            return null;
        }
        Double endStation = end;
        if (run.getEndStation() == null && end >= totalLength - EPSILON) {
            endStation = null;
        } else if (end >= totalLength - EPSILON) {
            endStation = null;
        }
        if (endStation != null && endStation <= start + EPSILON) {
            return null;
        }
        return new StationFacilityRun(start, endStation, run.getKind(), side, run.getMaterial(), run.getHeight());
    }

    private static List<Interval> buildIntervals(
            List<StationCrossSection> sorted,
            RoadCrossSection defaultSection,
            double totalLength) {
        List<Interval> intervals = new ArrayList<>();
        double previous = 0.0;
        RoadCrossSection active = defaultSection;
        for (StationCrossSection entry : sorted) {
            if (entry.getStation() > previous + EPSILON) {
                intervals.add(new Interval(previous, entry.getStation(), active));
            }
            previous = entry.getStation();
            active = entry.getCrossSection();
        }
        if (totalLength > previous + EPSILON) {
            intervals.add(new Interval(previous, totalLength, active));
        }
        return intervals;
    }

    private static List<Interval> mirrorIntervalInRange(
            Interval interval,
            double rangeStart,
            double rangeEnd) {
        List<Interval> parts = new ArrayList<>();
        if (interval.end <= interval.start + EPSILON) {
            return parts;
        }
        if (interval.start < rangeStart - EPSILON) {
            parts.add(new Interval(interval.start, Math.min(interval.end, rangeStart), interval.template));
        }
        if (interval.end > rangeEnd + EPSILON) {
            parts.add(new Interval(Math.max(interval.start, rangeEnd), interval.end, interval.template));
        }
        double midStart = Math.max(interval.start, rangeStart);
        double midEnd = Math.min(interval.end, rangeEnd);
        if (midEnd > midStart + EPSILON) {
            parts.add(new Interval(
                rangeStart + rangeEnd - midEnd,
                rangeStart + rangeEnd - midStart,
                interval.template));
        }
        return parts;
    }

    private static List<Interval> mergeAdjacentIntervals(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            return intervals;
        }
        intervals.sort(Comparator.comparingDouble(interval -> interval.start));
        List<Interval> merged = new ArrayList<>();
        Interval current = intervals.getFirst();
        for (int i = 1; i < intervals.size(); i++) {
            Interval next = intervals.get(i);
            if (Math.abs(next.start - current.end) <= EPSILON
                && crossSectionEquivalent(next.template, current.template)) {
                current = new Interval(current.start, next.end, current.template);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static RoadVariableCrossSections intervalsToVariableCrossSections(
            List<Interval> intervals,
            RoadCrossSection defaultSection) {
        List<StationCrossSection> stations = new ArrayList<>();
        for (Interval interval : intervals) {
            if (interval.start <= EPSILON) {
                if (!crossSectionEquivalent(interval.template, defaultSection)) {
                    stations.add(StationCrossSection.at(0.0, interval.template.copy()));
                }
                continue;
            }
            stations.add(StationCrossSection.at(interval.start, interval.template.copy()));
        }
        return stations.isEmpty() ? null : new RoadVariableCrossSections(stations);
    }

    static RoadFacilitySide mirrorSide(RoadFacilitySide side) {
        if (side == null) {
            return null;
        }
        return switch (side) {
            case LEFT -> RoadFacilitySide.RIGHT;
            case RIGHT -> RoadFacilitySide.LEFT;
            case BOTH -> RoadFacilitySide.BOTH;
        };
    }

    private static boolean crossSectionEquivalent(RoadCrossSection left, RoadCrossSection right) {
        if (left == null || right == null) {
            return false;
        }
        Integer leftWidth = left.getCarriageway().getWidth();
        Integer rightWidth = right.getCarriageway().getWidth();
        return leftWidth != null && leftWidth.equals(rightWidth);
    }

    private record Interval(double start, double end, RoadCrossSection template) {
    }
}
