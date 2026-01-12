package com.masterplanner.ui.shortcut;

import com.masterplanner.api.shortcut.IShortcutListener;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.command.RedoEvent;
import com.masterplanner.infrastructure.event.command.UndoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 编辑操作快捷键监听器
 * 处理撤销、重做等编辑相关的快捷键
 */
public class EditShortcutListener implements IShortcutListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditShortcutListener.class);
    private static final String SHORTCUT_UNDO = "ctrl+z";
    private static final String SHORTCUT_REDO_1 = "ctrl+y"; 
    private static final String SHORTCUT_REDO_2 = "ctrl+shift+z";
    
    private final EventBus eventBus;
    
    public EditShortcutListener() {
        this.eventBus = EventBus.getInstance();
        LOGGER.debug("编辑快捷键监听器已初始化");
    }
    
    @Override
    public boolean onShortcutTriggered(String shortcut) {
        if (shortcut == null) return false;
        
        String normalizedShortcut = shortcut.toLowerCase();
        
        LOGGER.debug("处理快捷键: {}", normalizedShortcut);
        
        switch (normalizedShortcut) {
            case SHORTCUT_UNDO:
                LOGGER.debug("触发撤销快捷键");
                eventBus.publish(new UndoEvent("EditShortcutListener"));
                return true;
                
            case SHORTCUT_REDO_1:
            case SHORTCUT_REDO_2:
                LOGGER.debug("触发重做快捷键");
                eventBus.publish(new RedoEvent("EditShortcutListener"));
                return true;
                
            default:
                return false;
        }
    }
    
    @Override
    public int getPriority() {
        // 编辑操作通常应该具有较高的优先级
        return 100;
    }
    
    @Override
    public String getDescription() {
        return "处理基本编辑操作（撤销、重做）的快捷键";
    }
} 