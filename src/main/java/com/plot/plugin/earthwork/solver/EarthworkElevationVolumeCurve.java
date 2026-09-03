package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把整块设计面按统一 ΔY 平移后，扫描各整数平台高度的挖、填、工作量。
 * <p>
 * 「挖填平衡」取 {@code |cut − fill|} 最小；「最少施工」取 {@code cut + fill} 最小。两者独立，不合成一个分数。
 */
public final class EarthworkElevationVolumeCurve {
    public static final EarthworkElevationVolumeCurve EMPTY = new EarthworkElevationVolumeCurve(
        List.of(), 0, 0, 0, 0, 0, 0, 0, 0);

    private static final int Y_PAD = 2;
    private static final int MAX_SAMPLES = 48;

    private final List<Sample> samples;
    private final int referenceY;
    private final int balanceY;
    private final int minWorkY;
    private final int existingMin;
    private final int existingMax;
    private final int existingMedian;
    private final int designMin;
    private final int designMax;

    public record Sample(int y, long cut, long fill) {
        public long work() {
            return cut + fill;
        }

        public long imbalance() {
            return Math.abs(cut - fill);
        }
    }

    public record Column(int existingY, int designY) {
    }

    private EarthworkElevationVolumeCurve(
            List<Sample> samples,
            int referenceY,
            int balanceY,
            int minWorkY,
            int existingMin,
            int existingMax,
            int existingMedian,
            int designMin,
            int designMax) {
        this.samples = List.copyOf(samples);
        this.referenceY = referenceY;
        this.balanceY = balanceY;
        this.minWorkY = minWorkY;
        this.existingMin = existingMin;
        this.existingMax = existingMax;
        this.existingMedian = existingMedian;
        this.designMin = designMin;
        this.designMax = designMax;
    }

    public static EarthworkElevationVolumeCurve fromGrid(DesignTerrainGrid grid, int referenceY) {
        if (grid == null || grid.cellCount() == 0) {
            return EMPTY;
        }
        List<Column> columns = new ArrayList<>();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            columns.add(new Column(cell.existingGroundY(), cell.targetY()));
        }
        return fromColumns(columns, referenceY);
    }

    public static EarthworkElevationVolumeCurve fromTerrain(TerrainSnapshot terrain, int referenceY) {
        if (terrain == null || terrain.isEmpty()) {
            return EMPTY;
        }
        List<Column> columns = new ArrayList<>();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            columns.add(new Column(column.groundY(), referenceY));
        }
        return fromColumns(columns, referenceY);
    }

    public static EarthworkElevationVolumeCurve fromColumns(List<Column> columns, int referenceY) {
        if (columns == null || columns.isEmpty()) {
            return EMPTY;
        }
        int existingMin = Integer.MAX_VALUE;
        int existingMax = Integer.MIN_VALUE;
        int designMin = Integer.MAX_VALUE;
        int designMax = Integer.MIN_VALUE;
        List<Integer> existingHeights = new ArrayList<>(columns.size());
        for (Column column : columns) {
            existingMin = Math.min(existingMin, column.existingY());
            existingMax = Math.max(existingMax, column.existingY());
            designMin = Math.min(designMin, column.designY());
            designMax = Math.max(designMax, column.designY());
            existingHeights.add(column.existingY());
        }
        Collections.sort(existingHeights);
        int existingMedian = existingHeights.get(existingHeights.size() / 2);

        int yMin = existingMin - Y_PAD;
        int yMax = existingMax + Y_PAD;
        int span = yMax - yMin;
        int step = 1;
        if (span > MAX_SAMPLES - 1) {
            step = (int) Math.ceil(span / (double) (MAX_SAMPLES - 1));
        }

        List<Sample> samples = new ArrayList<>();
        Sample bestBalance = null;
        Sample bestWork = null;
        for (int y = yMin; y <= yMax; y += step) {
            Sample sample = volumeAt(columns, referenceY, y);
            samples.add(sample);
            if (isBetterBalance(sample, bestBalance, referenceY)) {
                bestBalance = sample;
            }
            if (isBetterMinWork(sample, bestWork, referenceY)) {
                bestWork = sample;
            }
        }
        if (yMax >= yMin && (samples.isEmpty() || samples.get(samples.size() - 1).y() != yMax)) {
            Sample last = volumeAt(columns, referenceY, yMax);
            samples.add(last);
            if (isBetterBalance(last, bestBalance, referenceY)) {
                bestBalance = last;
            }
            if (isBetterMinWork(last, bestWork, referenceY)) {
                bestWork = last;
            }
        }
        if (bestBalance == null || bestWork == null) {
            return EMPTY;
        }
        return new EarthworkElevationVolumeCurve(
            samples,
            referenceY,
            bestBalance.y(),
            bestWork.y(),
            existingMin,
            existingMax,
            existingMedian,
            designMin,
            designMax);
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public List<Sample> samples() {
        return samples;
    }

    public int referenceY() {
        return referenceY;
    }

    public int balanceY() {
        return balanceY;
    }

    public int minWorkY() {
        return minWorkY;
    }

    public boolean optimaDiffer() {
        return !isEmpty() && balanceY != minWorkY;
    }

    public int existingMin() {
        return existingMin;
    }

    public int existingMax() {
        return existingMax;
    }

    public int existingMedian() {
        return existingMedian;
    }

    public int designMin() {
        return designMin;
    }

    public int designMax() {
        return designMax;
    }

    public Sample sampleAt(int y) {
        Sample nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (Sample sample : samples) {
            int dist = Math.abs(sample.y() - y);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = sample;
            }
        }
        return nearest;
    }

    public long maxWork() {
        long max = 1L;
        for (Sample sample : samples) {
            max = Math.max(max, sample.work());
        }
        return max;
    }

    static Sample volumeAt(List<Column> columns, int referenceY, int candidateY) {
        int shift = candidateY - referenceY;
        long cut = 0L;
        long fill = 0L;
        for (Column column : columns) {
            int designY = column.designY() + shift;
            int delta = designY - column.existingY();
            if (delta > 0) {
                fill += delta;
            } else {
                cut -= delta;
            }
        }
        return new Sample(candidateY, cut, fill);
    }

    static boolean isBetterBalance(Sample candidate, Sample incumbent, int referenceY) {
        if (candidate == null) {
            return false;
        }
        if (incumbent == null) {
            return true;
        }
        if (candidate.imbalance() != incumbent.imbalance()) {
            return candidate.imbalance() < incumbent.imbalance();
        }
        if (candidate.work() != incumbent.work()) {
            return candidate.work() < incumbent.work();
        }
        int candDist = Math.abs(candidate.y() - referenceY);
        int incDist = Math.abs(incumbent.y() - referenceY);
        if (candDist != incDist) {
            return candDist < incDist;
        }
        return candidate.y() < incumbent.y();
    }

    static boolean isBetterMinWork(Sample candidate, Sample incumbent, int referenceY) {
        if (candidate == null) {
            return false;
        }
        if (incumbent == null) {
            return true;
        }
        if (candidate.work() != incumbent.work()) {
            return candidate.work() < incumbent.work();
        }
        if (candidate.imbalance() != incumbent.imbalance()) {
            return candidate.imbalance() < incumbent.imbalance();
        }
        int candDist = Math.abs(candidate.y() - referenceY);
        int incDist = Math.abs(incumbent.y() - referenceY);
        if (candDist != incDist) {
            return candDist < incDist;
        }
        return candidate.y() < incumbent.y();
    }
}
