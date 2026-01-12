package com.masterplanner.ui.toolbar.group;

import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolEvent;
import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.toolbar.ControlPanel;
import com.masterplanner.ui.toolbar.ToolbarUIUtils;

/**
 * 文件工具组
 * 包含新建、保存、导入、撤销、重做等文件操作工具
 */
public class FileToolsGroup extends AbstractToolbarGroup {
    
    public FileToolsGroup() {
        super("文件工具");
    }
    
    public FileToolsGroup(AppState appState, EventBus eventBus) {
        super("文件工具", appState, eventBus);
    }
    
    @Override
    protected void renderGroupContent() {
        pushButtonStyles();
        
        try {
            // 新建文件
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(ControlPanelIcons.NEW_FILE),
                    "新建")) {
                handleToolEvent("new_file");
            }
            addButtonSpacing();

            // 保存文件
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(ControlPanelIcons.SAVE),
                    "保存")) {
                handleToolEvent("save");
            }
            addButtonSpacing();

            // 导入文件
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(ControlPanelIcons.IMPORT),
                    "导入")) {
                handleToolEvent("import");
            }
            addButtonSpacing();

            // 撤销 - 检查是否可以撤销
            boolean canUndo = appState.getCommandHistory().canUndo();
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(ControlPanelIcons.UNDO),
                    "撤销", !canUndo, false)) {
                handleToolEvent("undo");
            }
            addButtonSpacing();

            // 重做 - 检查是否可以重做
            boolean canRedo = appState.getCommandHistory().canRedo();
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(ControlPanelIcons.REDO),
                    "重做", !canRedo, false)) {
                handleToolEvent("redo");
            }
            
        } finally {
            popButtonStyles();
        }
    }
    
    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(5); // 5个按钮
    }
    
    /**
     * 处理工具事件
     */
    private void handleToolEvent(String toolId) {
        try {
            eventBus.publish(new ToolEvent(
                ControlPanel.SOURCE, 
                ToolEvent.ToolEventType.TOOL_FILE, 
                toolId
            ));
            LOGGER.debug("Published file tool event: {}", toolId);
        } catch (Exception e) {
            LOGGER.error("Error handling file tool event: {}", toolId, e);
        }
    }
}