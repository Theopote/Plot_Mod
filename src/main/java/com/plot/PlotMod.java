package com.plot;

import com.plot.core.command.CommandManager;
import com.plot.core.state.AppState;
import com.plot.core.tool.ToolManager;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.manager.UIManager;
import com.plot.ui.tools.DrawingToolsModule;
import com.plot.ui.tools.ModifyToolsModule;
import com.plot.infrastructure.event.EventBus;
import com.plot.registry.ModItems;
import com.plot.infrastructure.event.command.CommandEventListener;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.ui.shortcut.EditShortcutListener;
import com.plot.ui.shortcut.DeleteShortcutListener;
import com.plot.ui.shortcut.EscapeShortcutListener;
import com.plot.ui.imgui.ImGuiWorldRenderer;
import com.plot.ui.utils.PlotTextureLifecycle;
import com.plot.infrastructure.event.block.GhostBlockWorldRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 模组主类 (最终修复版 V3 - 优化版)
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
public class PlotMod implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "plot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openScreenKey;

    @Override
    public void onInitialize() {
        LOGGER.info("初始化 Master Planner Mod (通用逻辑)...");

        try {
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
            ModItems.registerItemGroups();

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
            // 1. 获取已经部分初始化的 AppState
            LOGGER.debug("步骤1: 获取AppState实例");
            AppState appState = AppState.getInstance();

            // 2. ImGui 改为延迟初始化：在用户首次打开 Plot 界面时再初始化
            //    避免在模组启动阶段初始化时 OpenGL 上下文未就绪导致的 "GImGui != NULL" 断言失败
            //    见 UIManager.openPlotScreen() 中的调用

             // 3. 注册 1.21.11 稳定的 ImGui 贴屏绘制入口（WorldRenderEvents.END）
            ImGuiWorldRenderer.init();

            // 3.1 注册幽灵方块世界渲染（线转方块预览）
            GhostBlockWorldRenderer.init();

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

            registerTextureReloadListener();

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
            com.plot.core.snap.SnapManager snapManager = com.plot.core.snap.SnapManager.getInstance();
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
            com.plot.infrastructure.event.block.LineToBlockHandler.getInstance();
            
            // 初始化方块投影事件处理器（客户端特有）
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance();
            
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
        // 离屏 FBO 须在渲染线程执行；与预加载拆开：processQueue 每帧都跑，不依赖玩家；预加载仅一次且需已进入世界
        final boolean[] preloaded = {false};
        LOGGER.info("注册 BlockIconRenderer：END_MAIN 每帧 processQueue（独立）");
        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            com.plot.ui.component.BlockIconRenderer.getInstance()
                .processQueue(com.plot.ui.component.BlockIconRenderer.DEFAULT_RENDER_BUDGET);
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            if (preloaded[0]) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                return;
            }
            try {
                LOGGER.info("触发 BlockIconRenderer 预加载常用方块缓存");
                com.plot.ui.component.BlockIconRenderer.preloadCommonBlocks();
                LOGGER.info("BlockIconRenderer 预加载完成: {}", com.plot.ui.component.BlockIconRenderer.getCacheStats());
            } catch (Exception e) {
                LOGGER.warn("BlockIconRenderer 预加载失败: {}", e.getMessage(), e);
            } finally {
                preloaded[0] = true;
            }
        });
        LOGGER.info("BlockIconRenderer：END_MAIN 预加载（world 非空时）已注册");
    }

    private void registerTextureReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(MOD_ID, "plot_textures");
                    }

                    @Override
                    public void reload(ResourceManager resourceManager) {
                        LOGGER.debug("资源包已重载，释放 Plot UI 纹理缓存");
                        PlotTextureLifecycle.disposeAll();
                    }
                }
        );
    }

    /**
     * 注册键绑定和客户端事件
     */
    private void registerKeyBindingsAndEvents() {
        try {
            // 注册键绑定
            openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.plot.open_screen", 
                    InputUtil.Type.KEYSYM, 
                    GLFW.GLFW_KEY_P, 
                    KeyBinding.Category.MISC));

            // 注册客户端tick事件
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    if (openScreenKey.wasPressed() && client.player != null) {
                        // 使用 UIManager 替代静态方法，提高可测试性
                        UIManager.getInstance().openPlotScreen();
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
