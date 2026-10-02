package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.road.pipeline.profile.RoadHeightRasterizer;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 双向坡度受限纵断面求解：forward + backward pass，再向地形趋势松弛并投影回可行域。
 */
public final class GradeLimitedProfileSolver {

    private static final int MAX_PROJECTION_ITERATIONS = 64;
    private static final double EPSILON = 1e-9;
    private static final double GRADE_MATCH_TOLERANCE_PERCENT = 0.75;
    private static final long MIN_IMBALANCE_TO_CORRECT = 10L;
    static final double CUT_FILL_STEP_BLOCKS = 0.5;

    private GradeLimitedProfileSolver() {
    }

    public record DesignSolveResult(
            List<Double> designElevations,
            RoadHeightRasterizer.RasterizationResult rasterization,
            boolean manualEndpointsFeasible) {

        public int startHeight() {
            return rasterization.startHeight();
        }

        public List<Integer> segmentBuildEnds() {
            return rasterization.segmentBuildEnds();
        }
    }

    public record SegmentEndSolveResult(
            int startHeight,
            List<Integer> segmentEnds,
            List<Double> designElevations,
            boolean manualEndpointsFeasible) {
    }

    /**
     * @return 每段末端建造高程（与 {@link com.plot.plugin.road.RoadSlopeUtils#computeChainedTargetHeights} 契约一致）
     */
    public static List<Integer> solveSegmentEnds(
            List<Double> trendElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight,
            TerrainFollowPreset preset) {
        return solveSegmentEndsWithStart(
            trendElevations,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight,
            preset).segmentEnds();
    }

    public static DesignSolveResult solveDesignProfile(
            List<Double> trendElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight,
            TerrainFollowPreset preset) {
        return solveDesignProfile(
            trendElevations,
            null,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight,
            preset,
            1.0f);
    }

    public static DesignSolveResult solveDesignProfile(
            List<Double> trendElevations,
            List<Integer> groundSamples,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight,
            TerrainFollowPreset preset,
            float fillFactor) {
        double[] stations = solveStationElevations(
            trendElevations,
            groundSamples,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight,
            preset,
            fillFactor);
        List<Double> designElevations = toDesignList(stations);
        int profileStart = manualStartHeight != null
            ? manualStartHeight
            : (int) Math.round(stations[0]);
        boolean endpointsFeasible = areManualEndpointsFeasible(
            manualStartHeight,
            manualEndHeight,
            segmentDistances,
            maxSlopePercents,
            profileStart);
        RoadHeightRasterizer.RasterizationResult raster = RoadHeightRasterizer.rasterize(
            designElevations,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight);
        return new DesignSolveResult(
            designElevations,
            raster,
            endpointsFeasible);
    }

    public static SegmentEndSolveResult solveSegmentEndsWithStart(
            List<Double> trendElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight,
            TerrainFollowPreset preset) {
        DesignSolveResult solved = solveDesignProfile(
            trendElevations,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight,
            preset);
        return new SegmentEndSolveResult(
            solved.startHeight(),
            solved.segmentBuildEnds(),
            solved.designElevations(),
            solved.manualEndpointsFeasible());
    }

