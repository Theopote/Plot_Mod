package com.masterplanner.ui.toolbar.group;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.block.GhostBlockManager;
import com.masterplanner.infrastructure.event.block.LineToBlockEvent;
import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog;
import com.masterplanner.ui.dialog.LineToBlockSettingsDialog;
import com.masterplanner.ui.dialog.ProjectionSettingsDialog;
import com.masterplanner.ui.toolbar.ToolbarUIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * 方块操作组
 * 包含方块配置、线转方块、投影方块等方块相关操作
 */
public class BlockOperationGroup extends AbstractToolbarGroup {
    
    private final CompactBlockConfigDialog blockConfigDialog;
    private final LineToBlockSettingsDialog lineToBlockSettingsDialog;
    private final ProjectionSettingsDialog projectionSettingsDialog;
    
    public BlockOperationGroup(AppState appState, EventBus eventBus,
                               CompactBlockConfigDialog blockConfigDialog,
                               LineToBlockSettingsDialog lineToBlockSettingsDialog,
                               ProjectionSettingsDialog projectionSettingsDialog) {
        super("方块操作", appState, eventBus);
        this.blockConfigDialog = blockConfigDialog;
        this.lineToBlockSettingsDialog = lineToBlockSettingsDialog;
        this.projectionSettingsDialog = projectionSettingsDialog;
    }
    
    @Override
    protected void renderGroupContent() {
        pushButtonStyles();
        
        try {
            renderBlockConfigButton();
            addButtonSpacing();
            
            renderLineToBlockButton();
            addButtonSpacing();
            
            renderProjectionButton();
            
        } catch (Exception e) {
            LOGGER.error("Error rendering block operation group", e);
        } finally {
            popButtonStyles();
        }
    }
    
    /**
     * 渲染方块配置按钮
     */
    private void renderBlockConfigButton() {
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.BLOCK_CONFIG),
                "方块配置")) {
            try {
                LOGGER.debug("方块配置按钮被点击，准备打开对话框...");
                if (blockConfigDialog != null) {
                    blockConfigDialog.open();
                    LOGGER.debug("方块配置对话框打开成功");
                } else {
                    LOGGER.error("方块配置对话框实例为null，无法打开");
                    // TODO: 显示警告消息
                }
            } catch (Exception e) {
                LOGGER.error("打开方块配置对话框时发生错误", e);
                // TODO: 显示警告消息
            }
        }
    }
    
    /**
     * 渲染线转方块按钮
     */
    private void renderLineToBlockButton() {
        List<Shape> shapes = appState.getSelectedShapes();
        boolean lineToBlockEnabled = !shapes.isEmpty();
        
        if (!lineToBlockEnabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0x80808080);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0x80808080);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0x80808080);
        }
        
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.LINE_TO_BLOCK),
                "线转方块", !lineToBlockEnabled, false)) {
            // 执行线转方块逻辑
            executeLineToBlock(shapes);
        }
        
        if (!lineToBlockEnabled) {
            ImGui.popStyleColor(3);
        }
        
        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseClicked(1)) {
                LOGGER.debug("线转方块按钮被右键点击，打开设置窗口");
                if (lineToBlockSettingsDialog != null) {
                    lineToBlockSettingsDialog.open();
                } else {
                    LOGGER.error("线转方块设置对话框实例为null");
                }
            }
            ImGui.setTooltip("左键: 执行线转方块\n右键: 线转方块设置");
        }
    }
    
    /**
     * 渲染投影按钮
     */
    private void renderProjectionButton() {
        if (ToolbarUIUtils.renderToolbarButton(
                ControlPanelIcons.getIdentifier(ControlPanelIcons.PROJECTION),
                "投影方块")) {
            executeProjection();
        }
        
        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseClicked(1)) {
                LOGGER.debug("投影方块按钮被右键点击，打开设置窗口");
                if (projectionSettingsDialog != null) {
                    projectionSettingsDialog.open();
                } else {
                    LOGGER.error("投影设置对话框实例为null");
                }
            }
            ImGui.setTooltip("左键: 执行投影\n右键: 投影设置");
        }
    }
    
    /**
     * 执行线转方块
     */
    private void executeLineToBlock(List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        double canvasHeight = client != null && client.player != null ? 
            Math.floor(client.player.getY()) : 64.0;
        
        // 发布线转方块事件（默认预览模式）
        eventBus.publish(new LineToBlockEvent(shapes, canvasHeight, true));
        LOGGER.debug("发布线转方块预览事件: 图形数量={}, 高度={}", shapes.size(), canvasHeight);
    }
    
    /**
     * 执行投影
     */
    private void executeProjection() {
        LOGGER.debug("执行投影操作...");
        
        var ghostBlockManager = GhostBlockManager.getInstance();
        int ghostBlockCount = ghostBlockManager.getVisibleGhostBlockCount();
        
        if (ghostBlockCount == 0) {
            LOGGER.warn("没有幽灵方块需要投影");
            return;
        }
        
        LOGGER.info("发现 {} 个幽灵方块需要投影", ghostBlockCount);
        int projectedCount = ghostBlockManager.projectAllGhostBlocks();
        
        if (projectedCount > 0) {
            LOGGER.info("投影操作完成，共投影 {} 个方块", projectedCount);
        } else {
            LOGGER.warn("投影操作失败，没有方块被投影");
        }
    }
    
    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(3); // 3个按钮（方块配置、线转方块、投影方块）
    }
}
