package com.masterplanner;

import com.masterplanner.core.command.CommandManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.ToolManager;
import com.masterplanner.ui.canvas.Canvas;
import com.masterplanner.ui.screen.MasterPlannerInitializer;
import com.masterplanner.ui.manager.UIManager;
import com.masterplanner.ui.tools.DrawingToolsModule;
import com.masterplanner.ui.tools.ModifyToolsModule;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.registry.ModItems;
import com.masterplanner.infrastructure.event.command.CommandEventListener;
import com.masterplanner.core.shortcut.ShortcutManager;
import com.masterplanner.ui.shortcut.EditShortcutListener;
import com.masterplanner.ui.shortcut.DeleteShortcutListener;
import com.masterplanner.ui.shortcut.EscapeShortcutListener;
import com.masterplanner.ui.imgui.ImGuiWorldRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MasterPlanner 模组主类 (最终修复版 V3 - 优化版)
 * <p>
 * 主要优化：
 * 1. 严格分离服务端和客户端初始化逻辑
 * 2. 使用 UIManager 替代静态屏幕管理方法，提高可测试性
 * 3. 将客户端特有的组件（事件监听器、快捷键）移到客户端初始化中
 * 4. 改进职责分离和模块边界清晰度
 * <p>
 * 职责：
 * - onInitialize: 仅处理通用逻辑（可在服务端运行）
 * - onInitializeClient: 处理客户端特有逻辑（UI、事件、快捷键等）
 */
