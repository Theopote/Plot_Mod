package com.masterplanner.ui.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.view.ThemeChangeEvent;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 主题管理器
 * 负责管理和切换不同的UI主题样式
 */
public class ThemeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ThemeManager");
    private UITheme.ThemeColors currentTheme;    // 当前使用的主题
    private Theme currentThemeType = Theme.DARK;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path themeConfigPath;

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
        themeConfigPath = initThemeConfigPath();
        currentThemeType = loadSavedTheme();
        currentTheme = mapTheme(currentThemeType);
        applyTheme();  // 立即应用深色主题
    }
    
    /**
     * 设置当前主题
     * 根据选择的主题类型切换颜色方案
     */
    public void setTheme(Theme theme) {
        Theme targetTheme = theme == null ? Theme.DARK : theme;
        UITheme.ThemeColors newTheme = mapTheme(targetTheme);

        if (currentTheme == newTheme && currentThemeType == targetTheme) {
            return;
        }
        
        // 更新当前主题
        currentThemeType = targetTheme;
        currentTheme = newTheme;
        
        // 应用新主题
        applyTheme();

        // 主题切换后发布事件
        EventBus.getInstance().publish(new ThemeChangeEvent(currentTheme));

        // 保存主题偏好
        saveTheme(targetTheme);
        
        LOGGER.info("切换主题: {}", targetTheme);
    }
    
    public UITheme.ThemeColors getCurrentTheme() {
        return currentTheme;
    }

    public Theme getCurrentThemeType() {
        return currentThemeType;
    }

    private UITheme.ThemeColors mapTheme(Theme theme) {
        return switch (theme) {
            case DARK -> UITheme.DARK_THEME;
            case LIGHT -> UITheme.LIGHT_THEME;
        };
    }

    private Path initThemeConfigPath() {
        try {
            Path base = Paths.get(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toString(),
                    "masterplanner"
            );
            if (!Files.exists(base)) {
                Files.createDirectories(base);
            }
            return base.resolve("ui_theme.json");
        } catch (Exception e) {
            LOGGER.warn("初始化主题配置路径失败，使用默认主题", e);
            return null;
        }
    }

    private Theme loadSavedTheme() {
        if (themeConfigPath == null || !Files.exists(themeConfigPath)) {
            return Theme.DARK;
        }
        try (Reader reader = Files.newBufferedReader(themeConfigPath)) {
            ThemeSetting setting = gson.fromJson(reader, ThemeSetting.class);
            if (setting == null || setting.theme == null || setting.theme.isBlank()) {
                return Theme.DARK;
            }
            return Theme.valueOf(setting.theme.toUpperCase());
        } catch (Exception e) {
            LOGGER.warn("读取主题配置失败，使用默认深色主题", e);
            return Theme.DARK;
        }
    }

    private void saveTheme(Theme theme) {
        if (themeConfigPath == null || theme == null) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(themeConfigPath)) {
            ThemeSetting setting = new ThemeSetting();
            setting.theme = theme.name();
            gson.toJson(setting, writer);
        } catch (Exception e) {
            LOGGER.warn("保存主题配置失败: {}", e.getMessage());
        }
    }

    private static class ThemeSetting {
        private String theme;
    }
    
    /**
     * 应用主题
     * 将当前主题的颜色设置应用到ImGui界面
     */
    private void applyTheme() {
        if (!isImGuiContextReady()) {
            LOGGER.debug("ImGui context not ready, postpone applying theme: {}", currentThemeType);
            return;
        }

        ImGuiStyle style = ImGui.getStyle();
        
        // 基础颜色
        style.setColor(ImGuiCol.Text, currentTheme.foreground);
        style.setColor(ImGuiCol.TextDisabled, currentTheme.mutedText);
        // 关键：不要让 ImGui 画“整屏不透明背景”（否则会挡住 Minecraft 场景）。
        // 各个面板/工具栏会自己 push WindowBg，因此这里全局设为透明是安全的。
        style.setColor(ImGuiCol.WindowBg, ImColor.rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.Border, currentTheme.border);
        
        // 移除所有阴影效果
        style.setColor(ImGuiCol.BorderShadow, ImColor.rgba(0, 0, 0, 0));      // 完全透明
        style.setColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);     // 不使用阴影效果
        style.setColor(ImGuiCol.TitleBgActive, currentTheme.panelBackground);   // 与标题栏背景统一
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
        style.setColor(ImGuiCol.TitleBgActive, currentTheme.panelBackground);
        style.setColor(ImGuiCol.TitleBgCollapsed, currentTheme.panelBackground);
        
        // 菜单相关
        style.setColor(ImGuiCol.MenuBarBg, currentTheme.panelBackground);
        style.setColor(ImGuiCol.PopupBg, currentTheme.panelBackground);

        // 标签页
        style.setColor(ImGuiCol.Tab, currentTheme.tabNormal);
        style.setColor(ImGuiCol.TabHovered, currentTheme.tabHovered);
        style.setColor(ImGuiCol.TabActive, currentTheme.tabActive);
        style.setColor(ImGuiCol.TabUnfocused, currentTheme.tabNormal);
        style.setColor(ImGuiCol.TabUnfocusedActive, currentTheme.tabActive);
        
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

    public void applyThemeIfReady() {
        applyTheme();
    }

    private boolean isImGuiContextReady() {
        try {
            return ImGui.getCurrentContext() != null;
        } catch (Throwable t) {
            return false;
        }
    }
} 