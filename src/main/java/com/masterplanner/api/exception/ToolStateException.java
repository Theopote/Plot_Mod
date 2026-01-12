package com.masterplanner.api.exception;

/**
 * 工具状态异常
 * 
 * 当工具处于无效状态或状态转换失败时抛出
 * 
 * @since 1.0
 */
public class ToolStateException extends ToolException {
    
    /**
     * 构造工具状态异常
     * 
     * @param toolId 工具ID
     * @param operation 发生异常的操作
     * @param message 异常消息
     */
    public ToolStateException(String toolId, String operation, String message) {
        super(toolId, operation, message);
    }
    
    /**
     * 构造工具状态异常
     * 
     * @param toolId 工具ID
     * @param operation 发生异常的操作
     * @param message 异常消息
     * @param cause 异常原因
     */
    public ToolStateException(String toolId, String operation, String message, Throwable cause) {
        super(toolId, operation, message, cause);
    }
    
    @Override
    public boolean requiresImmediateReset() {
        return true; // 状态异常需要立即重置
    }
    
    @Override
    public String getUserMessage() {
        return "工具状态异常，正在重置";
    }
    
    @Override
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.CRITICAL;
    }
} 