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

    public static final String OPTIMIZATION_MODE_NONE = OptimizationMode.NONE.name();
    public static final String OPTIMIZATION_MODE_UNIFORM_VERTICAL_SHIFT =
        OptimizationMode.UNIFORM_VERTICAL_SHIFT.name();
    public static final String OPTIMIZATION_MODE_CONSTRAINED_ZONE =
        OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION.name();

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

    /** 统计范围是否为场地级。 */
    public boolean isSiteBalanceScope() {
        return getBalanceScopeEnum() == BalanceScope.SITE;
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

    public boolean isNoneOptimization() {
        return getOptimizationModeEnum() == OptimizationMode.NONE;
    }

    public boolean isUniformVerticalShift() {
        return getOptimizationModeEnum().isUniformVerticalShift();
    }

    public boolean isConstrainedZoneOptimization() {
        return getOptimizationModeEnum().isConstrainedZoneOptimization();
    }

    /**
     * 是否在合成后运行竖向优化：统计范围为 SITE/PROJECT，且 OptimizationMode 会改设计。
     */
    public boolean isVerticalOptimizationEnabled() {
        return getBalanceScopeEnum().allowsSiteVerticalOptimization()
            && getOptimizationModeEnum().modifiesDesign();
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

    public CompositionPolicy copy() {
        CompositionPolicy copy = new CompositionPolicy();
        copy.overlapResolution = this.overlapResolution;
        copy.balanceScope = this.balanceScope;
        copy.optimizationMode = this.optimizationMode;
        copy.balanceResidualUniformPolish = this.balanceResidualUniformPolish;
        copy.outsideSiteBoundary = this.outsideSiteBoundary;
        copy.exclusionPrecedence = this.exclusionPrecedence;
        copy.breaklinePrecedence = this.breaklinePrecedence;
        copy.blendWidthBlocks = this.blendWidthBlocks;
        return copy;
    }

    /**
     * 按工作模式裁剪高级合成选项（不修改原实例）。
     * <p>
     * 仅用于快速平整生成管线的临时策略：Quick 不含 PROJECT 统计与约束分区优化。
     * 建造模式保留完整设置。切换工作模式<strong>不应</strong>把裁剪结果写回项目。
     */
    public CompositionPolicy clampedCopyForWorkMode(EarthworkWorkMode workMode) {
        CompositionPolicy copy = copy();
        EarthworkWorkMode safeMode = workMode != null ? workMode : EarthworkWorkMode.QUICK;
        if (!safeMode.allowsProjectBalanceScope() && copy.getBalanceScopeEnum() == BalanceScope.PROJECT) {
            copy.setBalanceScope(BalanceScope.SITE);
        }
        if (!safeMode.allowsConstrainedZoneOptimization()
            && copy.getOptimizationModeEnum() == OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION) {
            copy.setOptimizationMode(OptimizationMode.NONE);
        }
        return copy;
    }

    /** 高级合成设置：跨场地统计或约束分区优化（建造模式可用）。 */
    public boolean usesAdvancedCompositionSettings() {
        return getBalanceScopeEnum() == BalanceScope.PROJECT
            || getOptimizationModeEnum() == OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION;
    }
}
