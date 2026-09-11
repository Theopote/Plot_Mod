package com.plot.plugin.powerline.design;

/** 导线挂点绑定模式（v2）。 */
public enum AttachmentBindingMode {
    /** 相对横担归一化位置，横担几何变化时自动跟随。 */
    BOUND,
    /** 杆塔局部坐标系下的绝对偏移。 */
    FREE;

    public static AttachmentBindingMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
