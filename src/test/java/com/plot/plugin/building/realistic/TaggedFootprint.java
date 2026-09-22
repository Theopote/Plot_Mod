package com.plot.plugin.building.realistic;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 带场景标签的测试轮廓，供片区 gap 报告按类别汇总。
 */
public record TaggedFootprint(
        BuildingFootprint footprint,
        RealisticFootprintKind kind,
        BuildingFootprint.RoofType requestedRoof) {
}
