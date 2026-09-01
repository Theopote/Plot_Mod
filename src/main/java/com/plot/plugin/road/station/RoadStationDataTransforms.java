package com.plot.plugin.road.station;

import com.plot.plugin.road.alignment.HorizontalAlignmentChainOriginAligner;
import com.plot.plugin.road.alignment.HorizontalAlignmentPolylineFitter;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 沿桩号工程数据的变换：拆分、裁剪、重映射（{@link RoadStationMirroring} 负责反向镜像）。
 */
public final class RoadStationDataTransforms {

    private static final double EPSILON = 1e-6;

    private RoadStationDataTransforms() {
    }

    /**
     * 在 {@code splitStation} 处拆分工程数据（{@link CenterlineEditStationPolicy#PARTITION_AND_RESET_TAIL}）：
     * {@code head} 保留链头 [0, split)，{@code tail} 获得 [split, total) 并重映射为 K0+000 起。
     * <p>
     * 目标域规则为 half-open；VCS / 设施裁剪已按区间语义实现。PVI head 裁剪暂用
     * {@code station <= split}（分段界切分时通常无 PVI 落点）；任意桩号切分上线前须改为
     * {@code station < split}，见 ADR 0007 §7。
     *
     * @return 是否应基于折线重新拟合平面线形
     */
    public static boolean applyRoadSplit(Road head, Road tail, double splitStation, double totalLength) {
        if (head == null || tail == null || totalLength <= EPSILON) {
            return false;
        }
        if (splitStation <= EPSILON || splitStation >= totalLength - EPSILON) {
            return false;
        }

        boolean refitHorizontalAlignment = head.getHorizontalAlignment() != null;

        RoadVerticalAlignment verticalAlignment = head.getVerticalAlignment();
        RoadVariableCrossSections variableCrossSections = head.getVariableCrossSections();
        RoadStationFacilities stationFacilities = head.getStationFacilities();

        tail.setVerticalAlignment(extractTailVerticalAlignment(verticalAlignment, splitStation));
        tail.setVariableCrossSections(extractTailVariableCrossSections(
            variableCrossSections, head.getCrossSection(), splitStation, totalLength));
        tail.setStationFacilities(extractTailStationFacilities(stationFacilities, splitStation, totalLength));

        head.setVerticalAlignment(trimHeadVerticalAlignment(verticalAlignment, splitStation));
        head.setVariableCrossSections(trimHeadVariableCrossSections(
            variableCrossSections, head.getCrossSection(), splitStation, totalLength));
        head.setStationFacilities(trimHeadStationFacilities(stationFacilities, splitStation, totalLength));

        if (refitHorizontalAlignment) {
            head.setHorizontalAlignment(null);
            tail.setHorizontalAlignment(null);
        }
        return refitHorizontalAlignment;
    }

