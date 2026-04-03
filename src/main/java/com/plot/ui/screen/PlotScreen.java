package com.plot.ui.screen;

import com.plot.core.state.AppState;
import com.plot.ui.imgui.ImGuiRenderer;
import com.plot.ui.imgui.GuiOverlayRenderer;
import com.plot.ui.imgui.PlotStyleScope;
import com.plot.ui.toolbar.ControlPanel;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.ui.toolbar.SystemPanel;
import com.plot.ui.toolbar.ToolPanel;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.UITheme;
import com.plot.ui.panel.PropertyPanel;
import com.plot.ui.panel.extension.ExtensionPanel;
import com.plot.ui.panel.gallery.GalleryPanel;
import imgui.ImGui;
import imgui.ImGuiStyle;
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
import com.plot.ui.container.UIContainer;
import com.plot.ui.theme.ThemeManager;
import com.plot.camera.CameraManager;
import com.plot.PlotMod;
import com.plot.core.tool.BaseTool;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.core.shortcut.KeyboardShortcutConverter;
import com.plot.api.geometry.Vec2d;
/**
 * Plot 主界面类
 * 职责：渲染UI，并将用户输入转发给核心处理器。不管理核心状态。
 * 【优化】增强事件处理、全屏切换优化、日志优化
 */
