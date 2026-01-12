package com.masterplanner.api.tool;

/**
 * 工具状态枚举
 */
public enum ToolState {
    /** 工具未激活 */
    INACTIVE,
    
    /** 工具已激活但未开始操作 */
    ACTIVE,
    
    /** 工具正在执行操作 */
    OPERATING,
    
    /** 工具暂停操作 */
    PAUSED,
    
    /** 工具完成操作 */
    COMPLETED,
    
    /** 工具被禁用 */
    DISABLED
}
