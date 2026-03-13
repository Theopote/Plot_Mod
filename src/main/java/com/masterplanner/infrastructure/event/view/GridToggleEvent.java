package com.masterplanner.infrastructure.event.view;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.ui.grid.GridSettings;
import com.masterplanner.api.event.EventType;

/**
 * 网格显示切换事件
 */
public class GridToggleEvent extends Event {
    private final boolean enabled;
    private final GridSettings settings;
    private final boolean isSettingsUpdate;
    private final String source;

    public GridToggleEvent(boolean enabled, GridSettings settings, boolean isSettingsUpdate) {
        this("ViewManager", enabled, settings, isSettingsUpdate);
    }
    
    /**
     * 创建一个网格切换事件
     * @param enabled 是否启用网格
     * @param settings 网格设置
     * @param sourceObj 事件源对象
     */
    public GridToggleEvent(boolean enabled, GridSettings settings, Object sourceObj) {
        this(sourceObj != null ? sourceObj.getClass().getSimpleName() : "Unknown", 
             enabled, settings, settings != null);
    }
    
    public GridToggleEvent(String source, boolean enabled, GridSettings settings, boolean isSettingsUpdate) {
        super(EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为网格显示变化会影响视图
        this.source = source;
        this.enabled = enabled;
        this.settings = settings;
        this.isSettingsUpdate = isSettingsUpdate;
    }

    /**
     * 获取网格是否启用
     * @return true 如果网格显示开启，false 如果网格显示关闭
     */
    public boolean isEnabled() {
        return enabled;
    }

    public GridSettings getSettings() {
        return settings;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "GridToggleEvent{" +
                "source='" + source + '\'' +
                ", enabled=" + enabled +
                ", settings=" + settings +
                ", isSettingsUpdate=" + isSettingsUpdate +
                '}';
    }
} 