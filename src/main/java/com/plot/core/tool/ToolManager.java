package com.plot.core.tool;

import com.plot.utils.PlotI18n;
import com.plot.api.model.ICanvas;
import com.plot.api.tool.*;
import com.plot.api.event.IEvent;
import com.plot.core.log.LogManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolChangedEvent;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.infrastructure.event.tool.ToolEvent;
import com.plot.core.config.ConfigManager;
import com.plot.core.state.AppState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具管理器
 * <p>
 * 负责工具注册、激活/停用、分组与配置持久化。
 * 选择相关行为由 {@link com.plot.ui.tools.impl.modify.strategy.IModifyStrategy} 在策略层处理。
 */
public class ToolManager implements IToolManager {
    private static ToolManager INSTANCE;
    
    private final AppState appState; // 持有对 AppState 的引用
    private final Map<String, ITool> tools;
    private final Map<String, ToolGroup> groups;
    private final List<IToolListener> listeners;
    private ITool activeTool;
    private final ToolGroup defaultGroup;

    private ToolManager(AppState appState) {
        if (appState == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_null"));
        }
        this.appState = appState;
        this.tools = new ConcurrentHashMap<>();
        this.groups = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();

        // 创建默认工具组
        this.defaultGroup = new ToolGroupImpl("Default");
        addGroup(defaultGroup);
    }

    public static void initialize(AppState appState) {
        if (INSTANCE == null) {
            INSTANCE = new ToolManager(appState);
        }
    }