    public static void refitHorizontalAlignmentFromCenterline(RoadNetwork network, Road road) {
        if (network == null || road == null || !RoadStationing.isStationable(network, road)) {
            if (road != null) {
                road.setHorizontalAlignment(null);
            }
            return;
        }
        HorizontalAlignmentPolylineFitter.fit(network, road).ifPresentOrElse(
            fitted -> {
                road.setHorizontalAlignment(fitted);
                HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road);
            },
            () -> road.setHorizontalAlignment(null));
    }

    public static void applyRoadSplit(RoadNetwork network, Road head, Road tail, double splitStation) {
        if (network == null || head == null || tail == null) {
            return;
        }
        double totalLength = RoadStationing.canonicalLength(network, head);
        boolean refit = applyRoadSplit(head, tail, splitStation, totalLength);
        if (refit) {
            refitHorizontalAlignmentFromCenterline(network, head);
            refitHorizontalAlignmentFromCenterline(network, tail);
        }
    }

    /**
     * 将 {@code tail} 的沿桩号工程数据拼接到 {@code head} 之后（{@link CenterlineEditStationPolicy#OFFSET_BY_HEAD_LENGTH}），
     * 写入 {@code target}（通常为 head）。{@code head} 占 [0, headLength)，{@code tail} 整体平移 {@code headLength}。
     *
     * @return 是否应基于折线重新拟合平面线形
     */
    public static boolean applyRoadMerge(
            Road target,
            Road head,
            Road tail,
            double headLength,
            double tailLength) {
        if (target == null || head == null || tail == null) {
            return false;
        }
        if (headLength < -EPSILON || tailLength < -EPSILON) {
            return false;
        }

        boolean refitHorizontalAlignment = head.getHorizontalAlignment() != null
            || tail.getHorizontalAlignment() != null;

        RoadVerticalAlignment headVertical = head.getVerticalAlignment();
        RoadVariableCrossSections headVariable = head.getVariableCrossSections();
        RoadStationFacilities headFacilities = head.getStationFacilities();
        RoadCrossSection headDefault = head.getCrossSection();

        target.setVerticalAlignment(mergeVerticalAlignments(headVertical, tail.getVerticalAlignment(), headLength));
        target.setVariableCrossSections(mergeVariableCrossSections(
            headVariable,
            tail.getVariableCrossSections(),
            headDefault,
            tail.getCrossSection(),
            headLength,
            tailLength));
        target.setStationFacilities(mergeStationFacilities(
            headFacilities,
            tail.getStationFacilities(),
            headLength,
            tailLength));

        if (refitHorizontalAlignment) {
            target.setHorizontalAlignment(null);
        }
        return refitHorizontalAlignment;
    }

    public static void applyRoadMerge(RoadNetwork network, Road head, Road tail) {
        if (network == null || head == null || tail == null) {
            return;
        }
        double headLength = RoadStationing.canonicalLength(network, head);
        double tailLength = RoadStationing.canonicalLength(network, tail);
        boolean refit = applyRoadMerge(head, head, tail, headLength, tailLength);
        if (refit) {
            refitHorizontalAlignmentFromCenterline(network, head);
        }
    }

    /**
     * 分段几何长度变化后，按 {@link CenterlineEditStationPolicy#REPARAMETERIZE_STATION} 对 VA / VCS / 设施做仿射重映射：
     * {@code [rangeStart, rangeStart + oldSegmentLength)} 内按弧长比例缩放，其后整体平移差值。
     */
    public static void rescaleAfterGeometryEdit(
            Road road,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength,
            double totalLengthBefore) {
        if (road == null || oldSegmentLength <= EPSILON) {
            return;
        }
        if (Math.abs(oldSegmentLength - newSegmentLength) <= EPSILON) {
            return;
        }
        if (!hasStationEngineeringData(road)) {
            return;
        }

        road.setVerticalAlignment(rescaleVerticalAlignment(
            road.getVerticalAlignment(),
            rangeStart,
            oldSegmentLength,
            newSegmentLength));
        road.setVariableCrossSections(rescaleVariableCrossSections(
            road.getVariableCrossSections(),
            road.getCrossSection(),
            totalLengthBefore,
            rangeStart,
            oldSegmentLength,
            newSegmentLength));
        road.setStationFacilities(rescaleStationFacilities(
            road.getStationFacilities(),
            totalLengthBefore,
            rangeStart,
            oldSegmentLength,
            newSegmentLength));
    }

    public static void rescaleAfterGeometryEdit(
            RoadNetwork network,
            Road road,
            String edgeId,
            double oldSegmentLength,
            double newSegmentLength) {
        if (network == null || road == null || edgeId == null || oldSegmentLength <= EPSILON) {
            return;
        }
        double rangeStart = RoadStationing.segmentStartStation(network, road, edgeId);
        if (rangeStart < 0.0) {
            return;
        }
        double totalLengthBefore = RoadStationing.canonicalLength(network, road) - newSegmentLength + oldSegmentLength;
        rescaleAfterGeometryEdit(road, rangeStart, oldSegmentLength, newSegmentLength, totalLengthBefore);
    }

    public record SegmentGeometrySnapshot(
            double rangeStart,
            double oldSegmentLength,
            double totalLengthBefore) {

        public static SegmentGeometrySnapshot capture(RoadNetwork network, String edgeId) {
            if (network == null || edgeId == null || edgeId.isBlank()) {
                return null;
            }
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || edge.getRoadId() == null) {
                return null;
            }
            Road road = network.getRoadForEdge(edge);
            if (road == null) {
                return null;
            }
            double rangeStart = RoadStationing.segmentStartStation(network, road, edgeId);
            if (rangeStart < 0.0) {
                return null;
            }
            return new SegmentGeometrySnapshot(
                rangeStart,
                edge.getLength(),
                RoadStationing.canonicalLength(network, road));
        }
    }

    static double remapStationAfterSegmentEdit(
            double station,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        if (!Double.isFinite(station)) {
            return station;
        }
        double rangeEnd = rangeStart + oldSegmentLength;
        if (station < rangeStart - EPSILON) {
            return station;
        }
        if (station < rangeEnd - EPSILON) {
            return rangeStart + (station - rangeStart) * (newSegmentLength / oldSegmentLength);
        }
        return station + (newSegmentLength - oldSegmentLength);
    }

    private static boolean hasStationEngineeringData(Road road) {
        return road.getVerticalAlignment() != null
            || road.getVariableCrossSections() != null
            || road.getStationFacilities() != null;
    }

    static RoadVerticalAlignment rescaleVerticalAlignment(
            RoadVerticalAlignment source,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> remapped = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : source.sortedPvis()) {
            double station = remapStationAfterSegmentEdit(
                pvi.getStation(), rangeStart, oldSegmentLength, newSegmentLength);
            if (!remapped.isEmpty()) {
                PointOfVerticalIntersection last = remapped.getLast();
                if (Math.abs(last.getStation() - station) <= EPSILON) {
                    continue;
                }
            }
            remapped.add(new PointOfVerticalIntersection(
                station,
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint()));
        }
        return remapped.isEmpty() ? null : new RoadVerticalAlignment(remapped);
    }

    static RoadVariableCrossSections rescaleVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLengthBefore,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        RoadCrossSection defaultSection = roadDefault != null ? roadDefault : new RoadCrossSection();
        List<Interval> intervals = buildIntervals(
            source != null ? source.sortedStations() : List.of(),
            defaultSection,
            totalLengthBefore);
        List<Interval> remapped = new ArrayList<>(intervals.size());
        for (Interval interval : intervals) {
            double start = remapStationAfterSegmentEdit(
                interval.start, rangeStart, oldSegmentLength, newSegmentLength);
            double end = remapStationAfterSegmentEdit(
                interval.end, rangeStart, oldSegmentLength, newSegmentLength);
            if (end > start + EPSILON) {
                remapped.add(new Interval(start, end, interval.template));
            }
        }
        remapped = mergeAdjacentIntervals(remapped);
        return intervalsToVariableCrossSections(remapped, defaultSection);
    }

    static RoadStationFacilities rescaleStationFacilities(
            RoadStationFacilities source,
            double totalLengthBefore,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<StationFacilityRun> remapped = new ArrayList<>();
        for (StationFacilityRun run : source.getRuns()) {
            double start = remapStationAfterSegmentEdit(
                run.getStartStation(), rangeStart, oldSegmentLength, newSegmentLength);
            Double endStation = run.getEndStation() == null
                ? null
                : remapStationAfterSegmentEdit(
                    run.getEndStation(), rangeStart, oldSegmentLength, newSegmentLength);
            if (endStation != null && endStation <= start + EPSILON) {
                continue;
            }
            remapped.add(new StationFacilityRun(
                start,
                endStation,
                run.getKind(),
                run.getSide(),
                run.getMaterial(),
                run.getHeight()));
        }
        remapped.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return remapped.isEmpty() ? null : new RoadStationFacilities(remapped);
    }

    /**
     * 从 {@code source} 的沿桩号工程数据中提取 {@code [rangeStart, rangeEnd)}，写入 {@code target} 并重映射为 K0+000 起。
     */
    public static void applyStationRangeExtract(
            Road source,
            Road target,
            double totalSourceLength,
            double rangeStart,
            double rangeEnd) {
        if (source == null || target == null || totalSourceLength <= EPSILON) {
            return;
        }
        if (rangeEnd <= rangeStart + EPSILON) {
            target.setVerticalAlignment(null);
            target.setVariableCrossSections(null);
            target.setStationFacilities(null);
            return;
        }
        double stationOffset = -rangeStart;
        RoadCrossSection defaultSection = source.getCrossSection();
        target.setVerticalAlignment(remapVerticalAlignment(
            source.getVerticalAlignment(), rangeStart, rangeEnd, stationOffset));
        target.setVariableCrossSections(remapVariableCrossSections(
            source.getVariableCrossSections(),
            defaultSection,
            totalSourceLength,
            rangeStart,
            rangeEnd,
            stationOffset));
        target.setStationFacilities(remapStationFacilities(
            source.getStationFacilities(),
            totalSourceLength,
            rangeStart,
            rangeEnd,
            stationOffset));
    }

    /**
     * 计算分量内分段在道路链上的桩号区间（取各分段区间的包络）。
     */
    public static StationRange computeComponentStationRange(
            RoadNetwork network,
            Road road,
            Set<String> edgeIds) {
        if (network == null || road == null || edgeIds == null || edgeIds.isEmpty()) {
            return StationRange.invalid();
        }
        double rangeStart = Double.POSITIVE_INFINITY;
        double rangeEnd = 0.0;
        boolean found = false;
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            if (!edgeIds.contains(segment.edgeId())) {
                continue;
            }
            found = true;
            rangeStart = Math.min(rangeStart, segment.startStation());
            rangeEnd = Math.max(rangeEnd, segment.endStation());
        }
        return found ? new StationRange(rangeStart, rangeEnd) : StationRange.invalid();
    }

    public record StationRange(double start, double end) {
        public static StationRange invalid() {
            return new StationRange(0.0, 0.0);
        }

        public boolean isValid() {
            return end > start + EPSILON;
        }

        public double length() {
            return end - start;
        }
    }

    public record StationDataSnapshot(
            RoadVerticalAlignment verticalAlignment,
            RoadVariableCrossSections variableCrossSections,
            RoadStationFacilities stationFacilities,
            boolean hadHorizontalAlignment) {

        public static StationDataSnapshot capture(Road road) {
            if (road == null) {
                return new StationDataSnapshot(null, null, null, false);
            }
            return new StationDataSnapshot(
                road.getVerticalAlignment(),
                road.getVariableCrossSections(),
                road.getStationFacilities(),
                road.getHorizontalAlignment() != null);
        }

        public void applyRangeTo(Road target, double totalSourceLength, StationRange range) {
            if (target == null || !range.isValid()) {
                return;
            }
            Road scratch = new Road("snapshot");
            scratch.setVerticalAlignment(verticalAlignment);
            scratch.setVariableCrossSections(variableCrossSections);
            scratch.setStationFacilities(stationFacilities);
            scratch.setCrossSection(target.getCrossSection());
            applyStationRangeExtract(scratch, target, totalSourceLength, range.start(), range.end());
        }

        public boolean hasPhase2Data() {
            return verticalAlignment != null
                || variableCrossSections != null
                || stationFacilities != null
                || hadHorizontalAlignment;
        }
    }

    static RoadVerticalAlignment remapVerticalAlignment(
            RoadVerticalAlignment source,
            double rangeStart,
            double rangeEnd,
            double stationOffset) {
        if (source == null || source.isEmpty() || rangeEnd <= rangeStart + EPSILON) {
            return null;
        }
        List<PointOfVerticalIntersection> sorted = source.sortedPvis();
        if (sorted.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> remapped = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : sorted) {
            double station = pvi.getStation();
            if (station + EPSILON < rangeStart) {
                continue;
            }
            if (station > rangeEnd + EPSILON) {
                break;
            }
            remapped.add(new PointOfVerticalIntersection(
                station + stationOffset,
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint()));
        }
        return remapped.isEmpty() ? null : new RoadVerticalAlignment(remapped);
    }

    static RoadVerticalAlignment trimHeadVerticalAlignment(
            RoadVerticalAlignment source,
            double splitStation) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> sorted = source.sortedPvis();
        if (sorted.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> trimmed = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : sorted) {
            if (pvi.getStation() <= splitStation + EPSILON) {
                trimmed.add(pvi.copy());
            } else {
                break;
            }
        }
        return trimmed.isEmpty() ? null : new RoadVerticalAlignment(trimmed);
    }

    static RoadVerticalAlignment extractTailVerticalAlignment(
            RoadVerticalAlignment source,
            double splitStation) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> sorted = source.sortedPvis();
        if (sorted.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> tail = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : sorted) {
            if (pvi.getStation() + EPSILON < splitStation) {
                continue;
            }
            tail.add(new PointOfVerticalIntersection(
                pvi.getStation() - splitStation,
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint()));
        }
        return tail.isEmpty() ? null : new RoadVerticalAlignment(tail);
    }

    static RoadVariableCrossSections trimHeadVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double splitStation,
            double totalLength) {
        return remapVariableCrossSections(source, roadDefault, totalLength, 0.0, splitStation, 0.0);
    }

    static RoadVariableCrossSections extractTailVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double splitStation,
            double totalLength) {
        return remapVariableCrossSections(
            source,
            roadDefault,
            totalLength,
            splitStation,
            totalLength,
            -splitStation);
    }

    static RoadStationFacilities trimHeadStationFacilities(
            RoadStationFacilities source,
            double splitStation,
            double totalLength) {
        return remapStationFacilities(source, totalLength, 0.0, splitStation, 0.0);
    }

    static RoadStationFacilities extractTailStationFacilities(
            RoadStationFacilities source,
            double splitStation,
            double totalLength) {
        return remapStationFacilities(source, totalLength, splitStation, totalLength, -splitStation);
    }

    static RoadVerticalAlignment mergeVerticalAlignments(
            RoadVerticalAlignment head,
            RoadVerticalAlignment tail,
            double headLength) {
        List<PointOfVerticalIntersection> merged = new ArrayList<>();
        if (head != null) {
            for (PointOfVerticalIntersection pvi : head.sortedPvis()) {
                merged.add(pvi.copy());
            }
        }
        if (tail != null) {
            for (PointOfVerticalIntersection pvi : tail.sortedPvis()) {
                double station = pvi.getStation() + headLength;
                if (!merged.isEmpty()) {
                    PointOfVerticalIntersection last = merged.getLast();
                    if (Math.abs(last.getStation() - station) <= EPSILON) {
                        continue;
                    }
                }
                merged.add(new PointOfVerticalIntersection(
                    station,
                    pvi.getElevation(),
                    pvi.getCurveLength(),
                    pvi.getConstraint()));
            }
        }
        return merged.isEmpty() ? null : new RoadVerticalAlignment(merged);
    }

    static RoadVariableCrossSections mergeVariableCrossSections(
            RoadVariableCrossSections head,
            RoadVariableCrossSections tail,
            RoadCrossSection headDefault,
            RoadCrossSection tailDefault,
            double headLength,
            double tailLength) {
        RoadCrossSection mergedDefault = headDefault != null ? headDefault : new RoadCrossSection();
        RoadCrossSection tailSectionDefault = tailDefault != null ? tailDefault : mergedDefault;
        List<Interval> intervals = new ArrayList<>();
        intervals.addAll(intervalsForRoad(head, mergedDefault, headLength, 0.0));
        if (tailLength > EPSILON) {
            intervals.addAll(intervalsForRoad(tail, tailSectionDefault, tailLength, headLength));
        }
        intervals = mergeAdjacentIntervals(intervals);
        return intervalsToVariableCrossSections(intervals, mergedDefault);
    }

    static RoadStationFacilities mergeStationFacilities(
            RoadStationFacilities head,
            RoadStationFacilities tail,
            double headLength,
            double tailLength) {
        List<StationFacilityRun> merged = new ArrayList<>();
        if (head != null) {
            for (StationFacilityRun run : head.getRuns()) {
                merged.add(run.copy());
            }
        }
        if (tail != null) {
            for (StationFacilityRun run : tail.getRuns()) {
                double start = run.getStartStation() + headLength;
                Double endStation = run.getEndStation() == null
                    ? null
                    : run.getEndStation() + headLength;
                if (!merged.isEmpty()) {
                    StationFacilityRun last = merged.getLast();
                    if (Math.abs(last.getStartStation() - start) <= EPSILON
                            && last.getKind() == run.getKind()
                            && last.getSide() == run.getSide()) {
                        continue;
                    }
                }
                merged.add(new StationFacilityRun(
                    start,
                    endStation,
                    run.getKind(),
                    run.getSide(),
                    run.getMaterial(),
                    run.getHeight()));
            }
        }
        merged.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return merged.isEmpty() ? null : new RoadStationFacilities(merged);
    }

    private static List<Interval> intervalsForRoad(
            RoadVariableCrossSections variable,
            RoadCrossSection roadDefault,
            double totalLength,
            double stationOffset) {
        List<Interval> intervals = buildIntervals(
            variable != null ? variable.sortedStations() : List.of(),
            roadDefault,
            totalLength);
        if (Math.abs(stationOffset) <= EPSILON) {
            return intervals;
        }
        List<Interval> shifted = new ArrayList<>(intervals.size());
        for (Interval interval : intervals) {
            shifted.add(new Interval(
                interval.start + stationOffset,
                interval.end + stationOffset,
                interval.template));
        }
        return shifted;
    }

    private static RoadVariableCrossSections remapVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLength,
            double rangeStart,
            double rangeEnd,
            double stationOffset) {
        if (source == null || source.isEmpty() || rangeEnd <= rangeStart + EPSILON) {
            return null;
        }
        RoadCrossSection defaultSection = roadDefault != null ? roadDefault : new RoadCrossSection();
        List<Interval> intervals = buildIntervals(source.sortedStations(), defaultSection, totalLength);
        List<Interval> clipped = new ArrayList<>();
        for (Interval interval : intervals) {
            double start = Math.max(interval.start, rangeStart);
            double end = Math.min(interval.end, rangeEnd);
            if (end > start + EPSILON) {
                clipped.add(new Interval(start + stationOffset, end + stationOffset, interval.template));
            }
        }
        clipped = mergeAdjacentIntervals(clipped);
        return intervalsToVariableCrossSections(clipped, defaultSection);
    }

    private static RoadStationFacilities remapStationFacilities(
            RoadStationFacilities source,
            double totalLength,
            double rangeStart,
            double rangeEnd,
            double stationOffset) {
        if (source == null || source.isEmpty() || rangeEnd <= rangeStart + EPSILON) {
            return null;
        }
        List<StationFacilityRun> remapped = new ArrayList<>();
        for (StationFacilityRun run : source.getRuns()) {
            double runStart = run.getStartStation();
            double runEnd = run.getEndStation() != null ? run.getEndStation() : totalLength;
            double start = Math.max(runStart, rangeStart);
            double end = Math.min(runEnd, rangeEnd);
            if (end <= start + EPSILON) {
                continue;
            }
            Double endStation = end >= totalLength - EPSILON && run.getEndStation() == null
                ? null
                : end + stationOffset;
            remapped.add(new StationFacilityRun(
                start + stationOffset,
                endStation,
                run.getKind(),
                run.getSide(),
                run.getMaterial(),
                run.getHeight()));
        }
        remapped.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return remapped.isEmpty() ? null : new RoadStationFacilities(remapped);
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
                    && RoadCrossSectionEngineeringEquality.equals(next.template, current.template)) {
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
                if (!RoadCrossSectionEngineeringEquality.equals(interval.template, defaultSection)) {
                    stations.add(StationCrossSection.at(0.0, interval.template.copy()));
                }
                continue;
            }
            stations.add(StationCrossSection.at(interval.start, interval.template.copy()));
        }
        return stations.isEmpty() ? null : new RoadVariableCrossSections(stations);
    }

    private record Interval(double start, double end, RoadCrossSection template) {
    }
}
