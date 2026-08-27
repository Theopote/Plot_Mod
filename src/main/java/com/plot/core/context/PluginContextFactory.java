package com.plot.core.context;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 插件宿主服务组装入口。默认只提供逻辑侧 {@link ApplicationContext}；
 * 客户端通过 {@link #setSupplier} 注入带世界服务的完整 {@link PluginContext}。
 */
public final class PluginContextFactory {
    private static volatile Supplier<PluginContext> supplier =
        () -> PluginContext.from(ApplicationContext.getInstance());

    private PluginContextFactory() {
    }

    public static void setSupplier(Supplier<PluginContext> next) {
        supplier = Objects.requireNonNull(next, "supplier");
    }

    public static void resetToDefault() {
        supplier = () -> PluginContext.from(ApplicationContext.getInstance());
    }

    public static PluginContext create() {
        return Objects.requireNonNull(supplier.get(), "PluginContext supplier returned null");
    }
}
