package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.List;

/**
 * Experimental short-distance oscillation merge. Produces a candidate profile and
 * replaces the baseline only when hard constraints pass and the composite score
 * improves by the preset benefit threshold.
 */
final class ProfileOscillationReducer {

    private static final double EPSILON = 1e-9;
    private static final double REVERSAL_WEIGHT = 2.0;
    private static final double GRADE_CHANGE_WEIGHT = 1.0;
    private static final double TERRAIN_DEVIATION_WEIGHT = 0.25;

    record ReductionResult(
            boolean attempted,
            boolean accepted,
            int gradeReversalsBefore,
            int gradeReversalsAfter,
            double totalAbsoluteGradeChangeBefore,
            double totalAbsoluteGradeChangeAfter,
            boolean finalConstraintsSatisfied) {

        static ReductionResult skipped(double[] baseline, List<Double> segmentDistances) {
            return new ReductionResult(
                false,
                false,
                countGradeSignReversals(baseline, segmentDistances),
                countGradeSignReversals(baseline, segmentDistances),
                GradeLimitedProfileSolver.totalAbsoluteGradeChange(baseline, segmentDistances),
                GradeLimitedProfileSolver.totalAbsoluteGradeChange(baseline, segmentDistances),
                true);
        }
    }

    private static final ThreadLocal<ReductionResult> LAST_RESULT = new ThreadLocal<>();

    private ProfileOscillationReducer() {
    }

    static void recordLastResult(ReductionResult result) {
        if (result != null) {
            LAST_RESULT.set(result);
        }
    }

    static ReductionResult lastResult() {
        return LAST_RESULT.get();
    }

    static void clearLastResult() {
        LAST_RESULT.remove();
    }

    /**
     * Compare baseline against an oscillation-reduced candidate; write back only when accepted.
     * Disabled unless {@code oscillationReductionEnabled} or {@code forceEnable}.
     */
    static ReductionResult tryApplyCandidate(
            double[] baseline,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock,
            double[] minElevations,
            double[] maxElevations,
            List<Double> trendElevations,
            boolean oscillationReductionEnabled,
            boolean forceEnable) {
        if (baseline == null || baseline.length < 4 || segmentDistances == null
                || segmentDistances.isEmpty()) {
            return ReductionResult.skipped(baseline, segmentDistances);
        }
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        if (!forceEnable && !oscillationReductionEnabled) {
            return ReductionResult.skipped(baseline, segmentDistances);
        }

        int reversalsBefore = countGradeSignReversals(baseline, segmentDistances);
        double gradeChangeBefore = GradeLimitedProfileSolver.totalAbsoluteGradeChange(
            baseline, segmentDistances);
        double scoreBefore = score(baseline, segmentDistances, trendElevations);

        double[] candidate = baseline.clone();
        applyClusterMerges(
            candidate,
            segmentDistances,
            maxSlopePercents,
            effectivePreset,
            lockStart,
            lockEnd,
            startLock,
            endLock,
            minElevations,
            maxElevations,
            trendElevations);

        boolean constraintsOk = GradeLimitedProfileSolver.satisfiesFullFeasibility(
            candidate,
            segmentDistances,
            maxSlopePercents,
            effectivePreset.maxGradeChangePercent(),
            lockStart,
            lockEnd,
            startLock,
            endLock,
            minElevations,
            maxElevations);
        int reversalsAfter = countGradeSignReversals(candidate, segmentDistances);
        double gradeChangeAfter = GradeLimitedProfileSolver.totalAbsoluteGradeChange(
            candidate, segmentDistances);
        double scoreAfter = score(candidate, segmentDistances, trendElevations);

        boolean accepted = constraintsOk
            && scoreAfter < scoreBefore * (1.0 - effectivePreset.minOscillationMergeBenefit());
        if (accepted) {
            System.arraycopy(candidate, 0, baseline, 0, baseline.length);
        } else {
            reversalsAfter = reversalsBefore;
            gradeChangeAfter = gradeChangeBefore;
        }

        return new ReductionResult(
            true,
            accepted,
            reversalsBefore,
            reversalsAfter,
            gradeChangeBefore,
            gradeChangeAfter,
            constraintsOk);
    }

