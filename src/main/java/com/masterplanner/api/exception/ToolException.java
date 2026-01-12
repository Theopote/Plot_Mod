package com.masterplanner.api.exception;

/**
 * 工具异常基类
 * 
 * 所有与绘图工具相关的异常的基类，提供统一的异常处理机制
 * 
 * @since 1.0
 */
public abstract class ToolException extends RuntimeException {
    
    private final String toolId;
    private final String operation;
    
    /**
     * 构造工具异常
     * 
     * @param toolId 工具ID
     * @param operation 发生异常的操作
     * @param message 异常消息
     */
    protected ToolException(String toolId, String operation, String message) {
        super(message);
        this.toolId = toolId;
        this.operation = operation;
    }
    
    /**
     * 构造工具异常
     * 
     * @param toolId 工具ID
     * @param operation 发生异常的操作
     * @param message 异常消息
     * @param cause 异常原因
     */
    protected ToolException(String toolId, String operation, String message, Throwable cause) {
        super(message, cause);
        this.toolId = toolId;
        this.operation = operation;
    }
    
    /**
     * 获取工具ID
     * @return 工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 获取发生异常的操作
     * @return 操作名称
     */
    public String getOperation() {
        return operation;
    }
    
    /**
     * 检查异常是否需要立即重置工具状态
     * @return 是否需要立即重置
     */
    public abstract boolean requiresImmediateReset();
    
    /**
     * 获取用户友好的错误消息
     * @return 用户友好的错误消息
     */
    public abstract String getUserMessage();
    
    /**
     * 获取异常的严重程度
     * @return 严重程度级别
     */
    public abstract ErrorSeverity getSeverity();
    
    /**
     * 异常严重程度枚举
     */
    public enum ErrorSeverity {
        /** 信息级别 - 不影响正常使用 */
        INFO,
        
        /** 警告级别 - 可能影响体验但不致命 */
        WARNING,
        
        /** 错误级别 - 影响当前操作 */
        ERROR,
        
        /** 严重级别 - 需要重置工具状态 */
        CRITICAL,
        
        /** 致命级别 - 需要停用工具 */
        FATAL
    }
} 