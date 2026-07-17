package com.plot.plugin.road.manager;

/**
 * 供 PropertyPanel 调用的道路节点属性区接口（与编辑 Tab 共用选中态与控件逻辑）。
 */
public interface RoadJunctionPropertyProvider {
    boolean hasJunctionPropertyContent();

    void renderJunctionPropertySection();

    /** i18n key：PropertyPanel 折叠标题，路口与端点节点可不同。 */
    String getPropertySectionTitleKey();
}
