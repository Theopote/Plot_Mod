package com.plot.infrastructure.event.tool;

import com.plot.api.event.EventType;
import com.plot.core.snap.SnapSettings;
import com.plot.infrastructure.event.base.Event;

/**
 * 捕捉设置改变事件
 */
public class SnapSettingsChangedEvent extends Event {
    private final SnapSettings settings;
    private final String source;

    public SnapSettingsChangedEvent(SnapSettings settings) {
        this("ToolManager", settings);
    }
    
    public SnapSettingsChangedEvent(String source, SnapSettings settings) {
        super(EventType.TOOL_CONFIG_CHANGED);  // 使用 TOOL_CONFIG_CHANGED 事件类型，因为捕捉设置是工具配置的一部分
        this.source = source;
        this.settings = settings;
    }

    public SnapSettings getSettings() {
        return settings;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("SnapSettingsChangedEvent[source=%s, settings=%s]", 
            source, settings);
    }
} 