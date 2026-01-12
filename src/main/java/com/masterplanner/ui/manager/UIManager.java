package com.masterplanner.ui.manager;

import com.masterplanner.ui.screen.MasterPlannerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI管理器
 * <p>
 * 负责管理屏幕的打开、关闭和切换操作。
 * 使用单例模式确保全局唯一性，同时提供更好的可测试性。
 * <p>
 * 主要职责：
 * 1. 屏幕生命周期管理
 * 2. 屏幕切换逻辑
 * 3. 客户端状态检查
 * 4. 错误处理和日志记录
 */
public class UIManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIManager.class);
    private static volatile UIManager instance;
    
    // 双重检查锁定单例模式
    public static UIManager getInstance() {
        if (instance == null) {
            synchronized (UIManager.class) {
                if (instance == null) {
                    instance = new UIManager();
                }
            }
        }
        return instance;
    }
    
    private UIManager() {
        LOGGER.debug("UIManager 初始化完成");
    }
    
    /**
     * 打开 MasterPlanner 主界面
     * <p>
     * 执行必要的状态检查，确保在正确的上下文中打开界面
     */
    public void openMasterPlannerScreen() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            
            // 检查客户端状态
            if (client == null) {
                LOGGER.warn("无法打开 MasterPlanner 界面：MinecraftClient 实例为空");
                return;
            }
            
            // 检查玩家状态（确保在游戏中）
            if (client.player == null) {
                LOGGER.debug("无法打开 MasterPlanner 界面：玩家未进入游戏");
                return;
            }
            
            // 检查是否已经在 MasterPlanner 界面中
            Screen currentScreen = client.currentScreen;
            if (currentScreen instanceof MasterPlannerScreen) {
                LOGGER.debug("MasterPlanner 界面已经打开，跳过重复打开");
                return;
            }
            
            // 创建并打开新界面
            MasterPlannerScreen screen = new MasterPlannerScreen();
            client.setScreen(screen);
            
            LOGGER.info("MasterPlanner 界面已打开");
            
        } catch (Exception e) {
            LOGGER.error("打开 MasterPlanner 界面时发生错误: {}", e.getMessage(), e);
        }
    }
} 