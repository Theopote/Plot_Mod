package com.masterplanner.infrastructure.event.view;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.api.event.EventType;

/**
 * 主题改变事件
 * 当UI主题发生变化时触发
 */
public class ThemeChangeEvent extends Event {
    private final UITheme.ThemeColors newTheme;
    private final String source;

    public ThemeChangeEvent(UITheme.ThemeColors newTheme) {
        this("ThemeManager", newTheme);
    }
    
    public ThemeChangeEvent(String source, UITheme.ThemeColors newTheme) {
        super(EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为主题变化会影响视图显示
        this.source = source;
        this.newTheme = newTheme;
    }

    public UITheme.ThemeColors getNewTheme() {
        return newTheme;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("ThemeChangeEvent[source=%s, newTheme=%s]", source, newTheme);
    }
} 