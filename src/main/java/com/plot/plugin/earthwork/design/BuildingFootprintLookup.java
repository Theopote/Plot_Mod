package com.plot.plugin.earthwork.design;
import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 运行时查询建筑轮廓（由建筑插件或侧车 JSON 提供）。
 */
@FunctionalInterface
public interface BuildingFootprintLookup {
    BuildingFootprintLookup NONE = id -> null;

    BuildingFootprint getFootprint(String footprintId);
}
