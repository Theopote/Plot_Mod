package com.plot.plugin.building.model.spec;

/**
 * 立面边索引的语义作用域。
 * <p>
 * {@link #BASE_FOOTPRINT}（默认）：{@code wallSegmentIndex} 相对建筑基础轮廓；
 * FloorPlate 拓扑变化时通过方向继承映射到当前层边。
 * {@link #FLOOR_LOCAL}：索引相对当前层 FloorPlate 外轮廓（拓扑变化时不稳定）。
 */
public enum FacadeEdgeScope {
    BASE_FOOTPRINT,
    FLOOR_LOCAL
}
