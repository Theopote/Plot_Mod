package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.core.material.MaterialConversionModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 FIT_TERRAIN 设计纵断面上施加预览级挖填平衡竖向偏移。
 * 使用与 {@link com.plot.plugin.road.RoadGuideLineUtils} 相同的 fillFactor 材料模型。
 * <p>
 * 当存在手动端点高程锁定时，偏移沿路线长度做 taper，并在应用后重新投影到可行域。
 */
public final class ProfileCutFillBalancer {

    private static final double EPSILON = 1e-9;
    private static final int INITIAL_SEARCH_RADIUS = 8;
    private static final int MAX_SEARCH_RADIUS = 32;
    /** Limit preview-level balance shift so grade transitions stay stable. */
    private static final int MAX_OFFSET_BLOCKS = 4;
    private static final long MIN_IMBALANCE_TO_CORRECT = 10L;
    private static final double DEFAULT_SEGMENT_DISTANCE = 10.0;
    private static final float DEFAULT_MAX_SLOPE_PERCENT = 100.0f;

    private ProfileCutFillBalancer() {
    }

    public static List<Double> apply(
            List<Integer> groundSamples,
            List<Double> designElevations,
            float fillFactor,
            double balanceWeight) {
        int segmentCount = Math.max(0, designElevations.size() - 1);
        return apply(
            groundSamples,
            designElevations,
            defaultSegmentDistances(segmentCount),
            defaultMaxSlopes(segmentCount),
            fillFactor,
            balanceWeight,
            TerrainFollowPreset.STANDARD,
            null,
            null,
            true);
    }

    public static List<Double> apply(
            List<Integer> groundSamples,
            List<Double> designElevations,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            float fillFactor,
            double balanceWeight,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight,
            boolean manualEndpointsFeasible) {
        if (balanceWeight <= EPSILON
                || groundSamples == null
                || designElevations == null
                || groundSamples.isEmpty()
                || designElevations.isEmpty()
                || groundSamples.size() != designElevations.size()) {
            return designElevations;
        }
        MaterialConversionModel materials = MaterialConversionModel.fromLegacyFillFactor(fillFactor);
        if (containsLargeTerrainStep(groundSamples)) {
            return designElevations;
        }
        if (Math.abs(computeBalanceDiff(groundSamples, designElevations, 0, materials))
                < MIN_IMBALANCE_TO_CORRECT) {
            return designElevations;
        }
        double offset = findBalancingOffset(groundSamples, designElevations, fillFactor);
        if (Math.abs(offset) <= EPSILON) {
            return designElevations;
        }
        double appliedOffset = offset * balanceWeight;
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        boolean lockStart = manualStartHeight != null;
        boolean lockEnd = manualEndHeight != null && manualEndpointsFeasible;
        boolean canProject = segmentDistances != null
            && maxSlopePercents != null
            && segmentDistances.size() == designElevations.size() - 1
            && maxSlopePercents.size() == segmentDistances.size();
        List<Double> adjusted;
        if (lockStart || lockEnd) {
            if (!canProject) {
                return designElevations;
            }
            adjusted = applyTaperedOffset(
                designElevations,
                segmentDistances,
                appliedOffset,
                effectivePreset,
                lockStart,
                lockEnd);
        } else {
            adjusted = new ArrayList<>(designElevations.size());
            for (double elevation : designElevations) {
                adjusted.add(elevation + appliedOffset);
            }
        }
        if (canProject) {
            return GradeLimitedProfileSolver.projectDesignFeasible(
                adjusted,
                segmentDistances,
                maxSlopePercents,
                effectivePreset,
                manualStartHeight,
                manualEndHeight,
                manualEndpointsFeasible);
        }
        return List.copyOf(adjusted);
    }

    private static List<Double> applyTaperedOffset(
            List<Double> designElevations,
            List<Double> segmentDistances,
            double appliedOffset,
            TerrainFollowPreset preset,
            boolean lockStart,
            boolean lockEnd) {
        double[] stations = cumulativeStations(segmentDistances);
        double totalLength = stations[stations.length - 1];
        double taperMeters = Math.min(
            preset.minGradeTransitionMeters(),
            totalLength * 0.5);
        List<Double> adjusted = new ArrayList<>(designElevations.size());
        for (int i = 0; i < designElevations.size(); i++) {
            double weight = endpointBlendWeight(
                stations[i],
                totalLength,
                taperMeters,
                lockStart,
                lockEnd);
            adjusted.add(designElevations.get(i) + appliedOffset * weight);
        }
        return adjusted;
    }

