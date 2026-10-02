package com.plot.plugin.road.profile;

import com.plot.plugin.road.pipeline.profile.BuildHeightSample;

import java.util.ArrayList;
import java.util.List;

/** 从道路级纵断面图数据估算 design / build / terrain 三组质量指标。 */
public final class RoadProfileQualityAnalyzer {

    private static final double EPSILON = 1e-6;
    private static final double GRADE_TOLERANCE_PERCENT = 0.5;

    private RoadProfileQualityAnalyzer() {
    }

    public static RoadProfileQualityMetrics analyze(RoadProfileChartData chart) {
        if (chart == null || !chart.hasProfileData()) {
            return RoadProfileQualityMetrics.empty();
        }
        RoadProfileQualityMetrics.DesignMetrics design = analyzeDesign(chart);
        if (chart.hasBuildSamples()) {
            return analyzeWithBuildSamples(chart, chart.buildSamples(), design);
        }
        return analyzeSegmentFallback(chart, design);
    }

    private static RoadProfileQualityMetrics analyzeWithBuildSamples(
            RoadProfileChartData chart,
            List<BuildHeightSample> samples,
            RoadProfileQualityMetrics.DesignMetrics design) {
        int cut = 0;
        int fill = 0;
        double maxDeviation = 0.0;

        for (BuildHeightSample sample : samples) {
            maxDeviation = Math.max(
                maxDeviation,
                Math.abs(sample.designElevation() - sample.buildY()));
        }

        for (int i = 0; i < samples.size() - 1; i++) {
            BuildHeightSample current = samples.get(i);
            BuildHeightSample next = samples.get(i + 1);
            double span = next.station() - current.station();
            if (span <= EPSILON) {
                continue;
            }
            EarthworkDelta earthwork = earthworkDelta(chart, current, span);
            cut += earthwork.cut;
            fill += earthwork.fill;
        }

        StepAnalysis stepAnalysis = analyzeBuildSteps(samples);

        return new RoadProfileQualityMetrics(
            design,
            new RoadProfileQualityMetrics.BuildMetrics(
                stepAnalysis.stepCount(),
                longestFlatRun(samples),
                maxDeviation,
                stepAnalysis.abnormalStepCount(),
                cumulativeGradeError(samples)),
            new RoadProfileQualityMetrics.TerrainMetrics(cut, fill));
    }

    private static RoadProfileQualityMetrics analyzeSegmentFallback(
            RoadProfileChartData chart,
            RoadProfileQualityMetrics.DesignMetrics design) {
        List<Double> stations = chart.stations();
        List<Double> build = chart.buildElevations();
        List<Double> designElevations = chart.previewElevations();

        int cut = 0;
        int fill = 0;
        int abnormalSteps = 0;
        int steps = 0;
        double maxDeviation = 0.0;

        for (int i = 1; i < stations.size(); i++) {
            double span = stations.get(i) - stations.get(i - 1);
            if (span <= EPSILON) {
                continue;
            }
            int buildY = (int) Math.round(build.get(i));
            int prevBuildY = (int) Math.round(build.get(i - 1));
            int delta = buildY - prevBuildY;
            if (delta != 0) {
                steps++;
            }
            if (Math.abs(delta) > 1) {
                abnormalSteps++;
            }
            maxDeviation = Math.max(maxDeviation, Math.abs(designElevations.get(i) - build.get(i)));

            double mid = stations.get(i - 1) + span * 0.5;
            int groundY = (int) Math.round(chart.groundElevationAt(mid));
            int diff = prevBuildY - groundY;
            int roundedSpan = Math.max(1, (int) Math.round(span));
            if (diff > 1) {
                fill += (diff - 1) * roundedSpan;
            } else if (diff < -1) {
                cut += (-diff - 1) * roundedSpan;
            }
        }

        int longestFlat = 1;
        int currentFlat = 1;
        for (int i = 1; i < build.size(); i++) {
            if (Math.round(build.get(i)) == Math.round(build.get(i - 1))) {
                currentFlat++;
            } else {
                longestFlat = Math.max(longestFlat, currentFlat);
                currentFlat = 1;
            }
        }
        longestFlat = Math.max(longestFlat, currentFlat);

        double totalDesignDelta = designElevations.getLast() - designElevations.getFirst();
        double totalBuildDelta = build.getLast() - build.getFirst();

        return new RoadProfileQualityMetrics(
            design,
            new RoadProfileQualityMetrics.BuildMetrics(
                steps,
                longestFlat,
                maxDeviation,
                abnormalSteps,
                Math.abs(totalBuildDelta - totalDesignDelta)),
            new RoadProfileQualityMetrics.TerrainMetrics(cut, fill));
    }