    /**
     * Re-project a design profile onto the feasible domain (max slope, max grade change, endpoint locks).
     */
    public static List<Double> projectDesignFeasible(
            List<Double> designElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight,
            boolean manualEndpointsFeasible) {
        Objects.requireNonNull(designElevations, "designElevations");
        Objects.requireNonNull(segmentDistances, "segmentDistances");
        Objects.requireNonNull(maxSlopePercents, "maxSlopePercents");
        if (designElevations.isEmpty()) {
            return designElevations;
        }
        if (designElevations.size() != segmentDistances.size() + 1) {
            throw new IllegalArgumentException("design elevations must have one more sample than segments");
        }
        if (maxSlopePercents.size() != segmentDistances.size()) {
            throw new IllegalArgumentException("max slope list must match segment count");
        }
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        double[] elevations = designElevations.stream().mapToDouble(Double::doubleValue).toArray();
        boolean lockStart = manualStartHeight != null;
        boolean lockEnd = manualEndHeight != null && manualEndpointsFeasible;
        double startLock = lockStart ? manualStartHeight.doubleValue() : elevations[0];
        double endLock = lockEnd ? manualEndHeight.doubleValue() : elevations[elevations.length - 1];
        applyEndpointLocks(elevations, lockStart, lockEnd, startLock, endLock);
        projectFeasible(
            elevations,
            segmentDistances,
            maxSlopePercents,
            effectivePreset.maxGradeChangePercent(),
            lockStart,
            lockEnd,
            startLock,
            endLock);
        return toDesignList(elevations);
    }

    public static boolean areManualEndpointsFeasible(
            Integer manualStartHeight,
            Integer manualEndHeight,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            int profileStartHeight) {
        if (manualEndHeight == null) {
            return true;
        }
        int startHeight = manualStartHeight != null ? manualStartHeight : profileStartHeight;
        double maxTotalRise = totalMaxRise(segmentDistances, maxSlopePercents);
        return Math.abs(manualEndHeight - startHeight) <= maxTotalRise + EPSILON;
    }

    private static List<Double> toDesignList(double[] stations) {
        List<Double> designElevations = new ArrayList<>(stations.length);
        for (double station : stations) {
            designElevations.add(station);
        }
        return designElevations;
    }

    static double[] solveStationElevations(
            List<Double> trendElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset) {
        return solveStationElevations(
            trendElevations,
            null,
            segmentDistances,
            maxSlopePercents,
                null,
                null,
            preset,
            1.0f);
    }

    static double[] solveStationElevations(
            List<Double> trendElevations,
            List<Integer> groundSamples,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight,
            TerrainFollowPreset preset,
            float fillFactor) {
        Objects.requireNonNull(trendElevations, "trendElevations");
        Objects.requireNonNull(segmentDistances, "segmentDistances");
        Objects.requireNonNull(maxSlopePercents, "maxSlopePercents");
        if (segmentDistances.isEmpty()) {
            return new double[0];
        }
        if (trendElevations.size() != segmentDistances.size() + 1) {
            throw new IllegalArgumentException("trend elevations must have one more sample than segments");
        }
        if (maxSlopePercents.size() != segmentDistances.size()) {
            throw new IllegalArgumentException("max slope list must match segment count");
        }

        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        int stationCount = trendElevations.size();
        int profileStart = manualStartHeight != null
            ? manualStartHeight
            : (int) Math.round(trendElevations.getFirst());
        boolean endpointsFeasible = areManualEndpointsFeasible(
            manualStartHeight,
            manualEndHeight,
            segmentDistances,
            maxSlopePercents,
            profileStart);
        boolean lockStart = manualStartHeight != null;
        boolean lockEnd = manualEndHeight != null && endpointsFeasible;
        double startTarget = lockStart
            ? manualStartHeight.doubleValue()
            : trendElevations.getFirst();
        double endTarget = lockEnd
            ? manualEndHeight.doubleValue()
            : trendElevations.getLast();

        double[] forward = singlePass(
            trendElevations, segmentDistances, maxSlopePercents, startTarget, endTarget, true);
        double[] backward = singlePass(
            trendElevations, segmentDistances, maxSlopePercents, startTarget, endTarget, false);

        double[] current = new double[stationCount];
        for (int i = 0; i < stationCount; i++) {
            current[i] = (forward[i] + backward[i]) * 0.5;
        }
        applyEndpointLocks(current, lockStart, lockEnd, startTarget, endTarget);

        relaxTowardTrendAndCutFill(
            current,
            trendElevations,
            groundSamples,
            segmentDistances,
            maxSlopePercents,
            effectivePreset,
            fillFactor,
            lockStart,
            lockEnd,
            startTarget,
            endTarget);
        smoothGradeChanges(
            current,
            segmentDistances,
            maxSlopePercents,
            effectivePreset,
            lockStart,
            lockEnd,
            startTarget,
            endTarget);
        enforceGradeTransitionLengths(
            current,
            segmentDistances,
            maxSlopePercents,
            effectivePreset,
            lockStart,
            lockEnd,
            startTarget,
            endTarget);
        projectFeasible(
            current,
            segmentDistances,
            maxSlopePercents,
            effectivePreset.maxGradeChangePercent(),
            lockStart,
            lockEnd,
            startTarget,
            endTarget);
        projectFeasible(
            current,
            segmentDistances,
            maxSlopePercents,
            0.0,
            lockStart,
            lockEnd,
            startTarget,
            endTarget);
        return current;
    }

