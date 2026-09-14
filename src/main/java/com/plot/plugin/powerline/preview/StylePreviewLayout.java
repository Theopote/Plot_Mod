package com.plot.plugin.powerline.preview;

/** 风格预览视口：画廊卡片、tooltip、大图等边界 → 结构布局策略。 */
public final class StylePreviewLayout {
    private StylePreviewLayout() {
    }

    /**
     * 画廊卡片与 Build 紧凑预览：高度有限，宽臂塔若按宽度缩放会缩成「小矮塔」。
     */
    public static TowerStructuralElevationRenderer.LayoutFit fitForBounds(
            float x0,
            float y0,
            float x1,
            float y1) {
        float width = x1 - x0;
        float height = y1 - y0;
        if (width <= 124f && height <= 104f) {
            return TowerStructuralElevationRenderer.LayoutFit.CARD_THUMBNAIL;
        }
        return TowerStructuralElevationRenderer.LayoutFit.BALANCED;
    }

    /** 塔体在视口内至少应占用的纵向比例（CARD_THUMBNAIL）。 */
    public static float cardThumbnailMinHeightFill() {
        return 0.72f;
    }
}
