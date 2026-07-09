package com.plot.ui.panel.layer;

import com.plot.utils.PlotI18n;
import com.plot.api.model.ILayer;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.base.Event;
import com.plot.ui.component.UIComponent;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import java.util.*;
import com.plot.core.layer.LayerManager;

import com.plot.infrastructure.event.EventListener;
import net.minecraft.util.Identifier;
import com.plot.ui.utils.TextureManager;


/**
 * 图层面板组件
 * 负责管理和显示图层列表、工具栏以及相关对话框
 */
public class LayerPanel implements UIComponent {
    // === 常量定义 ===
    /** 工具栏高度 */
    private static final float TOOLBAR_HEIGHT = 24.0f;
    /** 单个图层项的高度 */
    private static final float LAYER_ITEM_HEIGHT = 24.0f;

    // === 图层名称编辑相关 ===
    /** 图层名称编辑缓冲区 */
    private final ImString layerNameBuffer;

    // === 核心依赖 ===
    /** 应用程序状态管理器 */
    private final AppState appState;
    /** 事件总线 */
    private final EventBus eventBus;
    /** 图层管理器 */
    private final LayerManager layerManager;

    /** 组件是否已初始化 */
    private boolean initialized = false;

    /** 警告消息内容 */
    private String warningMessage = "";

    /** 事件监听器映射表 */
    private final Map<Class<? extends Event>, EventListener> eventListeners = new HashMap<>();

    /** 当前选中的图层集合 */
    private final Set<ILayer> selectedLayers = new HashSet<>();

    // === 纹理资源 ===
    /** 锁定图标纹理ID */
    private static int TEXTURE_LOCK;
    /** 解锁图标纹理ID */
    private static int TEXTURE_UNLOCK;
    /** 可见图标纹理ID */
    private static int TEXTURE_EYE;
    /** 隐藏图标纹理ID */
    private static int TEXTURE_EYE_SLASH;
    /** 新建图层图标纹理ID */
    private static int TEXTURE_NEW_LAYER;
    /** 删除图层图标纹理ID */
    private static int TEXTURE_DELETE_LAYER;
    /** 合并图层图标纹理ID */
    private static int TEXTURE_MERGE_LAYERS;
    /** 上移图层图标纹理ID */
    private static int TEXTURE_MOVE_UP;
    /** 下移图层图标纹理ID */
    private static int TEXTURE_MOVE_DOWN;
    /** 选择所有图元图标纹理ID */
    private static int TEXTURE_SELECT_ALL;
    /** 纹理是否已初始化 */
    private static boolean texturesInitialized = false;

    // === 子组件 ===
    /** 图层项渲染器 */
    private LayerItemRenderer layerItemRenderer;
    /** 工具栏渲染器 */
    private ToolbarRenderer toolbarRenderer;
    /** 图层列表渲染器 */
    private LayerListRenderer layerListRenderer;
    /** 新建图层对话框 */
    private final NewLayerDialog newLayerDialog;
    /** 删除图层对话框 */
    private final DeleteLayerDialog deleteLayerDialog;

    /**
     * 构造函数
     * 初始化所有必要的组件和依赖
     * @throws RuntimeException 如果初始化失败
     */
    public LayerPanel() {
        try {
            // 确保AppState已初始化
            this.appState = AppState.getInstance();
            if (this.appState == null) {
                throw new IllegalStateException("AppState未能正确初始化");
            }

            // 确保EventBus已初始化
            this.eventBus = EventBus.getInstance();
            if (this.eventBus == null) {
                throw new IllegalStateException("EventBus未能正确初始化");
            }

            // 从AppState获取LayerManager
            this.layerManager = appState.getLayerManager();
            if (this.layerManager == null) {
                throw new IllegalStateException("LayerManager未能正确初始化");
            }

            // 初始化图层状态
            this.layerNameBuffer = new ImString(64);

            // 初始化纹理
            initializeTextures();

            // 首先初始化NewLayerDialog
            this.newLayerDialog = new NewLayerDialog(
                this.layerManager,
                this::showWarningDialog
            );

            // 初始化对话框
            this.deleteLayerDialog = new DeleteLayerDialog(
                this.appState,
                selectedLayers,
                this::showWarningDialog
            );

            // 初始化组件
            initializeComponents();

        } catch (Exception e) {
            throw new RuntimeException(PlotI18n.error("error.plot.init.layer_panel_failed"), e);
        }
    }

    /**
     * 初始化组件
     * 加载纹理资源并订阅事件
     */
    @Override
    public void init() {
        if (initialized) {
            return;
        }

        try {
            validateDependencies();
            subscribeToEvents();
            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException(PlotI18n.error("error.plot.init.layer_panel_failed"), e);
        }
    }

