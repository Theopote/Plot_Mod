package com.masterplanner.ui.shortcut;

import com.masterplanner.api.shortcut.IShortcutListener;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.BaseTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Escape键快捷键监听器
 * 处理Escape键的各种功能：取消当前操作、清除选择等
 */
public class EscapeShortcutListener implements IShortcutListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EscapeShortcutListener.class);
    private static final String SHORTCUT_ESCAPE = "escape";
    
    public EscapeShortcutListener() {
        LOGGER.debug("Escape快捷键监听器已初始化");
    }
    
    @Override
    public boolean onShortcutTriggered(String shortcut) {
        if (shortcut == null) return false;
        
        String normalizedShortcut = shortcut.toLowerCase();
        
        LOGGER.debug("处理Escape快捷键: {}", normalizedShortcut);
        
        if (SHORTCUT_ESCAPE.equals(normalizedShortcut)) {
            return handleEscape();
        }
        
        return false;
    }
    
    /**
     * 处理Escape操作
     */
    private boolean handleEscape() {
        LOGGER.debug("Escape键按下，执行取消操作");
        
        AppState appState = AppState.getInstance();
        boolean handled = false;
        
        // 1. 取消当前工具的操作
        BaseTool currentTool = appState.getCurrentTool();
        if (currentTool != null) {
            try {
                currentTool.cancel();
                LOGGER.debug("取消了当前工具的操作: {}", currentTool.getClass().getSimpleName());
                handled = true;
            } catch (Exception e) {
                LOGGER.error("取消当前工具操作时出错: {}", e.getMessage(), e);
            }
        }
        
        // 2. 清除选择
        if (!appState.getSelectedShapes().isEmpty()) {
            appState.clearSelection();
            LOGGER.debug("清除了图形选择");
            handled = true;
        }
        
        // 3. 如果有其他需要取消的状态，可以在这里添加
        
        return handled;
    }
    
    @Override
    public int getPriority() {
        // Escape键应该有很高的优先级，因为它是取消操作
        return 200;
    }
    
    @Override
    public String getDescription() {
        return "处理Escape键取消操作";
    }
    
    @Override
    public boolean isEnabled() {
        return IShortcutListener.super.isEnabled();
    }
}