package com.plot.infrastructure.event;

/**
 * EventBus 订阅句柄。保留引用以便可靠取消（尤其是 method reference / lambda）。
 */
public interface SubscriptionToken extends AutoCloseable {
    /**
     * 取消本订阅。可重复调用。
     */
    void unsubscribe();

    /**
     * 是否仍处于订阅状态。
     */
    boolean isActive();

    @Override
    default void close() {
        unsubscribe();
    }
}
