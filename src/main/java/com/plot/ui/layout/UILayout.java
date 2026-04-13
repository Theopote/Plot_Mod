package com.plot.ui.layout;

/**
 * UI布局常量定义
 */
public class UILayout {

    // 工具栏布局常量
    public static class Toolbar {
        public static final float BUTTON_SIZE = 40.0f;          // 按钮大小
        public static final float BUTTON_PADDING = 4.0f;        // 按钮内边距
        public static final float ITEM_SPACING = 4.0f;          // 组内项目间距
        public static final float SEPARATOR_WIDTH = 1;          // 分隔符宽度
        public static final float SLIDER_WIDTH = 172.0f;        // 滑动条宽度
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

        
        // 面板宽度计算：两列按钮
        // 2 * BUTTON_SIZE + 1 * ITEM_SPACING + 2 * BUTTON_PADDING + 2 * WINDOW_BORDER
        // 2 * 40 + 1 * 4 + 2 * 4 + 2 * 1 = 80 + 4 + 8 + 2 = 94
        public static final float PANEL_WIDTH = 94.0f;        // 工具面板默认宽度（两列按钮）
        
        // 默认工具面板宽度（使用固定宽度180，与控制面板一致）
        public static final float TOOL_PANEL_WIDTH = PANEL_WIDTH;
        
        // 响应式布局阈值
        public static final float SINGLE_COLUMN_THRESHOLD = 80.0f;  // 单列布局阈值
    }

    // 右侧工具栏
    public static final float RIGHT_PANEL_DEFAULT_WIDTH = 300.0f;

    // 状态栏
    public static final float STATUS_BAR_HEIGHT = 24.0f;

    // 内容区域边距（与 collapsingHeader 默认边距一致）
    public static final float CONTENT_PADDING = 4;

    // 获取内容区域高度
    public static float getContentHeight(float windowHeight) {
        return windowHeight - Toolbar.CONTROL_PANEL_HEIGHT - STATUS_BAR_HEIGHT;
    }
    // 获取右侧面板起始X坐标
    public static float getRightPanelX(float windowWidth) {
        return windowWidth - RIGHT_PANEL_DEFAULT_WIDTH;
    }

}
