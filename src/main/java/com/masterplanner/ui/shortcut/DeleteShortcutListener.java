package com.masterplanner.ui.shortcut;

import com.masterplanner.api.shortcut.IShortcutListener;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.command.CommandManager;
import com.masterplanner.core.command.commands.DeleteShapesCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除操作快捷键监听器
 * 处理删除键和相关快捷键
 */
public class DeleteShortcutListener implements IShortcutListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteShortcutListener.class);
    private static final String SHORTCUT_DELETE = "delete";
    
    public DeleteShortcutListener() {
        LOGGER.debug("删除快捷键监听器已初始化");
    }
    
    @Override
    public boolean onShortcutTriggered(String shortcut) {
        if (shortcut == null) return false;
        
        String normalizedShortcut = shortcut.toLowerCase();
        
        LOGGER.debug("处理删除快捷键: {}", normalizedShortcut);
        
        if (SHORTCUT_DELETE.equals(normalizedShortcut)) {
            return handleDelete();
        }
        
        return false;
    }
    
    /**
     * 处理删除操作
     */
    private boolean handleDelete() {
        AppState appState = AppState.getInstance();
        List<Shape> selectedShapes = appState.getSelectedShapes();
        
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            LOGGER.debug("Delete键按下，但没有选中的图形");
            return false;
        }
        
        LOGGER.debug("Delete键按下，通过CommandManager删除 {} 个选中图形", selectedShapes.size());
        
        try {
            // 使用CommandManager执行删除命令，这样可以：
            // 1. 记录到命令历史中，支持撤销/重做
            // 2. 发布正确的命令事件
            // 3. 确保业务逻辑的完整性
            CommandManager commandManager = CommandManager.getInstance();
            
            DeleteShapesCommand deleteCommand = new DeleteShapesCommand(
                new ArrayList<>(selectedShapes));
            
            commandManager.executeCommand(deleteCommand);
            
            LOGGER.debug("删除命令执行成功");
            return true;
            
        } catch (Exception e) {
            LOGGER.error("执行删除命令时出错: {}", e.getMessage(), e);
            // 降级处理：如果命令执行失败，仍然使用原来的方式
            appState.deleteSelectedShapes();
            return true;
        }
    }
    
    @Override
    public int getPriority() {
        // 删除操作通常应该具有较高的优先级
        return 90;
    }
    
    @Override
    public String getDescription() {
        return "处理删除快捷键";
    }
    
    @Override
    public boolean isEnabled() {
        return IShortcutListener.super.isEnabled();
    }
}