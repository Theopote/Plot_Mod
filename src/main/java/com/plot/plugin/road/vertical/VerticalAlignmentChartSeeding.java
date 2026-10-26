package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 从预览纵断面采样生成编辑器用 PVI：稀疏化自动设计曲线，避免每块一个控制点。
 */
public final class VerticalAlignmentChartSeeding {

    private static final double COLLINEAR_EPSILON = 1e-6;
    /** 相对弦线的最大标高偏差（格），Douglas–Peucker 容差。 */
    private static final double DEFAULT_VERTICAL_TOLERANCE = 0.35;
    private static final double MAX_VERTICAL_TOLERANCE = 2.0;
    /** 相邻可编辑内点最小桩号间距（米/格）。 */
    private static final double MIN_INTERIOR_SPACING = 25.0;
    private static final int MAX_EDITOR_PVI_COUNT = 64;
    /** 超过此数量且均为 FREE 约束时视为未简化的自动 seed。 */
    static final int OVERDENSE_AUTO_SEED_THRESHOLD = 48;
    private static final double INTERSECTION_STATION_TOLERANCE = 1.0;

    private VerticalAlignmentChartSeeding() {
    }

    public static List<PointOfVerticalIntersection> buildEditorPvisFromChart(
            RoadProfileChartData chart,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (chart == null || !chart.hasProfileData()) {
            return List.of();
        }
        List<Sample> samples = collectSamples(chart);
        if (samples.size() < 2) {
            return List.of();
        }
        Set<Double> pinnedStations = pinnedStations(chart, road, samples);
        double tolerance = verticalTolerance(road, config);
        List<Sample> simplified = simplifySamples(samples, pinnedStations, tolerance);
        while (simplified.size() > MAX_EDITOR_PVI_COUNT && tolerance < MAX_VERTICAL_TOLERANCE) {
            tolerance = Math.min(MAX_VERTICAL_TOLERANCE, tolerance * 1.6);
            simplified = simplifySamples(samples, pinnedStations, tolerance);
        }
        simplified = enforceMinimumSpacing(simplified, pinnedStations);
        return toPvis(simplified);
    }

    /**
     * 自动设计 seed 的 VA 是否过密（全 FREE 且 PVI 过多），应在打开编辑器时重新稀疏化。
     */
    public static boolean isOverdenseAutoSeed(Road road) {
        if (road == null || road.getEffectiveProfileSource() == ProfileSource.EDITED) {
            return false;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (alignment == null || alignment.pviCount() <= OVERDENSE_AUTO_SEED_THRESHOLD) {
            return false;
        }
        for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
            if (pvi.getConstraint() != VerticalControlPointConstraint.FREE) {
                return false;
            }
        }
        return true;
    }

