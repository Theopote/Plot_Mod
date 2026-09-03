package com.plot.plugin.earthwork.model;

/**
 * Site 级 Design Terrain 合成策略。
 * <p>
 * 全场「平衡方法」只控制 Mode B（是否优化竖向设计），与 Mode A 土方调配报告无关。
 * 调配矩阵始终由既定设计面方量计算，不修改标高。
 */
public class CompositionPolicy {
    public static final String OVERLAP_HIGHEST_PRIORITY_WINS = "HIGHEST_PRIORITY_WINS";
    public static final String OVERLAP_LARGEST_ZONE_WINS = "LARGEST_ZONE_WINS";
    public static final String BALANCE_SCOPE_PER_ZONE = "PER_ZONE";
    public static final String BALANCE_SCOPE_SITE_WIDE = "SITE_WIDE";
    /** Mode A 默认：设计面不变，仅报告调配。 */
    public static final String BALANCE_METHOD_NONE = "NONE";
    public static final String BALANCE_METHOD_UNIFORM = "UNIFORM_OFFSET";
    /** Mode B：在 VerticalAdjustmentPolicy 约束下优化可调分区标高。 */
    public static final String BALANCE_METHOD_EARTHWORK_OPTIMIZATION = "EARTHWORK_OPTIMIZATION";
    /**
     * @deprecated 旧名；语义等同 {@link #BALANCE_METHOD_EARTHWORK_OPTIMIZATION}。
     * 调配矩阵本身不再隐含改标高。
     */
    @Deprecated
    public static final String BALANCE_METHOD_ZONE_ALLOCATION = "ZONE_ALLOCATION";
    public static final String OUTSIDE_IGNORE = "IGNORE";
    public static final String PRECEDENCE_ABSOLUTE = "ABSOLUTE";

    public static final CompositionPolicy DEFAULT = new CompositionPolicy();

    private String overlapResolution = OVERLAP_HIGHEST_PRIORITY_WINS;
    private String balanceScope = BALANCE_SCOPE_SITE_WIDE;
    private String balanceMethod = BALANCE_METHOD_NONE;
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
        return balanceMethod != null ? balanceMethod : BALANCE_METHOD_NONE;
    }

    public void setBalanceMethod(String balanceMethod) {
        this.balanceMethod = balanceMethod;
    }

    public boolean isNoneBalance() {
        return BALANCE_METHOD_NONE.equals(getBalanceMethod());
    }

    public boolean isUniformOffsetBalance() {
        return BALANCE_METHOD_UNIFORM.equals(getBalanceMethod());
    }

    /** Mode B：分区差异化竖向优化（含旧 {@code ZONE_ALLOCATION}）。 */
    public boolean isEarthworkOptimization() {
        String method = getBalanceMethod();
        return BALANCE_METHOD_EARTHWORK_OPTIMIZATION.equals(method)
            || BALANCE_METHOD_ZONE_ALLOCATION.equals(method);
    }

    /**
     * @deprecated 使用 {@link #isEarthworkOptimization()}
     */
    @Deprecated
    public boolean isZoneAllocationBalance() {
        return isEarthworkOptimization();
    }

    public boolean isSiteBalanceOptimizationEnabled() {
        return isSiteWideBalance() && !isNoneBalance();
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
