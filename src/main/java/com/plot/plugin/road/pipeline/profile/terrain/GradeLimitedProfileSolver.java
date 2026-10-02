package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.plugin.road.pipeline.profile.RoadHeightRasterizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 双向坡度受限纵断面求解：forward + backward pass，再向地形趋势松弛并投影回可行域。
 */
public final class GradeLimitedProfileSolver {

    private static final int MAX_PROJECTION_ITERATIONS = 64;
    private static final double EPSILON = 1e-9;

    private GradeLimitedProfileSolver() {
    }

    public record DesignSolveResult(
            List<Double> designElevations,
            int startHeight,
            List<Integer> segmentBuildEnds,
            boolean manualEndpointsFeasible) {
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
        double[] stations = solveStationElevations(
            trendElevations,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight,
            preset);
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
            manualStartHeight,
            manualEndHeight);
        return new DesignSolveResult(
            designElevations,
            raster.startHeight(),
            raster.segmentBuildEnds(),
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
            Integer manualStartHeight,
            Integer manualEndHeight,
            TerrainFollowPreset preset) {
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

        double alpha = effectivePreset.trendBlendWeight();
        for (int iteration = 0; iteration < effectivePreset.relaxationIterations(); iteration++) {
            for (int i = 1; i < stationCount - 1; i++) {
                current[i] = (1.0 - alpha) * current[i] + alpha * trendElevations.get(i);
            }
            applyEndpointLocks(current, lockStart, lockEnd, startTarget, endTarget);
            projectFeasible(
                current, segmentDistances, maxSlopePercents, lockStart, lockEnd, startTarget, endTarget);
        }
        projectFeasible(
            current, segmentDistances, maxSlopePercents, lockStart, lockEnd, startTarget, endTarget);
        return current;
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
            for (int i = 0; i < segmentDistances.size(); i++) {
                double maxRise = maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
                double projected = clampToSlopeBracket(elevations[i], elevations[i + 1], maxRise);
                if (Math.abs(projected - elevations[i + 1]) > EPSILON) {
                    elevations[i + 1] = projected;
                    changed = true;
                }
            }
            for (int i = segmentDistances.size() - 1; i >= 0; i--) {
                double maxRise = maxRise(segmentDistances.get(i), maxSlopePercents.get(i));
                double projected = clampToSlopeBracket(elevations[i + 1], elevations[i], maxRise);
                if (Math.abs(projected - elevations[i]) > EPSILON) {
                    elevations[i] = projected;
                    changed = true;
                }
            }
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
