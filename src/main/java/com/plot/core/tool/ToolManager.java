package com.plot.core.tool;

import com.plot.api.model.ICanvas;
import com.plot.api.tool.*;
import com.plot.api.event.IEvent;
import com.plot.core.log.LogManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolChangedEvent;
import com.plot.infrastructure.event.tool.ToolEvent;
import com.plot.infrastructure.event.selection.SelectionChangedEvent;
import com.plot.infrastructure.event.EventListener;
import com.plot.core.config.ConfigManager;
import com.plot.core.state.AppState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具管理器 (最终修复版 V3 - 选择感知优化版)
 * <p>
 * 主要优化：
 * 1. 移除过时的setCanvas和initializeTools方法 - 工具注册现在由专门的模块负责
 * 2. 简化事件处理 - 专注于核心工具管理功能
 * 3. 通过AppState统一获取Canvas引用 - 确保数据一致性
 * 4. 清晰的职责分离 - ToolManager专注工具生命周期管理
 * 5. 新增选择感知功能 - 监听选择变更事件，自动更新选择感知工具的状态
 * <p>
 * <strong>选择感知设计：</strong>
 * ToolManager现在负责监听SelectionChangedEvent，并通知所有实现了
 * ISelectionAwareTool接口的工具更新其状态。这将之前在各个管理器中
 * 分散的updateForSelection逻辑统一到了ToolManager中。
 */
public class ToolManager implements IToolManager {
    private static ToolManager INSTANCE;
    
    private final AppState appState; // 持有对 AppState 的引用
    private final Map<String, ITool> tools;
    private final Map<String, ToolGroup> groups;
    private final List<IToolListener> listeners;
    private ITool activeTool;
    private final ToolGroup defaultGroup;
    
    // 选择感知功能
    private final EventListener selectionChangedListener;

    private ToolManager(AppState appState) {
        if (appState == null) {
            throw new IllegalArgumentException("AppState 不能为空");
        }
        this.appState = appState;
        this.tools = new ConcurrentHashMap<>();
        this.groups = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();

        // 创建默认工具组
        this.defaultGroup = new ToolGroupImpl("Default");
        addGroup(defaultGroup);
        
        // 初始化选择感知功能
        this.selectionChangedListener = this::onSelectionChanged;
        EventBus.getInstance().subscribe(SelectionChangedEvent.class, selectionChangedListener);
        LogManager.getInstance().debug("ToolManager: 已注册选择变更监听器");
    }

    public static void initialize(AppState appState) {
        if (INSTANCE == null) {
            INSTANCE = new ToolManager(appState);
        }
    }

    public static ToolManager getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ToolManager 必须先通过 initialize(appState) 初始化");
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

        ToolGroup group = tool.getGroup();
        if (group != null) {
            group.removeTool(tool);
        }

        tools.remove(tool.getId());
        notifyToolUnregistered(tool);
        LogManager.getInstance().debug("Unregistered tool: " + tool.getName());
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
                if (tool instanceof BaseTool) {
                    // Load tool-specific configuration
                    String configData = ConfigManager.getInstance().getString("tool." + tool.getId() + ".config", "");
                    
                    if (!configData.isEmpty()) {
                    // Parse and apply configuration（预留扩展点）
                    LogManager.getInstance().debug("Loaded config for tool: {}", tool.getName());
                    }
                }
            }
            
            // Load active tool
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
                if (tool instanceof BaseTool) {
                    // Save tool-specific configuration（预留扩展点）
                    LogManager.getInstance().debug("Saved config for tool: " + tool.getName());
                }
            }
            
            // Save active tool
            if (activeTool != null) {
                ConfigManager.getInstance().setString("tool.active", activeTool.getId());
            }
            
            ConfigManager.getInstance().saveConfig();
            LogManager.getInstance().debug("Tool configurations saved successfully");
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to save tool configurations", e);
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
            
            // 取消订阅选择变更事件
            if (selectionChangedListener != null) {
                EventBus.getInstance().unsubscribe(SelectionChangedEvent.class, selectionChangedListener);
                LogManager.getInstance().debug("ToolManager: 已取消选择变更监听器");
            }
            
            // Deactivate current tool
            if (activeTool != null) {
                activeTool.deactivate();
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
    
    /**
     * 处理选择变更事件
     * 
     * <p>当选择状态发生变化时，此方法会被调用。它会遍历所有注册的工具，
     * 对于实现了 ISelectionAwareTool 接口的工具，调用其 onSelectionChanged 方法。</p>
     * 
     * @param event 选择变更事件
     */
    private void onSelectionChanged(com.plot.infrastructure.event.base.Event event) {
        if (!(event instanceof SelectionChangedEvent selectionEvent)) {
            return;
        }
        
        boolean hasSelection = !selectionEvent.getSelectedShapes().isEmpty();
        int selectionCount = selectionEvent.getSelectedShapes().size();
        
        LogManager.getInstance().debug("ToolManager: 处理选择变更事件，选中对象数量: {}", selectionCount);
        
        // 通知所有选择感知工具
        int notifiedCount = 0;
        for (ITool tool : tools.values()) {
            if (tool instanceof ISelectionAwareTool selectionAwareTool) {
                try {
                    selectionAwareTool.onSelectionChanged(hasSelection);
                    notifiedCount++;
                    
                    LogManager.getInstance().debug("ToolManager: 已通知工具 '{}' 选择状态变更", tool.getName());
                } catch (Exception e) {
                    LogManager.getInstance().error("ToolManager: 通知工具 '{}' 选择状态变更时发生错误", tool.getName(), e);
                }
            }
        }
        
        LogManager.getInstance().debug("ToolManager: 选择状态更新完成，通知了 {} 个选择感知工具", notifiedCount);
    }
}