package com.plot.api.resource;

/**
 * 可清理资源接口
 * 
 * 实现此接口的类表示它们拥有需要显式清理的资源，
 * 如文件句柄、网络连接、缓存等。
 * 
 * 这个接口遵循了"接口隔离原则"，提供了类型安全的资源清理机制，
 * 避免了使用反射进行资源清理的脆弱性。
 * 
 * @see java.lang.AutoCloseable
 * @since 1.0
 */
public interface IDisposable {
    
    /**
     * 清理资源
     * 
     * 此方法应该是幂等的，即多次调用不会产生副作用。
     * 实现类应该确保在调用此方法后，对象处于安全的状态，
     * 不再持有任何需要清理的资源。
     * 
     * 与 {@link java.lang.AutoCloseable#close()} 不同，
     * 此方法不会抛出检查异常，以简化调用方的错误处理。
     * 
     * @throws RuntimeException 如果清理过程中发生不可恢复的错误
     */
    void dispose();
    
    /**
     * 检查资源是否已被清理
     * 
     * @return 如果资源已被清理则返回 true，否则返回 false
     */
    default boolean isDisposed() {
        return false; // 默认实现，子类可以覆盖
    }
    
    /**
     * 获取清理状态的描述信息
     * 
     * 主要用于调试和日志记录
     * 
     * @return 清理状态的描述字符串
     */
    default String getDisposalStatus() {
        return isDisposed() ? "已清理" : "未清理";
    }
} 