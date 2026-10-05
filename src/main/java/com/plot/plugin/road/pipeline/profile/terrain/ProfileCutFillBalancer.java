package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.List;

/**
 * Cut/fill balance metrics shared by {@link GradeLimitedProfileSolver}.
 */
public final class ProfileCutFillBalancer {

    private static final int INITIAL_SEARCH_RADIUS = 8;
    private static final int MAX_SEARCH_RADIUS = 32;
    private static final int MAX_OFFSET_BLOCKS = 4;
    static final long MIN_IMBALANCE_TO_CORRECT = 10L;
    static final int LARGE_TERRAIN_STEP_BLOCKS = 8;

    private ProfileCutFillBalancer() {
    }

    static boolean containsLargeTerrainStep(List<Integer> groundSamples) {
        for (int i = 1; i < groundSamples.size(); i++) {
            if (Math.abs(groundSamples.get(i) - groundSamples.get(i - 1)) >= LARGE_TERRAIN_STEP_BLOCKS) {
                return true;
            }
        }
        return false;
    }

    static double findBalancingOffset(
            List<Integer> groundSamples,
            List<Double> designElevations,
            float cutToFillBalanceRatio) {
        int lo = -INITIAL_SEARCH_RADIUS;
        int hi = INITIAL_SEARCH_RADIUS;
        while (lo > -MAX_SEARCH_RADIUS
                && computeBalanceDiff(groundSamples, designElevations, lo, cutToFillBalanceRatio) < 0) {
            lo--;
        }
        while (hi < MAX_SEARCH_RADIUS
                && computeBalanceDiff(groundSamples, designElevations, hi, cutToFillBalanceRatio) > 0) {
            hi++;
        }
        lo = Math.max(lo, -MAX_SEARCH_RADIUS);
        hi = Math.min(hi, MAX_SEARCH_RADIUS);
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (computeBalanceDiff(groundSamples, designElevations, mid, cutToFillBalanceRatio) > 0) {
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
            float cutToFillBalanceRatio) {
        float ratio = cutToFillBalanceRatio > 0f ? cutToFillBalanceRatio : 1.0f;
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
        long supply = Math.round(cutVolume * ratio);
        return supply - fillVolume;
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
