package com.plot.ui.tools;

import java.util.List;
import java.util.ArrayList;

import com.plot.api.tool.ToolGroup;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.command.CommandManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.core.tool.ToolGroupImpl;
import com.plot.infrastructure.event.EventBus;
import com.plot.ui.tools.impl.modify.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.PlotI18n;

/**
 * 修改工具模块 - 负责创建和注册所有修改/编辑工具
 * 
 * <p>此类是 DrawingToolsModule 的姊妹模块，专门负责修改工具的初始化。
 * 它的唯一职责是在应用启动时创建所有修改工具实例，
 * 并将它们注册到 ToolManager 中。所有工具的管理
 * （激活、分组、列表）都完全委托给 ToolManager。</p>
 * 
 * <p><strong>设计原则：</strong></p>
 * <ul>
 *   <li>单一职责：只负责修改工具的创建和注册</li>
 *   <li>无状态：不维护任何工具列表或状态</li>
 *   <li>委托管理：所有工具管理都由 ToolManager 负责</li>
 *   <li>完全依赖注入：通过参数传递所有依赖，避免服务定位</li>
 *   <li>无默认工具设置：让调用方统一管理默认工具</li>
 * </ul>
 * 
 * <p><strong>与 DrawingToolsModule 的关系：</strong></p>
 * <p>两个模块职责清晰分离：DrawingToolsModule 负责绘图工具，
 * ModifyToolsModule 负责修改工具。它们都遵循相同的设计模式，
 * 确保整个工具系统架构的一致性。</p>
 * 
 * @author Plot Team
 * @version 3.0 - 完全依赖注入版：消除所有单例依赖，实现最高内聚最低耦合
 */
public final class ModifyToolsModule {

