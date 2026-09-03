package com.plot.plugin.earthwork.model;

/**
 * Site 级 Design Terrain 合成策略。
 * <p>
 * {@link BalanceScope} 只定义土方<strong>统计范围</strong>；{@link OptimizationMode} 定义是否/如何
 * <strong>改设计标高</strong>。二者正交：
 * <ul>
 *   <li>{@code SITE + NONE}：看全场净土方，不改设计</li>
 *   <li>{@code SITE + UNIFORM_VERTICAL_SHIFT}：可调区统一 ΔY</li>
 *   <li>{@code SITE + CONSTRAINED_ZONE_OPTIMIZATION}：约束下分区优化以外运/外借</li>
 * </ul>
 * 调配矩阵（Mode A）始终由既定设计面方量计算，与 OptimizationMode 无关。
 */
public class CompositionPolicy {
    public static final String OVERLAP_HIGHEST_PRIORITY_WINS = "HIGHEST_PRIORITY_WINS";
    public static final String OVERLAP_LARGEST_ZONE_WINS = "LARGEST_ZONE_WINS";

    public static final String BALANCE_SCOPE_ZONE = BalanceScope.ZONE.name();
    public static final String BALANCE_SCOPE_SITE = BalanceScope.SITE.name();
    public static final String BALANCE_SCOPE_PROJECT = BalanceScope.PROJECT.name();
    /** @deprecated 使用 {@link #BALANCE_SCOPE_ZONE} / {@link BalanceScope#ZONE} */
    @Deprecated
    public static final String BALANCE_SCOPE_PER_ZONE = BALANCE_SCOPE_ZONE;
    /** @deprecated 使用 {@link #BALANCE_SCOPE_SITE} / {@link BalanceScope#SITE}；旧名易被误解为「全场平移」。 */
    @Deprecated
    public static final String BALANCE_SCOPE_SITE_WIDE = BALANCE_SCOPE_SITE;

    public static final String OPTIMIZATION_MODE_NONE = OptimizationMode.NONE.name();
    public static final String OPTIMIZATION_MODE_UNIFORM_VERTICAL_SHIFT =
        OptimizationMode.UNIFORM_VERTICAL_SHIFT.name();
    public static final String OPTIMIZATION_MODE_CONSTRAINED_ZONE =
        OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION.name();

    /** @deprecated 使用 {@link #OPTIMIZATION_MODE_NONE} */
    @Deprecated
    public static final String BALANCE_METHOD_NONE = OPTIMIZATION_MODE_NONE;
    /** @deprecated 使用 {@link #OPTIMIZATION_MODE_UNIFORM_VERTICAL_SHIFT} */
    @Deprecated
    public static final String BALANCE_METHOD_UNIFORM = OPTIMIZATION_MODE_UNIFORM_VERTICAL_SHIFT;
    /** @deprecated 使用 {@link #OPTIMIZATION_MODE_CONSTRAINED_ZONE} */
    @Deprecated
    public static final String BALANCE_METHOD_EARTHWORK_OPTIMIZATION = OPTIMIZATION_MODE_CONSTRAINED_ZONE;
    /**
     * @deprecated 旧名；语义等同 {@link #OPTIMIZATION_MODE_CONSTRAINED_ZONE}。
     */
    @Deprecated
    public static final String BALANCE_METHOD_ZONE_ALLOCATION = OPTIMIZATION_MODE_CONSTRAINED_ZONE;

    public static final String OUTSIDE_IGNORE = "IGNORE";
    public static final String PRECEDENCE_ABSOLUTE = "ABSOLUTE";

    public static final CompositionPolicy DEFAULT = new CompositionPolicy();

    private String overlapResolution = OVERLAP_HIGHEST_PRIORITY_WINS;
    private BalanceScope balanceScope = BalanceScope.SITE;
    private OptimizationMode optimizationMode = OptimizationMode.NONE;
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

    public BalanceScope getBalanceScopeEnum() {
        return balanceScope != null ? balanceScope : BalanceScope.SITE;
    }

    /** 规范线名：{@code ZONE} / {@code SITE} / {@code PROJECT}。 */
    public String getBalanceScope() {
        return getBalanceScopeEnum().name();
    }

