package com.plot.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 屏幕状态管理器
 * 用于跟踪 Plot 屏幕是否打开，以便控制云渲染、雾渲染和 HUD 显示
 */
public class PlotScreenState {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlotScreenState.class);
    
    private static boolean plotScreenOpen = false;
    /** 一次性标记：当前正从 PlotScreen 切换到 Plot 子界面。 */
    private static boolean switchingToPlotSubScreen = false;
    /** 防点击穿透：返回 Plot 后短时间内吞掉一次鼠标点击。 */
    private static long suppressNextPlotClickUntilMs = 0L;

    /**
     * 检查 Plot 屏幕是否打开
     */
    public static boolean isPlotScreenOpen() {
        return plotScreenOpen;
    }

    /**
     * 检查 Plot 模组界面是否处于活跃状态。
     *
     * 仅靠 plotScreenOpen 布尔值不够：从 PlotScreen 切到 BlockConfigNativeScreen 时，
     * PlotScreen.removed() 会把状态设为 false，导致 HUD/手臂/雾/云重新出现。
     * 这里同时检查当前 Screen 是否属于 Plot 界面族，保证界面切换期间状态连续。
     */
    public static boolean isPlotUiActive() {
        if (plotScreenOpen) {
            return true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }

        Screen currentScreen = client.currentScreen;
        return currentScreen instanceof PlotScreen || currentScreen instanceof BlockConfigNativeScreen;
    }
    
    /**
     * 设置 Plot 屏幕打开状态
     */
    public static void setPlotScreenOpen(boolean open) {
        plotScreenOpen = open;
        LOGGER.debug("Plot 屏幕状态: {}", open ? "打开" : "关闭");
    }

    /** 标记一次从 PlotScreen 进入子界面的过渡。 */
    public static void markSwitchingToPlotSubScreen() {
        switchingToPlotSubScreen = true;
    }

    /** 消费一次过渡标记。 */
    public static boolean consumeSwitchingToPlotSubScreen() {
        boolean value = switchingToPlotSubScreen;
        switchingToPlotSubScreen = false;
        return value;
    }

    /** 标记在返回 Plot 后需要屏蔽一次点击穿透。 */
    public static void markSuppressNextPlotClick() {
        suppressNextPlotClickUntilMs = System.currentTimeMillis() + 200L;
    }

    /** 消费点击穿透屏蔽标记（仅在有效时间窗内生效一次）。 */
    public static boolean consumeSuppressNextPlotClick() {
        long now = System.currentTimeMillis();
        if (suppressNextPlotClickUntilMs > 0L && now <= suppressNextPlotClickUntilMs) {
            suppressNextPlotClickUntilMs = 0L;
            return true;
        }
        if (suppressNextPlotClickUntilMs > 0L && now > suppressNextPlotClickUntilMs) {
            suppressNextPlotClickUntilMs = 0L;
        }
        return false;
    }

}
