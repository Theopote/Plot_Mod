package com.masterplanner.ui.panel;

import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.view.ThemeChangeEvent;
import com.masterplanner.ui.component.CustomTabBar;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.ui.layout.UILayout;
import com.masterplanner.ui.panel.extension.ExtensionPanel;
import com.masterplanner.ui.panel.gallery.GalleryPanel;
import com.masterplanner.ui.theme.TabTheme;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 右侧综合面板，包含属性、图库、扩展等功能
 * 负责管理和显示右侧的多个功能面板，包括：
 * 1. 属性面板：显示和编辑选中对象的属性
 * 2. 图库面板：提供预设模板和资源
 * 3. 扩展面板：管理和配置扩展功能
 */
public class RightPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/RightPanel");

    // ====== 面板尺寸常量 ======
    /** 调整大小手柄的宽度（像素） */
    private static final float RESIZE_HANDLE_WIDTH = 4;

    // ====== 标签样式常量 ======
    /** 标签下方内容区域的顶部间距 */
    private static final float TAB_CONTENT_TOP_SPACING = 8;
    /** 标签栏底部边框宽度 */
    private static final float TAB_BAR_BORDER_SIZE = 1.0f;

    // ====== 内容区域样式常量 ======
    /** 内容区域内边距（像素） */
    private static final float CONTENT_PADDING = 8;
    /** 滚动条宽度（像素） */
    private static final float SCROLLBAR_SIZE = 14.0f;
    /** 折叠状态下的宽度（像素） */
    private static final float COLLAPSED_WIDTH = 24;

    // ====== 组件实例 ======
    /** 属性面板实例 */
    private final PropertyPanel propertyPanel;
    /** 图库面板实例 */
    private final GalleryPanel galleryPanel;
    /** 扩展面板实例 */
    private final ExtensionPanel extensionPanel;

    // ====== 面板状态 ======
    /** 当前面板宽度（像素） */
    private float currentWidth = UILayout.RIGHT_PANEL_DEFAULT_WIDTH;

    // 添加新的成员变量
    private final CustomTabBar tabBar;
    private final String[] tabLabels = {"属性", "图库", "扩展"};
    private static final float TAB_HEIGHT = 30.0f;

    /**
     * 构造函数：初始化右侧面板及其子组件
     */
    public RightPanel() {
        LOGGER.info("Initializing RightPanel...");
        try {
            // 初始化核心组件
            AppState appState = AppState.getInstance();
            EventBus eventBus = EventBus.getInstance();
            ThemeManager themeManager = ThemeManager.getInstance();

            // 创建子面板
            this.propertyPanel = new PropertyPanel();
            this.galleryPanel = new GalleryPanel();
            this.extensionPanel = new ExtensionPanel();

            // 初始化各个子面板
            this.propertyPanel.init();
            this.galleryPanel.init();
            this.extensionPanel.init();

            // 初始化自定义标签栏
            TabTheme theme = new TabTheme(themeManager);
            this.tabBar = new CustomTabBar(theme);

            // 监听主题变更事件
            eventBus.subscribe(ThemeChangeEvent.class, event -> {
                // 标签栏会自动使用新的主题颜色，因为它是从 ThemeManager 动态获取的
            });

            LOGGER.info("RightPanel initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize RightPanel", e);
            throw new RuntimeException("Failed to initialize RightPanel", e);
        }
    }

    /**
     * 渲染右侧面板
     * 负责渲染整个右侧面板的框架和内容
     */
    @Override
    public void render() {
        try {
            LOGGER.debug("Rendering RightPanel...");

            // 使用 try-with-resources 来管理 styleVar，保证 popStyleVar 被调用
            try (ImGuiTempStyle style = new ImGuiTempStyle(2)){
                style.push(0);  // 禁用默认滚动条
                style.push(0, 0);  // 移除默认内边距

                // 渲染面板主体内容
                renderContent();
            }

            LOGGER.debug("RightPanel rendered successfully");
        } catch (Exception e) {
            LOGGER.error("Error rendering RightPanel", e);
        }
    }

    /**
     * 渲染面板主体内容
     * 包括标签栏和各个子面板的内容
     */
    private void renderContent() {
        // 创建一个无滚动条的子窗口来包装内容
        ImGui.beginChild("##RightPanelContent",
                0,
                0,
                false,
                ImGuiWindowFlags.NoScrollbar |
                        ImGuiWindowFlags.NoScrollWithMouse);

        // 渲染自定义标签栏
        tabBar.render("##RightPanelTabs", tabLabels);
        
        // 绘制标签栏底部横线
        drawTabBarBottomLine();

        // 根据选中的标签渲染对应的内容
        int selectedTab = tabBar.getSelectedTabIndex();
        if (selectedTab >= 0) {
            switch (selectedTab) {
                case 0:
                    renderTabContent("##PropertyPanelContent", propertyPanel::render);
                    break;
                case 1:
                    renderTabContent("##GalleryPanelContent", galleryPanel::render);
                    break;
                case 2:
                    renderTabContent("##ExtensionPanelContent", extensionPanel::render);
                    break;
            }
        }

        // 渲染调整大小的手柄
        renderResizeHandle();
        ImGui.endChild();
    }

    private void drawTabBarBottomLine() {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x1 = ImGui.getWindowPosX();
        float y1 = ImGui.getWindowPosY() + TAB_HEIGHT;
        float x2 = ImGui.getWindowPosX() + ImGui.getWindowWidth();

        // 使用 ImColor 的新方法创建颜色
        int lineColor = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f);
        
        drawList.addLine(x1, y1, x2, y1, 
            lineColor,  // 使用新的颜色格式
            TAB_BAR_BORDER_SIZE);
        
        // 在横线后添加一些空间
        ImGui.dummy(0, TAB_CONTENT_TOP_SPACING);
    }

    private void renderTabContent(String childId, Runnable renderContent) {
        try (ImGuiTempStyle style = new ImGuiTempStyle(2)){
            style.push(CONTENT_PADDING, CONTENT_PADDING);
            style.push(SCROLLBAR_SIZE);
            
            // 添加顶部间距
            ImGui.dummy(0, TAB_CONTENT_TOP_SPACING);
            
            // 计算剩余可用高度（减去顶部间距）
            float remainingHeight = ImGui.getContentRegionAvailY() - TAB_CONTENT_TOP_SPACING;
            
            ImGui.beginChild(childId,
                    0,
                    remainingHeight,
                    false);
            renderContent.run();
            ImGui.endChild();
        }
    }

    /**
     * 渲染调整大小的手柄
     * 允许用户通过拖动来调整面板宽度
     */
    private void renderResizeHandle() {
        // 计算手柄的位置
        float startX = ImGui.getCursorScreenPosX() - RESIZE_HANDLE_WIDTH;
        float startY = ImGui.getCursorScreenPosY();
        float height = ImGui.getWindowHeight();

        // 创建一个透明的按钮作为调整大小的手柄
        ImGui.invisibleButton("##resize", RESIZE_HANDLE_WIDTH, height);

        // 处理拖动逻辑
        if (ImGui.isItemActive()) {
            float mouseX = ImGui.getMousePosX();
            float newWidth = ImGui.getMainViewport().getWorkSizeX() - mouseX;

            // 确保新宽度在允许的范围内
            if (newWidth >= UILayout.RIGHT_PANEL_MIN_WIDTH && newWidth <= UILayout.RIGHT_PANEL_MAX_WIDTH) {
                currentWidth = newWidth;
            }
        }

        // 当鼠标悬停时显示调整大小的光标
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
    }

    /**
     * 获取面板当前宽度
     * @return 面板的当前宽度（像素）
     */
    @Override
    public int getWidth() {
        boolean isCollapsed = false;
        return (int)(isCollapsed ? COLLAPSED_WIDTH : currentWidth);
    }

    /**
     * 初始化右侧面板
     * 由于主要初始化工作在构造函数中完成，此方法主要用于满足接口要求
     */
    @Override
    public void init() {
        try {
            LOGGER.debug("Initializing RightPanel components...");
            // 目前无需额外初始化，因为所有初始化工作都在构造函数中完成了
            LOGGER.debug("RightPanel components initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize RightPanel components", e);
            throw new RuntimeException("Failed to initialize RightPanel components", e);
        }
    }

    /**
     * 清理资源
     * 在面板关闭时释放所有资源
     */
    @Override
    public void close() throws Exception {
        LOGGER.debug("Disposing RightPanel resources...");
        // TODO: 实现资源清理逻辑
    }

    private static class ImGuiTempStyle implements AutoCloseable {
        private final int count;
        private ImGuiTempStyle(int count) {
            this.count = count;
        }

        void push(float valueX, float valueY) {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, valueX, valueY);
        }
        void push(float value) {
            ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, value);
        }

        @Override
        public void close() {
            ImGui.popStyleVar(count);
        }
    }
}