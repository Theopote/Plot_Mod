package com.plot.ui.panel.tool.renderer;

import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.layout.UILayout;

/**
 * 工具选项渲染器抽象基类
 * 提供了一些共用的功能和常量
 */
public abstract class AbstractToolOptionRenderer implements ToolOptionRenderer {
    // 界面布局常量
    protected static final float BUTTON_SIZE = UILayout.Toolbar.LEFT_BUTTON_SIZE;         // 按钮大小，与工具面板一致
    protected static final float BUTTON_SPACING = UILayout.Toolbar.LEFT_BUTTON_SPACING;   // 按钮之间的间距，与工具面板一致
    protected static final float BUTTON_CORNER_ROUNDING = 4.0f;// 按钮圆角半径

    protected final EventBus eventBus;
    protected final String toolId;

    protected AbstractToolOptionRenderer(String toolId) {
        this.eventBus = EventBus.getInstance();
        this.toolId = toolId;
    }

    /**
     * 发布工具配置更新事件
     * @param key 配置键
     * @param value 配置值
     */
    protected void updateToolConfig(String key, String value) {
        ToolConfigEvent event = new ToolConfigEvent(toolId, key, null, value);
        eventBus.publish(event);
    }

    /**
     * 默认的清理方法实现
     */
    @Override
    public void cleanup() {
        // 默认实现为空，子类可以根据需要重写
    }
} 