public class PlotScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlotScreen.class);
    
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
    // NoScrollbar: 隐藏滚动条，但仍允许鼠标滚轮滚动
    private static final int DOCKABLE_WINDOW_FLAGS =
        ImGuiWindowFlags.NoCollapse |
        ImGuiWindowFlags.NoScrollbar;

    // ---- DockSpace（可停靠布局）----
    private static final String DOCKSPACE_HOST_WINDOW = "PlotDockSpace##DockspaceHost";
    private static final String DOCKSPACE_ID_STR = "PlotDockSpace";

    private static final String WIN_TOP = "ControlPanel##ControlPanel";
    private static final String WIN_TOP_SYSTEM = "SystemPanel##SystemPanel";
    private static final String WIN_LEFT = "ToolPanel##ToolPanel";
    // 右侧拆分为三个独立窗口，Dock 到同一个 right dock node 后会自动以 Tab 形式组合展示
    private static final String WIN_RIGHT_PROPERTY = "属性##PropertyPanel";
    private static final String WIN_RIGHT_GALLERY = "图库##GalleryPanel";
    private static final String WIN_RIGHT_EXTENSION = "扩展##ExtensionPanel";

    private int dockspaceId;
    private int dockIdTopLeft;  // 顶部左侧（ControlPanel）
    private int dockIdTopRight; // 顶部右侧（SystemPanel）
    private int dockIdLeft;
    private int dockIdRight;
    private boolean dockLayoutBuilt;
    private int lastDockW;
    private int lastDockH;
    private boolean firstRender = true; // 跟踪首次渲染，用于设置默认激活的标签

    /** 是否正在执行 render()，用于避免 removed() 在渲染中被调用时切换 ImGui 上下文导致崩溃 */
    private volatile boolean renderInProgress = false;

    /**
     * 用户点击关闭等：不可在 ImGui begin/end 嵌套内立刻 {@link #close()}，也不可仅依赖 {@link MinecraftClient#execute}，
     * 因其可能在同一帧 {@code Screen.render} 中途执行，仍早于各 dock 窗口的 {@code ImGui.end()}。
     * 须在 {@link ImGuiRenderer#endFrame()} 完成后再 {@code execute(close)}。
     */
    private volatile boolean closeAfterImGuiFrame = false;

    /** 由 SystemPanel 等在 ImGui 交互回调中调用，真正的 {@link #close()} 延迟到本帧 ImGui 结束后。 */
    public void scheduleCloseAfterImGuiFrame() {
        this.closeAfterImGuiFrame = true;
    }

    /**
     * 构造函数
     * 初始化所有UI组件和ImGui渲染器
     */
    public PlotScreen() {
        super(Text.translatable("screen.plot.title"));
        LOGGER.debug("创建 PlotScreen 实例...");

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
        
        // 设置 Plot 屏幕打开状态（用于控制云渲染和雾渲染）
        PlotScreenState.setPlotScreenOpen(true);
        
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
        uiContainer.register(SystemPanel.class, new SystemPanel());
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
        LOGGER.debug("初始化 PlotScreen 布局和样式...");
        
        this.imGuiRenderer.updateDisplaySize();
        // 样式由 PlotStyleScope 在每帧渲染时临时 push/pop，不在此处永久修改，避免影响 Treefactory/ChronoBlocks 等模组

        // 初始化所有UI组件
        ControlPanel controlPanel = uiContainer.get(ControlPanel.class);
        SystemPanel systemPanel = uiContainer.get(SystemPanel.class);
        ToolPanel toolPanel = uiContainer.get(ToolPanel.class);
        PropertyPanel propertyPanel = uiContainer.get(PropertyPanel.class);
        GalleryPanel galleryPanel = uiContainer.get(GalleryPanel.class);
        ExtensionPanel extensionPanel = uiContainer.get(ExtensionPanel.class);
        Canvas canvas = uiContainer.get(Canvas.class);

        if (controlPanel != null) {
            controlPanel.init();
        }
        if (systemPanel != null) {
            systemPanel.init();
        }
        if (toolPanel != null) {
            toolPanel.init();
        }
        if (propertyPanel != null) propertyPanel.init();
        // 暂时隐藏图库面板和扩展面板的初始化（保留代码供后续开发）
        // if (galleryPanel != null) galleryPanel.init();
        // if (extensionPanel != null) extensionPanel.init();
        if (canvas != null) {
            // 修复：确保Canvas被正确初始化，包括CanvasCore的camera
            canvas.init();
            canvas.refresh();
            LOGGER.debug("Canvas已初始化并刷新");
        }

        // 初始化键位管理（注册快捷键路由、加载keymap.json）
        try {
            com.plot.ui.dialog.KeymapManager.getInstance();
        } catch (Exception e) {
            LOGGER.error("初始化 KeymapManager 失败", e);
        }

        LOGGER.debug("PlotScreen 初始化完成");
    }

    /**
     * 渲染方法
     * 每帧调用，负责渲染整个界面
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInProgress = true;
        boolean imguiFrameEnded = false;
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
            
            // 渲染所有 UI 组件（使用 PlotStyleScope 临时应用样式，渲染后 pop 恢复，避免影响 Treefactory/ChronoBlocks 等模组）
            try (PlotStyleScope scope = PlotStyleScope.enter()) {
                renderUI();
                // 渲染回退的 ImGui 文字输入对话框（如果开启）
                try {
                    com.plot.ui.dialog.TextInputDialog.getInstance().render();
                } catch (Throwable ignored) {}
            } catch (Throwable uiErr) {
                LOGGER.error("renderUI 失败", uiErr);
                drawFatalOverlay(context, "renderUI 异常: " + safeMsg(uiErr));
            }
            
            // 结束 ImGui 帧并在后续的 swapBuffers 前由 mixin 渲染 ImGui draw data，
            // 本方法负责在 ImGui frame 结束后使用 DrawContext 绘制覆盖图标。
            imGuiRenderer.endFrame();
            GuiOverlayRenderer.setPendingDrawContext(context);
            imguiFrameEnded = true;
            
        } catch (Exception e) {
            LOGGER.error("Error rendering Plot UI", e);
            drawFatalOverlay(context, "PlotScreen.render 异常: " + safeMsg(e));
        } finally {
            renderInProgress = false;
            if (closeAfterImGuiFrame && imguiFrameEnded) {
                closeAfterImGuiFrame = false;
                MinecraftClient c = MinecraftClient.getInstance();
                PlotScreen self = this;
                c.execute(() -> {
                    if (c.currentScreen == self) {
                        self.close();
                    }
                });
            }
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
        if (m == null || m.isBlank()) {
            // 对于 NoClassDefFoundError 和 ClassNotFoundException，显示完整的类名
            if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException) {
                m = t.getClass().getSimpleName() + ": " + t.getClass().getName();
            } else {
                m = t.getClass().getSimpleName();
            }
        }
        // 限制错误消息长度，避免显示过长
        if (m.length() > 100) {
            m = m.substring(0, 97) + "...";
        }
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
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            context.drawTextWithShadow(tr, "Plot UI 渲染失败（1.21.11 适配中）", x, y, theme.errorText);
            context.drawTextWithShadow(tr, msg, x, y + 12, theme.text);
            context.drawTextWithShadow(tr, "请查看日志：ImGuiRenderer / PlotScreen", x, y + 24, theme.mutedText);
        } catch (Throwable ignored) {
        }
    }

    private void renderUI() {
        // 渲染各个组件
        ControlPanel controlPanel = uiContainer.get(ControlPanel.class);
        SystemPanel systemPanel = uiContainer.get(SystemPanel.class);
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
            renderDockSpaceLayout(displayWidth, displayHeight, controlPanel, systemPanel, toolPanel, propertyPanel, galleryPanel, extensionPanel);
            
            // 首次渲染完成后，标记为已完成
            if (firstRender) {
                firstRender = false;
            }
        } finally {
            ImGui.popStyleColor(2);
        }
    }

    /**
     * DockSpace 布局：Top/Left/Right 作为可停靠窗口，Canvas 固定在 central node。
     */
    private void renderDockSpaceLayout(float displayWidth, float displayHeight,
                                       ControlPanel controlPanel,
                                       SystemPanel systemPanel,
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
        // Dear ImGui：Begin 即使返回 false（折叠/裁剪）也必须 End，否则帧末 ImGui.render 断言 CurrentWindowStack.Size==1
        boolean dockHostOpen = ImGui.begin(DOCKSPACE_HOST_WINDOW, hostFlags);
        try {
            if (dockHostOpen) {
                dockspaceId = ImGui.getID(DOCKSPACE_ID_STR);
                int dockFlags = ImGuiDockNodeFlags.PassthruCentralNode;
                ImGui.dockSpace(dockspaceId, 0.0f, 0.0f, dockFlags);
                ensureDockLayout(displayWidth, displayHeight);
            }
        } finally {
            ImGui.end();
        }
        ImGui.popStyleVar(3);

        // 2) 渲染可停靠面板窗口（不再 setNextWindowPos/Size）
        renderDockedControlPanel(controlPanel);
        renderDockedSystemPanel(systemPanel);
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
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, displayWidth, displayHeight);

        ImInt dockMain = new ImInt(dockspaceId);
        ImInt dockLeft = new ImInt();
        ImInt dockRight = new ImInt();
        ImInt dockLeftTop = new ImInt();  // 左侧顶部（控制面板）
        ImInt dockLeftBottom = new ImInt(); // 左侧底部（工具面板）
        ImInt dockRightTop = new ImInt();   // 右侧顶部（系统面板）
        ImInt dockRightBottom = new ImInt(); // 右侧底部（属性面板）

        // 左侧 dock 宽度：控制面板与工具面板共享同一列宽
        // 目标宽度：刚好可容纳工具面板一行4个按钮，且控制面板单行滑动条宽度恰好可放下
        float leftDockWidth = UILayout.Toolbar.PANEL_WIDTH;
        float leftRatio = Math.min(0.45f, leftDockWidth / Math.max(1.0f, displayWidth));
        float rightRatio = Math.min(0.45f, UILayout.RIGHT_PANEL_DEFAULT_WIDTH / Math.max(1.0f, displayWidth));
        
        // 控制面板高度比例：使用实际高度计算，确保能完全显示内容
        // 根据实际需要的像素高度计算比例
        float controlPanelHeightRatio = UILayout.Toolbar.CONTROL_PANEL_HEIGHT / Math.max(1.0f, displayHeight);
        // 设置最大比例为屏幕高度的40%，避免控制面板过大，但确保足够显示内容
        controlPanelHeightRatio = Math.min(controlPanelHeightRatio, 0.40f);
        // 确保最小比例足够（至少能显示内容），如果计算出的比例太小，使用至少20%
        controlPanelHeightRatio = Math.max(controlPanelHeightRatio, 0.25f);
        
        // 系统面板高度：按钮高度 + 2*边距 + 标题栏高度
        // 标题栏高度通常等于文本行高 + 框架内边距 * 2（上下各一个）
        // 或者使用 ImGui 样式中的 TitleBarHeight（如果有的话）
        ImGuiStyle style = ImGui.getStyle();
        float titleBarHeight = ImGui.getTextLineHeight() + style.getFramePadding().y * 2.0f;
        float systemPanelHeight = UILayout.Toolbar.BUTTON_SIZE + 2.0f * UILayout.Toolbar.BUTTON_PADDING + titleBarHeight;
        float systemPanelHeightRatio = Math.min(0.15f, systemPanelHeight / Math.max(1.0f, displayHeight));

        // 先分割左右
        imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Left, leftRatio, dockLeft, dockMain);
        imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Right, rightRatio, dockRight, dockMain);
        
        // 左侧分割为上下：上部分控制面板，下部分工具面板
        imgui.internal.ImGui.dockBuilderSplitNode(dockLeft.get(), ImGuiDir.Up, controlPanelHeightRatio, dockLeftTop, dockLeftBottom);
        
        // 设置控制面板dock节点的最小大小，确保内容不被裁剪
        // 使用工具面板宽度作为最小宽度，控制面板高度作为最小高度
        float minControlPanelHeight = UILayout.Toolbar.CONTROL_PANEL_HEIGHT;
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockLeftTop.get(), leftDockWidth, minControlPanelHeight);
        
        // 右侧分割为上下：上部分系统面板，下部分属性面板
        imgui.internal.ImGui.dockBuilderSplitNode(dockRight.get(), ImGuiDir.Up, systemPanelHeightRatio, dockRightTop, dockRightBottom);

        dockIdTopLeft = dockLeftTop.get();
        dockIdLeft = dockLeftBottom.get();
        dockIdTopRight = dockRightTop.get();
        dockIdRight = dockRightBottom.get();

        // 停靠窗口到对应的dock节点
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_TOP, dockIdTopLeft);  // 控制面板在左侧顶部
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_LEFT, dockIdLeft);  // 工具面板在左侧底部
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_TOP_SYSTEM, dockIdTopRight);  // 系统面板在右侧顶部
        // 关键：确保属性面板第一个 dock 到右侧节点，这样它会成为默认激活的标签
        // ImGui 的 docking 系统中，第一个 dock 的窗口会默认激活并显示在最前面
        imgui.internal.ImGui.dockBuilderDockWindow(WIN_RIGHT_PROPERTY, dockIdRight);
        // 暂时隐藏图库面板和扩展面板的UI（保留代码供后续开发）
        // imgui.internal.ImGui.dockBuilderDockWindow(WIN_RIGHT_GALLERY, dockIdRight);
        // imgui.internal.ImGui.dockBuilderDockWindow(WIN_RIGHT_EXTENSION, dockIdRight);
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
        // 控制面板在左侧顶部，宽度与工具面板一致
        float toolPanelWidth = UILayout.Toolbar.PANEL_WIDTH;
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(toolPanelWidth, UILayout.Toolbar.CONTROL_PANEL_HEIGHT, ImGuiCond.FirstUseEver);
        boolean controlVisible = ImGui.begin(WIN_TOP, DOCKABLE_WINDOW_FLAGS);
        try {
            if (controlVisible) {
                controlPanel.renderInCurrentWindow();
            }
        } finally {
            ImGui.end();
        }
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
    }

    private void renderDockedSystemPanel(SystemPanel systemPanel) {
        if (systemPanel == null) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, UILayout.Toolbar.BUTTON_PADDING, UILayout.Toolbar.BUTTON_PADDING);
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, currentTheme.toolbarBackground);
        // 系统面板在右侧顶部，宽度和属性面板一样，高度 = 按钮高度 + 2*边距 + 标题栏高度
        // 标题栏高度通常等于文本行高 + 框架内边距 * 2（上下各一个）
        ImGuiStyle style = ImGui.getStyle();
        float titleBarHeight = ImGui.getTextLineHeight() + style.getFramePadding().y * 2.0f;
        float systemPanelHeight = UILayout.Toolbar.BUTTON_SIZE + 2.0f * UILayout.Toolbar.BUTTON_PADDING + titleBarHeight;
        float displayWidth = ImGui.getIO().getDisplaySizeX();
        float x = UILayout.getRightPanelX(displayWidth);
        ImGui.setNextWindowPos(x, 0.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(UILayout.RIGHT_PANEL_DEFAULT_WIDTH, systemPanelHeight, ImGuiCond.FirstUseEver);
        boolean systemVisible = ImGui.begin(WIN_TOP_SYSTEM, DOCKABLE_WINDOW_FLAGS);
        try {
            if (systemVisible) {
                systemPanel.render();
            }
        } finally {
            ImGui.end();
        }
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
        // 工具面板宽度（使用固定宽度，与控制面板一致）
        float toolPanelWidth = UILayout.Toolbar.PANEL_WIDTH;
        ImGui.setNextWindowPos(0.0f, UILayout.Toolbar.CONTROL_PANEL_HEIGHT, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(toolPanelWidth, UILayout.getContentHeight(displayHeight), ImGuiCond.FirstUseEver);
        boolean toolDockVisible = ImGui.begin(WIN_LEFT, DOCKABLE_WINDOW_FLAGS);
        try {
            if (toolDockVisible) {
                toolPanel.renderInCurrentWindow();
            }
        } finally {
            ImGui.end();
        }
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
        // 属性面板在系统面板下方，系统面板高度约两倍按钮高度
        float systemPanelHeight = UILayout.Toolbar.BUTTON_SIZE * 2.0f;
        float w = UILayout.RIGHT_PANEL_DEFAULT_WIDTH;
        // 属性面板高度 = 总高度 - 系统面板高度
        float h = displayHeight - systemPanelHeight;

        try {
            // 属性面板（第一个渲染，确保在最前面且默认打开）
            if (propertyPanel != null) {
                ImGui.setNextWindowPos(x, systemPanelHeight, ImGuiCond.FirstUseEver);
                ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
                // 首次渲染时设置焦点，确保属性面板默认激活
                if (firstRender) {
                    ImGui.setNextWindowFocus();
                }
                boolean propertyVisible = ImGui.begin(WIN_RIGHT_PROPERTY, DOCKABLE_WINDOW_FLAGS);
                try {
                    if (propertyVisible) {
                        propertyPanel.render();
                    }
                } finally {
                    ImGui.end();
                }
            }

            // 暂时隐藏图库面板和扩展面板的UI渲染（保留代码供后续开发）
            // 图库
            // if (galleryPanel != null) {
            //     ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
            //     ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
            //     ImGui.begin(WIN_RIGHT_GALLERY, DOCKABLE_WINDOW_FLAGS);
            //     galleryPanel.render();
            //     ImGui.end();
            // }

            // 扩展
            // if (extensionPanel != null) {
            //     ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
            //     ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
            //     ImGui.begin(WIN_RIGHT_EXTENSION, DOCKABLE_WINDOW_FLAGS);
            //     extensionPanel.render();
            //     ImGui.end();
            // }
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
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        
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
        boolean switchingToBlockConfig = PlotScreenState.consumeSwitchingToPlotSubScreen();

        if (switchingToBlockConfig) {
            LOGGER.debug("PlotScreen 切换到 Plot 子界面，跳过相机/HUD 清理。");
            super.removed();
            return;
        }

        LOGGER.debug("PlotScreen 已关闭。");
        
        // 关键修复：若 removed() 在 render() 中被触发（如点击关闭按钮），不可立即切换 ImGui 上下文，
        // 否则后续 ImGui.end() 会在错误上下文上执行，导致 "g.CurrentWindowStack.Size > 0" 断言崩溃。
        // 若正在渲染，则延迟到下一 tick 再恢复，确保本帧 ImGui Begin/End 配对完成。
        if (renderInProgress) {
            MinecraftClient.getInstance().execute(() -> ImGuiRenderer.getInstance().restorePreviousContext());
        } else {
            ImGuiRenderer.getInstance().restorePreviousContext();
        }

        // 关闭 Plot 时清理幽灵方块，避免未投影预览残留
        GhostBlockManager.getInstance().clearAllGhostBlocks();

        // 恢复 Plot 屏幕状态（恢复云渲染和雾渲染）
        PlotScreenState.setPlotScreenOpen(false);
        
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
            PlotMod.LOGGER.debug("ImGui 捕获鼠标点击: x={}, y={}, button={}", 
                click.x(), click.y(), click.button());
            return true;
        }
        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            PlotMod.LOGGER.debug("ImGui 捕获鼠标释放: x={}, y={}, button={}", 
                click.x(), click.y(), click.button());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        // 【优化】统一事件处理逻辑
        if (isInputCapturedByImGui()) {
            PlotMod.LOGGER.debug("ImGui 捕获鼠标拖动: x={}, y={}, deltaX={}, deltaY={}", 
                click.x(), click.y(), deltaX, deltaY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, double modifiers) {
        // 兼容不同输入映射：滚轮纵向增量可能来自 delta 或 modifiers
        double actualDelta;
        if (Math.abs(delta) < 1e-6 && Math.abs(modifiers) >= 1e-6) {
            actualDelta = modifiers;
        } else if (Math.abs(modifiers) < 1e-6 && Math.abs(delta) >= 1e-6) {
            actualDelta = delta;
        } else {
            actualDelta = Math.abs(modifiers) > Math.abs(delta) ? modifiers : delta;
        }

        // 统一把滚轮事件桥接给 ImGui（项目未使用 ImGuiImplGlfw 回调，需手动注入）
        imGuiRenderer.onMouseScrolled(actualDelta);

        // 基于 ImGui 实时捕获状态决定滚轮归属，避免 Dock 尺寸变化时坐标硬编码误判
        boolean imguiCapturingMouse = ImGui.getIO().getWantCaptureMouse();

        BaseTool activeTool = appState.getCurrentTool();

        if (!imguiCapturingMouse && activeTool != null) {
            // 转换为Vec2d坐标
            Vec2d mousePos = new Vec2d(mouseX, mouseY);

            // 尝试让工具处理鼠标滚轮事件，使用actualDelta
            boolean handled = activeTool.onMouseWheel(mousePos, actualDelta);

            if (handled) {
                return true;
            }
        }

        if (imguiCapturingMouse) {
            return true;
        }

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
        LOGGER.debug("PlotScreen.keyPressed: keyCode={}, modifiers={}, wantCaptureMouse={}, wantCaptureKeyboard={}", 
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
            
            PlotMod.LOGGER.debug("ImGui 捕获键盘输入: keyCode={}, modifiers={}", 
                keyCode, modifiers);
            return true;
        }
        
        LOGGER.debug("键盘事件传递给Plot组件，keyCode={}", keyCode);
        
        // 如果是ESC/Shift键，先尝试让当前活动工具处理
        if (keyCode == 256) { // ESC键
            LOGGER.debug("PlotScreen: ESC键按下，尝试让工具处理");
            // 获取当前活动工具
            BaseTool activeTool = appState.getCurrentTool();
            if (activeTool != null) {
                LOGGER.debug("PlotScreen: 当前工具: {}", activeTool.getClass().getSimpleName());
                // 尝试让工具处理ESC键
                try {
                    boolean handled = activeTool.onKeyDown(keyCode);
                    LOGGER.debug("PlotScreen: 工具处理ESC键结果: {}", handled);
                    if (handled) {
                        LOGGER.debug("工具 {} 处理了ESC键", activeTool.getName());
                        return true;
                    } else {
                        LOGGER.debug("工具 {} 未处理ESC键", activeTool.getName());
                    }
                } catch (Throwable t) {
                    LOGGER.error("工具 {} 处理ESC键时发生异常", activeTool.getClass().getSimpleName(), t);
                }
            } else {
                LOGGER.debug("PlotScreen: 当前没有活动工具");
            }
            
            // 如果工具没有处理ESC键，则消费这个事件，不让它关闭界面
            return true;
        }

        // 左/右 Shift 转发给工具，便于工具内部切换正交/角度约束
        if (keyCode == 340 || keyCode == 344) { // GLFW_KEY_LEFT_SHIFT / GLFW_KEY_RIGHT_SHIFT
            BaseTool activeTool = appState.getCurrentTool();
            if (activeTool != null) {
                try {
                    if (activeTool.onKeyDown(keyCode)) {
                        return true;
                    }
                } catch (Throwable t) {
                    LOGGER.error("工具 {} 处理Shift键时发生异常", activeTool.getClass().getSimpleName(), t);
                }
            }
        }

        // C 键转发给活动工具（如样条工具用于封闭曲线）
        if (keyCode == 67) { // GLFW_KEY_C
            BaseTool activeTool = appState.getCurrentTool();
            if (activeTool != null) {
                try {
                    if (activeTool.onKeyDown(keyCode)) {
                        return true;
                    }
                } catch (Throwable t) {
                    LOGGER.error("工具 {} 处理C键时发生异常", activeTool.getClass().getSimpleName(), t);
                }
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
            PlotMod.LOGGER.debug("ImGui 捕获字符输入: char='{}', modifiers={}", 
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
            PlotMod.LOGGER.debug("工具处理字符输入: char='{}', tool={}", 
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
        // 相机/HUD 恢复统一放到 removed() 中，根据目标界面决定是否真正执行。
        super.close();
    }
}