package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;

/** 风格卡片预览的完整绑定：设计 + 绘制方式 + 叠加层。 */
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
