package com.masterplanner.ui.theme;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.ImGuiStyle;
import imgui.ImColor;

/**
 * UI主题管理类
 * 集中管理所有ImGui样式和颜色设置
 */
public class UITheme {

    // ====== 基础样式常量 ======
    /** 窗口最小尺寸：1像素 */
    public static final float WINDOW_MIN_SIZE = 1.0f;
    /** 窗口内边距：无 */
    public static final float WINDOW_PADDING = 0.0f;
    /** 框架内边距：无 */
    public static final float FRAME_PADDING = 0.0f;
    /** 项目间距：无 */
    public static final float ITEM_SPACING = 0.0f;

    // ====== 通知颜色常量 ======
    /** 成功提示：绿色，90%透明度 */
    public static final int SUCCESS_COLOR = ImColor.rgba(0.2f, 0.7f, 0.2f, 0.9f);
    /** 警告提示：黄色，90%透明度 */
    public static final int WARNING_COLOR = ImColor.rgba(0.9f, 0.7f, 0.2f, 0.9f);
    /** 错误提示：红色，90%透明度 */
    public static final int ERROR_COLOR = ImColor.rgba(0.8f, 0.2f, 0.2f, 0.9f);
    /** 主要提示：蓝色，90%透明度 */
    public static final int PRIMARY_COLOR = ImColor.rgba(0.2f, 0.6f, 0.9f, 0.9f);

    // 颜色设置 (直接存储为整数)
    private static final int WINDOW_BG = ImColor.rgba(0.0f, 0.0f, 0.0f, 0.0f);
    private static final int MENU_BAR_BG = ImColor.rgba(0.3f, 0.3f, 0.3f, 1.0f);
    private static final int BUTTON = ImColor.rgba(0.4f, 0.4f, 0.4f, 1.0f);
    private static final int BUTTON_HOVERED = ImColor.rgba(0.5f, 0.5f, 0.5f, 1.0f);
    private static final int BUTTON_ACTIVE = ImColor.rgba(0.6f, 0.6f, 0.6f, 1.0f);

    /**
     * 应用全局样式
     */
    public static void applyGlobalStyle() {
        ImGuiStyle style = ImGui.getStyle();

        // 使用当前主题的样式设置
        ThemeColors defaultTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置基础样式
        style.setWindowRounding(defaultTheme.windowRounding);
        style.setFrameRounding(defaultTheme.frameRounding);
        style.setWindowBorderSize(defaultTheme.windowBorderSize);
        style.setWindowMinSize(WINDOW_MIN_SIZE, WINDOW_MIN_SIZE);
        style.setWindowPadding(WINDOW_PADDING, WINDOW_PADDING);
        style.setFramePadding(FRAME_PADDING, FRAME_PADDING);
        style.setItemSpacing(ITEM_SPACING, ITEM_SPACING);

        // 设置颜色：全局 WindowBg 必须透明，避免整屏背景遮挡 Minecraft 场景
        style.setColor(ImGuiCol.WindowBg, ImColor.rgba(0.0f, 0.0f, 0.0f, 0.0f));
        style.setColor(ImGuiCol.MenuBarBg, defaultTheme.panelBackground);
        style.setColor(ImGuiCol.Button, defaultTheme.buttonNormal);
        style.setColor(ImGuiCol.ButtonHovered, defaultTheme.buttonHovered);
        style.setColor(ImGuiCol.ButtonActive, defaultTheme.buttonActive);
    }

    /**
     * 获取颜色的RGBA值
     * @param colorId ImGuiCol 颜色ID
     * @return int 颜色的整数值
     */
    public static int getColor(int colorId) {
        return switch (colorId) {
            case ImGuiCol.WindowBg -> WINDOW_BG;
            case ImGuiCol.MenuBarBg -> MENU_BAR_BG;
            case ImGuiCol.Button -> BUTTON;
            case ImGuiCol.ButtonHovered -> BUTTON_HOVERED;
            case ImGuiCol.ButtonActive -> BUTTON_ACTIVE;
            default -> ImColor.rgba(0.0f, 0.0f, 0.0f, 1.0f); // 默认黑色
        };
    }

