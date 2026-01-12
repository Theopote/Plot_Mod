package com.masterplanner.api.command;

/**
 * 命令状态枚举
 */
public enum CommandState {
    /** 命令未执行 */
    INITIAL,
    
    /** 命令执行中 */
    EXECUTING,
    
    /** 命令已执行 */
    EXECUTED,
    
    /** 命令已撤销 */
    UNDONE,
    
    /** 命令执行失败 */
    FAILED
}
