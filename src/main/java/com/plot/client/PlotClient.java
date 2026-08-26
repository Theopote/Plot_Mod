package com.plot.client;

import com.plot.PlotMod;
import com.plot.core.command.CommandService;
import com.plot.core.plugin.PluginManager;
import com.plot.core.state.AppState;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.block.GhostBlockWorldRenderer;
import com.plot.infrastructure.event.block.LineToBlockHandler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.manager.UIManager;
import com.plot.ui.shortcut.DeleteShortcutListener;
import com.plot.ui.shortcut.EditShortcutListener;
import com.plot.ui.shortcut.EscapeShortcutListener;
import com.plot.ui.imgui.ImGuiWorldRenderer;
import com.plot.ui.tools.DrawingToolsModule;
import com.plot.ui.tools.ModifyToolsModule;
import com.plot.ui.utils.PlotTextureLifecycle;
import com.plot.utils.PlotI18n;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Plot 客户端入口：UI、渲染、键位与客户端事件仅在物理客户端加载并初始化。
 */
public final class PlotClient implements ClientModInitializer {
    private static final Identifier PLOT_TEXTURES_RELOADER_ID =
            Identifier.of(PlotMod.MOD_ID, "plot_textures");

    private static KeyBinding openScreenKey;

    @Override
    public void onInitializeClient() {
        PlotMod.LOGGER.info("初始化 Master Planner Mod (客户端逻辑)...");

        try {
            PlotMod.LOGGER.debug("步骤1: 获取AppState实例");
            AppState appState = AppState.getInstance();
            appState.ensureDefaultLayer();

            ImGuiWorldRenderer.init();
            GhostBlockWorldRenderer.init();

            PlotMod.LOGGER.debug("步骤4: 创建Canvas实例");
            Canvas canvas = new Canvas(appState);

            PlotMod.LOGGER.debug("步骤5: 注册Canvas到AppState");
            appState.setCanvas(canvas);

            PlotMod.LOGGER.debug("步骤6: 初始化绘图工具模块");
            initializeDrawingTools(appState);

            PlotMod.LOGGER.debug("步骤7: 初始化客户端事件系统");
            initializeClientEventSystems();

            PlotMod.LOGGER.debug("步骤8: 注册键绑定和事件");
            registerKeyBindingsAndEvents();
            registerClientShutdownHooks();

            PlotMod.LOGGER.debug("步骤9: 注册BlockIconRenderer延迟初始化");
            registerDelayedBlockIconRendererInitialization();

            registerTextureReloadListener();

            PlotMod.LOGGER.info("Master Planner Mod (客户端逻辑) 初始化完成");
        } catch (Exception e) {
            PlotMod.LOGGER.error("Master Planner Mod (客户端逻辑) 初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.client_failed"), e);
        }
    }

    /**
     * 打开 Plot 设计器界面（供物品右键等客户端入口调用）。
     */
    public static void openPlotScreen() {
        UIManager.getInstance().openPlotScreen();
    }

    public static UIManager getUIManager() {
        return UIManager.getInstance();
    }

    private void initializeDrawingTools(AppState appState) {
        try {
            PlotMod.LOGGER.debug("开始初始化绘图工具模块...");

            ToolManager toolManager = ToolManager.getInstance();
            EventBus eventBus = EventBus.getInstance();
            com.plot.core.snap.SnapManager snapManager = com.plot.core.snap.SnapManager.getInstance();
            CommandService commandService = CommandService.getInstance();

            DrawingToolsModule.initializeAndRegister(
                    toolManager,
                    appState,
                    eventBus,
                    snapManager,
                    commandService
            );

            ModifyToolsModule.initializeAndRegister(
                    toolManager,
                    appState,
                    eventBus,
                    snapManager,
                    commandService
            );

            boolean drawingToolsOk = DrawingToolsModule.verifyInitialization(toolManager);
            boolean modifyToolsOk = ModifyToolsModule.verifyInitialization(toolManager);

            if (drawingToolsOk && modifyToolsOk) {
                PlotMod.LOGGER.info("所有工具模块初始化成功");
            } else {
                if (!drawingToolsOk) {
                    PlotMod.LOGGER.warn("绘图工具模块初始化可能不完整，请检查日志");
                }
                if (!modifyToolsOk) {
                    PlotMod.LOGGER.warn("修改工具模块初始化可能不完整，请检查日志");
                }
            }

            toolManager.loadToolConfigs();
        } catch (Exception e) {
            PlotMod.LOGGER.error("初始化绘图工具模块失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.drawing_tools_failed"), e);
        }
    }

    private void initializeClientEventSystems() {
        try {
            CommandService.getInstance();
            ShortcutManager shortcutManager = ShortcutManager.getInstance();
            shortcutManager.addListener(new EditShortcutListener());
            shortcutManager.addListener(new DeleteShortcutListener());
            shortcutManager.addListener(new EscapeShortcutListener());

            LineToBlockHandler.getInstance();
            BlockProjectionHandler.getInstance();

            PlotMod.LOGGER.debug("客户端事件系统初始化完成");
        } catch (Exception e) {
            PlotMod.LOGGER.error("初始化客户端事件系统失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.client_events_failed"), e);
        }
    }

    private void registerDelayedBlockIconRendererInitialization() {
        final boolean[] preloaded = {false};
        PlotMod.LOGGER.info("注册 BlockIconRenderer：END_MAIN 每帧 processQueue（独立）");
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
                PlotMod.LOGGER.info("触发 BlockIconRenderer 预加载常用方块缓存");
                com.plot.ui.component.BlockIconRenderer.preloadCommonBlocks();
                PlotMod.LOGGER.info("BlockIconRenderer 预加载完成: {}",
                        com.plot.ui.component.BlockIconRenderer.getCacheStats());
            } catch (Exception e) {
                PlotMod.LOGGER.warn("BlockIconRenderer 预加载失败: {}", e.getMessage(), e);
            } finally {
                preloaded[0] = true;
            }
        });
        PlotMod.LOGGER.info("BlockIconRenderer：END_MAIN 预加载（world 非空时）已注册");
    }

    private void registerTextureReloadListener() {
        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(
                PLOT_TEXTURES_RELOADER_ID,
                new SynchronousResourceReloader() {
                    @Override
                    public void reload(ResourceManager resourceManager) {
                        PlotMod.LOGGER.debug("资源包已重载，释放 Plot UI 纹理缓存");
                        PlotTextureLifecycle.disposeAll();
                    }

                    @Override
                    public String getName() {
                        return PLOT_TEXTURES_RELOADER_ID.toString();
                    }
                }
        );
    }

    private void registerKeyBindingsAndEvents() {
        try {
            openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.plot.open_screen",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_0,
                    KeyBinding.Category.MISC));

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    if (openScreenKey.wasPressed() && client.player != null) {
                        openPlotScreen();
                    }
                } catch (Exception e) {
                    PlotMod.LOGGER.error("处理键盘事件时发生错误: {}", e.getMessage(), e);
                }
            });

            PlotMod.LOGGER.debug("键绑定和事件注册完成");
        } catch (Exception e) {
            PlotMod.LOGGER.error("注册键绑定和事件失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.keybindings_failed"), e);
        }
    }

    private void registerClientShutdownHooks() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                PlotMod.LOGGER.info("客户端关闭，卸载插件并保存配置...");
                PluginManager.getInstance().unloadAll();
            } catch (Exception e) {
                PlotMod.LOGGER.error("插件卸载失败: {}", e.getMessage(), e);
            }
        });
    }
}
