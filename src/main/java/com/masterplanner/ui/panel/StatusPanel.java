package com.masterplanner.ui.panel;

import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.ToolManager;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.tool.ToolChangedEvent;
import com.masterplanner.infrastructure.event.Events.StatusMessageEvent;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.grid.GridManager;
import com.masterplanner.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog;
import com.masterplanner.ui.dialog.ProjectionSettingsDialog;
import com.masterplanner.camera.CameraManager;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/StatusPanel");
    
    private final AppState appState;
    private final EventBus eventBus;
    private final ToolManager toolManager;
    private boolean initialized = false;
    
    // 状态信息
    private String currentToolName = "选择";
    private float opacity = 100.0f;
    private int selectedCount = 0;
    private String activeLayer;
    private String status = "就绪";
    
    // 事件监听器
    private final EventListener toolChangedListener;
    private final EventListener statusMessageListener;

    public StatusPanel() {
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
        this.toolManager = ToolManager.getInstance();
        
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
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        
        if (ImGui.collapsingHeader("状态属性", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.popStyleColor(4);
            
            // 设置边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8, 8);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4, 4);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.panelControlRounding);
            
            // 设置颜色
            ImGui.pushStyleColor(ImGuiCol.ChildBg, currentTheme.panelBackground);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);
            ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
            
            try {
                // 创建带边框的子窗口来显示状态信息
                if (ImGui.beginChild("##status_content", 0, 0, true)) {
                    // 使用表格形式显示状态信息
                    if (ImGui.beginTable("##status_table", 2, ImGuiTableFlags.SizingStretchProp)) {
                        ImGui.tableSetupColumn("属性", ImGuiTableColumnFlags.WidthFixed, 80.0f);
                        ImGui.tableSetupColumn("值", ImGuiTableColumnFlags.WidthStretch);
                        
                        // 当前工具
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("当前工具");
                        ImGui.tableNextColumn();
                        ImGui.text(currentToolName);
                        
                        // 视图状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("视图状态");
                        ImGui.tableNextColumn();
                        ImGui.text(getViewStatus());
                        
                        // 画布透明度
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("画布透明度");
                        ImGui.tableNextColumn();
                        ImGui.text(String.format("%.0f%%", opacity * 100.0f));
                        
                        // 图形数量
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("图形数量");
                        ImGui.tableNextColumn();
                        ImGui.text(String.valueOf(getTotalShapeCount()));
                        
                        // 图层数量
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("图层数量");
                        ImGui.tableNextColumn();
                        ImGui.text(String.valueOf(getLayerCount()));
                        
                        // 当前图层
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("当前图层");
                        ImGui.tableNextColumn();
                        ImGui.text(activeLayer != null ? activeLayer : "无");
                        
                        // 网格状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("网格状态");
                        ImGui.tableNextColumn();
                        ImGui.text(getGridStatus());
                        
                        // 摄像机状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("摄像机状态");
                        ImGui.tableNextColumn();
                        ImGui.text(getCameraStatus());
                        
                        // 方块配置 - 使用 textWrapped 支持换行
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("方块配置");
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
                        ImGui.text("投影模式");
                        ImGui.tableNextColumn();
                        ImGui.text(getProjectionModeStatus());
                        
                        // 状态
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("状态");
                        ImGui.tableNextColumn();
                        ImGui.text(status != null ? status : "就绪");
                        
                        ImGui.endTable();
                    }
                }
                ImGui.endChild();
            } finally {
                ImGui.popStyleColor(3);
                ImGui.popStyleVar(5);
            }
        } else {
            ImGui.popStyleColor(4);
        }
    }
    
    private void updateStatus() {
        try {
            // 从AppState更新状态信息
            selectedCount = appState.getSelectedShapes().size();
            activeLayer = appState.getActiveLayerName();
            
            // 使用已初始化的 ToolManager
            com.masterplanner.api.tool.ITool activeTool = toolManager.getActiveTool();
            currentToolName = activeTool != null ? activeTool.getName() : "选择";
            
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
                // 获取视图锁定状态
                com.masterplanner.camera.OrthographicCamera orthoCamera = cameraManager.getOrthographicCamera();
                boolean isLocked = orthoCamera != null && orthoCamera.isLocked();
                
                // 获取视图范围
                float viewDistance = cameraManager.getViewDistance();
                
                return String.format("%s, 范围: %.1f", isLocked ? "锁定" : "未锁定", viewDistance);
            }
        } catch (Exception e) {
            LOGGER.debug("获取视图状态失败: {}", e.getMessage());
        }
        return "未知";
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
            return gridManager != null && gridManager.isEnabled() ? "启用" : "禁用";
        } catch (Exception e) {
            LOGGER.debug("获取网格状态失败: {}", e.getMessage());
            return "未知";
        }
    }
    
    private String getCameraStatus() {
        try {
            CameraManager cameraManager = CameraManager.getInstance();
            if (cameraManager != null) {
                boolean isOrthographic = cameraManager.isOrthographic();
                return isOrthographic ? "正交相机" : "透视相机";
            }
        } catch (Exception e) {
            LOGGER.debug("获取摄像机状态失败: {}", e.getMessage());
        }
        return "未知";
    }
    
    private String getBlockConfigStatus() {
        try {
            CompactBlockConfigDialog.BlockConfigManager manager = 
                CompactBlockConfigDialog.BlockConfigManager.getInstance();
            if (manager == null || !manager.hasSelectedBlocks()) {
                return "未配置";
            }
            
            // 获取选中的方块ID列表
            List<String> blockIds = manager.getSelectedBlockIds();
            if (blockIds == null || blockIds.isEmpty()) {
                return "未配置";
            }
            
            // 转换为方块名称列表
            StringBuilder configInfo = new StringBuilder("已配置: ");
            try {
                for (int i = 0; i < blockIds.size(); i++) {
                    if (i > 0) {
                        configInfo.append(", ");
                    }
                    
                    String blockId = blockIds.get(i);
                    String blockName = getBlockDisplayName(blockId);
                    configInfo.append(blockName);
                }
            } catch (Exception e) {
                LOGGER.debug("转换方块名称时出错: {}", e.getMessage());
                return "已配置 (" + blockIds.size() + "个方块)";
            }
            
            return configInfo.toString();
        } catch (Exception e) {
            LOGGER.debug("获取方块配置状态失败: {}", e.getMessage());
            return "未知";
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
        return blockId; // 如果无法获取名称，返回ID
    }
    
    private String getProjectionModeStatus() {
        try {
            ProjectionSettingsDialog dialog = ProjectionSettingsDialog.getInstance();
            if (dialog != null) {
                ProjectionSettingsDialog.ProjectionMode mode = dialog.getProjectionMode();
                if (mode == ProjectionSettingsDialog.ProjectionMode.GROUND) {
                    return "投影到地面";
                } else if (mode == ProjectionSettingsDialog.ProjectionMode.ELEVATION) {
                    int elevation = dialog.getElevation();
                    return String.format("投影到指定标高 (Y=%d)", elevation);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("获取投影模式状态失败: {}", e.getMessage());
        }
        return "未知";
    }

    @Override
    public void close() throws Exception {
        try {
            LOGGER.debug("正在关闭StatusPanel...");
            
            // 取消注册事件监听器
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
