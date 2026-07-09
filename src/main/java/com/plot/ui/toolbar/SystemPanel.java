package com.plot.ui.toolbar;

import com.plot.ui.component.UIComponent;
import com.plot.ui.component.ControlPanelIcons;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.plot.ui.screen.PlotScreen;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统面板
 * 包含主题选择器和关闭按钮等系统级控制组件
 */
public class SystemPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/SystemPanel");
    
    /**
     * 获取系统面板的总宽度（实现 UIComponent 接口）
     * @return 面板宽度（主题选择器宽度 + 间距 + 关闭按钮宽度 + 右侧内边距）
     */
    @Override
    public int getWidth() {
        return (int) calculatePanelWidth();
    }
    
    /**
     * 计算系统面板的总宽度（内部使用 float 精度）
     * @return 面板宽度（主题选择器宽度 + 间距 + 关闭按钮宽度 + 右侧内边距）
     */
    private float calculatePanelWidth() {
        float themeWidth = 120;
        float closeButtonWidth = UILayout.Toolbar.BUTTON_SIZE;
        float padding = UILayout.Toolbar.BUTTON_PADDING;
        float spacing = UILayout.Toolbar.ITEM_SPACING;
        
        // 宽度 = 主题选择器宽度 + 间距 + 关闭按钮宽度 + 右侧内边距
        return themeWidth + spacing + closeButtonWidth + padding;
    }
    
    /**
     * 渲染系统面板
     * 作为独立面板使用，包含主题选择器和关闭按钮
     * 间距和边距与控制面板一致
     */
    public void render() {
        try {
            // 获取内容区域的起始位置（WindowPadding已经设置了BUTTON_PADDING，所以contentMinX/Y已经包含了padding）
            float contentMinX = ImGui.getWindowContentRegionMinX();
            float contentMinY = ImGui.getWindowContentRegionMinY();
            
            // 设置起始位置，与控制面板一致
            // WindowPadding已经设置了BUTTON_PADDING，所以直接使用contentMinX和contentMinY即可
            ImGui.setCursorPos(contentMinX, contentMinY);
            
            // 渲染主题选择器
            renderThemeSelector();
            
            // 添加间距（与控制面板一致）
            ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
            
            // 渲染关闭按钮
            renderCloseButton();
            
        } catch (Exception e) {
            LOGGER.error("Error rendering SystemPanel", e);
        }
    }
    
    /**
     * 渲染主题选择器
     */
    private void renderThemeSelector() {
        var currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置主题选择器样式
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 
            (UILayout.Toolbar.THEME_SELECTOR_HEIGHT - ImGui.getTextLineHeight()) / 2.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        // 主题选择器本体使用直角
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        // 下拉列表弹层也使用直角
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, currentTheme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        
        try {
            ImGui.setNextItemWidth(120);
            String[] themes = {PlotI18n.tr("toolbar.plot.theme_dark"), PlotI18n.tr("toolbar.plot.theme_light")};
            int currentThemeIndex = getCurrentThemeIndex();
            
            if (ImGui.beginCombo("##theme_selector", themes[currentThemeIndex])) {
                for (int i = 0; i < themes.length; i++) {
                    if (ImGui.selectable(themes[i], i == currentThemeIndex)) {
                        switch (i) {
                            case 0 -> ThemeManager.getInstance().setTheme(ThemeManager.Theme.DARK);
                            case 1 -> ThemeManager.getInstance().setTheme(ThemeManager.Theme.LIGHT);
                        }
                    }
                }
                ImGui.endCombo();
            }
        } finally {
            ImGui.popStyleColor(8);
            ImGui.popStyleVar(4);
        }
    }
    
    /**
     * 获取当前主题索引
     */
    private int getCurrentThemeIndex() {
        return ThemeManager.getInstance().getCurrentThemeType() == ThemeManager.Theme.DARK ? 0 : 1;
    }
    
    /**
     * 渲染关闭按钮
     */
    private void renderCloseButton() {
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.CLOSE), PlotI18n.tr("toolbar.plot.close_plot"))) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof PlotScreen mps) {
                // 必须等本帧 ImGui.endFrame() 完成后再关屏；仅用 client.execute 时任务仍可能在
                // 同一帧 render 中途运行，生产环境易触发 ImGui.end 栈断言。
                mps.scheduleCloseAfterImGuiFrame();
            }
        }
        
        if (ImGui.isItemHovered()) {
            ToolbarUIUtils.renderThemedTooltip(PlotI18n.tr("toolbar.plot.close_plot_tooltip"));
        }
    }
    
    @Override
    public void init() {
        LOGGER.debug("Initializing SystemPanel");
    }
    
    @Override
    public void close() throws Exception {
        LOGGER.debug("Closing SystemPanel");
    }
}
