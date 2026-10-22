package com.plot.plugin.road.model;

/** 节点手动标高的来源，用于区分用户锁定与编辑传播产生的临时约束。 */
public enum ManualElevationOrigin {
    /** 用户在 UI 中显式设置的平交/端点标高。 */
    USER,
    /** 交叉口注册表 sharedElevation 物化写入。 */
    CROSSING,
    /** 纵断面编辑或求解过程中自动传播到相邻节点。 */
    PROFILE_PROPAGATED,
    /** 立体交叉节点标高。 */
    GRADE_SEPARATED;

    public static ManualElevationOrigin fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
