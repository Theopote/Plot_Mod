package com.plot.plugin.earthwork.manager;

/**
 * 土方预览生命周期。
 * <ul>
 *   <li>{@link #NONE} — 无缓存结果</li>
 *   <li>{@link #VALID} — 可展示且可施工</li>
 *   <li>{@link #DIRTY} — 参数已变，旧图仅供参考，禁止施工</li>
 *   <li>{@link #CALCULATING} — 异步/延迟计算中，禁止施工</li>
 *   <li>{@link #FAILED} — 最近一次计算失败，禁止施工</li>
 * </ul>
 */
public enum EarthworkPreviewState {
    NONE,
    VALID,
    DIRTY,
    CALCULATING,
    FAILED;

    public boolean allowsBuild() {
        return this == VALID;
    }

    public boolean hasCachedResult() {
        return this == VALID || this == DIRTY || this == CALCULATING;
    }
}
