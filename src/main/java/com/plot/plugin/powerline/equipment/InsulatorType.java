package com.plot.plugin.powerline.equipment;

/** 绝缘子类型（Phase 3.3 简化工程语义）。 */
public enum InsulatorType {
    SUSPENSION,
    STRAIN,
    VERTICAL;

    public static InsulatorType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUSPENSION;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SUSPENSION;
        }
    }
}
