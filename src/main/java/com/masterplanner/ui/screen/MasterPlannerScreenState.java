package com.masterplanner.ui.screen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MasterPlanner 屏幕状态管理器
 * 用于跟踪 MasterPlanner 屏幕是否打开，以便控制云渲染、雾渲染和 HUD 显示
 */
public class MasterPlannerScreenState {
    private static final Logger LOGGER = LoggerFactory.getLogger(MasterPlannerScreenState.class);
    
    private static boolean isMasterPlannerScreenOpen = false;
    private static boolean savedCloudRenderState = true; // 默认云渲染开启
    
    /**
     * 检查 MasterPlanner 屏幕是否打开
     */
    public static boolean isMasterPlannerScreenOpen() {
        return isMasterPlannerScreenOpen;
    }
    
    /**
     * 设置 MasterPlanner 屏幕打开状态
     */
    public static void setMasterPlannerScreenOpen(boolean open) {
        isMasterPlannerScreenOpen = open;
        LOGGER.debug("MasterPlanner 屏幕状态: {}", open ? "打开" : "关闭");
    }
    
    /**
     * 保存云渲染状态
     */
    public static void saveCloudRenderState(boolean enabled) {
        savedCloudRenderState = enabled;
        LOGGER.debug("保存云渲染状态: {}", enabled);
    }
    
    /**
     * 获取保存的云渲染状态
     */
    public static boolean getSavedCloudRenderState() {
        return savedCloudRenderState;
    }
}
