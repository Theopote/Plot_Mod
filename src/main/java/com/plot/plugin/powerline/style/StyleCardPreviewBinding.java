package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;

/** 风格预览的完整绑定：设计 + 绘制方式 + 叠加层（画廊 / tooltip / 大图 / 建造摘要共用）。 */
public record StyleCardPreviewBinding(
        PoleDesign design,
        PreviewRepresentation representation,
        PreviewOverlay overlay) {

    public StyleCardPreviewBinding {
        if (representation == null) {
            representation = PreviewRepresentation.VOXEL_FRONT;
        }
        if (overlay == null) {
            overlay = PreviewOverlay.NONE;
        }
    }
}
