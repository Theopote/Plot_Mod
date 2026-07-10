package com.plot.api.event;

/**
 * 事件类型枚举
 * 统一管理所有事件类型
 */
public enum EventType {
    // === 鼠标事件 ===
    MOUSE_DOWN,
    MOUSE_UP,
    MOUSE_MOVE,
    MOUSE_DRAG,
    MOUSE_DOUBLE_CLICK,
    MOUSE_WHEEL,
    MOUSE_ENTER,
    MOUSE_LEAVE,
    MOUSE_RIGHT_DOWN,
    MOUSE_RIGHT_UP,
    MOUSE_MIDDLE_DOWN,
    MOUSE_MIDDLE_UP,
    CONTEXT_MENU,
    
    // === 键盘事件 ===
    KEY_DOWN,
    KEY_UP,
    KEY_PRESS,
    CTRL_KEY_DOWN,
    CTRL_KEY_UP,
    SHIFT_KEY_DOWN,
    SHIFT_KEY_UP,
    ALT_KEY_DOWN,
    ALT_KEY_UP,
    
    // === 拖放事件 ===
    DRAG_START,
    DRAG_OVER,
    DRAG_ENTER,
    DRAG_LEAVE,
    DROP,
    
    // === 文本事件 ===
    TEXT_INPUT,
    TEXT_CHANGED,
    
    // === 工具事件 ===
    TOOL_ACTIVATED,
    TOOL_DEACTIVATED,
    TOOL_CHANGED,
    TOOL_CONFIG_CHANGED,
    
    // === 视图事件 ===
    VIEW_CHANGED,
    ZOOM_CHANGED,
    PAN_CHANGED,
    CANVAS_RESIZED,
    
    // === 图层事件 ===
    LAYER_CHANGED,
    LAYER_ADDED,
    LAYER_REMOVED,
    
    // === 形状事件 ===
    SHAPE_ADDED,
    SHAPE_REMOVED,
    
    // === 选择事件 ===
    SELECTION_CHANGED,
    
    // === 命令事件 ===
    COMMAND_EXECUTED,
    UNDO,
    REDO,
    
    // === 文件事件 ===
    FILE_NEW,
    FILE_OPENED,
    FILE_SAVED,
    FILE_CLOSED,
    FILE_IMPORT,
    FILE_EXPORT,
    
    // === 窗口事件 ===
    WINDOW_ACTIVATED,
    WINDOW_DEACTIVATED,
    WINDOW_MINIMIZED,
    WINDOW_RESTORED,
    WINDOW_CLOSED,
    
    // === 网络事件 ===
    CONNECTION_ESTABLISHED,
    CONNECTION_LOST,
    CONNECTION_TIMEOUT,
    DATA_RECEIVED,
    DATA_SENT,
    
    // === 方块事件 ===
    BLOCK_CONVERSION,
    
    // === 其他事件 ===
    SAVE,
    LOAD,
    ERROR,
    WARNING,
    NOTIFICATION
}
