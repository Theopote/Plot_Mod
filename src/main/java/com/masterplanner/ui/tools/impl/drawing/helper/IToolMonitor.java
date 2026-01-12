package com.masterplanner.ui.tools.impl.drawing.helper;

/**
 * 工具监控器接口
 * 负责监控工具的性能和健康状态
 */
public interface IToolMonitor {
    
    /**
     * 记录事件处理
     * @param eventType 事件类型
     * @param success 是否成功
     */
    void recordEvent(String eventType, boolean success);
    
    /**
     * 记录性能指标
     * @param metricName 指标名称
     * @param value 指标值
     */
    void recordMetric(String metricName, double value);
    
    /**
     * 获取错误计数
     */
    int getErrorCount();
    
    /**
     * 重置统计
     */
    void resetStatistics();
    
    /**
     * 检查工具健康状态
     */
    boolean isHealthy();
    
    /**
     * 获取性能报告
     */
    String getPerformanceReport();
} 