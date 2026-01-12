package com.masterplanner.ui.canvas;

import com.masterplanner.core.tool.BaseTool;

/**
 * ICursorProvider 接口使用示例
 * 
 * 展示如何为工具实现自定义光标类型
 */
public class CursorProviderExample {
    
    /**
     * 示例：绘制工具实现 ICursorProvider
     */
    public static class DrawingToolWithCursor extends BaseTool implements ICursorProvider {
        
        public DrawingToolWithCursor() {
            super("drawing_tool", "绘制工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.CROSSHAIR; // 使用十字光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：选择工具实现 ICursorProvider
     */
    public static class SelectToolWithCursor extends BaseTool implements ICursorProvider {
        
        public SelectToolWithCursor() {
            super("select_tool", "选择工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.POINTER; // 使用指针光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：橡皮擦工具实现 ICursorProvider
     */
    public static class EraserToolWithCursor extends BaseTool implements ICursorProvider {
        
        public EraserToolWithCursor() {
            super("eraser_tool", "橡皮擦工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.ERASER; // 使用橡皮擦光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：平移工具实现 ICursorProvider
     */
    public static class PanToolWithCursor extends BaseTool implements ICursorProvider {
        
        public PanToolWithCursor() {
            super("pan_tool", "平移工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.HAND; // 使用手型光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：文本工具实现 ICursorProvider
     */
    public static class TextToolWithCursor extends BaseTool implements ICursorProvider {
        
        public TextToolWithCursor() {
            super("text_tool", "文本工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.TEXT; // 使用文本光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：移动工具实现 ICursorProvider
     */
    public static class MoveToolWithCursor extends BaseTool implements ICursorProvider {
        
        public MoveToolWithCursor() {
            super("move_tool", "移动工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.MOVE; // 使用移动光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：调整大小工具实现 ICursorProvider
     */
    public static class ResizeToolWithCursor extends BaseTool implements ICursorProvider {
        
        public ResizeToolWithCursor() {
            super("resize_tool", "调整大小工具");
        }
        
        @Override
        public String getCursorType() {
            return ICursorProvider.CursorTypes.RESIZE; // 使用调整大小光标
        }
        
        // 其他工具方法...
    }
    
    /**
     * 示例：动态光标工具
     * 根据工具状态返回不同的光标类型
     */
    public static class DynamicCursorTool extends BaseTool implements ICursorProvider {
        
        private boolean isDrawing = false;
        
        public DynamicCursorTool() {
            super("dynamic_tool", "动态光标工具");
        }
        
        @Override
        public String getCursorType() {
            if (isDrawing) {
                return ICursorProvider.CursorTypes.CROSSHAIR; // 绘制时使用十字光标
            } else {
                return ICursorProvider.CursorTypes.POINTER; // 选择时使用指针光标
            }
        }
        
        public void setDrawingMode(boolean drawing) {
            this.isDrawing = drawing;
        }
        
        // 其他工具方法...
    }
}
