package com.plot.api.tool;

import java.util.List;
import com.plot.api.event.IEvent;

/**
 * 工具管理器接口
 */
public interface IToolManager {
    /**
     * 注册工具
     * @param tool 要注册的工具
     */
    void registerTool(ITool tool);

    /**
     * 注销工具
     * @param tool 要注销的工具
     */
    void unregisterTool(ITool tool);

    /**
     * 获取所有已注册的工具
     * @return 工具列表
     */
    List<ITool> getRegisteredTools();

    /**
     * 获取当前激活的工具
     * @return 当前工具
     */
    ITool getActiveTool();

    /**
     * 激活工具
     * @param tool 要激活的工具
     */
    void activateTool(ITool tool);

    /**
     * 停用当前工具
     */
    void deactivateCurrentTool();

    /**
     * 根据名称获取工具
     * @param name 工具名称
     * @return 工具实例，如果不存在返回null
     */
    ITool getToolByName(String name);

    /**
     * 添加工具监听器
     * @param listener 工具监听器
     */
    void addToolListener(IToolListener listener);

    /**
     * 移除工具监听器
     * @param listener 工具监听器
     */
    void removeToolListener(IToolListener listener);

    /**
     * 获取工具分组
     * @return 工具分组列表
     */
    List<ToolGroup> getToolGroups();

    /**
     * 创建工具分组
     * @param name 分组名称
     * @return 创建的分组
     */
    ToolGroup createToolGroup(String name);

    /**
     * 移除工具分组
     * @param group 要移除的分组
     */
    void removeToolGroup(ToolGroup group);

    /**
     * 保存工具配置
     */
    void saveToolConfigs();

    /**
     * 加载工具配置
     */
    void loadToolConfigs();

    /**
     * 处理工具相关事件
     * @param event 事件对象
     */
    void handleEvent(IEvent event);
}