    /**
     * 验证所有必要的依赖组件是否已正确初始化
     * @throws IllegalStateException 如果任何依赖未初始化
     */
    private void validateDependencies() {
        if (appState == null) {
            throw new IllegalStateException("AppState未初始化");
        }
        if (eventBus == null) {
            throw new IllegalStateException("EventBus未初始化");
        }
        if (layerManager == null) {
            throw new IllegalStateException("LayerManager未初始化");
        }
    }

    /**
     * 订阅图层相关事件
     * 包括图层创建和添加事件
     */
    private void subscribeToEvents() {
        // 监听图层创建事件
        EventListener createdListener = event -> {
            if (event instanceof com.plot.core.layer.LayerEventSystem.LayerCreatedEvent) {
                layerListRenderer.invalidateContentHeight();
            }
        };
        
        // 监听图层内容变更事件（替代LayerAddedEvent）
        EventListener contentChangedListener = event -> {
            if (event instanceof com.plot.core.layer.LayerEventSystem.LayerContentChangedEvent) {
                layerListRenderer.invalidateContentHeight();
            }
        };

        // 注册事件监听器
        eventBus.subscribe(com.plot.core.layer.LayerEventSystem.LayerCreatedEvent.class, createdListener);
        eventBus.subscribe(com.plot.core.layer.LayerEventSystem.LayerContentChangedEvent.class, contentChangedListener);
        eventListeners.put(com.plot.core.layer.LayerEventSystem.LayerCreatedEvent.class, createdListener);
        eventListeners.put(com.plot.core.layer.LayerEventSystem.LayerContentChangedEvent.class, contentChangedListener);
    }

    /**
     * 初始化组件
     * 加载纹理资源并订阅事件
     */
    private void initializeComponents() {
        // 初始化图层名称渲染器时传入ThemeManager
        // 传入主题管理器实例
        LayerNameRenderer layerNameRenderer = new LayerNameRenderer(
                layerManager,
                this::showWarningDialog
                // 传入主题管理器实例
        );

        // 初始化图层项渲染器
        this.layerItemRenderer = new LayerItemRenderer(
            this.layerManager,
                this::showWarningDialog,
            this.deleteLayerDialog::show,
            selectedLayers,
            TEXTURE_LOCK,
            TEXTURE_UNLOCK,
            TEXTURE_EYE,
            TEXTURE_EYE_SLASH
        );

        // 初始化图层列表渲染器，传入必要的参数
        this.layerListRenderer = new LayerListRenderer(
            this.layerItemRenderer,
            this.layerManager,
            selectedLayers
        );
        
        this.toolbarRenderer = new ToolbarRenderer(
            this.layerManager,
            this.appState,
            this::showWarningDialog,
            newLayerDialog::show,
            deleteLayerDialog::show,
            selectedLayers,
            TEXTURE_NEW_LAYER,
            TEXTURE_DELETE_LAYER,
            TEXTURE_MERGE_LAYERS,
            TEXTURE_MOVE_UP,
            TEXTURE_MOVE_DOWN,
            TEXTURE_SELECT_ALL
        );
    }

    /**
     * 渲染图层面板
     * 包括工具栏、图层列表和各种对话框
     */
    @Override
    public void render() {
        // 获取当前主题颜色
        // 使用ImGui的颜色系统替代ThemeColors
        
        // 设置面板背景色为主题背景色
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ThemeManager.getInstance().getCurrentTheme().panelBackground);
        
        ImGui.pushID("LayerPanel");
        try {
            if (!initialized) {
                return;
            }

            try {
                // 获取ImGui样式
                float windowPaddingY = ImGui.getStyle().getWindowPadding().y;
                float itemSpacingY = ImGui.getStyle().getItemSpacing().y;
                
                // 计算固定的面板高度 - 按钮栏高度 + 五个图层控件高度 + 所有上下间距
                float panelHeight = TOOLBAR_HEIGHT +  // 按钮栏高度
                                  (LAYER_ITEM_HEIGHT * 5) +  // 5个图层项的高度
                                  windowPaddingY * 2 +  // 面板上下内边距
                                  itemSpacingY* 5;  // 按钮栏和图层列表之间的间距
                
                // 创建固定高度的面板边框，不带滚动条和鼠标滚动（避免工具栏区域滚动）
                ImGui.beginChild("##layers_panel", ImGui.getContentRegionAvailX(), panelHeight, true, 
                    ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
                
                // 渲染工具栏（固定在顶部，不受滚动影响）
                // 工具栏在固定容器中，不会受到滚轮操作的影响
                toolbarRenderer.render(ImGui.getContentRegionAvailX());
                
                // 计算图层列表区域的高度
                float listHeight = panelHeight - TOOLBAR_HEIGHT - itemSpacingY - windowPaddingY;
                
                // 获取图层数量
                int layerCount = layerManager.getLayers().size();
                
                // 设置图层列表区域的窗口标志
                // 图层列表区域允许鼠标滚轮滚动，但不显示滚动条
                int windowFlags = ImGuiWindowFlags.NoScrollbar;
                
                // 如果图层数量不超过可显示范围，也禁用鼠标滚动
                if (layerCount <= 5) { // 5是我们设计的可见图层数量
                    windowFlags |= ImGuiWindowFlags.NoScrollWithMouse;
                }
                
                // 创建图层列表子窗口，只允许图层列表区域滚动
                ImGui.beginChild("##layer_list_container", ImGui.getContentRegionAvailX(), listHeight, false, windowFlags);
                
                // 使用LayerListRenderer渲染图层列表
                layerListRenderer.render(ImGui.getContentRegionAvailX(), listHeight);
                
                ImGui.endChild(); // 结束图层列表容器
                
                // 渲染警告消息和新建图层对话框
                if (!warningMessage.isEmpty()) {
                    renderWarningMessage();
                }
                
                newLayerDialog.render();
                
                // 渲染删除图层确认对话框
                deleteLayerDialog.render();
                
                ImGui.endChild(); // 结束整个面板
                
            } catch (Exception e) {
                throw new RuntimeException(PlotI18n.error("error.plot.init.layer_panel_render_failed"), e);
            }
        } finally {
            ImGui.popID();
        }

        // 恢复样式
        ImGui.popStyleColor();
    }

