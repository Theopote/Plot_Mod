package com.masterplanner.ui.screen;

import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.imgui.ImGuiRenderer;
import com.masterplanner.ui.toolbar.ControlPanel;
import com.masterplanner.ui.toolbar.ToolPanel;
import com.masterplanner.ui.canvas.Canvas;
import com.masterplanner.ui.layout.UILayout;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.panel.PropertyPanel;
import com.masterplanner.ui.panel.extension.ExtensionPanel;
import com.masterplanner.ui.panel.gallery.GalleryPanel;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.MinecraftClient;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.client.util.Window;
import com.masterplanner.ui.container.UIContainer;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.camera.CameraManager;
import com.masterplanner.MasterPlannerMod;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.core.shortcut.ShortcutManager;
import com.masterplanner.core.shortcut.KeyboardShortcutConverter;
import com.masterplanner.api.geometry.Vec2d;
/**
 * MasterPlanner 主界面类
 * 职责：渲染UI，并将用户输入转发给核心处理器。不管理核心状态。
 * 【优化】增强事件处理、全屏切换优化、日志优化
 */
public class MasterPlannerScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MasterPlannerScreen.class);
    
    private final AppState appState;
    private final ImGuiRenderer imGuiRenderer;
    private final UIContainer uiContainer;
    
    // 全屏状态跟踪
    private boolean wasFullscreen = false;
    
    // 【新增】鼠标移动日志控制
    private static final boolean ENABLE_MOUSE_MOVE_LOGGING = false; // 默认关闭TRACE日志
    private long lastMouseMoveLogTime = 0;
    private static final long MOUSE_MOVE_LOG_INTERVAL = 1000; // 1秒间隔

    // Dockable 窗口标志：允许用户拖动/缩放/停靠
    private static final int DOCKABLE_WINDOW_FLAGS =
        ImGuiWindowFlags.NoCollapse |
        ImGuiWindowFlags.NoScrollbar;

    // ---- DockSpace（可停靠布局）----
    private static final String DOCKSPACE_HOST_WINDOW = "MasterPlannerDockSpace##DockspaceHost";
    private static final String DOCKSPACE_ID_STR = "MasterPlannerDockSpace";

    private static final String WIN_TOP = "ControlPanel##ControlPanel";
    private static final String WIN_LEFT = "ToolPanel##ToolPanel";
    // 右侧拆分为三个独立窗口，Dock 到同一个 right dock node 后会自动以 Tab 形式组合展示
    private static final String WIN_RIGHT_PROPERTY = "属性##PropertyPanel";
    private static final String WIN_RIGHT_GALLERY = "图库##GalleryPanel";
    private static final String WIN_RIGHT_EXTENSION = "扩展##ExtensionPanel";

    private int dockspaceId;
    private int dockIdTop;
    private int dockIdLeft;
    private int dockIdRight;
    private boolean dockLayoutBuilt;
    private int lastDockW;
    private int lastDockH;

    /**
     * 构造函数
     * 初始化所有UI组件和ImGui渲染器
     */
    public MasterPlannerScreen() {
        super(Text.translatable("screen.masterplanner.title"));
        LOGGER.debug("创建 MasterPlannerScreen 实例...");

        // 获取核心依赖
        this.appState = AppState.getInstance();
        this.imGuiRenderer = ImGuiRenderer.getInstance();
        this.uiContainer = UIContainer.getInstance();

        // 注册已经存在的UI组件实例到容器中，以便渲染时获取
        registerComponents();
        
        // 相机操作
        CameraManager.getInstance().saveCurrentState();
        if (!CameraManager.getInstance().isOrthographic()) {
            CameraManager.getInstance().toggleCamera();
        }
        
        // 设置 MasterPlanner 屏幕打开状态（用于控制云渲染和雾渲染）
        MasterPlannerScreenState.setMasterPlannerScreenOpen(true);
        
        // 隐藏 HUD（物品栏和玩家）
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.options.hudHidden = true;
            LOGGER.debug("已隐藏 HUD（物品栏和玩家）");
        }
    }

    /**
     * 将 AppState 中持有的核心UI组件注册到UI容器中，以便渲染循环使用
     */
    private void registerComponents() {
        LOGGER.debug("注册UI组件到容器...");
        // 从 AppState 获取已经完全初始化好的 Canvas
        Canvas canvas = appState.getCanvas();
        if (canvas != null) {
            uiContainer.register(Canvas.class, canvas);
        } else {
            LOGGER.error("registerComponents: AppState.getCanvas() 返回 null，Canvas 未注册");
        }
        
        // 其他UI组件
        uiContainer.register(ControlPanel.class, new ControlPanel());
        uiContainer.register(ToolPanel.class, new ToolPanel());
        uiContainer.register(PropertyPanel.class, new PropertyPanel());
        uiContainer.register(GalleryPanel.class, new GalleryPanel());
        uiContainer.register(ExtensionPanel.class, new ExtensionPanel());
        LOGGER.debug("UI组件注册完成。");
    }

    /**
     * init() 方法负责UI布局、样式和组件初始化
     */
    @Override
    protected void init() {
        LOGGER.debug("初始化 MasterPlannerScreen 布局和样式...");
        
        this.imGuiRenderer.updateDisplaySize();
        UITheme.applyGlobalStyle();

        // 初始化所有UI组件
        ControlPanel controlPanel = uiContainer.get(ControlPanel.class);
        ToolPanel toolPanel = uiContainer.get(ToolPanel.class);
        PropertyPanel propertyPanel = uiContainer.get(PropertyPanel.class);
        GalleryPanel galleryPanel = uiContainer.get(GalleryPanel.class);
        ExtensionPanel extensionPanel = uiContainer.get(ExtensionPanel.class);
        Canvas canvas = uiContainer.get(Canvas.class);

        if (controlPanel != null) {
            controlPanel.init();
        }
        if (toolPanel != null) {
            toolPanel.init();
        }
        if (propertyPanel != null) propertyPanel.init();
        if (galleryPanel != null) galleryPanel.init();
        if (extensionPanel != null) extensionPanel.init();
        if (canvas != null) {
            // 修复：确保Canvas被正确初始化，包括CanvasCore的camera
            canvas.init();
            canvas.refresh();
            LOGGER.debug("Canvas已初始化并刷新");
        }

        // 初始化键位管理（注册快捷键路由、加载keymap.json）
        try {
            com.masterplanner.ui.dialog.KeymapManager.getInstance();
        } catch (Exception e) {
            LOGGER.error("初始化 KeymapManager 失败", e);
        }

        LOGGER.debug("MasterPlannerScreen 初始化完成");
    }

    /**
     * 渲染方法
     * 每帧调用，负责渲染整个界面
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            // 更新相机状态
            CameraManager.getInstance().onFrame();
            
            // 获取 ImGui 渲染器实例
            if (imGuiRenderer == null) {
                LOGGER.error("ImGui渲染器为null，跳过渲染");
                drawFatalOverlay(context, "ImGuiRenderer 为 null（UI 未初始化）");
                return;
            }
            
            // 检查ImGui初始化状态
            if (!imGuiRenderer.isInitialized()) {
                LOGGER.warn("ImGui未初始化，尝试重新初始化");
                try {
                    imGuiRenderer.init();
                } catch (Exception e) {
                    LOGGER.error("重新初始化ImGui失败", e);
                    drawFatalOverlay(context, "ImGui 初始化失败: " + safeMsg(e));
                    return;
                }
            }
            
            // 检查窗口状态（全屏模式检测）
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                Window window = client.getWindow();
                boolean isFullscreen = window.isFullscreen();
                
                // 如果检测到全屏状态变化，强制更新显示尺寸
                if (isFullscreen != wasFullscreen) {
                    LOGGER.info("检测到全屏状态变化: {} -> {}", wasFullscreen, isFullscreen);
                    wasFullscreen = isFullscreen;
                    imGuiRenderer.updateDisplaySize();
                }
            }
            
            // 开始新的 ImGui 帧
            imGuiRenderer.beginFrame();
            // DisplaySize/FramebufferScale 由 ImGuiRenderer.updateDisplaySize() 统一维护（1.21.x 下更稳定）
            
            // 渲染所有 UI 组件
            try {
                renderUI();
            } catch (Throwable uiErr) {
                LOGGER.error("renderUI 失败", uiErr);
                drawFatalOverlay(context, "renderUI 异常: " + safeMsg(uiErr));
            }

            // 渲染回退的 ImGui 文字输入对话框（如果开启）
            try {
                com.masterplanner.ui.dialog.TextInputDialog.getInstance().render();
            } catch (Throwable ignored) {}
            
            // 结束 ImGui 帧并立即渲染
            imGuiRenderer.endFrame();
            
            // 在ImGui渲染后，执行覆盖层渲染（用于物品/方块图标）
            try {
                com.masterplanner.ui.imgui.GuiOverlayRenderer.flush(context);
            } catch (Exception overlayErr) {
                LOGGER.error("GuiOverlayRenderer 覆盖渲染失败", overlayErr);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error rendering MasterPlanner UI", e);
            drawFatalOverlay(context, "MasterPlannerScreen.render 异常: " + safeMsg(e));
        }
    }

    /**
     * 关闭 Minecraft 默认的“模糊/半透明”屏幕背景。
     * 1.21.x 下 Screen#renderWithTooltip 会在调用 render(...) 前先调用 renderBackground(...)，
     * 如果不覆写，这里会把游戏画面做一层模糊/变暗，造成“UI 被半透明背景遮住”的观感。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no-op: 不绘制任何背景/模糊层
    }

    private static String safeMsg(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        if (m == null || m.isBlank()) m = t.getClass().getSimpleName();
        return m;
    }

    /**
     * 当 ImGui 渲染链路失败时，直接用 Minecraft 的 DrawContext 画出错误文本，避免“只有模糊背景”。
     */
    private void drawFatalOverlay(DrawContext context, String msg) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            TextRenderer tr = client.textRenderer;
            if (tr == null) return;
            int x = 10;
            int y = 10;
            context.drawTextWithShadow(tr, "MasterPlanner UI 渲染失败（1.21.11 适配中）", x, y, 0xFFFF5555);
            context.drawTextWithShadow(tr, msg, x, y + 12, 0xFFFFFFFF);
            context.drawTextWithShadow(tr, "请查看日志：ImGuiRenderer / MasterPlannerScreen", x, y + 24, 0xFFAAAAAA);
        } catch (Throwable ignored) {
        }
    }

    private void renderUI() {
        // 渲染各个组件
        ControlPanel controlPanel = uiContainer.get(ControlPanel.class);
        ToolPanel toolPanel = uiContainer.get(ToolPanel.class);
        PropertyPanel propertyPanel = uiContainer.get(PropertyPanel.class);
        GalleryPanel galleryPanel = uiContainer.get(GalleryPanel.class);
        ExtensionPanel extensionPanel = uiContainer.get(ExtensionPanel.class);
        Canvas canvas = uiContainer.get(Canvas.class);

        float displayWidth = ImGui.getIO().getDisplaySizeX();
        float displayHeight = ImGui.getIO().getDisplaySizeY();

        if(displayWidth == 0 || displayHeight == 0) return;

        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();

        // 设置全局边框颜色
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);
        // 不要设置全局不透明背景色，否则会遮挡 Minecraft 场景
        ImGui.pushStyleColor(ImGuiCol.WindowBg, imgui.ImColor.rgba(0, 0, 0, 0));

        try {
            // 先渲染“全屏画布背景”（永远铺满屏幕，位于最底层，不参与 DockSpace）
            if (canvas != null) {
                canvas.setUseDockingLayout(false);
                canvas.render();
            }

            // 再渲染 DockSpace 与面板窗口（位于画布之上）
            renderDockSpaceLayout(displayWidth, displayHeight, controlPanel, toolPanel, propertyPanel, galleryPanel, extensionPanel);
        } finally {
            ImGui.popStyleColor(2);
        }
    }

    /**
     * DockSpace 布局：Top/Left/Right 作为可停靠窗口，Canvas 固定在 central node。
     */
    private void renderDockSpaceLayout(float displayWidth, float displayHeight,
                                       ControlPanel controlPanel,
                                       ToolPanel toolPanel,
                                       PropertyPanel propertyPanel,
                                       GalleryPanel galleryPanel,
                                       ExtensionPanel extensionPanel) {
        // 1) 创建全屏 DockSpace 宿主窗口
        final int hostFlags =
            ImGuiWindowFlags.NoTitleBar |
            ImGuiWindowFlags.NoCollapse |
            ImGuiWindowFlags.NoResize |
            ImGuiWindowFlags.NoMove |
            ImGuiWindowFlags.NoBringToFrontOnFocus |
            ImGuiWindowFlags.NoNavFocus |
            // 关键：宿主窗口不需要接收输入，否则会挡住底层全屏 Canvas 的绘制交互
            ImGuiWindowFlags.NoInputs |
            ImGuiWindowFlags.NoBackground |
            ImGuiWindowFlags.NoDocking;

        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(displayWidth, displayHeight, ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.begin(DOCKSPACE_HOST_WINDOW, hostFlags);

        dockspaceId = ImGui.getID(DOCKSPACE_ID_STR);
        int dockFlags = ImGuiDockNodeFlags.PassthruCentralNode;
        ImGui.dockSpace(dockspaceId, 0.0f, 0.0f, dockFlags);

        // 默认布局：自动分割 Top/Left/Right/Bottom + Central(Canvas)
        ensureDockLayout(displayWidth, displayHeight);

        ImGui.end();
        ImGui.popStyleVar(3);

        // 2) 渲染可停靠面板窗口（不再 setNextWindowPos/Size）
        renderDockedControlPanel(controlPanel);
        renderDockedToolPanel(toolPanel);
        renderDockedRightPanels(propertyPanel, galleryPanel, extensionPanel);
    }

    /**
     * 默认 Dock 布局：不需要用户首次手动拖拽。
     *
     * 注意：imgui-java 的 DockBuilder API 在 internal 包中。
     */
    private void ensureDockLayout(float displayWidth, float displayHeight) {
        int w = Math.max(1, Math.round(displayWidth));
        int h = Math.max(1, Math.round(displayHeight));
        boolean sizeChanged = (w != lastDockW) || (h != lastDockH);
        if (dockLayoutBuilt && !sizeChanged) {
            return;
        }
        lastDockW = w;
        lastDockH = h;

        // 清理并重建 dockspace 节点树
        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
        // 参考 ChronoBlocks：DockBuilderAddNode 要显式使用 DockSpace flag
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, displayWidth, displayHeight);

        ImInt dockMain = new ImInt(dockspaceId);
        ImInt dockTop = new ImInt();
        ImInt dockLeft = new ImInt();
        ImInt dockRight = new ImInt();

        float topRatio = Math.min(0.35f, UILayout.Toolbar.CONTROL_PANEL_HEIGHT / Math.max(1.0f, displayHeight));
        float leftRatio = Math.min(0.45f, UILayout.Toolbar.TOOL_PANEL_WIDTH / Math.max(1.0f, displayWidth));
        float rightRatio = Math.min(0.45f, UILayout.RIGHT_PANEL_DEFAULT_WIDTH / Math.max(1.0f, displayWidth));

        // 依次 split：上/左/右，剩余作为 central
        imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Up, topRatio, dockTop, dockMain);
        imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Left, leftRatio, dockLeft, dockMain);
        imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Right, rightRatio, dockRight, dockMain);

        dockIdTop = dockTop.get();
        dockIdLeft = dockLeft.get();
        dockIdRight = dockRight.get();

        imgui.internal.ImGui.dockBuilderDockWindow(WIN_TOP, dockIdTop);
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_LEFT, dockIdLeft);
        // 确保属性面板第一个 dock，这样它会成为默认激活的标签（ImGui 中第一个 dock 的窗口会默认激活）
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_RIGHT_PROPERTY, dockIdRight);
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_RIGHT_GALLERY, dockIdRight);
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_RIGHT_EXTENSION, dockIdRight);
        // 关键：中央节点不 dock 任何窗口，配合 PassthruCentralNode 让中央区域天然留空且透明（参考 ChronoBlocks）

        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);
        dockLayoutBuilt = true;
    }

    private void renderDockedControlPanel(ControlPanel controlPanel) {
        if (controlPanel == null) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, UILayout.Toolbar.BUTTON_PADDING, UILayout.Toolbar.BUTTON_PADDING);
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, currentTheme.toolbarBackground);
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), UILayout.Toolbar.CONTROL_PANEL_HEIGHT, ImGuiCond.FirstUseEver);
        ImGui.begin(WIN_TOP, DOCKABLE_WINDOW_FLAGS);
        controlPanel.renderInCurrentWindow();
        ImGui.end();
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
    }

    private void renderDockedToolPanel(ToolPanel toolPanel) {
        if (toolPanel == null) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, UILayout.Toolbar.BUTTON_PADDING, UILayout.Toolbar.BUTTON_PADDING);
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.getInstance().getCurrentTheme().border);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ThemeManager.getInstance().getCurrentTheme().toolbarBackground);
        float displayHeight = ImGui.getIO().getDisplaySizeY();
        ImGui.setNextWindowPos(0.0f, UILayout.Toolbar.CONTROL_PANEL_HEIGHT, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(UILayout.Toolbar.TOOL_PANEL_WIDTH, UILayout.getContentHeight(displayHeight), ImGuiCond.FirstUseEver);
        ImGui.begin(WIN_LEFT, DOCKABLE_WINDOW_FLAGS);
        toolPanel.renderInCurrentWindow();
        ImGui.end();
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
    }

    private void renderDockedRightPanels(PropertyPanel propertyPanel, GalleryPanel galleryPanel, ExtensionPanel extensionPanel) {
        // 右侧三个窗口共用同一套样式，并依赖 Docking 自动变成 Tab
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, UILayout.CONTENT_PADDING, UILayout.CONTENT_PADDING);
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.getInstance().getCurrentTheme().border);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ThemeManager.getInstance().getCurrentTheme().panelBackground);

        float displayWidth = ImGui.getIO().getDisplaySizeX();
        float displayHeight = ImGui.getIO().getDisplaySizeY();
        float x = UILayout.getRightPanelX(displayWidth);
        float y = UILayout.Toolbar.CONTROL_PANEL_HEIGHT;
        float w = UILayout.RIGHT_PANEL_DEFAULT_WIDTH;
        float h = UILayout.getContentHeight(displayHeight);

        try {
            // 属性面板（第一个渲染，确保在最前面且默认打开）
            if (propertyPanel != null) {
                ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
                ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
                ImGui.begin(WIN_RIGHT_PROPERTY, DOCKABLE_WINDOW_FLAGS);
                propertyPanel.render();
                ImGui.end();
            }

            // 图库
            if (galleryPanel != null) {
                ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
                ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
                ImGui.begin(WIN_RIGHT_GALLERY, DOCKABLE_WINDOW_FLAGS);
                galleryPanel.render();
                ImGui.end();
            }

            // 扩展
            if (extensionPanel != null) {
                ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
                ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
                ImGui.begin(WIN_RIGHT_EXTENSION, DOCKABLE_WINDOW_FLAGS);
                extensionPanel.render();
                ImGui.end();
            }
        } finally {
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
        }
    }


    /**
     * 【优化】处理窗口大小改变
     * 【新增】全屏切换优化：检测全屏变化后，强制刷新所有UI组件布局
     */
    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        
        LOGGER.info("窗口大小改变: {}x{} -> {}x{}", this.width, this.height, width, height);
        
        // 检查是否发生了显著变化（可能是全屏切换）
        boolean isSignificantChange = Math.abs(width - this.width) > 100 || Math.abs(height - this.height) > 100;
        
        if (isSignificantChange) {
            LOGGER.info("检测到显著窗口大小变化，可能是全屏模式切换");
            
            // 【新增】全屏切换优化：强制刷新所有UI组件布局
            refreshAllUIComponents();
        }
        
        this.width = width;
        this.height = height;
        
        if (imGuiRenderer != null) {
            try {
                // 强制更新ImGui显示尺寸
                imGuiRenderer.updateDisplaySize();
                
                // 检查ImGui状态
                if (imGuiRenderer.isInitialized()) {
                    LOGGER.debug("ImGui显示尺寸更新成功: {}x{}", 
                        ImGui.getIO().getDisplaySizeX(), 
                        ImGui.getIO().getDisplaySizeY());
                } else {
                    LOGGER.warn("ImGui未初始化，尝试重新初始化");
                    imGuiRenderer.init();
                }
            } catch (Exception e) {
                LOGGER.error("更新ImGui显示尺寸时出错", e);
            }
        } else {
            LOGGER.error("ImGui渲染器为null，无法更新显示尺寸");
        }
    }

    /**
     * 【新增】刷新所有UI组件布局
     * 在全屏切换或窗口大小显著变化时调用
     */
    private void refreshAllUIComponents() {
        LOGGER.debug("刷新所有UI组件布局...");
        
        try {
            // 刷新各个UI组件
            ControlPanel controlPanel = uiContainer.get(ControlPanel.class);
            ToolPanel toolPanel = uiContainer.get(ToolPanel.class);
            PropertyPanel propertyPanel = uiContainer.get(PropertyPanel.class);
            GalleryPanel galleryPanel = uiContainer.get(GalleryPanel.class);
            ExtensionPanel extensionPanel = uiContainer.get(ExtensionPanel.class);
            Canvas canvas = uiContainer.get(Canvas.class);

            if (controlPanel != null) {
                controlPanel.init();
            }
            if (toolPanel != null) {
                toolPanel.init();
            }
            if (propertyPanel != null) propertyPanel.init();
            if (galleryPanel != null) galleryPanel.init();
            if (extensionPanel != null) extensionPanel.init();
            if (canvas != null) {
                canvas.refresh();
            }
            
            LOGGER.debug("UI组件布局刷新完成");
        } catch (Exception e) {
            LOGGER.error("刷新UI组件布局时出错", e);
        }
    }

    /**
     * removed() 方法不再保存状态
     */
    @Override
    public void removed() {
        LOGGER.debug("MasterPlannerScreen 已关闭。");
        
        // 恢复 MasterPlanner 屏幕状态（恢复云渲染和雾渲染）
        MasterPlannerScreenState.setMasterPlannerScreenOpen(false);
        
        // 恢复相机状态（包括 HUD 状态）
        CameraManager.getInstance().setToPerspective();
        CameraManager.getInstance().restoreState();
        
        super.removed();
    }

    /**
     * 【优化】统一事件处理逻辑
     * 【新增】统一检查ImGui是否捕获输入
     */
    private boolean isInputCapturedByImGui() {
        return ImGui.getIO().getWantCaptureMouse() || ImGui.getIO().getWantCaptureKeyboard();
    }

    // 鼠标事件处理方法
    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            MasterPlannerMod.LOGGER.debug("ImGui 捕获鼠标点击: x={}, y={}, button={}", 
                click.x(), click.y(), click.button());
            return true;
        }
        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            MasterPlannerMod.LOGGER.debug("ImGui 捕获鼠标释放: x={}, y={}, button={}", 
                click.x(), click.y(), click.button());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            MasterPlannerMod.LOGGER.debug("ImGui 捕获鼠标拖动: x={}, y={}, deltaX={}, deltaY={}", 
                click.x(), click.y(), deltaX, deltaY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, double modifiers) {
        // 【测试】强制记录所有滚轮事件，确保事件能被接收到
        LOGGER.info("MasterPlannerScreen.mouseScrolled: 收到滚轮事件! mouseX={}, mouseY={}, delta={}, modifiers={}", 
            mouseX, mouseY, delta, modifiers);
        
        // 【调试】检查delta值是否正常
        if (Math.abs(delta) < 0.001) {
            LOGGER.warn("MasterPlannerScreen.mouseScrolled: delta值异常小，可能是滚轮事件被拦截或转换错误");
        }
        
        // 【修复】尝试从modifiers参数获取滚轮增量
        double actualDelta = delta;
        if (Math.abs(delta) < 0.001 && Math.abs(modifiers) > 0.001) {
            // 如果delta接近0但modifiers有值，可能滚轮信息在modifiers中
            actualDelta = modifiers;
            LOGGER.info("MasterPlannerScreen.mouseScrolled: 使用modifiers作为滚轮增量: {}", actualDelta);
        }
        
        // 添加调试信息
        boolean wantCaptureMouse = ImGui.getIO().getWantCaptureMouse();
        LOGGER.debug("MasterPlannerScreen.mouseScrolled: mouseX={}, mouseY={}, delta={}, actualDelta={}, modifiers={}, wantCaptureMouse={}", 
            mouseX, mouseY, delta, actualDelta, modifiers, wantCaptureMouse);
        
        // 【修复】优先让工具处理鼠标滚轮事件，不依赖ImGui状态
        BaseTool activeTool = appState.getCurrentTool();
        LOGGER.debug("MasterPlannerScreen.mouseScrolled: 当前活动工具={}", activeTool != null ? activeTool.getName() : "null");
        
        if (activeTool != null) {
            // 转换为Vec2d坐标
            Vec2d mousePos = new Vec2d(mouseX, mouseY);
            
            // 尝试让工具处理鼠标滚轮事件，使用actualDelta
            boolean handled = activeTool.onMouseWheel(mousePos, actualDelta);
            LOGGER.debug("MasterPlannerScreen.mouseScrolled: 工具处理结果={}", handled);
            
            if (handled) {
                LOGGER.debug("工具 {} 处理了鼠标滚轮事件: actualDelta={}", activeTool.getName(), actualDelta);
                return true;
            }
        }
        
        // 【修复】只有在工具没有处理时才让ImGui处理
        if (isInputCapturedByImGui()) {
            MasterPlannerMod.LOGGER.debug("ImGui 捕获鼠标滚轮: actualDelta={}", actualDelta);
            ImGui.getIO().setMouseWheel((float) actualDelta);
            return true;
        }
        
        LOGGER.debug("MasterPlannerScreen.mouseScrolled: 事件传递给父类处理");
        return super.mouseScrolled(mouseX, mouseY, delta, modifiers);
    }

    // 键盘事件处理方法
    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int modifiers = keyInput.modifiers();
        
        // 添加调试信息
        boolean wantCaptureMouse = ImGui.getIO().getWantCaptureMouse();
        boolean wantCaptureKeyboard = ImGui.getIO().getWantCaptureKeyboard();
        LOGGER.debug("MasterPlannerScreen.keyPressed: keyCode={}, modifiers={}, wantCaptureMouse={}, wantCaptureKeyboard={}", 
            keyCode, modifiers, wantCaptureMouse, wantCaptureKeyboard);

        // 同步修饰键状态到 ImGui（确保 getKeyShift/getKeyAlt 等可用）
        try {
            boolean ctrlPressed = (modifiers & 0x0002) != 0;  // GLFW_MOD_CONTROL
            boolean shiftPressed = (modifiers & 0x0001) != 0; // GLFW_MOD_SHIFT
            boolean altPressed = (modifiers & 0x0004) != 0;   // GLFW_MOD_ALT
            boolean superPressed = (modifiers & 0x0008) != 0; // GLFW_MOD_SUPER
            ImGui.getIO().setKeyCtrl(ctrlPressed);
            ImGui.getIO().setKeyShift(shiftPressed);
            ImGui.getIO().setKeyAlt(altPressed);
            ImGui.getIO().setKeySuper(superPressed);
        } catch (Exception ignored) {}
        
        // 【修复】优先尝试通过快捷键系统处理
        if (KeyboardShortcutConverter.isValidShortcut(keyCode, modifiers)) {
            String shortcutString = KeyboardShortcutConverter.convertToShortcutString(keyCode, modifiers);
            if (shortcutString != null) {
                LOGGER.debug("尝试处理快捷键: '{}'", shortcutString);
                if (ShortcutManager.getInstance().handleShortcut(shortcutString)) {
                    LOGGER.debug("快捷键 '{}' 已被处理", shortcutString);
                    return true;
                }
            }
        }
        
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            LOGGER.debug("键盘事件被ImGui捕获，keyCode={}", keyCode);
            
            // 处理组合键（剪切复制粘贴等）
            boolean ctrlPressed = (modifiers & 2) != 0; // GLFW_MOD_CONTROL
            
            if (ctrlPressed) {
                switch (keyCode) {
                    case 67: // C key - Copy
                        ImGui.getIO().setKeysDown(keyCode, true);
                        return true;
                    case 86: // V key - Paste
                        ImGui.getIO().setKeysDown(keyCode, true);
                        return true;
                    case 88: // X key - Cut
                        ImGui.getIO().setKeysDown(keyCode, true);
                        return true;
                    case 65: // A key - Select All
                        ImGui.getIO().setKeysDown(keyCode, true);
                        return true;
                    // 移除直接处理 Ctrl+Z 和 Ctrl+Y，让快捷键系统处理
                }
            }
            
            // 处理特殊键
            switch (keyCode) {
                case 259: // Backspace
                case 261: // Delete
                case 257: // Enter
                case 258: // Tab
                case 262: // Right
                case 263: // Left
                case 264: // Down
                case 265: // Up
                case 268: // Home
                case 269: // End
                case 256: // ESC - 添加ESC键的处理，但不关闭界面
                    ImGui.getIO().setKeysDown(keyCode, true);
                    return true;
            }
            
            MasterPlannerMod.LOGGER.debug("ImGui 捕获键盘输入: keyCode={}, modifiers={}", 
                keyCode, modifiers);
            return true;
        }
        
        LOGGER.debug("键盘事件传递给MasterPlanner组件，keyCode={}", keyCode);
        
        // 如果是ESC/Shift键，先尝试让当前活动工具处理
        if (keyCode == 256) { // ESC键
            // 获取当前活动工具
            BaseTool activeTool = appState.getCurrentTool();
            if (activeTool != null) {
                // 尝试让工具处理ESC键
                if (activeTool.onKeyDown(keyCode)) {
                    LOGGER.debug("工具 {} 处理了ESC键", activeTool.getName());
                    return true;
                }
            }
            
            // 如果工具没有处理ESC键，则消费这个事件，不让它关闭界面
            return true;
        }

        // 左/右 Shift 转发给工具，便于工具内部切换正交/角度约束
        if (keyCode == 340 || keyCode == 344) { // GLFW_KEY_LEFT_SHIFT / GLFW_KEY_RIGHT_SHIFT
            BaseTool activeTool = appState.getCurrentTool();
            if (activeTool != null && activeTool.onKeyDown(keyCode)) {
                return true;
            }
        }
        
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int modifiers = keyInput.modifiers();
        
        // 同步修饰键状态
        try {
            boolean ctrlPressed = (modifiers & 0x0002) != 0;
            boolean shiftPressed = (modifiers & 0x0001) != 0;
            boolean altPressed = (modifiers & 0x0004) != 0;
            boolean superPressed = (modifiers & 0x0008) != 0;
            ImGui.getIO().setKeyCtrl(ctrlPressed);
            ImGui.getIO().setKeyShift(shiftPressed);
            ImGui.getIO().setKeyAlt(altPressed);
            ImGui.getIO().setKeySuper(superPressed);
        } catch (Exception ignored) {}

        if (isInputCapturedByImGui()) {
            ImGui.getIO().setKeysDown(keyCode, false);
            return true;
        }

        // 转发 Shift 释放给当前工具
        if (keyCode == 340 || keyCode == 344) {
            BaseTool activeTool = appState.getCurrentTool();
            if (activeTool != null && activeTool.onKeyUp(keyCode)) {
                return true;
            }
        }
        return super.keyReleased(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();
        
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            MasterPlannerMod.LOGGER.debug("ImGui 捕获字符输入: char='{}', modifiers={}", 
                chr, modifiers);
            ImGui.getIO().addInputCharacter(chr);
            return true;
        }
        // 单键工具快捷键（不区分大小写），优先走 ShortcutManager
        try {
            String s;
            if (chr == ' ') {
                s = "space"; // 与 KeymapManager 默认绑定一致
            } else {
                s = String.valueOf(Character.toLowerCase(chr));
            }
            if (s.length() == 1) {
                if (ShortcutManager.getInstance().handleShortcut(s)) {
                    return true;
                }
            } else if ("space".equals(s)) {
                if (ShortcutManager.getInstance().handleShortcut(s)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 如果ImGui没有捕获，尝试传递给当前工具
        BaseTool currentTool = AppState.getInstance().getCurrentTool();
        if (currentTool != null && currentTool.onKeyTyped(chr)) {
            MasterPlannerMod.LOGGER.debug("工具处理字符输入: char='{}', tool={}", 
                chr, currentTool.getClass().getSimpleName());
            return true;
        }
        
        return super.charTyped(charInput);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // 【优化】日志优化：减少mouseMoved的TRACE日志，避免性能影响
        if (ENABLE_MOUSE_MOVE_LOGGING) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMouseMoveLogTime > MOUSE_MOVE_LOG_INTERVAL) {
                LOGGER.trace("Mouse moved: x={}, y={}", mouseX, mouseY);
                lastMouseMoveLogTime = currentTime;
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 不允许使用ESC键关闭界面，只能通过关闭按钮关闭
    }

    @Override
    public boolean shouldPause() {
        return false; // 不暂停游戏
    }

    @Override
    public void close() {
        // 在super.close()之前恢复相机，确保逻辑正确
        CameraManager.getInstance().setToPerspective();
        CameraManager.getInstance().restoreState();
        super.close();
    }
}