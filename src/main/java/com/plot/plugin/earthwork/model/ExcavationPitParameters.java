package com.plot.plugin.earthwork.model;

/**
 * 基坑竖向参数：把「地下室楼面深度」与「结构厚度 / 施工超挖」分开，
 * 避免单一 {@code basementDepth} 同时兼任基坑开挖深度。
 * <p>
 * {@code pitBottom = referenceElevation - basementFloorDepth - structuralAllowance()}
 * 其中 {@code structuralAllowance = foundationDepth + workingAllowance}。
 * <p>
 * 注意：水平工作面宽度仍由 {@link DesignSurface#getWorkingMarginBlocks()} 控制，与本类竖向
 * {@code workingAllowance} 不同。
 */
public class ExcavationPitParameters {
    public static final int DEFAULT_BASEMENT_FLOOR_DEPTH = 3;

    private int basementFloorDepth = DEFAULT_BASEMENT_FLOOR_DEPTH;
    private int foundationDepth;
    private int workingAllowance;

    public ExcavationPitParameters() {
    }

    public ExcavationPitParameters(int basementFloorDepth, int foundationDepth, int workingAllowance) {
        setBasementFloorDepth(basementFloorDepth);
        setFoundationDepth(foundationDepth);
        setWorkingAllowance(workingAllowance);
    }

    public static ExcavationPitParameters fromLegacyBasementDepth(int basementDepthBlocks) {
        return new ExcavationPitParameters(Math.max(0, basementDepthBlocks), 0, 0);
    }

    public ExcavationPitParameters copy() {
        return new ExcavationPitParameters(basementFloorDepth, foundationDepth, workingAllowance);
    }

    /** 地下室楼面相对基准标高（±0.000 / 基础底）的深度（格）。 */
    public int getBasementFloorDepth() {
        return Math.max(0, basementFloorDepth);
    }

    public void setBasementFloorDepth(int basementFloorDepth) {
        this.basementFloorDepth = clampDepth(basementFloorDepth);
    }

    /** 底板 + 垫层 + 基础等结构厚度（格）。 */
    public int getFoundationDepth() {
        return Math.max(0, foundationDepth);
    }

    public void setFoundationDepth(int foundationDepth) {
        this.foundationDepth = clampDepth(foundationDepth);
    }

    /** 坑底竖向施工超挖 / 工作面（格），与水平 workingMargin 不同。 */
    public int getWorkingAllowance() {
        return Math.max(0, workingAllowance);
    }

    public void setWorkingAllowance(int workingAllowance) {
        this.workingAllowance = clampDepth(workingAllowance);
    }

    /** 结构厚度 + 竖向施工余量。 */
    public int structuralAllowance() {
        return getFoundationDepth() + getWorkingAllowance();
    }

    public int totalExcavationDepth() {
        return getBasementFloorDepth() + structuralAllowance();
    }

    public int pitBottomFrom(int referenceElevation) {
        return referenceElevation - totalExcavationDepth();
    }

    private static int clampDepth(int value) {
        return Math.max(0, Math.min(64, value));
    }
}
