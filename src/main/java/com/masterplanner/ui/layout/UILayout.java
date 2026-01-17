package com.masterplanner.ui.layout;

/**
 * UI布局常量定义
 */
public class UILayout {

    // 工具栏布局常量
    public static class Toolbar {
        public static final float BUTTON_SIZE = 40.0f;          // 按钮大小
        public static final float BUTTON_PADDING = 4.0f;        // 按钮内边距
        public static final float GROUP_SPACING = 14.0f;         // 组之间的间距
        public static final float ITEM_SPACING = 4.0f;          // 组内项目间距
        public static final float SEPARATOR_WIDTH = 1;          // 分隔符宽度
        public static final float SLIDER_WIDTH = 200.0f;        // 滑动条宽度
        public static final float SLIDER_HEIGHT = 26.0f;        // 滑动条高度
        public static final float THEME_SELECTOR_HEIGHT = 40.0f; // 主题选择器高度
        public static final float CONTROL_PANEL_HEIGHT = BUTTON_SIZE + (BUTTON_PADDING * 2); // 控制面板高度
        
        // 左侧工具栏流式布局常量
        public static final float LEFT_BUTTON_SIZE = 40.0f;      // 左侧工具栏按钮大小
        public static final float LEFT_BUTTON_SPACING = 2.0f;    // 左侧工具栏按钮间距
        public static final float LEFT_GROUP_SPACING = 10.0f;    // 左侧工具栏组间距（增加以适应新布局）
        public static final float TOOL_PANEL_PADDING = 6.0f;   // 工具面板内边距（增加以适应流式布局）
        
        // 动态布局参数
        public static final float LEFT_MIN_WIDTH = 60.0f;        // 最小宽度（单列布局）
        public static final float LEFT_PREFERRED_WIDTH = 96.0f;   // 首选宽度（双列布局）
        public static final float LEFT_MAX_WIDTH = 140.0f;       // 最大宽度（三列布局）
        
        // 工具组标题相关
        public static final float GROUP_HEADER_HEIGHT = 22.0f;   // 组标题高度
        public static final float GROUP_HEADER_SPACING = 4.0f;   // 组标题与内容的间距
        
        // 动态计算工具面板宽度（支持多种布局模式）
        public static float getToolPanelWidth(int maxButtonsPerRow) {
            return TOOL_PANEL_PADDING * 2 +
                   maxButtonsPerRow * LEFT_BUTTON_SIZE + 
                   (maxButtonsPerRow - 1) * LEFT_BUTTON_SPACING;
        }
        
        // 默认工具面板宽度（双列布局）
        public static final float TOOL_PANEL_WIDTH = getToolPanelWidth(2);
        
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
    public static final float STATUS_BAR_PADDING = 8.0f;

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