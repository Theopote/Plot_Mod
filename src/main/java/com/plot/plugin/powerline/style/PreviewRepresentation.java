package com.plot.plugin.powerline.style;

/** 风格卡片缩略图的主体绘制方式（Gallery 专用；tooltip / 大预览仍用体素）。 */
public enum PreviewRepresentation {
    /** 真实 Minecraft 体素立面（小型电杆）。 */
    VOXEL_FRONT,
    /** 由 {@link com.plot.plugin.powerline.design.structure.TowerStructureDesign} 投影的结构线框（大型格构塔）。 */
    STRUCTURAL_FRONT
}