    /**
     * 渲染警告消息对话框
     */
    private void renderWarningMessage() {
        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0, ImGuiCond.Appearing);

            int popupFlags = ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoSavedSettings;

            if (ImGui.beginPopupModal("##warning_dialog", popupFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("layer_warning")) {
                        ImGui.closeCurrentPopup();
                        return;
                    }

                    DialogLayoutHelper.warningText(PlotI18n.localizeMessage(warningMessage));

                    if (DialogLayoutHelper.isConfirmShortcutPressed() || DialogLayoutHelper.isCancelShortcutPressed()) {
                        ImGui.closeCurrentPopup();
                    }

                    DialogLayoutHelper.beginFooter();
                    if (DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())) {
                        ImGui.closeCurrentPopup();
                    }
                } finally {
                    ImGui.endPopup();
                }
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    /**
     * 显示警告对话框
     * @param message 警告消息内容
     */
    private void showWarningDialog(String message) {
        ImGui.openPopup("##warning_dialog");
        warningMessage = message;
    }

    /**
     * 重置图层按钮纹理缓存，供界面关闭或资源包重载后重新加载。
     */
    public static void resetTextures() {
        texturesInitialized = false;
        TEXTURE_LOCK = 0;
        TEXTURE_UNLOCK = 0;
        TEXTURE_EYE = 0;
        TEXTURE_EYE_SLASH = 0;
        TEXTURE_NEW_LAYER = 0;
        TEXTURE_DELETE_LAYER = 0;
        TEXTURE_MERGE_LAYERS = 0;
        TEXTURE_MOVE_UP = 0;
        TEXTURE_MOVE_DOWN = 0;
        TEXTURE_SELECT_ALL = 0;
    }

    /**
     * 关闭组件并清理资源
     * 取消事件订阅并释放内存
     */
    @Override
    public void close() throws Exception {
        try {
            for (Map.Entry<Class<? extends Event>, EventListener> entry : eventListeners.entrySet()) {
                Class<? extends Event> eventClass = entry.getKey();
                EventListener listener = entry.getValue();
                eventBus.unsubscribe(eventClass, listener);
            }
            eventListeners.clear();
            
            // 清理资源
            if (layerNameBuffer != null) {
                layerNameBuffer.clear();
            }
            
        } catch (Exception e) {
            throw new RuntimeException(PlotI18n.error("error.plot.init.layer_panel_close_failed"), e);
        }
    }

    /**
     * 初始化图层控制按钮的纹理资源
     */
    private void initializeTextures() {
        if (!texturesInitialized) {
            try {
                TextureManager textureManager = TextureManager.getInstance();
                
                // 使用 Identifier.of() 静态方法创建标识符
                TEXTURE_LOCK = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/lock.png"));
                TEXTURE_UNLOCK = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/unlock.png"));
                TEXTURE_EYE = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/eye.png"));
                TEXTURE_EYE_SLASH = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/eye_slash.png"));
                TEXTURE_NEW_LAYER = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/new_layer.png"));
                TEXTURE_DELETE_LAYER = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/delete_layer.png"));
                TEXTURE_MERGE_LAYERS = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/merge_layers.png"));
                TEXTURE_MOVE_UP = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/move_up.png"));
                TEXTURE_MOVE_DOWN = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/move_down.png"));
                TEXTURE_SELECT_ALL = textureManager.loadTexture(
                    Identifier.of("plot", "textures/gui/layer/select_all.png"));
                
                texturesInitialized = true;
                
            } catch (Exception e) {
                texturesInitialized = false;
                throw new RuntimeException(PlotI18n.error("error.plot.init.layer_panel_texture_failed"), e);
            }
        }
    }
} 