    /**
     * Joint relaxation toward terrain trend and cut/fill balance each iteration:
     * {@code current = keep * current + alpha * trend + beta * cutFillTarget},
     * then project back to the feasible domain.
     */
    private static void relaxTowardTrendAndCutFill(
            double[] current,
            List<Double> trendElevations,
            List<Integer> groundSamples,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            float fillFactor,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock) {
        double alpha = preset.trendBlendWeight();
        double beta = 0.0;
        boolean cutFillEnabled = isCutFillBalanceEnabled(groundSamples, preset, current.length);
        if (cutFillEnabled) {
            beta = preset.cutFillBalanceWeight();
            double total = alpha + beta;
            if (total > 1.0 + EPSILON) {
                alpha /= total;
                beta /= total;
            }
        }
        MaterialConversionModel materials = cutFillEnabled
            ? MaterialConversionModel.fromLegacyFillFactor(fillFactor)
            : null;
        int stationCount = current.length;

        for (int iteration = 0; iteration < preset.relaxationIterations(); iteration++) {
            boolean applyCutFill = cutFillEnabled;
            long imbalance = 0L;
            if (applyCutFill) {
                imbalance = ProfileCutFillBalancer.computeBalanceDiff(
                    groundSamples, toDesignList(current), 0, materials);
                applyCutFill = Math.abs(imbalance) >= MIN_IMBALANCE_TO_CORRECT;
            }
            double iterationBeta = applyCutFill ? beta : 0.0;
            double iterationKeep = 1.0 - alpha - iterationBeta;
            double cutFillStepScale = applyCutFill
                ? Math.min(1.0, Math.abs(imbalance) / 30.0)
                : 0.0;

            for (int i = 1; i < stationCount - 1; i++) {
                double trendTarget = trendElevations.get(i);
                double blended = iterationKeep * current[i] + alpha * trendTarget;
                if (applyCutFill) {
                    double cutFillTarget = cutFillBalanceTarget(
                        imbalance,
                        groundSamples.get(i),
                        current[i],
                        cutFillStepScale);
                    blended += iterationBeta * cutFillTarget;
                }
                current[i] = blended;
            }
            applyEndpointLocks(current, lockStart, lockEnd, startLock, endLock);
            projectFeasible(
                current,
                segmentDistances,
                maxSlopePercents,
                preset.maxGradeChangePercent(),
                lockStart,
                lockEnd,
                startLock,
                endLock);
        }
    }

    private static boolean isCutFillBalanceEnabled(
            List<Integer> groundSamples,
            TerrainFollowPreset preset,
            int stationCount) {
        return groundSamples != null
            && preset.cutFillBalanceWeight() > EPSILON
            && groundSamples.size() == stationCount
            && !ProfileCutFillBalancer.containsLargeTerrainStep(groundSamples);
    }

