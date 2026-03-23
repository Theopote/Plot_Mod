package com.plot.ui.canvas;

/**
 * 光标提供者接口
 * 
 * 用于统一管理工具的光标类型，替代反射方式。
 * 所有需要自定义光标的工具都应该实现此接口。
 */
public interface ICursorProvider {
    
    /**
     * 获取工具对应的光标类型
     * @return 光标类型字符串
     */
    String getCursorType();
    
    /**
     * 默认光标类型常量
     */
    interface CursorTypes {
        String DEFAULT = "default";      // 默认箭头
        String CROSSHAIR = "crosshair";  // 十字光标
        String POINTER = "pointer";      // 指针光标
        String HAND = "hand";            // 手型光标
        String ERASER = "eraser";        // 橡皮擦光标
        String TEXT = "text";            // 文本光标
        String MOVE = "move";            // 移动光标
        String RESIZE = "resize";        // 调整大小光标
    }
}
