package com.plot.plugin.earthwork.model;

/**
 * Site 级 Design Terrain 合成策略。
 */
public class CompositionPolicy {
    public static final String OVERLAP_HIGHEST_PRIORITY_WINS = "HIGHEST_PRIORITY_WINS";
    public static final String OVERLAP_LARGEST_ZONE_WINS = "LARGEST_ZONE_WINS";
    public static final String BALANCE_SCOPE_PER_ZONE = "PER_ZONE";
    public static final String BALANCE_SCOPE_SITE_WIDE = "SITE_WIDE";
    public static final String BALANCE_METHOD_UNIFORM = "UNIFORM_OFFSET";
    public static final String BALANCE_METHOD_ZONE_ALLOCATION = "ZONE_ALLOCATION";
    public static final String OUTSIDE_IGNORE = "IGNORE";
    public static final String PRECEDENCE_ABSOLUTE = "ABSOLUTE";

    public static final CompositionPolicy DEFAULT = new CompositionPolicy();

    private String overlapResolution = OVERLAP_HIGHEST_PRIORITY_WINS;
    private String balanceScope = BALANCE_SCOPE_SITE_WIDE;
    private String balanceMethod = BALANCE_METHOD_ZONE_ALLOCATION;
    private boolean balanceResidualUniformPolish = true;
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

    public String getBalanceScope() {
        return balanceScope != null ? balanceScope : BALANCE_SCOPE_SITE_WIDE;
    }

    public void setBalanceScope(String balanceScope) {
        this.balanceScope = balanceScope;
    }

    public boolean isSiteWideBalance() {
        return BALANCE_SCOPE_SITE_WIDE.equals(getBalanceScope());
    }

    public String getBalanceMethod() {
        return balanceMethod != null ? balanceMethod : BALANCE_METHOD_ZONE_ALLOCATION;
    }

    public void setBalanceMethod(String balanceMethod) {
        this.balanceMethod = balanceMethod;
    }

    public boolean isZoneAllocationBalance() {
        return BALANCE_METHOD_ZONE_ALLOCATION.equals(getBalanceMethod());
    }

    public boolean isBalanceResidualUniformPolish() {
        return balanceResidualUniformPolish;
    }

    public void setBalanceResidualUniformPolish(boolean balanceResidualUniformPolish) {
        this.balanceResidualUniformPolish = balanceResidualUniformPolish;
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
