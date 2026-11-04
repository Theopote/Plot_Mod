package com.plot.plugin.road.ui;

/**
 * 道路系统 ImGui 主导航：路线 / 横断面 / 纵断面 / 建造。
 */
public enum RoadUiTab {
    /** 平面路径、中心线与交叉关系。 */
    ROUTE,
    /** 道路宽度、材质、结构与设施。 */
    CROSS_SECTION,
    /** 标高、坡度与纵断面曲线。 */
    VERTICAL_PROFILE,
    /** 正式预览、检查与世界落地。 */
    BUILD
}
