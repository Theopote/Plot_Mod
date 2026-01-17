package com.masterplanner.ui.toolbar.group;

import com.masterplanner.core.command.CommandHistory;
import com.masterplanner.core.command.commands.ClearCanvasCommand;
import com.masterplanner.core.snap.SnapManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.grid.GridManager;
import com.masterplanner.ui.toolbar.ToolbarUIUtils;
import imgui.ImGui;

/**
 * 工具设置组
 * 包含吸附、网格、清除画布等设置工具
 */
public class ToolSettingsGroup extends AbstractToolbarGroup {
    
    private final SnapManager snapManager;
    private final GridManager gridManager;
    
    public ToolSettingsGroup(AppState appState, EventBus eventBus) {
        super("工具设置", appState, eventBus);
        this.snapManager = SnapManager.getInstance();
        this.gridManager = GridManager.getInstance();
    }
    
    @Override
    protected void renderGroupContent() {
        pushButtonStyles();
        
        try {
            renderSnapButton();
            addButtonSpacing();
            
            renderGridButton();
            addButtonSpacing();
            
            renderClearButton();
            
        } catch (Exception e) {
            LOGGER.error("Error rendering tool settings group", e);
        } finally {
            popButtonStyles();
        }
        
        // 渲染设置窗口
        snapManager.renderSettingsWindow();
        gridManager.renderSettingsWindow();
    }
    
    /**
     * 渲染吸附按钮
     */
    private void renderSnapButton() {
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.MAGNET),
                "吸附", false, snapManager.isEnabled())) {
            snapManager.setEnabled(!snapManager.isEnabled());
        }
        
        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseClicked(1)) {
                snapManager.toggleSettings();
            }
            ImGui.setTooltip("左键: 开关吸附\n右键: 吸附设置");
        }
    }
    
    /**
     * 渲染网格按钮
     */
    private void renderGridButton() {
        boolean isGridEnabled = gridManager.isEnabled();
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.GRID),
                "网格", false, isGridEnabled)) {
            boolean newState = !isGridEnabled;
            
            LOGGER.debug("======= 网格按钮被点击 =======");
            LOGGER.debug("当前网格状态: {}", isGridEnabled);
            LOGGER.debug("即将切换到: {}", newState);
            
            try {
                gridManager.setEnabled(newState);
                LOGGER.debug("已调用gridManager.setEnabled({})，新状态已设置", newState);
            } catch (Exception e) {
                LOGGER.error("设置网格状态出错", e);
            }
        }
        
        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseClicked(1)) {
                LOGGER.debug("网格按钮被右键点击，打开设置窗口");
                gridManager.toggleSettings();
            }
            ImGui.setTooltip("左键: 开关网格\n右键: 网格设置");
        }
    }
    
    /**
     * 渲染清除按钮
     */
    private void renderClearButton() {
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.CLEAR),
                "清除绘图面板")) {
            // 使用命令系统，支持撤销和重做
            ClearCanvasCommand command = new ClearCanvasCommand(appState);
            CommandHistory.getInstance().execute(command);
        }
    }
    
    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(3); // 3个按钮（吸附、网格、清除）
    }
}