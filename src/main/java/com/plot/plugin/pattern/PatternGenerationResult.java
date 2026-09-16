package com.plot.plugin.pattern;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 铺装图案生成结果。
 */
public class PatternGenerationResult {
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();

    private PatternGenerationIssue issue = PatternGenerationIssue.NONE;
    private int sampleCount;
    private int skippedTransparentCount;
    private int fallbackElevationCount;
    private int skippedOverlapCount;

    public boolean hasPlacements() {
        return !placementRecords.isEmpty();
    }

    public int getBlockCount() {
        return placementRecords.size();
    }

    public PatternGenerationIssue getIssue() {
        return issue;
    }

    public void setIssue(PatternGenerationIssue issue) {
        this.issue = issue != null ? issue : PatternGenerationIssue.NONE;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = Math.max(0, sampleCount);
    }

    public int getSkippedTransparentCount() {
        return skippedTransparentCount;
    }

    public void setSkippedTransparentCount(int skippedTransparentCount) {
        this.skippedTransparentCount = Math.max(0, skippedTransparentCount);
    }

    public int getFallbackElevationCount() {
        return fallbackElevationCount;
    }

    public void setFallbackElevationCount(int fallbackElevationCount) {
        this.fallbackElevationCount = Math.max(0, fallbackElevationCount);
    }

    public int getSkippedOverlapCount() {
        return skippedOverlapCount;
    }

    public void setSkippedOverlapCount(int skippedOverlapCount) {
        this.skippedOverlapCount = Math.max(0, skippedOverlapCount);
    }

    public void mergeFrom(PatternGenerationResult other) {
        if (other == null) {
            return;
        }
        for (Map.Entry<BlockPos, BlockRecord> entry : other.placementRecords.entrySet()) {
            if (placementRecords.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                skippedOverlapCount++;
            }
        }
        sampleCount += other.sampleCount;
        skippedTransparentCount += other.skippedTransparentCount;
        fallbackElevationCount += other.fallbackElevationCount;
        skippedOverlapCount += other.skippedOverlapCount;
        if (issue == PatternGenerationIssue.NONE && other.issue != PatternGenerationIssue.NONE) {
            issue = other.issue;
        }
    }

    public boolean exceedsFallbackBuildThreshold() {
        if (fallbackElevationCount <= 0 || getBlockCount() == 0) {
            return false;
        }
        return fallbackElevationCount >= 10
            || fallbackElevationCount > getBlockCount() * 0.05;
    }
}
