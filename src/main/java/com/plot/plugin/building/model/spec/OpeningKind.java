package com.plot.plugin.building.model.spec;

/**
 * 立面开洞类型。
 */
public enum OpeningKind {
    /** 显式单窗（与窗型阵列 pattern 独立）。 */
    WINDOW,
    /** 门洞，底部贴楼层地面。 */
    DOOR,
    /** 拱洞/门洞变体，当前与 {@link #DOOR} 使用相同体素开洞逻辑。 */
    ARCH
}
