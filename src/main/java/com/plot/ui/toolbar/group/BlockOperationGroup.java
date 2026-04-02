package com.plot.ui.toolbar.group;

import com.plot.core.command.commands.ProjectGhostBlocksCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.infrastructure.event.block.BlockProjectionEvent;
import com.plot.infrastructure.event.block.LineToBlockEvent;
import com.plot.ui.component.ControlPanelIcons;
import com.plot.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog;
import com.plot.ui.dialog.LineToBlockSettingsDialog;
import com.plot.ui.dialog.ProjectionSettingsDialog;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.toolbar.ToolbarUIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

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
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        client.execute(() -> client.setScreen(new BlockConfigNativeScreen(blockConfigDialog, client.currentScreen)));
                        LOGGER.debug("原生方块配置面板打开成功");
                    }
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
            int disabledColor = ThemeManager.getInstance().getCurrentTheme().disabledBackground;
            ImGui.pushStyleColor(ImGuiCol.Button, disabledColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, disabledColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, disabledColor);
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
            ToolbarUIUtils.renderThemedTooltip("左键: 执行线转方块\n右键: 线转方块设置");
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
            ToolbarUIUtils.renderThemedTooltip("左键: 执行投影\n右键: 投影设置");
        }
    }
    
    /**
     * 执行线转方块
     */
    private void executeLineToBlock(List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return;
        }

        // 由处理器决定默认标高（优先玩家脚下），这里传入 NaN 表示“未显式指定标高”
        double canvasHeight = Double.NaN;
        
        // 发布线转方块事件（默认预览模式）
        LineToBlockSettingsDialog.ConversionMode conversionMode =
                lineToBlockSettingsDialog != null ? lineToBlockSettingsDialog.getConversionMode() : LineToBlockSettingsDialog.ConversionMode.FULL;
        float simplificationRatio =
                lineToBlockSettingsDialog != null ? lineToBlockSettingsDialog.getSimplificationRatio() : 0.5f;
        // 线转方块固定为“仅轮廓转换”，不对封闭图形执行内部填充
        boolean fillClosedShapes = false;

        eventBus.publish(new LineToBlockEvent(shapes, conversionMode, simplificationRatio, canvasHeight, true, fillClosedShapes));
        LOGGER.debug("发布线转方块预览事件: 图形数量={}, 高度={}", shapes.size(), canvasHeight);
    }
    
    /**
     * 执行投影
     */
    private void executeProjection() {
        LOGGER.debug("执行投影操作...");
        
        var ghostBlockManager = GhostBlockManager.getInstance();
        List<GhostBlockManager.GhostBlock> visibleGhostBlocks = ghostBlockManager.getVisibleGhostBlocks();
        int ghostBlockCount = visibleGhostBlocks.size();
        
        if (ghostBlockCount == 0) {
            LOGGER.warn("没有幽灵方块需要投影");
            return;
        }
        
        LOGGER.info("发现 {} 个幽灵方块需要投影", ghostBlockCount);
        ProjectGhostBlocksCommand projectionCommand = getProjectGhostBlocksCommand(visibleGhostBlocks);
        appState.getCommandHistory().execute(projectionCommand);

        int projectedCount = projectionCommand.getProjectedCount();
        
        if (projectedCount > 0) {
            LOGGER.info("投影操作完成，共投影 {} 个方块", projectedCount);
        } else {
            LOGGER.warn("投影操作失败，没有方块被投影");
        }
    }

    private @NotNull ProjectGhostBlocksCommand getProjectGhostBlocksCommand(List<GhostBlockManager.GhostBlock> visibleGhostBlocks) {
        BlockProjectionEvent.ProjectionMode projectionMode = BlockProjectionEvent.ProjectionMode.GROUND;
        Integer elevation = null;
        if (projectionSettingsDialog != null && projectionSettingsDialog.getProjectionMode() == ProjectionSettingsDialog.ProjectionMode.ELEVATION) {
            projectionMode = BlockProjectionEvent.ProjectionMode.ELEVATION;
            elevation = projectionSettingsDialog.getElevation();
        }

        return new ProjectGhostBlocksCommand(
                visibleGhostBlocks,
                projectionMode,
                elevation
        );
    }

    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(3); // 3个按钮（方块配置、线转方块、投影方块）
    }
}
