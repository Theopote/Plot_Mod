package com.plot.plugin.earthwork.model;

/**
 * Site 级 Design Terrain 合成策略。
 */
public class CompositionPolicy {
    public static final String OVERLAP_HIGHEST_PRIORITY_WINS = "HIGHEST_PRIORITY_WINS";
    public static final String OUTSIDE_IGNORE = "IGNORE";
    public static final String PRECEDENCE_ABSOLUTE = "ABSOLUTE";

    public static final CompositionPolicy DEFAULT = new CompositionPolicy();

    private String overlapResolution = OVERLAP_HIGHEST_PRIORITY_WINS;
    private String outsideSiteBoundary = OUTSIDE_IGNORE;
    private String exclusionPrecedence = PRECEDENCE_ABSOLUTE;
    private String breaklinePrecedence = PRECEDENCE_ABSOLUTE;
    private int blendWidthBlocks;

    public String getOverlapResolution() {
        return overlapResolution != null ? overlapResolution : OVERLAP_HIGHEST_PRIORITY_WINS;
    }

    public void setOverlapResolution(String overlapResolution) {
        this.overlapResolution = overlapResolution;
    }

    public String getOutsideSiteBoundary() {
        return outsideSiteBoundary != null ? outsideSiteBoundary : OUTSIDE_IGNORE;
    }

    public void setOutsideSiteBoundary(String outsideSiteBoundary) {
        this.outsideSiteBoundary = outsideSiteBoundary;
    }

    public String getExclusionPrecedence() {
        return exclusionPrecedence != null ? exclusionPrecedence : PRECEDENCE_ABSOLUTE;
    }

    public void setExclusionPrecedence(String exclusionPrecedence) {
        this.exclusionPrecedence = exclusionPrecedence;
    }

    public String getBreaklinePrecedence() {
        return breaklinePrecedence != null ? breaklinePrecedence : PRECEDENCE_ABSOLUTE;
    }

    public void setBreaklinePrecedence(String breaklinePrecedence) {
        this.breaklinePrecedence = breaklinePrecedence;
    }

    public int getBlendWidthBlocks() {
        return blendWidthBlocks;
    }

    public void setBlendWidthBlocks(int blendWidthBlocks) {
        this.blendWidthBlocks = Math.max(0, blendWidthBlocks);
    }
}
