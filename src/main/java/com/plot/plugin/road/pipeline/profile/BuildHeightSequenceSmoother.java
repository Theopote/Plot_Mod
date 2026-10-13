package com.plot.plugin.road.pipeline.profile;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Removes short-distance integer build-height reversals when the continuous design line
 * does not require a local crest or sag. Handles single-block spikes and short plateaus.
 */
public final class BuildHeightSequenceSmoother {

    private static final double EPSILON = 1e-9;
    private static final double DESIGN_FLAT_TOLERANCE = 0.55;

    private BuildHeightSequenceSmoother() {
    }

    public record SmoothingDiagnostics(
            int shortReversalCountBefore,
            int shortReversalCountAfter,
            int adjustmentCount) {
    }

    public record SmoothingResult(
            List<BuildHeightSample> samples,
            SmoothingDiagnostics diagnostics) {
    }

    public static SmoothingResult smooth(
            List<BuildHeightSample> samples,
            ProfileCompileOptions options,
            BitSet lockedSampleIndices) {
        if (samples == null || samples.size() < 3) {
            return new SmoothingResult(
                samples != null ? List.copyOf(samples) : List.of(),
                new SmoothingDiagnostics(0, 0, 0));
        }
        int n = samples.size();
        int[] buildY = new int[n];
        double[] design = new double[n];
        double[] station = new double[n];
        for (int i = 0; i < n; i++) {
            BuildHeightSample sample = samples.get(i);
            buildY[i] = sample.buildY();
            design[i] = sample.designElevation();
            station[i] = sample.station();
        }
        BitSet locked = lockedSampleIndices != null
            ? (BitSet) lockedSampleIndices.clone()
            : new BitSet();
        locked.set(0);
        locked.set(n - 1);

        int before = countShortReversals(buildY);
        int minRun = options != null ? options.minEffectiveRunBlocks() : 12;
        int adjustments = 0;

        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < n * 4) {
            changed = false;
            int i = 1;
            while (i < n - 1) {
                int runStart = i;
                int height = buildY[i];
                int runEnd = i;
                while (runEnd + 1 < n && buildY[runEnd + 1] == height) {
                    runEnd++;
                }
                boolean canEditRun = true;
                for (int j = runStart; j <= runEnd; j++) {
                    if (locked.get(j)) {
                        canEditRun = false;
                        break;
                    }
                }
                if (canEditRun && runStart > 0 && runEnd < n - 1) {
                    int left = buildY[runStart - 1];
                    int right = buildY[runEnd + 1];
                    double span = station[runEnd + 1] - station[runStart - 1];
                    if (span <= minRun + EPSILON) {
                        if (height > left && height > right
                                && designSupportsFlattening(design, station, runStart, runEnd, minRun)) {
                            int target = Math.min(left, right);
                            if (target != height) {
                                for (int j = runStart; j <= runEnd; j++) {
                                    buildY[j] = target;
                                }
                                adjustments += runEnd - runStart + 1;
                                changed = true;
                            }
                        } else if (height < left && height < right
                                && designSupportsFlattening(design, station, runStart, runEnd, minRun)) {
                            int target = Math.max(left, right);
                            if (target != height) {
                                for (int j = runStart; j <= runEnd; j++) {
                                    buildY[j] = target;
                                }
                                adjustments += runEnd - runStart + 1;
                                changed = true;
                            }
                        }
                    }
                }
                i = Math.max(runEnd + 1, i + 1);
            }
        }

        int after = countShortReversals(buildY);
        List<BuildHeightSample> smoothed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            smoothed.add(new BuildHeightSample(station[i], design[i], buildY[i]));
        }
        return new SmoothingResult(
            List.copyOf(smoothed),
            new SmoothingDiagnostics(before, after, adjustments));
    }

    static int countShortReversals(int[] buildY) {
        if (buildY == null || buildY.length < 3) {
            return 0;
        }
        int count = 0;
        int i = 1;
        while (i < buildY.length - 1) {
            int runStart = i;
            int height = buildY[i];
            int runEnd = i;
            while (runEnd + 1 < buildY.length && buildY[runEnd + 1] == height) {
                runEnd++;
            }
            if (runStart > 0 && runEnd < buildY.length - 1) {
                int left = buildY[runStart - 1];
                int right = buildY[runEnd + 1];
                if ((height > left && height > right) || (height < left && height < right)) {
                    count++;
                }
            }
            i = Math.max(runEnd + 1, i + 1);
        }
        return count;
    }

    private static boolean designSupportsFlattening(
            double[] design,
            double[] station,
            int runStart,
            int runEnd,
            int minRunBlocks) {
        if (runStart <= 0 || runEnd >= design.length - 1) {
            return false;
        }
        // Inspect design over roughly the same distance used for the short-reversal window so
        // a real crest/sag (significant elevation change across tens of blocks) is preserved,
        // while ±1 voxel noise on an otherwise flat design is removed.
        double center = 0.5 * (station[runStart] + station[runEnd]);
        double halfWindow = Math.max(1.0, minRunBlocks * 0.5);
        int left = runStart;
        while (left > 0 && center - station[left - 1] <= halfWindow + EPSILON) {
            left--;
        }
        int right = runEnd;
        while (right + 1 < design.length && station[right + 1] - center <= halfWindow + EPSILON) {
            right++;
        }
        double minDesign = design[left];
        double maxDesign = design[left];
        for (int i = left; i <= right; i++) {
            minDesign = Math.min(minDesign, design[i]);
            maxDesign = Math.max(maxDesign, design[i]);
        }
        return (maxDesign - minDesign) <= DESIGN_FLAT_TOLERANCE;
    }
}
