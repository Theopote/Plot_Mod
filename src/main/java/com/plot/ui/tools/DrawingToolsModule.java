package com.plot.ui.tools;

import java.util.List;
import java.util.ArrayList;

import com.plot.api.tool.ToolGroup;
import com.plot.api.state.IAppState;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.core.tool.ToolGroupImpl;
import com.plot.infrastructure.event.EventBus;
import com.plot.ui.tools.impl.drawing.DrawingTool;
import com.plot.ui.tools.impl.modify.TextTool;
import com.plot.core.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.PlotI18n;

/**
 * 绘图工具模块 - 负责创建和注册所有绘图工具
 * 
 * <p>此类不再是管理器或单例，而是一个简单的初始化模块。
 * 它的唯一职责是在应用启动时创建所有绘图工具实例，
 * 并将它们注册到 ToolManager 中。所有工具的管理
 * （激活、分组、列表）都完全委托给 ToolManager。</p>
 * 
 * <p><strong>设计原则：</strong></p>
 * <ul>
 *   <li>单一职责：只负责工具的创建和注册</li>
 *   <li>无状态：不维护任何工具列表或状态</li>
 *   <li>委托管理：所有工具管理都由 ToolManager 负责</li>
 *   <li>依赖注入：通过参数传递依赖，避免服务定位</li>
 * </ul>
 * 
 * <p><strong>命名说明：</strong></p>
 * <p>虽然Java中工具类通常以复数形式命名（如Collections、Executors），
 * 但DrawingToolsModule这个名字更清晰地表达了这是一个提供功能的模块，
 * 符合模块化设计的语义。备选名称如DrawingTools也是合理的选择。</p>
 * 
 * @author Plot Team
 * @version 2.0 - 重构版：从管理器简化为初始化模块
 */
public final class DrawingToolsModule {

