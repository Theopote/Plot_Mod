package com.plot.ui.screen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 屏幕状态管理器
 * 用于跟踪 Plot 屏幕是否打开，以便控制云渲染、雾渲染和 HUD 显示
 */
public class PlotScreenState {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlotScreenState.class);
    
    private static boolean plotScreenOpen = false;

    /**
     * 检查 Plot 屏幕是否打开
     */
    public static boolean isPlotScreenOpen() {
        return plotScreenOpen;
    }
    
    /**
     * 设置 Plot 屏幕打开状态
     */
    public static void setPlotScreenOpen(boolean open) {
        plotScreenOpen = open;
        LOGGER.debug("Plot 屏幕状态: {}", open ? "打开" : "关闭");
    }

}
