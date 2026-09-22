package com.plot.plugin.building.overlay;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 单栋建筑轮廓叠加层条目（二维编辑态，不含生成结果）。 */
public record BuildingOverlayEntry(
        String buildingId,
        String displayName,
        List<Vec2d> outerPoints,
        int floors,
        BuildingOverlayState state) {
}