public class MasterPlannerMod implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "masterplanner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openScreenKey;

    /**
     * 预加载关键API类，防止NoClassDefFoundError
     * 这个方法强制JVM加载所有关键的API接口，确保在使用时能找到它们
     */
    private void preloadAPIClasses() {
        LOGGER.debug("预加载关键API类...");
        
        try {
            // 强制加载关键的API接口类
            // 核心状态和管理接口
            Class.forName("com.masterplanner.api.state.IAppState");
            Class.forName("com.masterplanner.api.shortcut.IShortcutListener");
            Class.forName("com.masterplanner.api.shortcut.IShortcutManager");
            Class.forName("com.masterplanner.api.snap.ISnapManager");
            Class.forName("com.masterplanner.api.resource.IDisposable");
            
            // 图形和样式接口
            Class.forName("com.masterplanner.api.graphics.IShapeStyle");
            Class.forName("com.masterplanner.api.graphics.ILineStyle");
            Class.forName("com.masterplanner.api.graphics.IFillStyle");
            
            // 模型和图层接口
            Class.forName("com.masterplanner.api.model.ILayer");
            Class.forName("com.masterplanner.api.model.ICanvas");
            Class.forName("com.masterplanner.api.model.IElement");
            
            // 工具相关接口
            Class.forName("com.masterplanner.api.tool.ITool");
            Class.forName("com.masterplanner.api.tool.IToolManager");
            Class.forName("com.masterplanner.api.tool.ISelectionAwareTool");
            
            // 命令和事件接口
            Class.forName("com.masterplanner.api.command.ICommand");
            Class.forName("com.masterplanner.api.command.ICommandManager");
            Class.forName("com.masterplanner.api.event.IEvent");
            Class.forName("com.masterplanner.api.event.IEventListener");
            
            // 工具工厂类（防止运行时 NoClassDefFoundError）
            Class.forName("com.masterplanner.ui.tools.ToolFactory");
            
            // 空间索引类（防止内部类 Entry/Node 加载失败）
            Class.forName("com.masterplanner.core.snap.SpatialIndex");
            
            LOGGER.debug("关键API类预加载完成 (已加载 {} 个接口、工具工厂和空间索引)", 20);
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("预加载API类失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法加载关键API类: " + e.getMessage(), e);
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("初始化 Master Planner Mod (通用逻辑)...");

        try {
            // 0. 预加载关键API类，确保类加载器能找到它们
            preloadAPIClasses();
            
            // 1. 获取 AppState 单例
            AppState appState = AppState.getInstance();

            // 2. 在 AppState 内部初始化它的核心子系统
            appState.initializeLayerSystem();
            appState.initializePluginSystem();
            
            // 3. 初始化其他核心逻辑，并将 AppState 作为依赖注入
            ToolManager.initialize(appState);
            appState.setToolManager(ToolManager.getInstance()); // 将创建的实例注册回 AppState
            
            // 4. 初始化CommandManager（单例模式，无需initialize方法）
            CommandManager.getInstance();

            // 5. 注册物品（通用逻辑，服务端也需要）
            ModItems.registerItems();

            LOGGER.info("Master Planner Mod (通用逻辑) 初始化成功!");
            
        } catch (Exception e) {
            LOGGER.error("Master Planner Mod (通用逻辑) 初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("模组初始化失败", e);
        }
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("初始化 Master Planner Mod (客户端逻辑)...");

        try {
            // 0. 额外确保API类已加载（双重保险）
            preloadAPIClasses();
            
            // 1. 获取已经部分初始化的 AppState
            LOGGER.debug("步骤1: 获取AppState实例");
            AppState appState = AppState.getInstance();

            // 2. 初始化仅客户端相关的系统，如 ImGui
            LOGGER.debug("步骤2: 初始化MasterPlannerInitializer");
            MasterPlannerInitializer.initialize();

            // 3. 注册 1.21.11 稳定的 ImGui 贴屏绘制入口（WorldRenderEvents.END）
            ImGuiWorldRenderer.init();

            // 4. 创建 Canvas 实例，并将 AppState 注入
            LOGGER.debug("步骤4: 创建Canvas实例");
            Canvas canvas = new Canvas(appState);

            // 5. 将 Canvas 实例注册回 AppState，完成双向绑定
            LOGGER.debug("步骤5: 注册Canvas到AppState");
            appState.setCanvas(canvas);
            
            // 6. 初始化绘图工具模块 - 注册所有绘图工具到ToolManager
            LOGGER.debug("步骤6: 初始化绘图工具模块");
            initializeDrawingTools(appState);
            
            // 7. 初始化客户端特有的事件和快捷键系统
            LOGGER.debug("步骤7: 初始化客户端事件系统");
            initializeClientEventSystems();
            
            // 8. 注册键绑定和客户端事件
            LOGGER.debug("步骤8: 注册键绑定和事件");
            registerKeyBindingsAndEvents();
            
            // 9. 延迟初始化方块图标渲染器（等待客户端完全启动）
            LOGGER.debug("步骤9: 注册BlockIconRenderer延迟初始化");
            registerDelayedBlockIconRendererInitialization();

            LOGGER.info("Master Planner Mod (客户端逻辑) 初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("Master Planner Mod (客户端逻辑) 初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("客户端初始化失败", e);
        }
    }
    
    /**
     * 初始化绘图工具模块
     * 在Canvas创建完成后调用，确保所有绘图工具都能正确访问Canvas
     * 
     * <p>实现完全的依赖注入，避免DrawingToolsModule内部使用单例模式</p>
     */
    private void initializeDrawingTools(AppState appState) {
        try {
            LOGGER.debug("开始初始化绘图工具模块...");
            
            // 获取必要的依赖 - 将来可考虑进一步重构为完全的依赖注入
            ToolManager toolManager = ToolManager.getInstance();
            EventBus eventBus = EventBus.getInstance();
            com.masterplanner.core.snap.SnapManager snapManager = com.masterplanner.core.snap.SnapManager.getInstance();
            CommandManager commandManager = CommandManager.getInstance();
            
            // 使用新的DrawingToolsModule进行绘图工具注册，传递所有依赖
            DrawingToolsModule.initializeAndRegister(
                toolManager, 
                appState, 
                eventBus,
                snapManager,
                commandManager
            );
            
            // 使用新的ModifyToolsModule进行修改工具注册
            ModifyToolsModule.initializeAndRegister(
                toolManager,
                appState,
                eventBus,
                snapManager,
                commandManager
            );
            
            // 验证初始化结果
            boolean drawingToolsOk = DrawingToolsModule.verifyInitialization(toolManager);
            boolean modifyToolsOk = ModifyToolsModule.verifyInitialization(toolManager);
            
            if (drawingToolsOk && modifyToolsOk) {
                LOGGER.info("所有工具模块初始化成功");
            } else {
                if (!drawingToolsOk) {
                    LOGGER.warn("绘图工具模块初始化可能不完整，请检查日志");
                }
                if (!modifyToolsOk) {
                    LOGGER.warn("修改工具模块初始化可能不完整，请检查日志");
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("初始化绘图工具模块失败: {}", e.getMessage(), e);
            throw new RuntimeException("绘图工具初始化失败", e);
        }
    }
    
    /**
     * 初始化客户端特有的事件和快捷键系统
     */
    private void initializeClientEventSystems() {
        try {
            // 初始化命令事件监听器（客户端特有）
            CommandEventListener.getInstance();
            
            // 初始化快捷键管理器并添加监听器（客户端特有）
            ShortcutManager shortcutManager = ShortcutManager.getInstance();
            shortcutManager.addListener(new EditShortcutListener());
            shortcutManager.addListener(new DeleteShortcutListener());
            shortcutManager.addListener(new EscapeShortcutListener());
            
            // 初始化线转方块事件处理器（客户端特有）
            com.masterplanner.infrastructure.event.block.LineToBlockHandler.getInstance();
            
            // 初始化方块投影事件处理器（客户端特有）
            com.masterplanner.infrastructure.event.block.BlockProjectionHandler.getInstance();
            
            LOGGER.debug("客户端事件系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("初始化客户端事件系统失败: {}", e.getMessage(), e);
            throw new RuntimeException("客户端事件系统初始化失败", e);
        }
    }
    
    /**
     * 注册延迟的方块图标渲染器初始化
     * 等待客户端完全启动后再初始化，避免线程检查失败
     */
    private void registerDelayedBlockIconRendererInitialization() {
        // 渲染器现在为无状态安全实现；保留一个轻量的预加载触发器以填充缓存
        final boolean[] preloaded = {false};
        LOGGER.info("注册BlockIconRenderer预加载事件监听器 (轻量)");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!preloaded[0] && client.player != null) {
                try {
                    LOGGER.info("触发 BlockIconRenderer 预加载常用方块缓存");
                    com.masterplanner.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog tmp = null; // placeholder
                    // Use the compat preload method to warm caches
                    com.masterplanner.ui.component.BlockIconRenderer.preloadCommonBlocks();
                    LOGGER.info("BlockIconRenderer 预加载完成: {}", com.masterplanner.ui.component.BlockIconRenderer.getCacheStats());
                } catch (Exception e) {
                    LOGGER.warn("BlockIconRenderer 预加载失败: {}", e.getMessage());
                } finally {
                    preloaded[0] = true;
                }
            }
        });
        LOGGER.info("BlockIconRenderer 预加载事件监听器已注册");
    }

    /**
     * 注册键绑定和客户端事件
     */
    private void registerKeyBindingsAndEvents() {
        try {
            // 注册键绑定
            openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.masterplanner.open_screen", 
                    InputUtil.Type.KEYSYM, 
                    GLFW.GLFW_KEY_P, 
                    KeyBinding.Category.MISC));

            // 注册客户端tick事件
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    if (openScreenKey.wasPressed() && client.player != null) {
                        // 使用 UIManager 替代静态方法，提高可测试性
                        UIManager.getInstance().openMasterPlannerScreen();
                    }
                } catch (Exception e) {
                    LOGGER.error("处理键盘事件时发生错误: {}", e.getMessage(), e);
                }
            });
            
            LOGGER.debug("键绑定和事件注册完成");
            
        } catch (Exception e) {
            LOGGER.error("注册键绑定和事件失败: {}", e.getMessage(), e);
            throw new RuntimeException("键绑定注册失败", e);
        }
    }

    /**
     * 获取 UIManager 实例
     * 提供对外访问 UIManager 的便利方法
     * 
     * @return UIManager 单例实例
     */
    public static UIManager getUIManager() {
        return UIManager.getInstance();
    }
}