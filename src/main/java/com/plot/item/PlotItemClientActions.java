package com.plot.item;

/**
 * 客户端动作挂钩：物品注册在通用入口，不能在类型上引用 {@code PlotClient}/{@code MinecraftClient}。
 * 物理客户端在 {@code PlotClient} 初始化时注册；dedicated server 上保持空操作。
 */
public final class PlotItemClientActions {
    private static volatile Runnable openScreen = () -> {};

    private PlotItemClientActions() {
    }

    public static void setOpenScreen(Runnable action) {
        openScreen = action != null ? action : () -> {};
    }

    public static void openScreen() {
        openScreen.run();
    }
}