    // ====== 工具栏专用样式 ======
    public static final class Toolbar {
        /** 按钮圆角：4像素 */
        public static final float BUTTON_ROUNDING = 4.0f;

        /** 获取当前主题的工具栏按钮颜色 */
        public static int getButtonColor(boolean selected, boolean hovered, boolean active) {
            ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            if (selected) {
                return theme.buttonSelected;
            } else if (active) {
                return theme.buttonActive;
            } else if (hovered) {
                return theme.buttonHovered;
            } else {
                return theme.buttonNormal;
            }
        }
    }

    public static final class StatusBar {
        // ... 保持原有内容 ...
    }

    public static final class Panel {
        // ... 保持原有内容 ...
    }

    public static final class Control {
        // ... 保持原有内容 ...
    }

    // ====== 画布专用样式 ======
    public static class Canvas {
        /** 画布背景颜色：深灰色，20%透明度 */
        public static final int BACKGROUND = 0x33333333; // 前两位33表示20%透明度
        /** 默认透明度：20% */
        public static float DEFAULT_OPACITY = 0.2f;

        /**
         * 动态更新背景颜色，保持RGB不变，只改变透明度
         * @param opacity 透明度 (0.0 - 1.0)
         * @return int 具有指定透明度的背景颜色
         */
        public static int getBackgroundColor(float opacity) {
            int alpha = (int)(opacity * 255);
            return (alpha << 24) | (0x333333); // 保持RGB不变，只改变透明度
        }
    }

    // ====== 主题颜色配置类 ======
    public static class ThemeColors {
        /** 主背景色 */
        public int background;
        /** 前景色（文字） */
        public int foreground;
        /** 边框色 */
        public int border;
        /** 强调色 */
        public int accent;

        /** 按钮正常状态 */
        public int buttonNormal;
        /** 按钮悬停状态 */
        public int buttonHovered;
        /** 按钮激活状态 */
        public int buttonActive;
        /** 按钮选中状态 */
        public int buttonSelected;
        /** 按钮选中+悬停状态 */
        public int buttonSelectedHovered;
        /** 按钮选中+激活状态 */
        public int buttonSelectedActive;
        /** 按钮边框颜色 */
        public int buttonBorder;
        /** 按钮激活状态下的边框颜色 */
        public int buttonActiveBorder;

        /** 画布背景色 */
        public int canvasBackground;
        /** 网格线颜色 */
        public int gridLine;
        /** 选择框颜色 */
        public int selectionOutline;

        /** 面板背景色 */
        public int panelBackground;
        /** 分隔线颜色 */
        public int separatorColor;

        /** 工具栏背景色 */
        public int toolbarBackground;
        /** 工具栏边框色 */
        public int toolbarBorder;

        /** 状态栏背景色 */
        public int statusBarBackground;
        /** 状态栏文字颜色 */
        public int statusBarText;

        /** 标签正常状态颜色 */
        public int tabNormal;
        /** 标签悬停状态颜色 */
        public int tabHovered;
        /** 标签激活状态颜色 */
        public int tabActive;
        /** 标签文字颜色 */
        public int tabText;
        /** 标签边框颜色 */
        public int tabBorder;

        /** 控件边框颜色 */
        public int frameBorder;
        /** 滑动条边框颜色 */
        public int sliderBorder;
        /** 分组框边框颜色 */
        public int groupBorder;
        /** 选择器边框颜色 */
        public int selectorBorder;

        /** 控件背景颜色 */
        public int controlBackground;  // 用于滑动条和选择器等控件的背景色

        /** 滑动条滑块颜色 */
        public int sliderGrab;
        /** 滑动条滑块激活状态颜色 */
        public int sliderGrabActive;

        // 新增样式属性
        public float windowRounding = 0.0f;      // 窗口圆角
        public float frameRounding = 2.0f;       // 普通控件圆角
        public float childRounding = 0.0f;       // 子窗口圆角
        public float popupRounding = 2.0f;       // 弹出窗口圆角
        public float scrollbarRounding = 2.0f;   // 滚动条圆角
        public float grabRounding = 2.0f;        // 滑块圆角
        public float tabRounding = 2.0f;         // 标签页圆角
        
        public float windowBorderSize = 1.0f;    // 窗口边框大小
        public float frameBorderSize = 0.0f;     // 控件边框大小
        public float popupBorderSize = 1.0f;     // 弹出窗口边框大小
        