    private static List<Sample> collectSamples(RoadProfileChartData chart) {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < chart.stations().size(); i++) {
            double station = chart.stations().get(i);
            double elevation = chart.previewElevationAt(station);
            if (!Double.isFinite(station) || !Double.isFinite(elevation)) {
                continue;
            }
            while (samples.size() >= 2) {
                Sample a = samples.get(samples.size() - 2);
                Sample b = samples.getLast();
                double expected = a.elevation + (elevation - a.elevation)
                    * (b.station - a.station) / (station - a.station);
                if (Math.abs(expected - b.elevation) > COLLINEAR_EPSILON) {
                    break;
                }
                samples.removeLast();
            }
            samples.add(new Sample(station, elevation));
        }
        return samples;
    }

    private static Set<Double> pinnedStations(
            RoadProfileChartData chart,
            Road road,
            List<Sample> samples) {
        TreeSet<Double> pinned = new TreeSet<>();
        pinned.add(samples.getFirst().station);
        pinned.add(samples.getLast().station);
        if (road != null && chart.intersections() != null) {
            for (RoadProfileIntersection intersection : chart.intersections()) {
                if (intersection == null || !road.getId().equals(intersection.currentRoadId())) {
                    continue;
                }
                pinned.add(intersection.roadStation());
            }
        }
        return pinned;
    }

    private static int nearestSampleIndex(List<Sample> samples, double station) {
        int best = -1;
        double bestDelta = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            double delta = Math.abs(samples.get(i).station - station);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return bestDelta <= INTERSECTION_STATION_TOLERANCE ? best : -1;
    }

    private static double verticalTolerance(Road road, RoadSystemConfig config) {
        float maxGrade = road != null ? road.getEffectiveMaxSlope(config) : config.getMaxSlope();
        // 典型采样间距下，该坡度对应的单步标高变化约为 maxGrade/100 * spacing。
        return Math.max(DEFAULT_VERTICAL_TOLERANCE, maxGrade / 100.0 * 2.0);
    }

    private static List<Sample> simplifySamples(
            List<Sample> samples,
            Set<Double> pinnedStations,
            double tolerance) {
        if (samples.size() <= 2) {
            return List.copyOf(samples);
        }
        List<Integer> orderedPins = new ArrayList<>();
        for (double station : pinnedStations) {
            int index = nearestSampleIndex(samples, station);
            if (index >= 0 && !orderedPins.contains(index)) {
                orderedPins.add(index);
            }
        }
        if (orderedPins.isEmpty()) {
            orderedPins.add(0);
            orderedPins.add(samples.size() - 1);
        } else {
            if (orderedPins.getFirst() != 0) {
                orderedPins.addFirst(0);
            }
            int last = samples.size() - 1;
            if (orderedPins.getLast() != last) {
                orderedPins.add(last);
            }
        }
        orderedPins.sort(Integer::compareTo);
        LinkedHashSet<Sample> kept = new LinkedHashSet<>();
        for (int p = 0; p < orderedPins.size() - 1; p++) {
            int start = orderedPins.get(p);
            int end = orderedPins.get(p + 1);
            kept.add(samples.get(start));
            if (end - start <= 1) {
                if (p == orderedPins.size() - 2) {
                    kept.add(samples.get(end));
                }
                continue;
            }
            boolean[] segmentKeep = douglasPeucker(samples, start, end, tolerance);
            for (int i = start + 1; i < end; i++) {
                if (segmentKeep[i]) {
                    kept.add(samples.get(i));
                }
            }
            if (p == orderedPins.size() - 2) {
                kept.add(samples.get(end));
            }
        }
        return new ArrayList<>(kept);
    }

    private static boolean[] douglasPeucker(
            List<Sample> samples,
            int start,
            int end,
            double tolerance) {
        boolean[] keep = new boolean[samples.size()];
        keep[start] = true;
        keep[end] = true;
        douglasPeuckerRecursive(samples, start, end, tolerance, keep);
        return keep;
    }

    private static void douglasPeuckerRecursive(
            List<Sample> samples,
            int start,
            int end,
            double tolerance,
            boolean[] keep) {
        if (end <= start + 1) {
            return;
        }
        Sample a = samples.get(start);
        Sample b = samples.get(end);
        double maxError = -1.0;
        int maxIndex = -1;
        for (int i = start + 1; i < end; i++) {
            double error = verticalDistanceFromChord(samples.get(i), a, b);
            if (error > maxError) {
                maxError = error;
                maxIndex = i;
            }
        }
        if (maxError > tolerance && maxIndex >= 0) {
            keep[maxIndex] = true;
            douglasPeuckerRecursive(samples, start, maxIndex, tolerance, keep);
            douglasPeuckerRecursive(samples, maxIndex, end, tolerance, keep);
        }
    }

    private static double verticalDistanceFromChord(Sample point, Sample start, Sample end) {
        double deltaStation = end.station - start.station;
        if (Math.abs(deltaStation) <= COLLINEAR_EPSILON) {
            return Math.abs(point.elevation - start.elevation);
        }
        double expected = start.elevation
            + (point.station - start.station) / deltaStation * (end.elevation - start.elevation);
        return Math.abs(point.elevation - expected);
    }

    private static List<Sample> enforceMinimumSpacing(List<Sample> samples, Set<Double> pinnedStations) {
        if (samples.size() <= 2) {
            return samples;
        }
        List<Sample> filtered = new ArrayList<>();
        filtered.add(samples.getFirst());
        for (int i = 1; i < samples.size() - 1; i++) {
            Sample current = samples.get(i);
            if (isPinnedStation(current.station, pinnedStations)) {
                filtered.add(current);
                continue;
            }
            Sample last = filtered.getLast();
            if (current.station - last.station >= MIN_INTERIOR_SPACING) {
                filtered.add(current);
            }
        }
        Sample lastSample = samples.getLast();
        if (filtered.getLast().station < lastSample.station - COLLINEAR_EPSILON) {
            filtered.add(lastSample);
        }
        return filtered;
    }

    private static boolean isPinnedStation(double station, Set<Double> pinnedStations) {
        for (double pinned : pinnedStations) {
            if (Math.abs(station - pinned) <= INTERSECTION_STATION_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static List<PointOfVerticalIntersection> toPvis(List<Sample> samples) {
        List<PointOfVerticalIntersection> pvis = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            pvis.add(PointOfVerticalIntersection.of(sample.station, sample.elevation));
        }
        return pvis;
    }

    private record Sample(double station, double elevation) {
    }
}
