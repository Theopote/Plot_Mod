package com.plot.plugin.powerline.preview.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.TowerRole;

/** 单座杆塔的画布预览标记（屏幕空间绘制，世界坐标来源）。 */
public record TowerPreviewMarker(
        Vec2d position,
        Vec2d crossarmAxis,
        TowerRole role,
        MarkerSource source,
        String poleSiteId,
        double stationing,
        String designLabel,
        String sourceReasonKey) {
}