    /** Package-visible for unit tests that exercise merge logic directly. */
    static void reduceIfBeneficial(
            double[] elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock,
            double[] minElevations,
            double[] maxElevations,
            List<Double> trendElevations) {
        applyClusterMerges(
            elevations,
            segmentDistances,
            maxSlopePercents,
            preset != null ? preset : TerrainFollowPreset.STANDARD,
            lockStart,
            lockEnd,
            startLock,
            endLock,
            minElevations,
            maxElevations,
            trendElevations);
    }

    static int countGradeSignReversals(double[] elevations, List<Double> segmentDistances) {
        if (elevations.length < 2 || segmentDistances.isEmpty()) {
            return 0;
        }
        int reversals = 0;
        Double previousSign = null;
        for (int i = 0; i < segmentDistances.size(); i++) {
            double grade = GradeLimitedProfileSolver.gradeAtSegment(
                elevations[i], elevations[i + 1], segmentDistances.get(i));
            double sign = Math.signum(grade);
            if (Math.abs(sign) < EPSILON) {
                continue;
            }
            if (previousSign != null && previousSign * sign < 0) {
                reversals++;
            }
            previousSign = sign;
        }
        return reversals;
    }

    private static void applyClusterMerges(
            double[] elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock,
            double[] minElevations,
            double[] maxElevations,
            List<Double> trendElevations) {
        double maxSpan = preset.maxOscillationSpanMeters();
        double maxAmplitude = preset.maxOscillationAmplitudeBlocks();
        double minBenefit = preset.minOscillationMergeBenefit();
        if (maxSpan <= EPSILON || maxAmplitude <= EPSILON) {
            return;
        }

        double[] chainage = buildChainage(segmentDistances);
        int maxRightAnchor = lockEnd ? elevations.length - 1 : elevations.length - 2;
        int leftAnchor = 0;
        while (leftAnchor <= maxRightAnchor - 2) {
            int bestRight = -1;
            double bestTrialScore = Double.POSITIVE_INFINITY;
            double[] bestTrial = null;
            double baselineScore = score(elevations, segmentDistances, trendElevations);

            for (int rightAnchor = leftAnchor + 2; rightAnchor <= maxRightAnchor; rightAnchor++) {
                if (chainage[rightAnchor] - chainage[leftAnchor] > maxSpan + EPSILON) {
                    break;
                }
                if (!isEligibleCluster(
                        elevations,
                        segmentDistances,
                        leftAnchor,
                        rightAnchor,
                        maxAmplitude,
                        lockStart,
                        lockEnd)) {
                    continue;
                }
                double[] trial = elevations.clone();
                applyMonotonicReplacement(trial, chainage, leftAnchor, rightAnchor);
                GradeLimitedProfileSolver.reprojectFeasible(
                    trial,
                    segmentDistances,
                    maxSlopePercents,
                    preset,
                    lockStart,
                    lockEnd,
                    startLock,
                    endLock,
                    minElevations,
                    maxElevations);
                if (!satisfiesClusterHardConstraints(
                        trial,
                        lockStart,
                        lockEnd,
                        startLock,
                        endLock,
                        minElevations,
                        maxElevations)) {
                    continue;
                }
                double trialScore = score(trial, segmentDistances, trendElevations);
                if (trialScore < bestTrialScore) {
                    bestTrialScore = trialScore;
                    bestRight = rightAnchor;
                    bestTrial = trial;
                }
            }

            if (bestRight >= 0
                    && bestTrial != null
                    && bestTrialScore < baselineScore * (1.0 - minBenefit)) {
                System.arraycopy(bestTrial, 0, elevations, 0, elevations.length);
                leftAnchor = bestRight;
            } else {
                leftAnchor++;
            }
        }
    }

    private static boolean isEligibleCluster(
            double[] elevations,
            List<Double> segmentDistances,
            int leftAnchor,
            int rightAnchor,
            double maxAmplitude,
            boolean lockStart,
            boolean lockEnd) {
        if (rightAnchor - leftAnchor < 2) {
            return false;
        }
        if (clusterAmplitude(elevations, leftAnchor, rightAnchor) > maxAmplitude + EPSILON) {
            return false;
        }
        if (countGradeSignReversalsInRange(
                elevations, segmentDistances, leftAnchor, rightAnchor) < 2) {
            return false;
        }
        for (int vertex = leftAnchor + 1; vertex <= rightAnchor - 1; vertex++) {
            if (isLockedVertex(vertex, elevations.length, lockStart, lockEnd)) {
                return false;
            }
        }
        return true;
    }

