package com.plot.plugin.road.station;

import com.plot.plugin.road.model.Road;
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
        if (road == null || totalLength < 0.0 || !Double.isFinite(totalLength)) {
            return;
        }
        if (road.getVariableCrossSections() != null && !road.getVariableCrossSections().isEmpty()) {
            road.setVariableCrossSections(mirrorVariableCrossSections(
                road.getVariableCrossSections(),
                road.getCrossSection(),
                totalLength));
        }
        if (road.getStationFacilities() != null && !road.getStationFacilities().isEmpty()) {
            road.setStationFacilities(mirrorStationFacilities(road.getStationFacilities(), totalLength));
        }
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

        List<Interval> mirrored = new ArrayList<>();
        for (Interval interval : intervals) {
            mirrored.add(new Interval(
                totalLength - interval.end,
                totalLength - interval.start,
                interval.template));
        }
        mirrored.sort(Comparator.comparingDouble(interval -> interval.start));

        List<StationCrossSection> stations = new ArrayList<>();
        for (Interval interval : mirrored) {
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
        if (run == null || totalLength <= EPSILON) {
            return null;
        }
        double oldEnd = run.getEndStation() != null ? run.getEndStation() : totalLength;
        double newStart = totalLength - oldEnd;
        double newEnd = totalLength - run.getStartStation();
        if (newEnd <= newStart + EPSILON) {
            return null;
        }
        Double endStation = newEnd >= totalLength - EPSILON ? null : newEnd;
        return new StationFacilityRun(
            Math.max(0.0, newStart),
            endStation,
            run.getKind(),
            mirrorSide(run.getSide()),
            run.getMaterial(),
            run.getHeight());
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
