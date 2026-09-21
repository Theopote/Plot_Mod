package com.plot.plugin.powerline.preview.overlay;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 线路生成预览的画布叠加层（严格来自 {@link com.plot.plugin.powerline.PowerLineGenerationResult}）。 */
public record PowerLineCanvasPreviewOverlay(
        String lineId,
        List<TowerPreviewMarker> markers,
        List<Vec2d> spanPathPoints,
        boolean closedLoop,
        PreviewStats stats) {

    public record PreviewStats(
            int towerCount,
            int spanCount,
            int angleTowerCount,
            int terrainInsertCount,
            int userOverrideCount) {
    }
}
