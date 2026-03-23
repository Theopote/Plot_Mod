package com.plot.api.tool;

import com.plot.api.geometry.Vec2d;
import com.plot.api.shortcut.IShortcutListener;
import com.plot.core.graphics.DrawContext;

/**
 * 工具接口，定义了工具的基本操作
 */
public interface ITool extends IShortcutListener {
    /**
     * 获取工具ID
     * @return 工具唯一标识符
     */
    String getId();

    /**
     * 获取工具名称
     * @return 工具名称
     */
    String getName();

    /**
     * 获取工具描述
     * @return 工具描述
     */
    String getDescription();

    /**
     * 获取工具图标
     * @return 工具图标的资源位置
     */
    String getIcon();

    /**
     * 获取工具提示
     * @return 工具提示
     */
    String getTooltip();

    /**
     * 获取工具优先级
     * @return 工具优先级
     */
    int getPriority();

    /**
     * 获取工具状态
     * @return 工具状态
     */
    ToolState getState();

    /**
     * 设置工具状态
     * @param state 新状态
     */
    void setState(ToolState state);

    /**
     * 获取工具配置
     * @return 工具配置
     */
    IToolConfig getConfig();

    /**
     * 设置工具配置
     * @param config 工具配置
     */
    void setConfig(IToolConfig config);

    /**
     * 获取工具所属组
     * @return 工具组
     */
    ToolGroup getGroup();

    /**
     * 设置工具组
     * @param group 工具组
     */
    void setGroup(ToolGroup group);

    /**
     * 工具是否启用
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 设置工具启用状态
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);

    /**
     * 重置工具状态
     */
    void reset();

    /**
     * 工具是否可用
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 工具是否可见
     * @return 是否可见
     */
    boolean isVisible();

    /**
     * 工具是否活动
     * @return 是否活动
     */
    boolean isActive();

    /**
     * 激活工具
     */
    void activate();

    /**
     * 停用工具
     */
    void deactivate();

    /**
     * 执行工具操作
     */
    void execute();

    /**
     * 取消工具操作
     */
    void cancel();

    /**
     * 鼠标按下时调用
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @return 是否处理了此事件
     */
    boolean onMouseDown(Vec2d pos, int button);

    /**
     * 鼠标移动时调用
     * @param pos 鼠标位置
     * @return 是否处理了此事件
     */
    boolean onMouseMove(Vec2d pos);

    /**
     * 鼠标抬起时调用
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @return 是否处理了此事件
     */
    boolean onMouseUp(Vec2d pos, int button);

    /**
     * 鼠标双击时调用
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @return 是否处理了此事件
     */
    boolean onMouseDoubleClick(Vec2d pos, int button);

    /**
     * 键盘按键按下时调用
     * @param keyCode 按键代码
     * @return 是否处理了此事件
     */
    boolean onKeyDown(int keyCode);

    /**
     * 键盘按键抬起时调用
     * @param keyCode 按键代码
     * @return 是否处理了此事件
     */
    boolean onKeyUp(int keyCode);

    /**
     * 键盘字符输入时调用
     * @param character 输入的字符
     * @return 是否处理了此事件
     */
    boolean onKeyTyped(char character);

    /**
     * 鼠标滚轮事件调用
     * @param pos 鼠标位置
     * @param delta 滚轮增量（正值向上滚动，负值向下滚动）
     * @return 是否处理了此事件
     */
    boolean onMouseWheel(Vec2d pos, double delta);

    /**
     * 工具完成时调用
     */
    void onComplete();

    /**
     * 渲染工具
     * @param context 绘制上下文
     */
    void render(DrawContext context);

    /**
     * 清理工具资源
     */
    void dispose();
}
