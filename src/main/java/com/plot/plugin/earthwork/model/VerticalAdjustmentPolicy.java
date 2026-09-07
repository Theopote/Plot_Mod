package com.plot.plugin.earthwork.model;

/**
 * 分区竖向调整策略：把「设计标高」和「土方优化变量」分开。
 * <p>
 * 调配偏移按求解结果取值；全场统一 ΔY 再乘 {@code weight}；最后夹紧到
 * {@code [minOffset, maxOffset]}。{@link Mode#LOCKED} / {@link Mode#DERIVED} 施加偏移恒为 0。
 */
public class VerticalAdjustmentPolicy {
    public static final int UNBOUNDED_RANGE = 32;
    /** Quick 模式默认自动平衡搜索半幅（±8）。 */
    public static final int QUICK_AUTO_BALANCE_RANGE = 8;
    /** Builder 模式自动平衡搜索半幅上限（±16）。 */
    public static final int BUILDER_AUTO_BALANCE_RANGE = 16;
    public static final int ROAD_BOUNDED_RANGE = 1;
    public static final int LANDSCAPE_RANGE = 3;
    public static final float DEFAULT_WEIGHT = 1.0f;
    public static final float LANDSCAPE_WEIGHT = 0.5f;

    public enum Mode {
        LOCKED,
        DERIVED,
        BOUNDED,
        ADJUSTABLE;

        public static Mode fromId(String id) {
            if (id == null || id.isBlank()) {
                return LOCKED;
            }
            try {
                return valueOf(id.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return LOCKED;
            }
        }

        public boolean allowsVerticalAdjustment() {
            return this == ADJUSTABLE || this == BOUNDED;
        }
    }

    private Mode mode = Mode.LOCKED;
    private int minOffset;
    private int maxOffset;
    private float weight = DEFAULT_WEIGHT;

    public VerticalAdjustmentPolicy() {
        this(Mode.LOCKED, 0, 0, DEFAULT_WEIGHT);
    }

    public VerticalAdjustmentPolicy(Mode mode, int minOffset, int maxOffset, float weight) {
        this.mode = mode != null ? mode : Mode.LOCKED;
        this.minOffset = minOffset;
        this.maxOffset = maxOffset;
        setWeight(weight);
        normalizeRange();
    }

    public static VerticalAdjustmentPolicy locked() {
        return new VerticalAdjustmentPolicy(Mode.LOCKED, 0, 0, DEFAULT_WEIGHT);
    }

    public static VerticalAdjustmentPolicy derived() {
        return new VerticalAdjustmentPolicy(Mode.DERIVED, 0, 0, DEFAULT_WEIGHT);
    }

    public static VerticalAdjustmentPolicy bounded(int range, float weight) {
        int safeRange = Math.max(0, range);
        return new VerticalAdjustmentPolicy(Mode.BOUNDED, -safeRange, safeRange, weight);
    }

    public static VerticalAdjustmentPolicy adjustable(int range, float weight) {
        int safeRange = Math.max(0, range);
        return new VerticalAdjustmentPolicy(Mode.ADJUSTABLE, -safeRange, safeRange, weight);
    }

    public static VerticalAdjustmentPolicy defaultFor(
            GradingZoneType type,
            boolean autoBalance,
            DesignSurfaceKind kind) {
        if (kind == DesignSurfaceKind.MATCH_EXISTING) {
            return locked();
        }
        GradingZoneType safeType = type != null ? type : GradingZoneType.FLAT;
        return switch (safeType) {
            case BUILDING_PAD -> locked();
            case EXCAVATION_PIT -> derived();
            case ROAD_CORRIDOR -> locked();
            case LANDSCAPE, TERRAIN_FIT -> adjustable(LANDSCAPE_RANGE, LANDSCAPE_WEIGHT);
            case FLAT, SLOPED -> autoBalance
                ? adjustable(QUICK_AUTO_BALANCE_RANGE, DEFAULT_WEIGHT)
                : locked();
        };
    }

    /** 按工作模式限制全场统一 ΔY 离散搜索半幅。 */
    public static int maxAutoBalanceHalfRange(EarthworkWorkMode workMode) {
        EarthworkWorkMode safeMode = workMode != null ? workMode : EarthworkWorkMode.QUICK;
        return switch (safeMode) {
            case QUICK -> QUICK_AUTO_BALANCE_RANGE;
            case BUILDER -> BUILDER_AUTO_BALANCE_RANGE;
            case LEARN -> UNBOUNDED_RANGE;
        };
    }

    public VerticalAdjustmentPolicy copy() {
        return new VerticalAdjustmentPolicy(mode, minOffset, maxOffset, weight);
    }

    public Mode getMode() {
        return mode != null ? mode : Mode.LOCKED;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.LOCKED;
    }

    public int getMinOffset() {
        return minOffset;
    }

    public void setMinOffset(int minOffset) {
        this.minOffset = minOffset;
        normalizeRange();
    }

    public int getMaxOffset() {
        return maxOffset;
    }

    public void setMaxOffset(int maxOffset) {
        this.maxOffset = maxOffset;
        normalizeRange();
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        if (Float.isNaN(weight) || Float.isInfinite(weight)) {
            this.weight = DEFAULT_WEIGHT;
            return;
        }
        this.weight = Math.max(0.0f, Math.min(2.0f, weight));
    }

    public boolean allowsVerticalAdjustment() {
        return getMode().allowsVerticalAdjustment();
    }

    /**
     * 调配 ΔY 全额计入；统一 ΔY 乘权重后再夹紧。
     */
    public int applyProposedOffset(int zoneAllocationOffset, int uniformOffset) {
        if (!allowsVerticalAdjustment()) {
            return 0;
        }
        int weightedUniform = Math.round(uniformOffset * getWeight());
        return clamp(zoneAllocationOffset + weightedUniform);
    }

    public int clamp(int offset) {
        if (!allowsVerticalAdjustment()) {
            return 0;
        }
        int lo = Math.min(minOffset, maxOffset);
        int hi = Math.max(minOffset, maxOffset);
        return Math.max(lo, Math.min(hi, offset));
    }

    private void normalizeRange() {
        if (minOffset <= maxOffset) {
            return;
        }
        int swap = minOffset;
        minOffset = maxOffset;
        maxOffset = swap;
    }
}
