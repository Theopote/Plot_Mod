package com.plot.ui.panel;

import com.plot.core.state.AppState;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.tool.ToolChangedEvent;
import com.plot.infrastructure.event.Events.StatusMessageEvent;
import com.plot.ui.component.UIComponent;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.grid.GridManager;
import com.plot.ui.dialog.BlockConfigDialog.BlockConfigManager;
import com.plot.ui.dialog.ProjectionSettingsDialog;
import com.plot.camera.CameraManager;
import com.plot.utils.PlotI18n;
import net.minecraft.registry.Registries;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import java.util.List;
import imgui.ImGui;
import imgui.flag.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 状态属性面板组件
 * 负责显示应用程序的各种状态信息，包括工具、视图、图层、网格、摄像机等状态
 */
public class StatusPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/StatusPanel");
    
    private final AppState appState;
    private final EventBus eventBus;
    private final ToolManager toolManager;
    private boolean initialized = false;
    
    // 状态信息
    private String currentToolName;
    private float opacity = 100.0f;
    private String activeLayer;
    private String status;
    
    // 事件监听器
    private final EventListener toolChangedListener;
    private final EventListener statusMessageListener;

    public StatusPanel() {
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
        this.toolManager = ToolManager.getInstance();
        this.currentToolName = PlotI18n.toolLabel("select");
        this.status = PlotI18n.tr("status.plot.ready");
        
        // 初始化事件监听器
        this.toolChangedListener = event -> {
            if (event instanceof ToolChangedEvent) {
                updateStatus();
            }
        };
        
        this.statusMessageListener = event -> {
            if (event instanceof StatusMessageEvent statusEvent) {
                this.status = statusEvent.getMessage();
                LOGGER.debug("StatusPanel: 收到状态消息: {}", statusEvent.getMessage());
            }
        };
        
        // 注册事件监听器
        this.eventBus.subscribe(ToolChangedEvent.class, toolChangedListener);
        this.eventBus.subscribe(StatusMessageEvent.class, statusMessageListener);
    }

    @Override
    public void init() {
        if (initialized) {
            LOGGER.debug("StatusPanel已经初始化，跳过初始化流程");
            return;
        }

        try {
            LOGGER.info("正在初始化StatusPanel...");
            initialized = true;
            LOGGER.info("StatusPanel初始化完成");
        } catch (Exception e) {
            LOGGER.error("StatusPanel初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void render() {
        if (!initialized) {
            LOGGER.debug("StatusPanel未初始化，跳过渲染");
            return;
        }

        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 更新状态信息
        updateStatus();
        
        // 设置标题颜色
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.tabActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        
        if (ImGui.collapsingHeader(PlotI18n.tr("panel.plot.status_properties"), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.popStyleColor(4);
            
            // 设置边框样式 - 与HistoryPanel保持一致，使用统一的边距设置
            // 注意：beginChild 的边框应紧贴窗口边缘，内容边距由窗口级 WindowPadding 控制
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);  // 边框无内边距，与标题边距一致
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4, 4);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.panelControlRounding);
            
            // 设置颜色
            ImGui.pushStyleColor(ImGuiCol.ChildBg, currentTheme.panelBackground);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);
            ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
            
            try {
                // 创建带边框的子窗口来显示状态信息 - 禁用滚动条但允许鼠标滚动
                // 在 beginChild 内部设置内容边距，保持与标题边距一致
                ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4, 4);
                if (ImGui.beginChild("##status_content", 0, 0, true, ImGuiWindowFlags.NoScrollbar)) {
                    // 使用表格形式显示状态信息 - 添加边框和行背景
                        ImGui.pushStyleColor(ImGuiCol.TableHeaderBg, currentTheme.tabNormal);
                        ImGui.pushStyleColor(ImGuiCol.TableBorderStrong, currentTheme.border);
                        ImGui.pushStyleColor(ImGuiCol.TableBorderLight, currentTheme.separatorColor);
                        ImGui.pushStyleColor(ImGuiCol.TableRowBg, currentTheme.panelBackground);
                        ImGui.pushStyleColor(ImGuiCol.TableRowBgAlt, currentTheme.controlBackground);
                    if (ImGui.beginTable("##status_table", 2, 
                            ImGuiTableFlags.SizingStretchProp | 
                            ImGuiTableFlags.Borders | 
                            ImGuiTableFlags.RowBg)) {
                        ImGui.tableSetupColumn(PlotI18n.tr("status.plot.attribute"), ImGuiTableColumnFlags.WidthFixed, 80.0f);
                        ImGui.tableSetupColumn(PlotI18n.tr("status.plot.value"), ImGuiTableColumnFlags.WidthStretch);
                        // 显示表头行
                        ImGui.tableHeadersRow();
                        
                        // 当前工具
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.current_tool"));
                        ImGui.tableNextColumn();
                        ImGui.text(currentToolName);
                        
                        // 视图状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.view_status"));
                        ImGui.tableNextColumn();
                        ImGui.text(getViewStatus());
                        
                        // 画布透明度
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("panel.plot.canvas_opacity"));
                        ImGui.tableNextColumn();
                        ImGui.text(String.format("%.0f%%", opacity * 100.0f));
                        
                        // 图形数量
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.shape_count"));
                        ImGui.tableNextColumn();
                        ImGui.text(String.valueOf(getTotalShapeCount()));
                        
                        // 图层数量
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.layer_count"));
                        ImGui.tableNextColumn();
                        ImGui.text(String.valueOf(getLayerCount()));
                        
                        // 当前图层
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.current_layer"));
                        ImGui.tableNextColumn();
                        ImGui.text(activeLayer != null ? activeLayer : PlotI18n.tr("status.plot.none"));
                        
                        // 网格状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.grid_status"));
                        ImGui.tableNextColumn();
                        ImGui.text(getGridStatus());
                        
                        // 摄像机状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.camera_status"));
                        ImGui.tableNextColumn();
                        ImGui.text(getCameraStatus());
                        
                        // 方块配置 - 使用 textWrapped 支持换行
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.block_config"));
                        ImGui.tableNextColumn();
                        // 获取可用宽度以便文本换行（列宽度减去一些内边距）
                        float availableWidth = ImGui.getContentRegionAvailX();
                        if (availableWidth > 0) {
                            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + availableWidth);
                            ImGui.textWrapped(getBlockConfigStatus());
                            ImGui.popTextWrapPos();
                        } else {
                            ImGui.textWrapped(getBlockConfigStatus());
                        }
                        
                        // 投影模式
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.projection_mode"));
                        ImGui.tableNextColumn();
                        ImGui.text(getProjectionModeStatus());
                        
                        // 状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.textColored(currentTheme.mutedText, PlotI18n.tr("status.plot.status"));
                        ImGui.tableNextColumn();
                        ImGui.text(status != null ? status : PlotI18n.tr("status.plot.ready"));
                        
                        ImGui.endTable();
                    }
                    ImGui.popStyleColor(5);
                }
                ImGui.endChild();
                ImGui.popStyleVar(1);  // 弹出 beginChild 内部的 WindowPadding
            } finally {
                ImGui.popStyleColor(3);
                ImGui.popStyleVar(5);  // 弹出剩余的5个样式：WindowPadding, FramePadding, ItemSpacing, FrameBorderSize, FrameRounding
            }
        } else {
            ImGui.popStyleColor(4);
        }
    }
    
    private void updateStatus() {
        try {
            // 从AppState更新状态信息
            activeLayer = appState.getActiveLayerName();
            
            // 使用已初始化的 ToolManager
            com.plot.api.tool.ITool activeTool = toolManager.getActiveTool();
            if (activeTool != null) {
                currentToolName = PlotI18n.toolLabel(activeTool.getId());
            } else {
                currentToolName = PlotI18n.toolLabel("select");
            }
            
            // 更新透明度
            opacity = appState.getOpacity();
        } catch (Exception e) {
            LOGGER.error("Error updating status", e);
        }
    }
    
    private String getViewStatus() {
        try {
            CameraManager cameraManager = CameraManager.getInstance();
            if (cameraManager != null) {
                com.plot.camera.OrthographicCamera orthoCamera = cameraManager.getOrthographicCamera();
                boolean isLocked = orthoCamera != null && orthoCamera.isLocked();
                float viewDistance = cameraManager.getViewDistance();
                String lockState = isLocked ? PlotI18n.tr("status.plot.locked") : PlotI18n.tr("status.plot.unlocked");
                return String.format(PlotI18n.tr("status.plot.view_status_format"), lockState, viewDistance);
            }
        } catch (Exception e) {
            LOGGER.debug("获取视图状态失败: {}", e.getMessage());
        }
        return PlotI18n.tr("status.plot.unknown");
    }
    
    private int getTotalShapeCount() {
        try {
            return appState.getShapes().size();
        } catch (Exception e) {
            LOGGER.debug("获取图形数量失败: {}", e.getMessage());
            return 0;
        }
    }
    
    private int getLayerCount() {
        try {
            var layerManager = appState.getLayerManager();
            return layerManager != null ? layerManager.getLayerCount() : 0;
        } catch (Exception e) {
            LOGGER.debug("获取图层数量失败: {}", e.getMessage());
            return 0;
        }
    }
    
    private String getGridStatus() {
        try {
            GridManager gridManager = GridManager.getInstance();
            if (gridManager != null && gridManager.isEnabled()) {
                return PlotI18n.tr("status.plot.enabled");
            }
            return PlotI18n.tr("status.plot.disabled");
        } catch (Exception e) {
            LOGGER.debug("获取网格状态失败: {}", e.getMessage());
            return PlotI18n.tr("status.plot.unknown");
        }
    }
    
    private String getCameraStatus() {
        try {
            CameraManager cameraManager = CameraManager.getInstance();
            if (cameraManager != null) {
                boolean isOrthographic = cameraManager.isOrthographic();
                return isOrthographic ? PlotI18n.tr("status.plot.orthographic") : PlotI18n.tr("status.plot.perspective");
            }
        } catch (Exception e) {
            LOGGER.debug("获取摄像机状态失败: {}", e.getMessage());
        }
        return PlotI18n.tr("status.plot.unknown");
    }
    
    private String getBlockConfigStatus() {
        try {
            BlockConfigManager manager = BlockConfigManager.getInstance();
            if (manager == null || !manager.hasSelectedBlocks()) {
                return PlotI18n.tr("status.plot.not_configured");
            }
            
            List<String> blockIds = manager.getSelectedBlockIds();
            if (blockIds == null || blockIds.isEmpty()) {
                return PlotI18n.tr("status.plot.not_configured");
            }
            
            StringBuilder configInfo = new StringBuilder();
            try {
                for (int i = 0; i < blockIds.size(); i++) {
                    if (i > 0) {
                        configInfo.append(", ");
                    }
                    String blockId = blockIds.get(i);
                    configInfo.append(getBlockDisplayName(blockId));
                }
                return PlotI18n.tr("status.plot.configured", configInfo.toString());
            } catch (Exception e) {
                LOGGER.debug("转换方块名称时出错: {}", e.getMessage());
                return PlotI18n.tr("status.plot.configured_count", blockIds.size());
            }
        } catch (Exception e) {
            LOGGER.debug("获取方块配置状态失败: {}", e.getMessage());
            return PlotI18n.tr("status.plot.unknown");
        }
    }
    
    private String getBlockDisplayName(String blockId) {
        try {
            Identifier identifier = Identifier.of(blockId);
            Block block = Registries.BLOCK.get(identifier);
            String name = block.getName().getString();
            return name != null && !name.isEmpty() ? name : blockId;
        } catch (Exception e) {
            LOGGER.debug("获取方块 {} 名称失败: {}", blockId, e.getMessage());
        }
        return blockId;
    }
    
    private String getProjectionModeStatus() {
        try {
            ProjectionSettingsDialog dialog = ProjectionSettingsDialog.getInstance();
            if (dialog != null) {
                ProjectionSettingsDialog.ProjectionMode mode = dialog.getProjectionMode();
                if (mode == ProjectionSettingsDialog.ProjectionMode.GROUND) {
                    return PlotI18n.tr("status.plot.projection_ground");
                } else if (mode == ProjectionSettingsDialog.ProjectionMode.ELEVATION) {
                    return PlotI18n.tr("status.plot.projection_elevation", dialog.getElevation());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("获取投影模式状态失败: {}", e.getMessage());
        }
        return PlotI18n.tr("status.plot.unknown");
    }

    @Override
    public void close() throws Exception {
        try {
            LOGGER.debug("正在关闭StatusPanel...");
            
            if (eventBus != null) {
                if (toolChangedListener != null) {
                    eventBus.unsubscribe(ToolChangedEvent.class, toolChangedListener);
                }
                if (statusMessageListener != null) {
                    eventBus.unsubscribe(StatusMessageEvent.class, statusMessageListener);
                }
            }
            
            LOGGER.debug("StatusPanel关闭完成");
        } catch (Exception e) {
            LOGGER.error("StatusPanel关闭失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}