    /**
     * Signed nudge direction that reduces global cut/fill imbalance at one station.
     * Cut surplus raises cut stations only; fill surplus lowers fill stations only.
     */
    static double cutFillNudgeDirection(long imbalance, int ground, double design) {
        if (Math.abs(ground - design) <= EPSILON) {
            return 0.0;
        }
        if (imbalance > 0) {
            return ground > design ? 1.0 : 0.0;
        }
        if (imbalance < 0) {
            return design > ground ? -1.0 : 0.0;
        }
        return 0.0;
    }

    static double cutFillBalanceTarget(
            long imbalance,
            int ground,
            double design,
            double stepScale) {
        double direction = cutFillNudgeDirection(imbalance, ground, design);
        if (direction == 0.0) {
            return design;
        }
        double maxStep = CUT_FILL_STEP_BLOCKS * stepScale;
        double distanceToGround = Math.abs(ground - design);
        return design + direction * Math.min(maxStep, distanceToGround);
    }

    private static void smoothGradeChanges(
            double[] elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock) {
        double weight = preset.gradeChangeSmoothingWeight();
        int iterations = preset.gradeChangeSmoothingIterations();
        if (weight <= EPSILON || iterations <= 0 || elevations.length < 3) {
            return;
        }
        for (int iteration = 0; iteration < iterations; iteration++) {
            double[] updated = elevations.clone();
            for (int i = 1; i < elevations.length - 1; i++) {
                double distLeft = segmentDistances.get(i - 1);
                double distRight = segmentDistances.get(i);
                double equalGradeElevation = equalGradeElevationAt(
                    elevations[i - 1],
                    elevations[i + 1],
                    distLeft,
                    distRight);
                updated[i] = (1.0 - weight) * elevations[i] + weight * equalGradeElevation;
            }
            applyEndpointLocks(updated, lockStart, lockEnd, startLock, endLock);
            projectFeasible(
                updated,
                segmentDistances,
                maxSlopePercents,
                preset.maxGradeChangePercent(),
                lockStart,
                lockEnd,
                startLock,
                endLock);
            System.arraycopy(updated, 0, elevations, 0, elevations.length);
        }
    }

    /**
     * Spread significant grade reversals over a minimum transition length so crests / sags
     * do not collapse into a single station kink.
     */
    private static void enforceGradeTransitionLengths(
            double[] elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock) {
        if (elevations.length < 3 || segmentDistances.isEmpty()) {
            return;
        }
        double[] chainage = cumulativeChainage(segmentDistances);
        List<Integer> breakVertices = new ArrayList<>();
        List<Double> breakMagnitudes = new ArrayList<>();
        int firstInterior = lockStart ? 2 : 1;
        int lastInterior = lockEnd ? elevations.length - 3 : elevations.length - 2;
        for (int vertex = firstInterior; vertex <= lastInterior; vertex++) {
            double leftGrade = gradeAtSegment(
                elevations[vertex - 1],
                elevations[vertex],
                segmentDistances.get(vertex - 1));
            double rightGrade = gradeAtSegment(
                elevations[vertex],
                elevations[vertex + 1],
                segmentDistances.get(vertex));
            double magnitude = Math.abs(rightGrade - leftGrade);
            if (magnitude <= gradeTransitionTriggerPercent(preset)) {
                continue;
            }
            breakVertices.add(vertex);
            breakMagnitudes.add(magnitude);
        }
        if (breakVertices.isEmpty()) {
            return;
        }

        Integer[] order = new Integer[breakVertices.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparingDouble(breakMagnitudes::get).reversed());

        double[] updated = elevations.clone();
        for (int index : order) {
            int vertex = breakVertices.get(index);
            double leftGrade = gradeAtSegment(
                updated[vertex - 1],
                updated[vertex],
                segmentDistances.get(vertex - 1));
            double rightGrade = gradeAtSegment(
                updated[vertex],
                updated[vertex + 1],
                segmentDistances.get(vertex));
            double leftRun = constantGradeRunLength(
                updated, segmentDistances, vertex - 1, -1, leftGrade);
            double rightRun = constantGradeRunLength(
                updated, segmentDistances, vertex, 1, rightGrade);
            double transitionLength = resolveGradeTransitionLength(
                preset, leftRun, rightRun);
            if (transitionLength <= EPSILON) {
                continue;
            }
            spreadGradeTransitionAtVertex(
                updated,
                chainage,
                vertex,
                leftGrade,
                rightGrade,
                transitionLength);
        }
        applyEndpointLocks(updated, lockStart, lockEnd, startLock, endLock);
        projectFeasible(
            updated,
            segmentDistances,
            maxSlopePercents,
            preset.maxGradeChangePercent(),
            lockStart,
            lockEnd,
            startLock,
            endLock);
        System.arraycopy(updated, 0, elevations, 0, elevations.length);
    }