    private static RoadProfileQualityMetrics.DesignMetrics analyzeDesign(RoadProfileChartData chart) {
        List<Double> stations = chart.stations();
        List<Double> elevations = chart.previewElevations();
        if (stations.size() < 2 || elevations.size() != stations.size()) {
            return RoadProfileQualityMetrics.DesignMetrics.empty();
        }

        List<Double> signedGrades = new ArrayList<>();
        List<Double> runLengths = new ArrayList<>();
        double maxGrade = 0.0;

        for (int i = 1; i < stations.size(); i++) {
            double span = stations.get(i) - stations.get(i - 1);
            if (span <= EPSILON) {
                continue;
            }
            double signedGrade = (elevations.get(i) - elevations.get(i - 1)) / span * 100.0;
            signedGrades.add(signedGrade);
            runLengths.add(span);
            maxGrade = Math.max(maxGrade, Math.abs(signedGrade));
        }

        return new RoadProfileQualityMetrics.DesignMetrics(
            maxGrade,
            maxAdjacentGradeChange(signedGrades),
            longestSameGradeRun(signedGrades, runLengths));
    }

    private static StepAnalysis analyzeBuildSteps(List<BuildHeightSample> samples) {
        if (samples.size() < 2) {
            return StepAnalysis.empty();
        }
        double lastStepStation = samples.getFirst().station();
        int lastBuildY = samples.getFirst().buildY();
        int steps = 0;
        int abnormalSteps = 0;

        for (int i = 1; i < samples.size(); i++) {
            BuildHeightSample sample = samples.get(i);
            if (sample.buildY() == lastBuildY) {
                continue;
            }
            int delta = sample.buildY() - lastBuildY;
            if (Math.abs(delta) > 1) {
                abnormalSteps++;
            }
            steps++;
            lastStepStation = sample.station();
            lastBuildY = sample.buildY();
        }

        return new StepAnalysis(steps, abnormalSteps);
    }

    private record StepAnalysis(int stepCount, int abnormalStepCount) {

        static StepAnalysis empty() {
            return new StepAnalysis(0, 0);
        }
    }

    private static double longestSameGradeRun(List<Double> signedGrades, List<Double> runLengths) {
        if (signedGrades.isEmpty() || signedGrades.size() != runLengths.size()) {
            return 0.0;
        }
        double longest = runLengths.getFirst();
        double current = runLengths.getFirst();
        for (int i = 1; i < signedGrades.size(); i++) {
            if (Math.abs(signedGrades.get(i) - signedGrades.get(i - 1)) <= GRADE_TOLERANCE_PERCENT) {
                current += runLengths.get(i);
            } else {
                longest = Math.max(longest, current);
                current = runLengths.get(i);
            }
        }
        return Math.max(longest, current);
    }

    private static EarthworkDelta earthworkDelta(
            RoadProfileChartData chart,
            BuildHeightSample sample,
            double span) {
        double mid = sample.station() + span * 0.5;
        int groundY = (int) Math.round(chart.groundElevationAt(mid));
        int diff = sample.buildY() - groundY;
        int roundedSpan = Math.max(1, (int) Math.round(span));
        if (diff > 1) {
            return new EarthworkDelta(0, (diff - 1) * roundedSpan);
        }
        if (diff < -1) {
            return new EarthworkDelta((-diff - 1) * roundedSpan, 0);
        }
        return new EarthworkDelta(0, 0);
    }

    private record EarthworkDelta(int cut, int fill) { }

    private static double maxAdjacentGradeChange(List<Double> signedGrades) {
        if (signedGrades.size() < 2) {
            return 0.0;
        }
        double max = 0.0;
        for (int i = 1; i < signedGrades.size(); i++) {
            max = Math.max(max, Math.abs(signedGrades.get(i) - signedGrades.get(i - 1)));
        }
        return max;
    }

    static int longestFlatRun(List<BuildHeightSample> samples) {
        if (samples.size() < 2) {
            return samples.size();
        }
        int longest = 1;
        int current = 1;
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).buildY() == samples.get(i - 1).buildY()) {
                current++;
            } else {
                longest = Math.max(longest, current);
                current = 1;
            }
        }
        return Math.max(longest, current);
    }

    private static double cumulativeGradeError(List<BuildHeightSample> samples) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        BuildHeightSample first = samples.getFirst();
        BuildHeightSample last = samples.getLast();
        double designDelta = last.designElevation() - first.designElevation();
        double buildDelta = last.buildY() - first.buildY();
        return Math.abs(buildDelta - designDelta);
    }
}