    public static ToolManager getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(PlotI18n.error("error.plot.validation.tool_manager_not_initialized"));
        }
        return INSTANCE;
    }

    /**
     * 获取Canvas引用
     * 总是从AppState获取最新的Canvas引用，确保数据一致性
     */
    public ICanvas getCanvas() {
        return this.appState.getCanvas();
    }

    @Override
    public void registerTool(ITool tool) {
        if (tool == null || tools.containsKey(tool.getId())) {
            return;
        }
        tools.put(tool.getId(), tool);
        defaultGroup.addTool(tool);
        notifyToolRegistered(tool);
        LogManager.getInstance().debug("Registered tool: " + tool.getName());
    }

    @Override
    public void unregisterTool(ITool tool) {
        if (tool == null || !tools.containsKey(tool.getId())) {
            return;
        }

        if (tool == activeTool) {
            setActiveTool(null);
        }

        for (ToolGroup group : new ArrayList<>(groups.values())) {
            group.removeTool(tool);
        }

        tools.remove(tool.getId());
        notifyToolUnregistered(tool);
        disposeTool(tool);
        LogManager.getInstance().debug("Unregistered tool: " + tool.getName());
    }

    /**
     * 注销指定分组内的所有工具并释放其资源。
     */
    public void unregisterToolsInGroup(ToolGroup group) {
        if (group == null) {
            return;
        }
        for (ITool tool : new ArrayList<>(group.getTools())) {
            unregisterTool(tool);
        }
    }

    /**
     * 按名称查找已注册的工具分组。
     */
    public ToolGroup findGroupByName(String name) {
        if (name == null) {
            return null;
        }
        return groups.values().stream()
                .filter(group -> name.equals(group.getName()))
                .findFirst()
                .orElse(null);
    }

    private void disposeTool(ITool tool) {
        if (tool == null) {
            return;
        }
        try {
            tool.dispose();
            LogManager.getInstance().debug("Disposed tool: " + tool.getName());
        } catch (Exception e) {
            LogManager.getInstance().error("Error disposing tool: " + tool.getName(), e);
        }
    }

    public ITool getTool(String toolId) {
        return tools.get(toolId);
    }

    public List<ITool> getTools() {
        return getRegisteredTools();
    }

    public void addGroup(ToolGroup group) {
        if (group != null && !groups.containsKey(group.getId())) {
            groups.put(group.getId(), group);
            LogManager.getInstance().debug("Added tool group: " + group.getName());
        }
    }

    @Override
    public ITool getActiveTool() {
        return activeTool;
    }

    public void setActiveTool(ITool tool) {
        if (tool != activeTool) {
            ITool oldTool = activeTool;
            
            // Deactivate the old tool
            if (oldTool != null) {
                try {
                    oldTool.deactivate();
                    LogManager.getInstance().debug("Deactivated tool: " + oldTool.getName());
                } catch (Exception e) {
                    LogManager.getInstance().error("Error deactivating tool: " + oldTool.getName(), e);
                }
            }
            
            activeTool = tool;
            
            // Activate the new tool
            if (tool != null) {
                try {
                    // 修复：在激活工具前，先设置Canvas和Camera
                    setupToolCanvasAndCamera(tool);
                    
                    tool.activate();
                    LogManager.getInstance().debug("Activated tool: " + tool.getName());

                    // 同步：更新 AppState 的当前工具，确保输入与渲染一致
                    try {
                        if (tool instanceof BaseTool baseTool) {
                            this.appState.setCurrentTool(baseTool);
                            LogManager.getInstance().debug("AppState currentTool 同步为: {}", baseTool.getId());
                        } else {
                            // 非 BaseTool 类型则清空 AppState 的 currentTool 以避免指向旧工具
                            this.appState.setCurrentTool(null);
                        }
                    } catch (Exception syncEx) {
                        LogManager.getInstance().warn("同步 AppState.currentTool 失败: {}", syncEx.getMessage());
                    }
                } catch (Exception e) {
                    LogManager.getInstance().error("Error activating tool: " + tool.getName(), e);
                }
            }
            
            // 发布工具变更事件
            String newToolName = tool != null ? tool.getName() : "none";
            EventBus.getInstance().publish(new ToolChangedEvent(newToolName));
        }
    }

    /**
     * 为工具设置Canvas和Camera
     * 修复：确保工具激活时能获得正确的Canvas和Camera引用
     */
    private void setupToolCanvasAndCamera(ITool tool) {
        if (tool == null) {
            return;
        }
        
        try {
            // 获取Canvas
            com.plot.api.model.ICanvas canvas = getCanvas();
            if (canvas == null) {
                LogManager.getInstance().warn("Canvas未初始化，无法为工具设置Canvas");
                return;
            }
            
            // 如果是DrawingTool，设置Canvas和Camera
            if (tool instanceof com.plot.ui.tools.impl.drawing.DrawingTool drawingTool) {

                // 设置Canvas
                if (canvas instanceof com.plot.ui.canvas.Canvas uiCanvas) {
                    drawingTool.setCanvas(uiCanvas);
                    
                    // 设置Camera
                    com.plot.ui.canvas.CanvasCamera camera = uiCanvas.getCamera();
                    if (camera != null) {
                        drawingTool.setCamera(camera);
                        LogManager.getInstance().debug("为工具 [{}] 设置了Canvas和Camera", tool.getName());
                    } else {
                        LogManager.getInstance().warn("Canvas的Camera为null，工具 [{}] 可能无法正常工作", tool.getName());
                    }
                } else {
                    LogManager.getInstance().warn("Canvas类型不匹配，无法为DrawingTool设置Canvas");
                }
            }

            // 同样为 ModifyTool 设置 Canvas 和 Camera（选择/编辑类工具依赖吸附与坐标转换）
            if (tool instanceof com.plot.ui.tools.impl.modify.ModifyTool modifyTool) {
                if (canvas instanceof com.plot.ui.canvas.Canvas uiCanvas) {
                    modifyTool.setCanvas(uiCanvas);

                    com.plot.ui.canvas.CanvasCamera camera = uiCanvas.getCamera();
                    if (camera != null) {
                        modifyTool.setCamera(camera);
                        LogManager.getInstance().debug("为修改类工具 [{}] 设置了Canvas和Camera", tool.getName());
                    } else {
                        LogManager.getInstance().warn("Canvas的Camera为null，修改类工具 [{}] 可能无法正常工作", tool.getName());
                    }
                } else {
                    LogManager.getInstance().warn("Canvas类型不匹配，无法为ModifyTool设置Canvas");
                }
            }
            
        } catch (Exception e) {
            LogManager.getInstance().error("为工具设置Canvas和Camera时出错: {}", e.getMessage(), e);
        }
    }

    @Override
    public void addToolListener(IToolListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeToolListener(IToolListener listener) {
        listeners.remove(listener);
    }

    private void notifyToolRegistered(ITool tool) {
        for (IToolListener listener : listeners) {
            try {
                listener.onToolRegistered(tool);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying tool registration", e);
            }
        }
    }

    private void notifyToolUnregistered(ITool tool) {
        for (IToolListener listener : listeners) {
            try {
                listener.onToolUnregistered(tool);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying tool unregistration", e);
            }
        }
    }

    public void reset() {
        setActiveTool(null);
        for (ITool tool : new ArrayList<>(tools.values())) {
            disposeTool(tool);
        }
        tools.clear();
        groups.clear();
        listeners.clear();
    }

    @Override
    public List<ITool> getRegisteredTools() {
        return new ArrayList<>(tools.values());
    }

    @Override
    public ITool getToolByName(String name) {
        return tools.values().stream()
                .filter(tool -> tool.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void removeToolGroup(ToolGroup group) {
        if (group != null && groups.containsKey(group.getId())) {
            groups.remove(group.getId());
            LogManager.getInstance().debug("Removed tool group: " + group.getName());
        }
    }

    @Override
    public ToolGroup createToolGroup(String name) {
        ToolGroup group = new ToolGroupImpl(name);
        addGroup(group);
        return group;
    }

    @Override
    public void deactivateCurrentTool() {
        setActiveTool(null);
    }

    @Override
    public void activateTool(ITool tool) {
        setActiveTool(tool);
    }

    @Override
    public List<ToolGroup> getToolGroups() {
        return new ArrayList<>(groups.values());
    }

    @Override
    public void loadToolConfigs() {
        try {
            LogManager.getInstance().debug("Loading tool configurations...");

            for (ITool tool : tools.values()) {
                IToolConfig toolConfig = tool.getConfig();
                if (toolConfig == null) {
                    continue;
                }

                String configData = ConfigManager.getInstance().getString(configKeyFor(tool.getId()), "");
                if (configData.isEmpty()) {
                    continue;
                }

                toolConfig.loadFromJson(configData);
                publishLoadedConfig(tool);
                LogManager.getInstance().debug("Loaded config for tool: {}", tool.getName());
            }

            String activeToolId = ConfigManager.getInstance().getString("tool.active", "");
            if (!activeToolId.isEmpty()) {
                ITool tool = getTool(activeToolId);
                if (tool != null) {
                    setActiveTool(tool);
                    LogManager.getInstance().debug("Restored active tool: " + tool.getName());
                }
            }

            LogManager.getInstance().debug("Tool configurations loaded successfully");
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to load tool configurations", e);
        }
    }

    @Override
    public void saveToolConfigs() {
        try {
            LogManager.getInstance().debug("Saving tool configurations...");

            for (ITool tool : tools.values()) {
                IToolConfig toolConfig = tool.getConfig();
                if (toolConfig == null) {
                    continue;
                }

                String json = toolConfig.saveToJson();
                if (json == null || json.isBlank()) {
                    continue;
                }

                ConfigManager.getInstance().setString(configKeyFor(tool.getId()), json);
                LogManager.getInstance().debug("Saved config for tool: {}", tool.getName());
            }

            if (activeTool != null) {
                ConfigManager.getInstance().setString("tool.active", activeTool.getId());
            }

            ConfigManager.getInstance().saveConfig();
            LogManager.getInstance().debug("Tool configurations saved successfully");
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to save tool configurations", e);
        }
    }

    private static String configKeyFor(String toolId) {
        return "tool." + toolId + ".config";
    }

    private void publishLoadedConfig(ITool tool) {
        IToolConfig toolConfig = tool.getConfig();
        if (toolConfig == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : toolConfig.getAllValues().entrySet()) {
            EventBus.getInstance().publish(
                    new ToolConfigEvent("ToolManager", tool.getId(), entry.getKey(), null, entry.getValue())
            );
        }
    }

    /**
     * 处理工具相关事件
     * <p>
     * 优化：简化事件处理，专注于核心工具管理功能
     * 大部分工具事件现在由EventBus和专门的监听器处理
     */
    @Override
    public void handleEvent(IEvent event) {
        if (event instanceof ToolEvent toolEvent) {
            LogManager.getInstance().debug("Handling tool event: type={}, toolId={}", 
                toolEvent.getToolEventType(), toolEvent.getToolId());
            
            // 处理工具特定事件
            switch (toolEvent.getToolEventType()) {
                case TOOL_SELECT -> {
                    // 工具选择事件 - 激活指定工具
                    ITool tool = getTool(toolEvent.getToolId());
                    if (tool != null) {
                        setActiveTool(tool);
                    }
                }
                case TOOL_CONFIG -> // 工具配置事件 - 重新加载配置
                        loadToolConfigs();
                default -> // 其他事件类型由专门的监听器处理
                        LogManager.getInstance().debug("Event type {} handled by specialized listeners",
                            toolEvent.getToolEventType());
            }
        }
    }

    public void dispose() {
        try {
            // Save configurations before disposing
            saveToolConfigs();
            
            // Deactivate current tool
            if (activeTool != null) {
                activeTool.deactivate();
                activeTool = null;
            }

            for (ITool tool : new ArrayList<>(tools.values())) {
                disposeTool(tool);
            }

            // Clear all tools and groups
            tools.clear();
            groups.clear();
            listeners.clear();
            
            LogManager.getInstance().debug("ToolManager disposed successfully");
        } catch (Exception e) {
            LogManager.getInstance().error("Error disposing ToolManager", e);
        }
    }
}