    static double resolveGradeTransitionLength(
            TerrainFollowPreset preset,
            double leftRun,
            double rightRun) {
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        double presetMinimum = Math.max(
            VerticalProfileDesignRules.MIN_VERTICAL_TRANSITION_LENGTH,
            effectivePreset.minGradeTransitionMeters());
        double suggested = VerticalProfileDesignRules.suggestedTransitionLength(leftRun, rightRun);
        if (suggested <= EPSILON) {
            return Math.min(presetMinimum, leftRun + rightRun);
        }
        return Math.max(presetMinimum, suggested);
    }

    private static double gradeTransitionTriggerPercent(TerrainFollowPreset preset) {
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        return Math.max(
            2.0,
            effectivePreset.maxGradeChangePercent() * 0.75);
    }

    static double constantGradeRunLength(
            double[] elevations,
            List<Double> segmentDistances,
            int segmentIndex,
            int direction,
            double referenceGrade) {
        if (segmentIndex < 0 || segmentIndex >= segmentDistances.size()) {
            return 0.0;
        }
        double run = segmentDistances.get(segmentIndex);
        int index = segmentIndex + direction;
        while (index >= 0 && index < segmentDistances.size()) {
            int startVertex = Math.min(index, index + 1);
            int endVertex = Math.max(index, index + 1);
            double segmentGrade = gradeAtSegment(
                elevations[startVertex],
                elevations[endVertex],
                segmentDistances.get(index));
            if (Math.abs(segmentGrade - referenceGrade) > GRADE_MATCH_TOLERANCE_PERCENT) {
                break;
            }
            run += segmentDistances.get(index);
            index += direction;
        }
        return run;
    }

    static void spreadGradeTransitionAtVertex(
            double[] elevations,
            double[] chainage,
            int vertex,
            double incomingGradePercent,
            double outgoingGradePercent,
            double transitionLength) {
        double center = chainage[vertex];
        double start = center - transitionLength * 0.5;
        double end = center + transitionLength * 0.5;
        start = Math.max(start, chainage[0]);
        end = Math.min(end, chainage[chainage.length - 1]);
        double effectiveLength = end - start;
        if (effectiveLength <= EPSILON) {
            return;
        }
        double startElevation = elevationAtChainage(elevations, chainage, start);
        for (int i = 0; i < elevations.length; i++) {
            double station = chainage[i];
            if (station + EPSILON < start || station - EPSILON > end) {
                continue;
            }
            double offset = station - start;
            elevations[i] = elevationAlongLinearGradeRamp(
                startElevation,
                incomingGradePercent,
                outgoingGradePercent,
                offset,
                effectiveLength);
        }
    }

    static double elevationAlongLinearGradeRamp(
            double startElevation,
            double incomingGradePercent,
            double outgoingGradePercent,
            double offset,
            double transitionLength) {
        if (transitionLength <= EPSILON) {
            return startElevation;
        }
        double clampedOffset = Math.max(0.0, Math.min(offset, transitionLength));
        return startElevation
            + (incomingGradePercent * clampedOffset
                + (outgoingGradePercent - incomingGradePercent)
                    * clampedOffset * clampedOffset / (2.0 * transitionLength))
            / 100.0;
    }

