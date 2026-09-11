package com.plot.plugin.powerline.style;

/** 风格卡片缩略图上的装饰叠加层（不替代主体结构）。 */
public enum PreviewOverlay {
    NONE,
    /** 挂点 + 绝缘子 + 短导线暗示（大型塔）。 */
    ATTACHMENTS,
    /** 体素杆上的短导线 / 相线暗示（木杆、街灯等）。 */
    DECORATIVE_CONDUCTORS,
    /** 废土风电桨叶暗示。 */
    WIND_ROTOR,
    /** 智能分级塔高度刻度。 */
    ADAPTIVE_MARKER
}
