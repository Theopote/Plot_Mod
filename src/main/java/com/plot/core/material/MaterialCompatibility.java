package com.plot.core.material;

/**
 * 挖方 spoil → 填方需求的材料兼容性。
 */
public enum MaterialCompatibility {
    /** 可直接调配 */
    ALLOWED,
    /** 有条件可用（MVP 仍参与调配，报告中标注） */
    CONDITIONAL,
    /** 禁止调配（余方外运 / 缺方外借） */
    FORBIDDEN;

    public boolean allowsTransfer() {
        return this == ALLOWED || this == CONDITIONAL;
    }
}
