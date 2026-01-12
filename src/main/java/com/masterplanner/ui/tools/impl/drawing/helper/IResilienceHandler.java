package com.masterplanner.ui.tools.impl.drawing.helper;

/**
 * 恢复处理器接口
 * 负责错误处理和系统恢复
 */
public interface IResilienceHandler {
    
    /**
     * 处理错误
     * @param operation 操作名称
     * @param error 错误对象
     */
    void handleError(String operation, Exception error);
    
    /**
     * 检查是否需要保护性重置
     */
    boolean shouldPerformProtectiveReset();
    
    /**
     * 执行保护性重置
     * @param reason 重置原因
     */
    void performProtectiveReset(String reason);
    
    /**
     * 获取连续错误计数
     */
    int getConsecutiveErrorCount();
    
    /**
     * 重置错误状态
     */
    void resetErrorState();
    
    /**
     * 检查是否处于保护状态
     */
    boolean isInProtectiveState();
} 