        public float windowPaddingX = 8.0f;      // 窗口水平内边距
        public float windowPaddingY = 8.0f;      // 窗口垂直内边距
        public float framePaddingX = 4.0f;       // 控件水平内边距
        public float framePaddingY = 3.0f;       // 控件垂直内边距
        public float itemSpacingX = 8.0f;        // 项目水平间距
        public float itemSpacingY = 4.0f;        // 项目垂直间距
        
        public float scrollbarSize = 14.0f;      // 滚动条大小
        public float grabMinSize = 10.0f;        // 最小滑块大小

        // 新增工具栏专用圆角设置
        public float toolbarFrameRounding = 4.0f;    // 工具栏控件圆角
        public float toolbarGrabRounding = 4.0f;     // 工具栏滑块圆角

        // 亮度调整参数
        private static final float CONTROL_BACKGROUND_FACTOR = 0.9f;  // 控件背景暗化因子
        private static final float SLIDER_GRAB_BRIGHT_FACTOR = 1.5f;  // 滑块激活亮化因子
        
        /**
         * 获取颜色的更暗版本
         * @param color 原始颜色
         * @param factor 亮度衰减系数 (0.0 - 1.0)
         * @return 暗化后的颜色
         */
        private int getDarkerColor(int color, float factor) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            
            r = Math.max(0, (int)(r * factor));
            g = Math.max(0, (int)(g * factor));
            b = Math.max(0, (int)(b * factor));
            
            return ImColor.rgba(r, g, b, 255);
        }
        
        /**
         * 获取颜色的更亮版本
         * @param color 原始颜色
         * @param factor 亮度增加系数 (1.0+)
         * @return 亮化后的颜色
         */
        private int getBrighterColor(int color, float factor) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            
            r = Math.min(255, (int)(r * factor));
            g = Math.min(255, (int)(g * factor));
            b = Math.min(255, (int)(b * factor));
            