    public static final String DRAWING_GROUP_NAME = "Drawing Tools";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DrawingToolsModule.class);
    
    // 防止实例化
    private DrawingToolsModule() {
        throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.utility_class"));
    }
    
    /**
     * 初始化并注册所有绘图工具到ToolManager
     * 
     * <p>实现完全的依赖注入，避免任何单例依赖，达到最高的内聚和最低的耦合。</p>
     * 
     * @param toolManager 工具管理器，负责管理所有工具
     * @param appState 应用状态，提供工具创建所需的依赖
     * @param eventBus 事件总线，用于工具间通信
     * @param snapManager 吸附管理器，用于工具的吸附功能
     * @param commandManager 命令管理器，用于工具的命令执行
     * @throws IllegalArgumentException 如果任何参数为null
     * @throws RuntimeException 如果初始化过程中发生错误
     */
    public static void initializeAndRegister(
            ToolManager toolManager, 
            IAppState appState, 
            EventBus eventBus,
            com.plot.api.snap.ISnapManager snapManager,
            com.plot.core.command.CommandManager commandManager) {
        // 参数验证
        if (toolManager == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.tool_manager_null"));
        }
        if (appState == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_null"));
        }
        if (eventBus == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.event_bus_null"));
        }
        if (snapManager == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.snap_manager_null"));
        }
        if (commandManager == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.command_manager_null"));
        }
        
        LOGGER.info("开始初始化绘图工具模块...");

        DrawingTool.configureSharedDependencies(eventBus, com.plot.core.shortcut.ShortcutManager.getInstance());
        
        try {
            boolean reinitializing = hasRegisteredDrawingTools(toolManager);

            // 获取或创建绘图工具组；重初始化时先释放旧工具
            ToolGroup drawingGroup = getOrCreateDrawingGroup(toolManager);
            
            // 创建所有绘图工具
            List<BaseTool> tools = createAllDrawingTools(appState, snapManager, commandManager);
            
            // 注册工具到ToolManager和工具组
            registerTools(toolManager, drawingGroup, tools);
            
            // 仅在首次初始化时设置默认激活工具
            if (!reinitializing) {
                setDefaultActiveTool(toolManager);
            }
            
            LOGGER.info("绘图工具模块初始化完成，共注册 {} 个工具", tools.size());
            
        } catch (Exception e) {
            String errorMsg = "初始化绘图工具模块时发生错误: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    private static boolean hasRegisteredDrawingTools(ToolManager toolManager) {
        ToolGroup drawingGroup = toolManager.findGroupByName(DRAWING_GROUP_NAME);
        return drawingGroup != null && !drawingGroup.getTools().isEmpty();
    }

    /**
     * 获取或创建绘图工具组；若分组已存在则先注销并释放其中工具。
     */
    private static ToolGroup getOrCreateDrawingGroup(ToolManager toolManager) {
        ToolGroup existingGroup = toolManager.findGroupByName(DRAWING_GROUP_NAME);
        if (existingGroup != null) {
            LOGGER.debug("释放现有绘图工具组中的工具: {}", DRAWING_GROUP_NAME);
            toolManager.unregisterToolsInGroup(existingGroup);
            return existingGroup;
        }

        LOGGER.debug("创建绘图工具组");
        ToolGroup drawingGroup = new ToolGroupImpl(DRAWING_GROUP_NAME);
        toolManager.addGroup(drawingGroup);
        return drawingGroup;
    }
    
    /**
     * 创建所有绘图工具实例
     * 
     * <p>完全通过依赖注入获取所需组件，不再使用任何单例模式，实现最高的解耦。</p>
     * 
     * @param appState 应用状态，提供工具创建所需的依赖
     * @param snapManager 吸附管理器，用于工具的吸附功能
     * @param commandManager 命令管理器，用于工具的命令执行
     * @return 创建的工具列表
     */
    private static List<BaseTool> createAllDrawingTools(
            IAppState appState,
            com.plot.api.snap.ISnapManager snapManager,
            CommandManager commandManager) {
        LOGGER.debug("开始创建绘图工具实例...");
        
        List<BaseTool> tools = new ArrayList<>();
        
        try {
            // 直接使用传入的依赖，避免任何单例调用
            
            // 创建工具工厂 - 统一的依赖注入方式
            ToolFactory toolFactory = new ToolFactory(
                    appState,
                snapManager, 
                appState.getCanvas(), 
                commandManager
            );
            
            // 使用工厂创建支持依赖注入的核心绘图工具
            tools.add(toolFactory.createLineTool());
            tools.add(toolFactory.createCircleTool());
            tools.add(toolFactory.createRectangleTool());
            tools.add(toolFactory.createEllipseTool());
            tools.add(toolFactory.createFreeDrawTool());
            tools.add(toolFactory.createPolygonTool());
            tools.add(toolFactory.createArcTool());
            tools.add(toolFactory.createCatenaryLineTool());
            
            // 创建其他绘图工具（逐步迁移到工厂模式）
            tools.add(toolFactory.createSplineTool());
            tools.add(toolFactory.createPolylineTool());
            tools.add(toolFactory.createSemicircleTool());
            tools.add(toolFactory.createSpiralTool());
            tools.add(toolFactory.createStarTool());
            tools.add(toolFactory.createSineCurveTool());

            // 新增：将文字工具视为绘图工具进行注册
            if (appState.getCanvas() != null) {
                tools.add(new TextTool(appState.getCanvas()));
            }
            
            LOGGER.debug("成功创建 {} 个绘图工具", tools.size());
            
            // 记录创建的工具列表用于调试
            tools.forEach(tool -> 
                LOGGER.debug("已创建工具: id={}, name={}, 类型={}", 
                    tool.getId(), tool.getName(), tool.getClass().getSimpleName()));
            
        } catch (Exception e) {
            LOGGER.error("创建绘图工具时发生错误", e);
            throw new RuntimeException(PlotI18n.error("error.plot.tool.create_drawing_failed", e.getMessage()), e);
        }
        
        return tools;
    }
    
    /**
     * 将工具注册到ToolManager和工具组
     * 
     * <p>采用激进的失败处理策略：任何工具注册失败都会中断整个初始化过程，
     * 确保应用不会在工具不完整的状态下运行。</p>
     * 
     * @param toolManager 工具管理器
     * @param drawingGroup 绘图工具组
     * @param tools 要注册的工具列表
     * @throws RuntimeException 如果任何工具注册失败
     */
    private static void registerTools(ToolManager toolManager, ToolGroup drawingGroup, List<BaseTool> tools) {
        LOGGER.debug("开始注册工具到ToolManager...");
        
        int successCount = 0;
        
        for (BaseTool tool : tools) {
            try {
                // 注册到ToolManager（这是工具管理的唯一入口）
                toolManager.registerTool(tool);
                
                // 添加到绘图工具组
                drawingGroup.addTool(tool);
                
                successCount++;
                LOGGER.debug("工具注册成功: id={}, name={}", tool.getId(), tool.getName());
                
            } catch (Exception e) {
                // 激进策略：立即抛出异常，中断初始化过程
                String errorMsg = PlotI18n.status("status.plot.module.register_tool_failed", tool.getName());
                LOGGER.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
        }
        
        LOGGER.info("工具注册完成: 成功注册 {} 个工具", successCount);
    }
    
    /**
     * 设置默认激活工具
     * 
     * @param toolManager 工具管理器
     */
    private static void setDefaultActiveTool(ToolManager toolManager) {
        try {
            LOGGER.debug("设置默认激活工具: line");
            // 通过工具ID获取工具实例，然后激活
            com.plot.api.tool.ITool lineTool = toolManager.getRegisteredTools().stream()
                .filter(tool -> "line".equals(tool.getId()))
                .findFirst()
                .orElse(null);
            
            if (lineTool != null) {
                toolManager.setActiveTool(lineTool);
                LOGGER.debug("默认工具设置成功: {}", lineTool.getName());
            } else {
                LOGGER.warn("未找到ID为'line'的工具，跳过默认工具设置");
            }
        } catch (Exception e) {
            LOGGER.warn("设置默认激活工具失败: {}", e.getMessage(), e);
            // 非致命错误，不抛异常
        }
    }

    /**
     * 验证绘图工具模块是否正确初始化（仅用于调试）
     * 
     * @param toolManager 工具管理器
     * @return 是否初始化成功
     */
    public static boolean verifyInitialization(ToolManager toolManager) {
        if (toolManager == null) {
            LOGGER.error("验证失败: ToolManager为null");
            return false;
        }
        
        try {
            // 检查是否有绘图工具组
            ToolGroup drawingGroup = toolManager.findGroupByName(DRAWING_GROUP_NAME);
            
            if (drawingGroup == null) {
                LOGGER.error("验证失败: 未找到绘图工具组");
                return false;
            }
            
            // 检查工具数量
            long toolCount = toolManager.getRegisteredTools().size();
            if (toolCount == 0) {
                LOGGER.error("验证失败: 未注册任何工具");
                return false;
            }
            
            LOGGER.info("绘图工具模块验证成功: 工具组={}, 工具数量={}", 
                drawingGroup.getName(), toolCount);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("验证绘图工具模块时发生错误", e);
            return false;
        }
    }
}
