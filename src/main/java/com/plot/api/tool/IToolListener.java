package com.plot.api.tool;

/**
 * 工具监听器接口
 */
public interface IToolListener {
    /**
     * 当工具被注册时调用
     * @param tool 被注册的工具
     */
    void onToolRegistered(ITool tool);
    
    /**
     * 当工具被注销时调用
     * @param tool 被注销的工具
     */
    void onToolUnregistered(ITool tool);
    
    /**
     * 当活动工具改变时调用
     * @param oldTool 原活动工具
     * @param newTool 新活动工具
     */
    void onActiveToolChanged(ITool oldTool, ITool newTool);
    
    /**
     * 当工具状态改变时调用
     * @param tool 状态改变的工具
     * @param state 新状态
     */
    void onToolStateChanged(ITool tool, ToolState state);
    
    /**
     * 当工具组改变时调用
     * @param tool 改变组的工具
     * @param oldGroup 原工具组
     * @param newGroup 新工具组
     */
    void onToolGroupChanged(ITool tool, ToolGroup oldGroup, ToolGroup newGroup);
}