    public static final String MODIFY_GROUP_NAME = "Modify Tools";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyToolsModule.class);
    
    // 防止实例化
    private ModifyToolsModule() {
        throw new UnsupportedOperationException("ModifyToolsModule是工具类，不能被实例化");
    }
    
    /**
     * 初始化并注册所有修改工具到ToolManager
     * 
     * <p>实现完全的依赖注入，与DrawingToolsModule保持完全一致的设计模式。
     * 所有依赖都通过参数传入，消除了对任何全局单例的依赖。</p>
     * 
     * @param toolManager 工具管理器，负责管理所有工具
     * @param appState 应用状态，提供工具创建所需的依赖
     * @param eventBus 事件总线，用于工具间通信
     * @param snapManager 吸附管理器，用于需要吸附功能的工具
     * @param commandManager 命令管理器，用于需要撤销/重做功能的工具
     * @throws IllegalArgumentException 如果任何参数为null
     * @throws RuntimeException 如果初始化过程中发生错误
     */
    public static void initializeAndRegister(
            ToolManager toolManager, 
            IAppState appState, 
            EventBus eventBus,
            ISnapManager snapManager,
            CommandManager commandManager) {
        
        // 参数验证
        if (toolManager == null) {
            throw new IllegalArgumentException("ToolManager不能为null");
        }
        if (appState == null) {
            throw new IllegalArgumentException("AppState不能为null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("EventBus不能为null");
        }
        if (snapManager == null) {
            throw new IllegalArgumentException("SnapManager不能为null");
        }
        if (commandManager == null) {
            throw new IllegalArgumentException("CommandManager不能为null");
        }
        
        LOGGER.info("开始初始化修改工具模块...");

        ModifyTool.configureSharedDependencies(eventBus, com.plot.core.shortcut.ShortcutManager.getInstance());
        
        try {
            // 获取或创建修改工具组；重初始化时先释放旧工具
            ToolGroup modifyGroup = getOrCreateModifyGroup(toolManager);
            
            // 创建所有修改工具
            List<BaseTool> tools = createAllModifyTools(appState, eventBus, snapManager, commandManager);
            
            // 注册工具到ToolManager和工具组
            registerTools(toolManager, modifyGroup, tools);
            
            LOGGER.info("修改工具模块初始化完成，共注册 {} 个工具", tools.size());
            
        } catch (Exception e) {
            String errorMsg = "初始化修改工具模块时发生错误: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    private static ToolGroup getOrCreateModifyGroup(ToolManager toolManager) {
        ToolGroup existingGroup = toolManager.findGroupByName(MODIFY_GROUP_NAME);
        if (existingGroup != null) {
            LOGGER.debug("释放现有修改工具组中的工具: {}", MODIFY_GROUP_NAME);
            toolManager.unregisterToolsInGroup(existingGroup);
            return existingGroup;
        }

        LOGGER.debug("创建修改工具组");
        ToolGroup modifyGroup = new ToolGroupImpl(MODIFY_GROUP_NAME);
        toolManager.addGroup(modifyGroup);
        return modifyGroup;
    }
    
    /**
     * 创建所有修改工具实例
     * 
     * <p>根据实际工具类的构造函数签名创建工具实例。
     * 有些工具需要特定的依赖（如Canvas），从AppState中获取。</p>
     * 
     * @param appState 应用状态，提供工具创建所需的依赖
     * @param eventBus 事件总线，传递给需要事件处理的工具
     * @param snapManager 吸附管理器，传递给需要吸附功能的工具
     * @param commandManager 命令管理器，传递给需要撤销/重做功能的工具
     * @return 创建的工具列表
     */
    private static List<BaseTool> createAllModifyTools(
            IAppState appState, 
            EventBus eventBus,
            ISnapManager snapManager,
            CommandManager commandManager) {
        
        LOGGER.debug("开始创建修改工具实例...");
        
        List<BaseTool> tools = new ArrayList<>();
        
        try {
            // 验证 AppState 状态并获取必要的依赖
            com.plot.api.model.ICanvas canvas = appState.getCanvas();
            if (canvas == null) {
                throw new IllegalStateException("Canvas未在AppState中初始化");
            }
            
            // 基础工具 - 使用依赖注入构造函数
            tools.add(new SelectionTool(canvas));       // 选择工具（需要Canvas）
            tools.add(new EraserTool(appState, snapManager)); // 擦除工具（依赖注入）
            
            // 变换工具 - 使用依赖注入构造函数
            tools.add(new MoveTool(appState, snapManager));    // 移动工具（依赖注入）
            tools.add(new RotateTool(appState, snapManager));  // 旋转工具（依赖注入）
            tools.add(new ScaleTool(appState, snapManager));   // 缩放工具（依赖注入）
            tools.add(new MirrorTool(appState, snapManager));  // 镜像工具（依赖注入）
            tools.add(new AlignTool(appState, snapManager));   // 对齐工具（依赖注入）
            tools.add(new ArrayTool(appState, snapManager));   // 阵列工具（依赖注入）
            tools.add(new OffsetTool(appState, snapManager));
            
            // 编辑工具 - 这些工具通常需要选中对象才能使用
            tools.add(new BreakTool(appState, snapManager));
            tools.add(new FilletTool(appState, snapManager));
            tools.add(new ChamferTool(appState, snapManager));
            tools.add(new ExtendTool(appState, snapManager));
            tools.add(new TrimTool(appState, snapManager));
            tools.add(new TransformTool(appState, snapManager, eventBus));
            
            // 标注工具
            tools.add(new AnnotationTool(appState, snapManager)); // 标注工具（依赖注入）
            
            LOGGER.debug("成功创建 {} 个修改工具", tools.size());
            
            // 记录创建的工具列表用于调试
            tools.forEach(tool -> 
                LOGGER.debug("已创建修改工具: id={}, name={}, 类型={}", 
                    tool.getId(), tool.getName(), tool.getClass().getSimpleName()));
            
        } catch (Exception e) {
            LOGGER.error("创建修改工具时发生错误", e);
            throw new RuntimeException(PlotI18n.error("error.plot.tool.create_modify_failed", e.getMessage()), e);
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
     * @param modifyGroup 修改工具组
     * @param tools 要注册的工具列表
     * @throws RuntimeException 如果任何工具注册失败
     */
    private static void registerTools(ToolManager toolManager, ToolGroup modifyGroup, List<BaseTool> tools) {
        LOGGER.debug("开始注册修改工具到ToolManager...");
        
        int successCount = 0;
        
        for (BaseTool tool : tools) {
            try {
                // 注册到ToolManager（这是工具管理的唯一入口）
                toolManager.registerTool(tool);
                
                // 添加到修改工具组
                modifyGroup.addTool(tool);
                
                successCount++;
                LOGGER.debug("修改工具注册成功: id={}, name={}", tool.getId(), tool.getName());
                
            } catch (Exception e) {
                // 激进策略：立即抛出异常，中断初始化过程
                String errorMsg = PlotI18n.status("status.plot.module.register_modify_tool_failed", tool.getName());
                LOGGER.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
        }
        
        LOGGER.info("修改工具注册完成: 成功注册 {} 个工具", successCount);
    }

    /**
     * 验证修改工具模块是否正确初始化（仅用于调试）
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
            // 检查是否有修改工具组
            ToolGroup modifyGroup = toolManager.findGroupByName(MODIFY_GROUP_NAME);
            
            if (modifyGroup == null) {
                LOGGER.error("验证失败: 未找到修改工具组");
                return false;
            }
            
            // 检查是否有选择工具
            boolean hasSelectionTool = toolManager.getRegisteredTools().stream()
                .anyMatch(tool -> "select".equals(tool.getId()) || "selection".equals(tool.getId()));
            
            if (!hasSelectionTool) {
                LOGGER.error("验证失败: 未找到选择工具");
                return false;
            }
            
            LOGGER.info("修改工具模块验证成功: 工具组={}, 修改工具数量={}", 
                modifyGroup.getName(), modifyGroup.getTools().size());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("验证修改工具模块时发生错误", e);
            return false;
        }
    }
} 