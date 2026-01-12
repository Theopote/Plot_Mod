package com.masterplanner.ui.toolbar.group;

import com.masterplanner.ui.component.UIComponent;

/**
 * 工具栏组接口
 * 所有工具栏组件都应该实现此接口
 */
public interface ToolbarGroup extends UIComponent {
    
    /**
     * 获取组的显示名称
     * @return 组名称
     */
    String getGroupName();
    
    /**
     * 获取组的宽度
     * 用于布局计算和分隔符定位
     * @return 组的总宽度
     */
    float getGroupWidth();
    
    /**
     * 是否需要在此组后渲染分隔符
     * @return true 如果需要分隔符
     */
    default boolean needsSeparator() {
        return true;
    }
    
    /**
     * 是否启用此工具组
     * @return true 如果启用
     */
    default boolean isEnabled() {
        return true;
    }
}