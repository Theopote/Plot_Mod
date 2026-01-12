package com.masterplanner.api.exception;

/**
 * 资源不可用异常
 * 
 * 当所需资源（如画布、相机、图层等）不可用时抛出
 * 
 * @since 1.0
 */
public class ResourceNotAvailableException extends ToolException {
    
    private final String resourceType;
    
    /**
     * 构造资源不可用异常
     * 
     * @param toolId 工具ID
     * @param operation 发生异常的操作
     * @param resourceType 资源类型
     * @param message 异常消息
     */
    public ResourceNotAvailableException(String toolId, String operation, String resourceType, String message) {
        super(toolId, operation, message);
        this.resourceType = resourceType;
    }
    
    /**
     * 构造资源不可用异常
     * 
     * @param toolId 工具ID
     * @param operation 发生异常的操作
     * @param resourceType 资源类型
     * @param message 异常消息
     * @param cause 异常原因
     */
    public ResourceNotAvailableException(String toolId, String operation, String resourceType, String message, Throwable cause) {
        super(toolId, operation, message, cause);
        this.resourceType = resourceType;
    }
    
    /**
     * 获取资源类型
     * @return 资源类型
     */
    public String getResourceType() {
        return resourceType;
    }
    
    @Override
    public boolean requiresImmediateReset() {
        return false; // 资源不可用通常可以重试
    }
    
    @Override
    public String getUserMessage() {
        return "资源暂不可用，请重试";
    }
    
    @Override
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.ERROR;
    }
} 