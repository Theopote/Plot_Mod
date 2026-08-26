package com.plot.api.render;

import com.plot.api.geometry.Vec2d;

/**
 * 画布视图变换（世界 ↔ 屏幕）。UI 的 CanvasCamera 实现此接口；Core/API 不依赖 UI。
 */
public interface ViewTransform {
    Vec2d worldToScreen(Vec2d worldPoint);

    Vec2d getOffset();

    float getZoom();

    /** 屏幕像素距离换算为世界坐标距离。 */
    default double screenToWorldDistance(double screenDistance) {
        float zoom = getZoom();
        return zoom == 0f ? screenDistance : screenDistance / zoom;
    }
}