    static double elevationAtChainage(double[] elevations, double[] chainage, double station) {
        if (station <= chainage[0] + EPSILON) {
            return elevations[0];
        }
        if (station >= chainage[chainage.length - 1] - EPSILON) {
            return elevations[elevations.length - 1];
        }
        for (int i = 1; i < chainage.length; i++) {
            if (station <= chainage[i] + EPSILON) {
                double span = chainage[i] - chainage[i - 1];
                if (span <= EPSILON) {
                    return elevations[i];
                }
                double blend = (station - chainage[i - 1]) / span;
                return elevations[i - 1] + (elevations[i] - elevations[i - 1]) * blend;
            }
        }
        return elevations[elevations.length - 1];
    }

    private static double[] cumulativeChainage(List<Double> segmentDistances) {
        double[] chainage = new double[segmentDistances.size() + 1];
        for (int i = 0; i < segmentDistances.size(); i++) {
            chainage[i + 1] = chainage[i] + segmentDistances.get(i);
        }
        return chainage;
    }

    /** Elevation at vertex {@code i} that makes grades on both adjacent segments equal. */
    static double equalGradeElevationAt(
            double leftElevation,
            double rightElevation,
            double leftDistance,
            double rightDistance) {
        if (leftDistance <= EPSILON && rightDistance <= EPSILON) {
            return (leftElevation + rightElevation) * 0.5;
        }
        if (leftDistance <= EPSILON) {
            return rightElevation;
        }
        if (rightDistance <= EPSILON) {
            return leftElevation;
        }
        return (leftElevation * rightDistance + rightElevation * leftDistance)
            / (leftDistance + rightDistance);
    }

    private static double totalMaxRise(List<Double> segmentDistances, List<Float> maxSlopePercents) {
        double total = 0.0;
        for (int i = 0; i < segmentDistances.size(); i++) {
            total += maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
        }
        return total;
    }

    private static double[] singlePass(
            List<Double> trend,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            double startLock,
            double endLock,
            boolean forwardDirection) {
        int stationCount = trend.size();
        double[] elevations = new double[stationCount];
        if (forwardDirection) {
            elevations[0] = startLock;
            for (int i = 0; i < segmentDistances.size(); i++) {
                double maxRise = maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
                double target = i == segmentDistances.size() - 1
                    ? endLock
                    : trend.get(i + 1);
                elevations[i + 1] = clampToward(elevations[i], target, maxRise);
            }
        } else {
            elevations[stationCount - 1] = endLock;
            for (int i = segmentDistances.size() - 1; i >= 0; i--) {
                double maxRise = maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
                double desired = i == 0 ? startLock : trend.get(i);
                elevations[i] = clampToSlopeBracket(elevations[i + 1], desired, maxRise);
            }
        }
        return elevations;
    }

    private static void projectFeasible(
            double[] elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            double maxGradeChangePercent,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock) {
        for (int iteration = 0; iteration < MAX_PROJECTION_ITERATIONS; iteration++) {
            boolean changed = false;
            if (lockStart) {
                elevations[0] = startLock;
            }
            if (lockEnd) {
                elevations[elevations.length - 1] = endLock;
            }
            if (maxGradeChangePercent > EPSILON) {
                changed |= projectGradeChangeFeasible(
                    elevations,
                    segmentDistances,
                    maxGradeChangePercent,
                    lockStart,
                    lockEnd);
            }
            changed |= projectSegmentSlopes(
                elevations,
                segmentDistances,
                maxSlopePercents,
                lockStart,
                lockEnd,
                startLock,
                endLock);
            if (!changed) {
                break;
            }
        }
        if (lockStart) {
            elevations[0] = startLock;
        }
        if (lockEnd) {
            elevations[elevations.length - 1] = endLock;
        }
    }