    public void setBalanceScope(String balanceScope) {
        this.balanceScope = BalanceScope.fromId(balanceScope);
    }

    public void setBalanceScope(BalanceScope balanceScope) {
        this.balanceScope = balanceScope != null ? balanceScope : BalanceScope.SITE;
    }

    /** 统计范围是否为场地级（含旧 {@code SITE_WIDE} 语义）。 */
    public boolean isSiteBalanceScope() {
        return getBalanceScopeEnum() == BalanceScope.SITE;
    }

    /**
     * @deprecated 名称易误解为「全场平移」；使用 {@link #isSiteBalanceScope()} 或
     * {@link #getBalanceScopeEnum()}{@code .defersPerZoneBalance()}。
     */
    @Deprecated
    public boolean isSiteWideBalance() {
        return getBalanceScopeEnum().defersPerZoneBalance();
    }

    public boolean isZoneBalanceScope() {
        return getBalanceScopeEnum() == BalanceScope.ZONE;
    }

    public boolean isProjectBalanceScope() {
        return getBalanceScopeEnum() == BalanceScope.PROJECT;
    }

    public OptimizationMode getOptimizationModeEnum() {
        return optimizationMode != null ? optimizationMode : OptimizationMode.NONE;
    }

    /** 规范线名：{@code NONE} / {@code UNIFORM_VERTICAL_SHIFT} / {@code CONSTRAINED_ZONE_OPTIMIZATION}。 */
    public String getOptimizationMode() {
        return getOptimizationModeEnum().name();
    }

    public void setOptimizationMode(String optimizationMode) {
        this.optimizationMode = OptimizationMode.fromId(optimizationMode);
    }

    public void setOptimizationMode(OptimizationMode optimizationMode) {
        this.optimizationMode = optimizationMode != null ? optimizationMode : OptimizationMode.NONE;
    }

    /**
     * @deprecated 使用 {@link #getOptimizationMode()}；保留以兼容旧 JSON / API。
     */
    @Deprecated
    public String getBalanceMethod() {
        return getOptimizationMode();
    }

    /**
     * @deprecated 使用 {@link #setOptimizationMode(String)}
     */
    @Deprecated
    public void setBalanceMethod(String balanceMethod) {
        setOptimizationMode(balanceMethod);
    }

    public boolean isNoneOptimization() {
        return getOptimizationModeEnum() == OptimizationMode.NONE;
    }

    /**
     * @deprecated 使用 {@link #isNoneOptimization()}
     */
    @Deprecated
    public boolean isNoneBalance() {
        return isNoneOptimization();
    }

    public boolean isUniformVerticalShift() {
        return getOptimizationModeEnum().isUniformVerticalShift();
    }

    /**
     * @deprecated 使用 {@link #isUniformVerticalShift()}
     */
    @Deprecated
    public boolean isUniformOffsetBalance() {
        return isUniformVerticalShift();
    }

    public boolean isConstrainedZoneOptimization() {
        return getOptimizationModeEnum().isConstrainedZoneOptimization();
    }

    /**
     * @deprecated 使用 {@link #isConstrainedZoneOptimization()}
     */
    @Deprecated
    public boolean isEarthworkOptimization() {
        return isConstrainedZoneOptimization();
    }

    /**
     * @deprecated 使用 {@link #isConstrainedZoneOptimization()}
     */
    @Deprecated
    public boolean isZoneAllocationBalance() {
        return isConstrainedZoneOptimization();
    }

    /**
     * 是否在合成后运行竖向优化：统计范围为 SITE/PROJECT，且 OptimizationMode 会改设计。
     */
    public boolean isVerticalOptimizationEnabled() {
        return getBalanceScopeEnum().allowsSiteVerticalOptimization()
            && getOptimizationModeEnum().modifiesDesign();
    }

    /**
     * @deprecated 使用 {@link #isVerticalOptimizationEnabled()}
     */
    @Deprecated
    public boolean isSiteBalanceOptimizationEnabled() {
        return isVerticalOptimizationEnabled();
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
