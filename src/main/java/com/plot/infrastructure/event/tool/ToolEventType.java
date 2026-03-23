package com.plot.infrastructure.event.tool;

/**
 * 工具事件类型
 */
public enum ToolEventType {
    ACTIVATE,   // 激活工具
    DEACTIVATE, // 停用工具
    EXECUTE,    // 执行工具操作
    CANCEL,     // 取消操作
    PREVIEW,    // 预览操作
    START,      // 开始操作
    END,        // 结束操作
    UPDATE,     // 更新操作
    CONFIG      // 配置工具
} 