    private static boolean projectSegmentSlopes(
            double[] elevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock) {
        if (lockStart) {
            elevations[0] = startLock;
        }
        if (lockEnd) {
            elevations[elevations.length - 1] = endLock;
        }
        boolean changed = false;
        for (int i = 0; i < segmentDistances.size(); i++) {
            if (lockEnd && i + 1 == elevations.length - 1) {
                continue;
            }
            double maxRise = maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
            double projected = clampToSlopeBracket(elevations[i], elevations[i + 1], maxRise);
            if (Math.abs(projected - elevations[i + 1]) > EPSILON) {
                elevations[i + 1] = projected;
                changed = true;
            }
        }
        if (lockStart) {
            elevations[0] = startLock;
        }
        if (lockEnd) {
            elevations[elevations.length - 1] = endLock;
        }
        for (int i = segmentDistances.size() - 1; i >= 0; i--) {
            if (lockStart && i == 0) {
                continue;
            }
            double maxRise = maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
            double projected = clampToSlopeBracket(elevations[i + 1], elevations[i], maxRise);
            if (Math.abs(projected - elevations[i]) > EPSILON) {
                elevations[i] = projected;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean projectGradeChangeFeasible(
            double[] elevations,
            List<Double> segmentDistances,
            double maxGradeChangePercent,
            boolean lockStart,
            boolean lockEnd) {
        boolean changed = false;
        int lastInterior = elevations.length - 2;
        int firstInterior = 1;
        if (lockStart) {
        }
        if (lockEnd) {
            lastInterior = Math.min(lastInterior, elevations.length - 2);
        }
        for (int i = firstInterior; i <= lastInterior; i++) {
            double distLeft = segmentDistances.get(i - 1);
            double distRight = segmentDistances.get(i);
            if (distLeft <= EPSILON || distRight <= EPSILON) {
                continue;
            }
            double reciprocalSum = 1.0 / distLeft + 1.0 / distRight;
            double center = elevations[i + 1] / distRight + elevations[i - 1] / distLeft;
            double low = (center - maxGradeChangePercent / 100.0) / reciprocalSum;
            double high = (center + maxGradeChangePercent / 100.0) / reciprocalSum;
            double clamped = Math.max(low, Math.min(high, elevations[i]));
            if (Math.abs(clamped - elevations[i]) > EPSILON) {
                elevations[i] = clamped;
                changed = true;
            }
        }
        return changed;
    }

    static double gradeAtSegment(double startElevation, double endElevation, double distance) {
        if (distance <= EPSILON) {
            return 0.0;
        }
        return (endElevation - startElevation) / distance * 100.0;
    }

    static double totalAbsoluteGradeChange(double[] elevations, List<Double> segmentDistances) {
        if (elevations.length < 3 || segmentDistances.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < elevations.length - 1; i++) {
            double leftGrade = gradeAtSegment(
                elevations[i - 1], elevations[i], segmentDistances.get(i - 1));
            double rightGrade = gradeAtSegment(
                elevations[i], elevations[i + 1], segmentDistances.get(i));
            total += Math.abs(rightGrade - leftGrade);
        }
        return total;
    }

    private static double clampToSlopeBracket(double anchor, double value, double maxRise) {
        double low = anchor - maxRise;
        double high = anchor + maxRise;
        return Math.max(low, Math.min(high, value));
    }

    private static void applyEndpointLocks(
            double[] elevations,
            boolean lockStart,
            boolean lockEnd,
            double startLock,
            double endLock) {
        if (lockStart) {
            elevations[0] = startLock;
        }
        if (lockEnd) {
            elevations[elevations.length - 1] = endLock;
        }
    }

    private static double maxRise(double segmentDistance, float maxSlopePercent) {
        return segmentDistance * maxSlopePercent / 100.0;
    }

    static double clampToward(double from, double target, double maxDelta) {
        double delta = target - from;
        if (Math.abs(delta) <= maxDelta + 1e-9) {
            return target;
        }
        return from + (delta > 0.0 ? maxDelta : -maxDelta);
    }
}
