package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.core.material.MaterialConversionModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 FIT_TERRAIN 设计纵断面上施加预览级挖填平衡竖向偏移。
 * 使用与 {@link com.plot.plugin.road.RoadGuideLineUtils} 相同的 fillFactor 材料模型。
 */
public final class ProfileCutFillBalancer {

    private static final double EPSILON = 1e-9;
    private static final int INITIAL_SEARCH_RADIUS = 8;
    private static final int MAX_SEARCH_RADIUS = 32;
    /** Limit preview-level balance shift so grade transitions stay stable. */
    private static final int MAX_OFFSET_BLOCKS = 4;
    private static final long MIN_IMBALANCE_TO_CORRECT = 10L;

    private ProfileCutFillBalancer() {
    }

    public static List<Double> apply(
            List<Integer> groundSamples,
            List<Double> designElevations,
            float fillFactor,
            double balanceWeight) {
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
        List<Double> adjusted = new ArrayList<>(designElevations.size());
        for (double elevation : designElevations) {
            adjusted.add(elevation + appliedOffset);
        }
        return List.copyOf(adjusted);
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
