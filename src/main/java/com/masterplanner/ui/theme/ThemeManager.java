package com.masterplanner.ui.theme;

import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.view.ThemeChangeEvent;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主题管理器
 * 负责管理和切换不同的UI主题样式
 */
public class ThemeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ThemeManager");
    private UITheme.ThemeColors currentTheme;    // 当前使用的主题
    
    // 保存工具栏原始样式值
    private float toolbarOriginalFrameRounding;
    private float toolbarOriginalGrabRounding;
    
    // 使用静态内部类实现线程安全的单例模式
    private static class SingletonHolder {
        private static final ThemeManager INSTANCE = new ThemeManager();
    }
    
    public static ThemeManager getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    /**
     * 主题类型枚举
     * 定义了所有可用的主题类型
     */
    public enum Theme {
        DARK,       // 深色主题
        LIGHT       // 浅色主题
    }
    
    /**
     * 构造函数
     * 初始化默认使用深色主题
     */
    private ThemeManager() {
        currentTheme = UITheme.DARK_THEME;
        applyTheme();  // 立即应用深色主题
    }
    
    /**
     * 设置当前主题
     * 根据选择的主题类型切换颜色方案
     */
    public void setTheme(Theme theme) {
        UITheme.ThemeColors newTheme;
        switch (theme) {
            case DARK -> newTheme = UITheme.DARK_THEME;
            case LIGHT -> newTheme = UITheme.LIGHT_THEME;
            default -> {
                LOGGER.warn("未知主题类型: {}, 使用默认深色主题", theme);
                newTheme = UITheme.DARK_THEME;
            }
        }
        
        // 更新当前主题
        currentTheme = newTheme;
        
        // 在应用主题之前发布事件
        EventBus.getInstance().publish(new ThemeChangeEvent(currentTheme));
        
        // 应用新主题
        applyTheme();
        
        LOGGER.info("切换主题: {}", theme);
    }
    
    public UITheme.ThemeColors getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * 应用主题
     * 将当前主题的颜色设置应用到ImGui界面
     */
    private void applyTheme() {
        ImGuiStyle style = ImGui.getStyle();
        
        // 基础颜色
        style.setColor(ImGuiCol.Text, currentTheme.foreground);
        style.setColor(ImGuiCol.TextDisabled, ImColor.rgba(0.5f, 0.5f, 0.5f, 1.0f));
        // 关键：不要让 ImGui 画“整屏不透明背景”（否则会挡住 Minecraft 场景）。
        // 各个面板/工具栏会自己 push WindowBg，因此这里全局设为透明是安全的。
        style.setColor(ImGuiCol.WindowBg, ImColor.rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.Border, currentTheme.border);
        
        // 移除所有阴影效果
        style.setColor(ImGuiCol.BorderShadow, ImColor.rgba(0, 0, 0, 0));      // 完全透明
        style.setColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);     // 不使用阴影效果
        style.setColor(ImGuiCol.TitleBgActive, currentTheme.buttonActive);     // 不使用阴影效果
        style.setColor(ImGuiCol.NavHighlight, currentTheme.accent);            // 使用纯色
        style.setColor(ImGuiCol.NavWindowingHighlight, currentTheme.accent);   // 使用纯色
        style.setColor(ImGuiCol.NavWindowingDimBg, ImColor.rgba(0, 0, 0, 0)); // 完全透明
        style.setColor(ImGuiCol.ModalWindowDimBg, ImColor.rgba(0, 0, 0, 0));  // 完全透明
        
        // 按钮相关 - 使用纯色而不是渐变或阴影
        style.setColor(ImGuiCol.Button, currentTheme.buttonNormal);
        style.setColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
        style.setColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
        style.setColor(ImGuiCol.Border, currentTheme.buttonBorder);
        
        // 输入框和控件背景
        style.setColor(ImGuiCol.FrameBg, currentTheme.controlBackground);
        style.setColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
        style.setColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
        
        // 滑动条相关
        style.setColor(ImGuiCol.ScrollbarBg, currentTheme.controlBackground);
        style.setColor(ImGuiCol.ScrollbarGrab, currentTheme.buttonNormal);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, currentTheme.buttonHovered);
        style.setColor(ImGuiCol.ScrollbarGrabActive, currentTheme.buttonActive);
        
        // 滑动条专用
        style.setColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
        style.setColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
        
        // 标题栏
        style.setColor(ImGuiCol.TitleBg, currentTheme.panelBackground);
        style.setColor(ImGuiCol.TitleBgActive, currentTheme.buttonActive);
        style.setColor(ImGuiCol.TitleBgCollapsed, currentTheme.buttonNormal);
        
        // 菜单相关
        style.setColor(ImGuiCol.MenuBarBg, currentTheme.panelBackground);
        style.setColor(ImGuiCol.PopupBg, currentTheme.panelBackground);
        
        // 选择器
        style.setColor(ImGuiCol.CheckMark, currentTheme.accent);
        style.setColor(ImGuiCol.Header, currentTheme.buttonNormal);
        style.setColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        style.setColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        
        // 分隔线和其他
        style.setColor(ImGuiCol.Separator, currentTheme.separatorColor);
        style.setColor(ImGuiCol.ResizeGrip, currentTheme.buttonNormal);
        style.setColor(ImGuiCol.ResizeGripHovered, currentTheme.buttonHovered);
        style.setColor(ImGuiCol.ResizeGripActive, currentTheme.buttonActive);
        
        // 应用圆角设置 - 默认使用较小的圆角
        style.setWindowRounding(currentTheme.windowRounding);
        style.setFrameRounding(currentTheme.frameRounding);        // 默认使用2.0f
        style.setChildRounding(currentTheme.childRounding);
        style.setPopupRounding(currentTheme.popupRounding);
        style.setScrollbarRounding(currentTheme.scrollbarRounding);
        style.setGrabRounding(currentTheme.grabRounding);
        style.setTabRounding(currentTheme.tabRounding);
        
        // 应用边框设置
        style.setWindowBorderSize(currentTheme.windowBorderSize);
        style.setFrameBorderSize(currentTheme.frameBorderSize);
        style.setPopupBorderSize(currentTheme.popupBorderSize);
        
        // 应用内边距和间距设置
        style.setWindowPadding(currentTheme.windowPaddingX, currentTheme.windowPaddingY);
        style.setFramePadding(currentTheme.framePaddingX, currentTheme.framePaddingY);
        style.setItemSpacing(currentTheme.itemSpacingX, currentTheme.itemSpacingY);
        
        // 应用其他尺寸设置
        style.setScrollbarSize(currentTheme.scrollbarSize);
        style.setGrabMinSize(currentTheme.grabMinSize);
        
        // 设置控件圆角
        style.setFrameRounding(currentTheme.panelControlRounding);
    }

    /**
     * 设置工具栏样式
     * @param push true表示应用工具栏样式，false表示恢复原始样式
     */
    public void setToolbarStyle(boolean push) {
        ImGuiStyle style = ImGui.getStyle();
        if (push) {
            // 保存原始值
            toolbarOriginalFrameRounding = style.getFrameRounding();
            toolbarOriginalGrabRounding = style.getGrabRounding();
            // 应用工具栏样式
            style.setFrameRounding(currentTheme.toolbarFrameRounding);
            style.setGrabRounding(currentTheme.toolbarGrabRounding);
        } else {
            // 恢复原始值
            style.setFrameRounding(toolbarOriginalFrameRounding);
            style.setGrabRounding(toolbarOriginalGrabRounding);
        }
    }

    /**
     * 判断当前是否为浅色主题
     * @return 如果是浅色主题返回true，否则返回false
     */
    public boolean isLightTheme() {
        return getCurrentTheme().equals(UITheme.LIGHT_THEME);
    }
} 