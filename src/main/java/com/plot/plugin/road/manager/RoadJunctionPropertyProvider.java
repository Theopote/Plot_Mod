package com.plot.plugin.road.manager;

/**
 * 供 PropertyPanel 调用的交叉口属性区接口。
 */
public interface RoadJunctionPropertyProvider {
    boolean hasJunctionPropertyContent();

    void renderJunctionPropertySection();
}