    private static int countGradeSignReversalsInRange(
            double[] elevations,
            List<Double> segmentDistances,
            int leftAnchor,
            int rightAnchor) {
        int reversals = 0;
        Double previousSign = null;
        for (int i = leftAnchor; i < rightAnchor; i++) {
            double grade = GradeLimitedProfileSolver.gradeAtSegment(
                elevations[i], elevations[i + 1], segmentDistances.get(i));
            double sign = Math.signum(grade);
            if (Math.abs(sign) < EPSILON) {
                continue;
            }
            if (previousSign != null && previousSign * sign < 0) {
                reversals++;
            }
            previousSign = sign;
        }
        return reversals;
    }

    private static double clusterAmplitude(double[] elevations, int leftAnchor, int rightAnchor) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = leftAnchor; i <= rightAnchor; i++) {
            min = Math.min(min, elevations[i]);
            max = Math.max(max, elevations[i]);
        }
        return max - min;
    }

    private static void applyMonotonicReplacement(
            double[] elevations,
            double[] chainage,
            int leftAnchor,
            int rightAnchor) {
        double startElevation = elevations[leftAnchor];
        double endElevation = elevations[rightAnchor];
        double span = chainage[rightAnchor] - chainage[leftAnchor];
        if (span <= EPSILON) {
            for (int i = leftAnchor + 1; i < rightAnchor; i++) {
                elevations[i] = startElevation;
            }
            return;
        }
        for (int i = leftAnchor + 1; i < rightAnchor; i++) {
            double fraction = (chainage[i] - chainage[leftAnchor]) / span;
            elevations[i] = startElevation + (endElevation - startElevation) * fraction;
        }
    }

    private static double score(
            double[] elevations,
            List<Double> segmentDistances,
            List<Double> trendElevations) {
        double reversalCount = countGradeSignReversals(elevations, segmentDistances);
        double gradeChange = GradeLimitedProfileSolver.totalAbsoluteGradeChange(
            elevations, segmentDistances);
        int interiorCount = Math.max(1, elevations.length - 2);
        double normalizedGradeChange = gradeChange / interiorCount;
        double terrainDeviation = meanAbsDevFromTrend(elevations, trendElevations);
        return REVERSAL_WEIGHT * reversalCount
            + GRADE_CHANGE_WEIGHT * normalizedGradeChange
            + TERRAIN_DEVIATION_WEIGHT * terrainDeviation;
    }

    private static double meanAbsDevFromTrend(double[] elevations, List<Double> trendElevations) {
        if (trendElevations == null || trendElevations.size() != elevations.length) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < elevations.length; i++) {
            sum += Math.abs(elevations[i] - trendElevations.get(i));
        }
        return sum / elevations.length;
    }

    private static boolean satisfiesClusterHardConstraints(
            double[] elevations,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock,
            double[] minElevations,
            double[] maxElevations) {
        if (lockStart && Math.abs(elevations[0] - startLock) > EPSILON) {
            return false;
        }
        if (lockEnd && Math.abs(elevations[elevations.length - 1] - endLock) > EPSILON) {
            return false;
        }
        if (minElevations != null) {
            for (int i = 0; i < elevations.length; i++) {
                double min = minElevations[i];
                if (!Double.isNaN(min) && elevations[i] < min - EPSILON) {
                    return false;
                }
            }
        }
        if (maxElevations != null) {
            for (int i = 0; i < elevations.length; i++) {
                double max = maxElevations[i];
                if (!Double.isNaN(max) && elevations[i] > max + EPSILON) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isLockedVertex(
            int vertex,
            int stationCount,
            boolean lockStart,
            boolean lockEnd) {
        return (lockStart && vertex == 0) || (lockEnd && vertex == stationCount - 1);
    }

    private static double[] buildChainage(List<Double> segmentDistances) {
        double[] chainage = new double[segmentDistances.size() + 1];
        for (int i = 0; i < segmentDistances.size(); i++) {
            chainage[i + 1] = chainage[i] + segmentDistances.get(i);
        }
        return chainage;
    }
}
