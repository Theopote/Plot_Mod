package com.plot.plugin.powerline.style;

/** 风格预览的主体绘制方式（画廊卡片、tooltip、Quick Tune 大图、建造摘要共用）。 */
public enum PreviewRepresentation {
    /** 真实 Minecraft 体素立面（小型电杆）。 */
    VOXEL_FRONT,
    /** 由 {@link com.plot.plugin.powerline.design.structure.TowerStructureDesign} 投影的结构线框（大型格构塔）。 */
    STRUCTURAL_FRONT
}
