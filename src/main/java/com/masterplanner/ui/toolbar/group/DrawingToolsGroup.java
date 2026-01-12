package com.masterplanner.ui.toolbar.group;

import com.masterplanner.core.group.ShapeGroupManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;

import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.toolbar.ToolbarUIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * 绘图工具组
 * 包含成组、解组等绘图操作工具
 * 注意：剪切、拷贝、粘贴工具已被移除
 */
public class DrawingToolsGroup extends AbstractToolbarGroup {
    
    private final ShapeGroupManager groupManager;
    
    public DrawingToolsGroup() {
        super("绘图工具");
        this.groupManager = new ShapeGroupManager(AppState.getInstance(), EventBus.getInstance());
    }
    
    public DrawingToolsGroup(EventBus eventBus) {
        super("绘图工具", AppState.getInstance(), eventBus);
        this.groupManager = new ShapeGroupManager(appState, eventBus);
    }
    
    @Override
    protected void renderGroupContent() {
        pushButtonStyles();
        
        try {
            // 成组
            renderGroupButton();
            addButtonSpacing();

            // 解组
            renderUngroupButton();
            
        } catch (Exception e) {
            LOGGER.error("Error rendering drawing tools group", e);
        } finally {
            popButtonStyles();
        }
    }
    
    /**
     * 渲染成组按钮
     */
    private void renderGroupButton() {
        var canGroupResult = groupManager.canGroup();
        boolean canGroup = canGroupResult.isValid();
        
        // 如果不能成组，将按钮设为禁用状态
        if (!canGroup) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0x80808080);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0x80808080);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0x80808080);
        }
        
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.GROUP),
                "成组", !canGroup, false)) {
            
            if (canGroup) {
                var result = groupManager.groupSelectedShapes();
                if (!result.isSuccess()) {
                    showWarningMessage(result.getMessage());
                } else {
                    LOGGER.info("成组操作成功: {}", result.getMessage());
                }
            }
        }
        
        if (!canGroup) {
            ImGui.popStyleColor(3);
        }
        
        // 添加工具提示
        if (ImGui.isItemHovered()) {
            if (canGroup) {
                ImGui.setTooltip("将选中的图形组成一组");
            } else {
                ImGui.setTooltip("成组: " + canGroupResult.getMessage());
            }
        }
    }
    
    /**
     * 渲染解组按钮
     */
    private void renderUngroupButton() {
        var canUngroupResult = groupManager.canUngroup();
        boolean canUngroup = canUngroupResult.isValid();
        
        // 如果不能解组，将按钮设为禁用状态
        if (!canUngroup) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0x80808080);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0x80808080);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0x80808080);
        }
        
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.UNGROUP),
                "解组", !canUngroup, false)) {
            
            if (canUngroup) {
                var result = groupManager.ungroupSelectedShapes();
                if (!result.isSuccess()) {
                    showWarningMessage(result.getMessage());
                } else {
                    LOGGER.info("解组操作成功: {}", result.getMessage());
                }
            }
        }
        
        if (!canUngroup) {
            ImGui.popStyleColor(3);
        }
        
        // 添加工具提示
        if (ImGui.isItemHovered()) {
            if (canUngroup) {
                ImGui.setTooltip("将选中的组解散为独立图形");
            } else {
                ImGui.setTooltip("解组: " + canUngroupResult.getMessage());
            }
        }
    }
    
    /**
     * 显示警告消息
     * TODO: 这里可以集成到ControlPanel的警告对话框系统中
     */
    private void showWarningMessage(String message) {
        LOGGER.warn("操作警告: {}", message);
        // 暂时通过日志显示警告，后续可以集成到UI警告系统
    }
    
    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(2); // 2个按钮（成组、解组）
    }
}