            return ImColor.rgba(r, g, b, 255);
        }
        
        /**
         * 预计算所有派生颜色
         */
        private void computeDerivedColors() {
            // 计算控件背景色 (比面板背景暗10%)
            controlBackground = getDarkerColor(panelBackground, CONTROL_BACKGROUND_FACTOR);
            
            // 计算滑块激活状态色 (比accent亮50%)
            sliderGrabActive = getBrighterColor(accent, SLIDER_GRAB_BRIGHT_FACTOR);
        }
        
        /**
         * 在设置完所有基础颜色后调用此方法
         */
        public void initialize() {
            computeDerivedColors();
        }

        // 添加通用颜色常量
        public static final int BORDER_COLOR = ImColor.rgba(0.4f, 0.4f, 0.4f, 1.0f);
        public static final int SURFACE_COLOR = ImColor.rgba(0.3f, 0.3f, 0.3f, 1.0f);
        
        // 通知颜色常量
        public static final int SUCCESS_COLOR = ImColor.rgba(0.2f, 0.7f, 0.2f, 0.9f);
        public static final int WARNING_COLOR = ImColor.rgba(0.9f, 0.7f, 0.2f, 0.9f);
        public static final int ERROR_COLOR = ImColor.rgba(0.8f, 0.2f, 0.2f, 0.9f);
        public static final int PRIMARY_COLOR = ImColor.rgba(0.2f, 0.6f, 0.9f, 0.9f);

        /**
         * 获取通知颜色
         * @param type 通知类型
         * @return 对应的颜色值
         */
        public static int getNotificationColor(NotificationType type) {
            return switch (type) {
                case SUCCESS -> SUCCESS_COLOR;
                case WARNING -> WARNING_COLOR;
                case ERROR -> ERROR_COLOR;
                case PRIMARY -> PRIMARY_COLOR;
            };
        }
        
        /**
         * 通知类型枚举
         */
        public enum NotificationType {
            SUCCESS,
            WARNING,
            ERROR,
            PRIMARY
        }

        /** 工具栏控件圆角 */
        public float toolbarControlRounding = 4.0f;    // 工具栏控件圆角
        /** 属性面板控件圆角 */
        public float panelControlRounding = 0.2f;      // 属性面板上的控件使用较小圆角

        // 文本颜色
        /** 普通文本颜色 */
        public int text;
        /** 激活状态文本颜色 */
        public int activeText;
        /** 提示框文本颜色 */
        public int tooltipText;
        /** 提示框背景颜色 */
        public int tooltipBackground;
        /** 次级文本颜色 */
        public int mutedText;
        /** 成功态文本颜色 */
        public int successText;
        /** 警告态文本颜色 */
        public int warningText;
        /** 错误态文本颜色 */
        public int errorText;
        /** 信息态文本颜色 */
        public int infoText;
        /** 禁用态背景色 */
        public int disabledBackground;

        // 输入框颜色 - 移除final修饰符
        public int inputBackground;        // 输入框背景色
        public int inputBackgroundHovered; // 输入框悬停背景色
        public int inputBackgroundActive;  // 输入框激活背景色
        public int inputText;             // 输入框文本颜色
        public int inputBorder;           // 输入框边框颜色

        public ThemeColors() {
            // 设置默认值
            this.inputBackground = ImColor.rgba(0.20f, 0.20f, 0.20f, 1.0f);
            this.inputBackgroundHovered = ImColor.rgba(0.25f, 0.25f, 0.25f, 1.0f);
            this.inputBackgroundActive = ImColor.rgba(0.30f, 0.30f, 0.30f, 1.0f);
            this.inputText = ImColor.rgba(0.90f, 0.90f, 0.90f, 1.0f);
            this.inputBorder = ImColor.rgba(0.40f, 0.40f, 0.40f, 1.0f);
        }
    }

    // 预定义主题
    public static final ThemeColors DARK_THEME = createDarkTheme();
    public static final ThemeColors LIGHT_THEME = createLightTheme();

    /**
     * 创建深色主题
     */
    private static ThemeColors createDarkTheme() {
        ThemeColors theme = new ThemeColors();
        
        // 主色调：深色
        theme.background = ImColor.rgba(0.12f, 0.12f, 0.12f, 1.0f);
        theme.foreground = ImColor.rgba(0.9f, 0.9f, 0.9f, 1.0f);
        theme.accent = ImColor.rgba(0.12f, 0.56f, 1.00f, 1.0f);
        
        // 边框和分割线颜色：浅灰色
        theme.border = ImColor.rgba(0.4f, 0.4f, 0.4f, 1.0f);          // 通用边框颜色
        theme.separatorColor = ImColor.rgba(0.4f, 0.4f, 0.4f, 1.0f);  // 分割线颜色
        
        // 按钮颜色
        theme.buttonNormal = ImColor.rgba(0.25f, 0.25f, 0.25f, 1.0f);    // 正常状态：深灰色
        theme.buttonHovered = ImColor.rgba(0.30f, 0.30f, 0.30f, 1.0f);   // 悬停状态：稍亮的深灰色
        theme.buttonActive = ImColor.rgba(0.35f, 0.35f, 0.35f, 1.0f);    // 激活状态：更亮的深灰色
        theme.buttonSelected = ImColor.rgba(0.12f, 0.56f, 1.00f, 1.0f);  // 选中状态：蓝色
        theme.buttonSelectedHovered = ImColor.rgba(0.00f, 0.75f, 1.00f, 1.0f); // 选中+悬停：亮蓝色
        theme.buttonSelectedActive = ImColor.rgba(0.00f, 0.00f, 0.80f, 1.0f);  // 选中+激活：深蓝色
        theme.buttonBorder = ImColor.rgba(0.40f, 0.40f, 0.40f, 1.0f);    // 按钮边框：中灰色
        theme.buttonActiveBorder = ImColor.rgba(0.8f, 0.8f, 0.8f, 1.0f); // 激活边框：亮灰色
        
        // 面板颜色
        theme.panelBackground = ImColor.rgba(0.15f, 0.15f, 0.15f, 1.0f);
        theme.toolbarBackground = theme.panelBackground;
        theme.toolbarBorder = theme.border;                               // 使用通用边框颜色
        theme.statusBarBackground = theme.toolbarBackground;
        theme.statusBarText = theme.foreground;

        // 控件边框颜色
        theme.controlBackground = theme.buttonNormal;                      // 使用与按钮相同的背景色
        theme.sliderGrab = ImColor.rgba(0.7f, 0.7f, 0.7f, 1.0f);         // 滑块颜色：灰白色
        theme.sliderGrabActive = ImColor.rgba(0.6f, 0.6f, 0.6f, 1.0f);   // 滑块激活颜色：深灰色
        theme.frameBorder = theme.border;
        theme.sliderBorder = theme.border;
        theme.groupBorder = theme.border;
        theme.selectorBorder = theme.border;

        // 画布颜色
        theme.canvasBackground = 0x33222222;
        theme.gridLine = ImColor.rgba(0.38f, 0.38f, 0.38f, 0.45f);
        theme.selectionOutline = theme.accent;
        
        // 标签颜色
        theme.tabNormal = ImColor.rgba(0.20f, 0.20f, 0.20f, 1.0f);
        theme.tabHovered = ImColor.rgba(0.25f, 0.25f, 0.25f, 1.0f);
        theme.tabActive = ImColor.rgba(0.30f, 0.30f, 0.30f, 1.0f);
        theme.tabText = theme.foreground;
        theme.tabBorder = theme.border;                                  // 标签边框使用通用边框颜色
        
        // 圆角设置
        theme.windowRounding = 0.0f;
        theme.toolbarControlRounding = 4.0f;     // 工具栏控件圆角
        theme.panelControlRounding = 0.2f;       // 属性面板控件圆角
        theme.childRounding = 0.0f;
        theme.popupRounding = theme.toolbarControlRounding;  // 弹出菜单使用与工具栏一致的圆角
        theme.scrollbarRounding = theme.panelControlRounding;
        theme.grabRounding = theme.panelControlRounding;
        theme.tabRounding = theme.toolbarControlRounding;
        
        // 边框设置
        theme.windowBorderSize = 1.0f;
        theme.frameBorderSize = 1.0f;
        theme.popupBorderSize = 1.0f;
        
        // 内边距和间距
        theme.windowPaddingX = 8.0f;
        theme.windowPaddingY = 8.0f;
        theme.framePaddingX = 4.0f;
        theme.framePaddingY = 3.0f;
        theme.itemSpacingX = 8.0f;
        theme.itemSpacingY = 4.0f;
        
        // 其他尺寸
        theme.scrollbarSize = 14.0f;
        theme.grabMinSize = 10.0f;

        // 工具栏按钮样式
        theme.toolbarFrameRounding = 4.0f;  // 添加这行，设置工具栏圆角
        theme.toolbarGrabRounding = 4.0f;

        // 初始化派生颜色
        theme.initialize();
        
        // 设置文本颜色
        theme.text = ImColor.rgba(0.9f, 0.9f, 0.9f, 1.0f);           // 普通文本：浅灰白色
        theme.activeText = ImColor.rgba(0.4f, 0.8f, 1.0f, 1.0f);     // 激活文本：浅蓝色
        theme.tooltipText = ImColor.rgba(0.9f, 0.9f, 0.9f, 1.0f);    // 提示文本：浅灰白色
        theme.tooltipBackground = ImColor.rgba(0.2f, 0.2f, 0.2f, 0.95f); // 提示框背景：深灰色
        theme.mutedText = ImColor.rgba(0.65f, 0.65f, 0.65f, 1.0f);
        theme.successText = ImColor.rgba(0.45f, 0.90f, 0.45f, 1.0f);
        theme.warningText = ImColor.rgba(0.95f, 0.76f, 0.35f, 1.0f);
        theme.errorText = ImColor.rgba(1.0f, 0.45f, 0.40f, 1.0f);
        theme.infoText = ImColor.rgba(0.55f, 0.78f, 1.0f, 1.0f);
        theme.disabledBackground = ImColor.rgba(0.28f, 0.28f, 0.28f, 0.55f);

        // 输入框颜色
        theme.inputBackground = ImColor.rgba(0.20f, 0.20f, 0.20f, 1.0f);
        theme.inputBackgroundHovered = ImColor.rgba(0.25f, 0.25f, 0.25f, 1.0f);
        theme.inputBackgroundActive = ImColor.rgba(0.30f, 0.30f, 0.30f, 1.0f);
        theme.inputText = theme.text;
        theme.inputBorder = theme.border;

        return theme;
    }
    
    /**
     * 创建浅色主题
     */
    private static ThemeColors createLightTheme() {
        ThemeColors theme = new ThemeColors();
        
        // 主色调：浅灰色
        theme.background = ImColor.rgba(0.95f, 0.95f, 0.95f, 1.0f);
        theme.foreground = ImColor.rgba(0.1f, 0.1f, 0.1f, 1.0f);
        theme.accent = ImColor.rgba(0.12f, 0.47f, 0.92f, 1.0f);
        theme.border = ImColor.rgba(0.7f, 0.7f, 0.7f, 1.0f);          // 面板边框颜色
        theme.separatorColor = theme.border;                           // 分割线使用相同的颜色
        
        // 按钮颜色
        theme.buttonNormal = ImColor.rgba(0.75f, 0.75f, 0.75f, 1.0f);    // 正常状态：浅灰色
        theme.buttonHovered = ImColor.rgba(0.70f, 0.70f, 0.70f, 1.0f);   // 悬停状态：稍深的灰色
        theme.buttonActive = ImColor.rgba(0.80f, 0.90f, 0.95f, 1.0f);    // 激活状态：淡蓝色
        theme.buttonSelected = ImColor.rgba(0.12f, 0.56f, 1.00f, 1.0f);  // 选中状态：蓝色
        theme.buttonSelectedHovered = ImColor.rgba(0.00f, 0.75f, 1.00f, 1.0f); // 选中+悬停：亮蓝色
        theme.buttonSelectedActive = ImColor.rgba(0.00f, 0.00f, 0.80f, 1.0f);  // 选中+激活：深蓝色
        theme.buttonBorder = ImColor.rgba(0.60f, 0.60f, 0.60f, 1.0f);    // 按钮边框：中灰色
        theme.buttonActiveBorder = ImColor.rgba(1.0f, 1.0f, 1.0f, 1.0f); // 激活边框：白色

        // 面板颜色
        theme.panelBackground = ImColor.rgba(0.93f, 0.93f, 0.93f, 1.0f); // 面板背景色
        theme.toolbarBackground = theme.panelBackground;
        theme.toolbarBorder = theme.border;
        theme.statusBarBackground = theme.toolbarBackground;
        theme.statusBarText = theme.foreground;

        // 控件颜色
        theme.controlBackground = ImColor.rgba(0.85f, 0.85f, 0.85f, 1.0f);    // 浅灰色背景
        theme.sliderGrab = ImColor.rgba(0.65f, 0.65f, 0.65f, 1.0f);          // 中灰色滑块
        theme.sliderGrabActive = ImColor.rgba(0.60f, 0.60f, 0.60f, 1.0f);    // 深灰色激活滑块

        // 画布颜色
        theme.canvasBackground = 0x33FFFFFF;
        theme.gridLine = ImColor.rgba(0.7f, 0.7f, 0.7f, 0.5f);
        theme.selectionOutline = theme.accent;

        // 标签颜色
        theme.tabNormal = ImColor.rgba(0.85f, 0.85f, 0.85f, 1.0f);    // 更深的灰色
        theme.tabHovered = ImColor.rgba(0.88f, 0.88f, 0.88f, 1.0f);   // 略微更深的悬停色
        theme.tabActive = ImColor.rgba(0.92f, 0.92f, 0.92f, 1.0f);    // 选中时的颜色
        theme.tabText = theme.foreground;
        theme.tabBorder = theme.border;

        // 圆角设置
        theme.windowRounding = 0.0f;
        theme.toolbarControlRounding = 4.0f;     // 工具栏控件圆角
        theme.panelControlRounding = 0.2f;       // 属性面板控件圆角
        theme.childRounding = 0.0f;
        theme.popupRounding = theme.toolbarControlRounding;
        theme.scrollbarRounding = theme.panelControlRounding;
        theme.grabRounding = theme.panelControlRounding;
        theme.tabRounding = theme.toolbarControlRounding;
        
        // 边框设置
        theme.windowBorderSize = 1.0f;
        theme.frameBorderSize = 1.0f;
        theme.popupBorderSize = 1.0f;
        
        // 内边距和间距
        theme.windowPaddingX = 8.0f;
        theme.windowPaddingY = 8.0f;
        theme.framePaddingX = 4.0f;
        theme.framePaddingY = 3.0f;
        theme.itemSpacingX = 8.0f;
        theme.itemSpacingY = 4.0f;
        
        // 其他尺寸
        theme.scrollbarSize = 14.0f;
        theme.grabMinSize = 10.0f;

        // 工具栏控件使用较大圆角
        theme.toolbarFrameRounding = 4.0f;
        theme.toolbarGrabRounding = 4.0f;

        // 初始化派生颜色
        theme.initialize();
        
        // 设置文本颜色
        theme.text = ImColor.rgba(0.1f, 0.1f, 0.1f, 1.0f);           // 普通文本：深灰色
        theme.activeText = ImColor.rgba(0.0f, 0.4f, 0.8f, 1.0f);     // 激活文本：深蓝色
        theme.tooltipText = ImColor.rgba(0.1f, 0.1f, 0.1f, 1.0f);    // 提示文本：深灰色
        theme.tooltipBackground = ImColor.rgba(0.95f, 0.95f, 0.95f, 0.95f); // 提示框背景：浅灰色
        theme.mutedText = ImColor.rgba(0.42f, 0.42f, 0.42f, 1.0f);
        theme.successText = ImColor.rgba(0.18f, 0.56f, 0.22f, 1.0f);
        theme.warningText = ImColor.rgba(0.74f, 0.48f, 0.12f, 1.0f);
        theme.errorText = ImColor.rgba(0.78f, 0.22f, 0.18f, 1.0f);
        theme.infoText = ImColor.rgba(0.16f, 0.38f, 0.72f, 1.0f);
        theme.disabledBackground = ImColor.rgba(0.70f, 0.70f, 0.70f, 0.55f);

        // 输入框颜色
        theme.inputBackground = ImColor.rgba(0.90f, 0.90f, 0.90f, 1.0f);
        theme.inputBackgroundHovered = ImColor.rgba(0.85f, 0.85f, 0.85f, 1.0f);
        theme.inputBackgroundActive = ImColor.rgba(0.80f, 0.80f, 0.80f, 1.0f);
        theme.inputText = theme.text;
        theme.inputBorder = theme.border;

        return theme;
    }

    /**
     * 选择相关的样式常量
     */
    public static class Selection {
        // 选择边框颜色 (蓝色)
        public static final int BORDER_COLOR = ImColor.rgba(0, 120, 215, 255);
        
        // 选择边框粗细
        public static final float BORDER_THICKNESS = 1.0f;
        
        // 控制点颜色 (白色)
        public static final int CONTROL_POINT_COLOR = ImColor.rgba(255, 255, 255, 255);
        
        // 控制点大小
        public static final float CONTROL_POINT_SIZE = 4.0f;
    }

    /**
     * 形状样式相关的常量
     */
    public static class Shape {
        // 默认填充颜色 (半透明白色)
        public static final int DEFAULT_FILL_COLOR = ImColor.rgba(255, 255, 255, 128);
        
        // 默认描边颜色 (黑色)
        public static final int DEFAULT_STROKE_COLOR = ImColor.rgba(0, 0, 0, 255);
        
        // 默认描边宽度
        public static final float DEFAULT_STROKE_WIDTH = 1.0f;
        
        // 选中状态填充颜色
        public static final int SELECTED_FILL_COLOR = ImColor.rgba(100, 149, 237, 128);
        
        // 选中状态描边颜色
        public static final int SELECTED_STROKE_COLOR = ImColor.rgba(65, 105, 225, 255);
    }

    /**
     * 文本样式相关的常量
     */
    public static class Text {
        // 默认字体大小
        public static final float DEFAULT_FONT_SIZE = 14.0f;
        
        // 默认字体颜色 (黑色)
        public static final int DEFAULT_FONT_COLOR = ImColor.rgba(0, 0, 0, 255);
        
        // 默认字体
        public static final String DEFAULT_FONT_FAMILY = "Arial";
        
        // 选中状态文本颜色
        public static final int SELECTED_FONT_COLOR = ImColor.rgba(65, 105, 225, 255);
    }

    private UITheme() {
        // 私有构造函数防止实例化
    }
}