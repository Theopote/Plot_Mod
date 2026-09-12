package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;

/** 单塔放置会话的只读快照（供画布叠加层与世界 Ghost 预览使用）。 */
public record SingleTowerPlacementState(
        Vec2d planPoint,
        int buildBaseY,
        int rotationQuadrant,
        PoleDesign design,
        String designLabel,
        boolean hoverValid) {

    public Vec2d tangent() {
        return SingleTowerOrientation.tangentForQuadrant(rotationQuadrant);
    }
}
