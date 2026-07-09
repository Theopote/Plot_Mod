package com.plot.ui.toolbar.group;

import com.plot.core.command.CommandHistory;
import com.plot.core.command.commands.ClearCanvasCommand;
import com.plot.core.snap.SnapManager;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.ui.component.ControlPanelIcons;
import com.plot.ui.grid.GridManager;
import com.plot.ui.toolbar.ToolbarUIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 工具设置组
 * 包含吸附、网格、清除画布等设置工具
 */
public class ToolSettingsGroup extends AbstractToolbarGroup {
    
    private final SnapManager snapManager;
    private final GridManager gridManager;
    
    public ToolSettingsGroup(AppState appState, EventBus eventBus) {
        super("toolbar.plot.group.tool_settings", appState, eventBus);
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
                PlotI18n.tr("toolbar.plot.snap"), false, snapManager.isEnabled())) {
            snapManager.setEnabled(!snapManager.isEnabled());
        }
        
        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseClicked(1)) {
                snapManager.toggleSettings();
            }
            ToolbarUIUtils.renderThemedTooltip(PlotI18n.tr("toolbar.plot.snap_tooltip"));
        }
    }
    
    /**
     * 渲染网格按钮
     */
    private void renderGridButton() {
        boolean isGridEnabled = gridManager.isEnabled();
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.GRID),
                PlotI18n.tr("toolbar.plot.grid"), false, isGridEnabled)) {
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
            ToolbarUIUtils.renderThemedTooltip(PlotI18n.tr("toolbar.plot.grid_tooltip"));
        }
    }
    
    /**
     * 渲染清除按钮
     */
    private void renderClearButton() {
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.CLEAR),
                PlotI18n.tr("toolbar.plot.clear_canvas"))) {
            // 使用命令系统，支持撤销和重做
            ClearCanvasCommand command = new ClearCanvasCommand(appState);
            CommandHistory.getInstance().execute(command);
        }

        if (ImGui.isItemHovered()) {
            ToolbarUIUtils.renderThemedTooltip(PlotI18n.tr("toolbar.plot.clear_canvas_tooltip"));
        }
    }
    
    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(3); // 3个按钮（吸附、网格、清除）
    }
}