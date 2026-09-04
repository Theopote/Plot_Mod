package com.plot.plugin.building.generation.resolve;

/**
 * 地基标高来源（Building 侧最终选用谁）。
 * <p>
 * 与土方 {@code PadElevationMode} 正交：土方可 EARTHWORK_OWNED / BUILDING_LINKED；
 * Building 生成只消费「已解析」的垫层值，不再与 requested 混为一个数。
 */
public enum FoundationElevationSource {
    /** 建筑手动 ±0。 */
    MANUAL,
    /** 土方垫层设计标高（EARTHWORK_OWNED）。 */
    EARTHWORK_PAD,
    /** 地形采样众数。 */
    TERRAIN
}