    private static double[] cumulativeStations(List<Double> segmentDistances) {
        double[] stations = new double[segmentDistances.size() + 1];
        for (int i = 0; i < segmentDistances.size(); i++) {
            stations[i + 1] = stations[i] + segmentDistances.get(i);
        }
        return stations;
    }

    private static double endpointBlendWeight(
            double station,
            double totalLength,
            double taperMeters,
            boolean lockStart,
            boolean lockEnd) {
        double weight = 1.0;
        if (lockStart) {
            weight *= linearRamp(station, taperMeters);
        }
        if (lockEnd) {
            weight *= linearRamp(totalLength - station, taperMeters);
        }
        return weight;
    }

    private static double linearRamp(double distanceFromAnchor, double taperMeters) {
        if (taperMeters <= EPSILON) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.0, distanceFromAnchor / taperMeters));
    }

    private static List<Double> defaultSegmentDistances(int segmentCount) {
        List<Double> distances = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            distances.add(DEFAULT_SEGMENT_DISTANCE);
        }
        return distances;
    }

    private static List<Float> defaultMaxSlopes(int segmentCount) {
        List<Float> slopes = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            slopes.add(DEFAULT_MAX_SLOPE_PERCENT);
        }
        return slopes;
    }

    static double findBalancingOffset(
            List<Integer> groundSamples,
            List<Double> designElevations,
            float fillFactor) {
        MaterialConversionModel materials = MaterialConversionModel.fromLegacyFillFactor(fillFactor);
        int lo = -INITIAL_SEARCH_RADIUS;
        int hi = INITIAL_SEARCH_RADIUS;
        while (lo > -MAX_SEARCH_RADIUS
                && computeBalanceDiff(groundSamples, designElevations, lo, materials) < 0) {
            lo--;
        }
        while (hi < MAX_SEARCH_RADIUS
                && computeBalanceDiff(groundSamples, designElevations, hi, materials) > 0) {
            hi++;
        }
        lo = Math.max(lo, -MAX_SEARCH_RADIUS);
        hi = Math.min(hi, MAX_SEARCH_RADIUS);
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (computeBalanceDiff(groundSamples, designElevations, mid, materials) > 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return Math.max(-MAX_OFFSET_BLOCKS, Math.min(MAX_OFFSET_BLOCKS, lo));
    }

    static long computeBalanceDiff(
            List<Integer> groundSamples,
            List<Double> designElevations,
            int offsetBlocks,
            MaterialConversionModel materials) {
        MaterialConversionModel safeMaterials = materials != null
            ? materials
            : MaterialConversionModel.DEFAULT;
        long cutVolume = 0L;
        long fillVolume = 0L;
        for (int i = 0; i < groundSamples.size(); i++) {
            int ground = groundSamples.get(i);
            int design = (int) Math.round(designElevations.get(i)) + offsetBlocks;
            if (ground > design) {
                cutVolume += ground - design;
            } else if (design > ground) {
                fillVolume += design - ground;
            }
        }
        long supply = Math.round(cutVolume * safeMaterials.effectiveCutToCompactedFillRatio());
        return supply - fillVolume;
    }

    private static boolean containsLargeTerrainStep(List<Integer> groundSamples) {
        for (int i = 1; i < groundSamples.size(); i++) {
            if (Math.abs(groundSamples.get(i) - groundSamples.get(i - 1)) >= 8) {
                return true;
            }
        }
        return false;
    }

    static long estimateCutVolume(
            List<Integer> groundSamples,
            List<Double> designElevations) {
        long cut = 0L;
        for (int i = 0; i < groundSamples.size(); i++) {
            int ground = groundSamples.get(i);
            int design = (int) Math.round(designElevations.get(i));
            if (ground > design) {
                cut += ground - design;
            }
        }
        return cut;
    }

    static long estimateFillVolume(
            List<Integer> groundSamples,
            List<Double> designElevations) {
        long fill = 0L;
        for (int i = 0; i < groundSamples.size(); i++) {
            int ground = groundSamples.get(i);
            int design = (int) Math.round(designElevations.get(i));
            if (design > ground) {
                fill += design - ground;
            }
        }
        return fill;
    }
}
