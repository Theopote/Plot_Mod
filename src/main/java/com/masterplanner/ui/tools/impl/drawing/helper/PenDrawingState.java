package com.masterplanner.ui.tools.impl.drawing.helper;

/**
 * 钢笔绘制状态枚举
 * 
 * 用于管理钢笔工具的不同绘制阶段，提供更清晰的状态转换逻辑。
 */
public enum PenDrawingState {
    /** 空闲状态：等待开始绘制 */
    IDLE,
    
    /** 等待下一个点：已有至少一个点，等待用户添加下一个锚点 */
    WAITING_FOR_POINT,
    
    /** 定义控制手柄：用户正在拖拽以定义控制点 */
    DEFINING_HANDLE,
    
    /** 完成状态：绘制已完成，等待确认或取消 */
    COMPLETED
} 