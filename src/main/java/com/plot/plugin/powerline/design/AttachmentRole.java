package com.plot.plugin.powerline.design;

/**
 * 导线挂点角色（不含电压/负荷等电气工程语义）。
 * <p>
 * {@link #TOP_WIRE} 表示塔顶架空装饰线，增强轮廓与工业感，不是接地到地下的电气接地系统。
 */
public enum AttachmentRole {
    PHASE_A,
    PHASE_B,
    PHASE_C,
    NEUTRAL,
    /** 塔顶架空装饰线（视觉）。 */
    TOP_WIRE,
    AUXILIARY;

    /** 解析 JSON / 旧数据中的角色名；兼容 {@code GROUND_WIRE}。 */
    public static AttachmentRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return PHASE_A;
        }
        if ("GROUND_WIRE".equals(raw)) {
            return TOP_WIRE;
        }
        return valueOf(raw);
    }
}
