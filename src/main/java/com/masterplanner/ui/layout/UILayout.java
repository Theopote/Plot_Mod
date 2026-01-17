package com.masterplanner.ui.layout;

/**
 * UI布局常量定义
 */
public class UILayout {

    // 工具栏布局常量
    public static class Toolbar {
        public static final float BUTTON_SIZE = 40.0f;          // 按钮大小
        public static final float BUTTON_PADDING = 4.0f;        // 按钮内边距
        public static final float GROUP_SPACING = 10.0f;         // 组之间的间距
        public static final float ITEM_SPACING = 4.0f;          // 组内项目间距
        public static final float SEPARATOR_WIDTH = 1;          // 分隔符宽度
        public static final float SLIDER_WIDTH = 172.0f;        // 滑动条宽度（172 = 180 - 2*边距）
        public static final float SLIDER_HEIGHT = 26.0f;        // 滑动条高度
        public static final float THEME_SELECTOR_HEIGHT = 40.0f; // 主题选择器高度
        public static final float LABEL_HEIGHT = 16.0f;         // 标题高度（文本行高）
        
        // 控制面板高度计算：四排按钮 + 两排按钮 + 两排标题 + 7*间距 + 2*边距
        // 6排按钮：6 * BUTTON_SIZE = 240
        // 2排标题：2 * LABEL_HEIGHT = 32
        // 7个间距：7 * ITEM_SPACING = 28
        // 2个边距：2 * BUTTON_PADDING = 8
        // 总计：240 + 32 + 28 + 8 = 308
        public static final float CONTROL_PANEL_HEIGHT = 
            6 * BUTTON_SIZE +                    // 六排按钮
            2 * LABEL_HEIGHT +                   // 两排标题（滑动条标题）
            7 * ITEM_SPACING +                   // 七个间距
            2 * BUTTON_PADDING;                  // 两个边距（上下各一个）
        
        // 左侧工具栏流式布局常量（与控制面板间距保持一致）
        public static final float LEFT_BUTTON_SIZE = 40.0f;      // 左侧工具栏按钮大小
        public static final float LEFT_BUTTON_SPACING = ITEM_SPACING;    // 左侧工具栏按钮间距（与控制面板一致）
        public static final float TOOL_PANEL_PADDING = BUTTON_PADDING;   // 工具面板内边距（与控制面板一致）
        
        // 动态布局参数
        public static final float LEFT_MIN_WIDTH = 60.0f;        // 最小宽度（单列布局）
        public static final float LEFT_PREFERRED_WIDTH = 96.0f;   // 首选宽度（双列布局）
        public static final float LEFT_MAX_WIDTH = 140.0f;       // 最大宽度（三列布局）
        
        // 工具组标题相关
        public static final float GROUP_HEADER_HEIGHT = 22.0f;   // 组标题高度
        public static final float GROUP_HEADER_SPACING = 4.0f;   // 组标题与内容的间距
        
        // 面板宽度计算：四个按钮宽度 + 三个间距 + 两个边距 + 窗口边框 = 滑动条宽度 + 两个边距 + 窗口边框
        // 考虑窗口边框（WindowBorderSize = 1.0f，左右各1像素，共2像素）
        // 4 * BUTTON_SIZE + 3 * ITEM_SPACING + 2 * BUTTON_PADDING + 2 * WINDOW_BORDER = 4 * 40 + 3 * 4 + 2 * 4 + 2 * 1 = 160 + 12 + 8 + 2 = 182
        // SLIDER_WIDTH + 2 * BUTTON_PADDING + 2 * WINDOW_BORDER = 172 + 2 * 4 + 2 * 1 = 172 + 8 + 2 = 182
        public static final float WINDOW_BORDER_SIZE = 1.0f;   // 窗口边框大小
        public static final float PANEL_WIDTH = 182.0f;        // 控制面板和工具面板的默认宽度（180 + 2边框）
        
        // 动态计算工具面板宽度（支持多种布局模式，保留用于响应式布局）
        public static float getToolPanelWidth(int maxButtonsPerRow) {
            return TOOL_PANEL_PADDING * 2 +
                   maxButtonsPerRow * LEFT_BUTTON_SIZE + 
                   (maxButtonsPerRow - 1) * LEFT_BUTTON_SPACING;
        }
        
        // 默认工具面板宽度（使用固定宽度180，与控制面板一致）
        public static final float TOOL_PANEL_WIDTH = PANEL_WIDTH;
        
        // 响应式布局阈值
        public static final float SINGLE_COLUMN_THRESHOLD = 80.0f;  // 单列布局阈值
        public static final float TRIPLE_COLUMN_THRESHOLD = 120.0f; // 三列布局阈值
    }

    // 右侧工具栏
    public static final float RIGHT_PANEL_DEFAULT_WIDTH = 300.0f;
    public static final float RIGHT_PANEL_MIN_WIDTH = 200.0f;
    public static final float RIGHT_PANEL_MAX_WIDTH = 400.0f;

    // 状态栏
    public static final float STATUS_BAR_HEIGHT = 24.0f;

    // 内容区域边距
    public static final float CONTENT_PADDING = 8;

    // 获取内容区域高度
    public static float getContentHeight(float windowHeight) {
        return windowHeight - Toolbar.CONTROL_PANEL_HEIGHT - STATUS_BAR_HEIGHT;
    }
    // 获取右侧面板起始X坐标
    public static float getRightPanelX(float windowWidth) {
        return windowWidth - RIGHT_PANEL_DEFAULT_WIDTH;
    }

}