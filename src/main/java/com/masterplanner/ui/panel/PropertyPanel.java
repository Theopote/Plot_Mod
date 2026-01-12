package com.masterplanner.ui.panel;

import com.masterplanner.ui.panel.layer.LayerPanel;
import com.masterplanner.ui.panel.tool.ToolOptionsPanel;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;

import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.tool.ToolChangedEvent;
import com.masterplanner.infrastructure.event.Events.StatusMessageEvent;
import com.masterplanner.core.tool.ToolManager;
import com.masterplanner.MasterPlannerMod;
import com.masterplanner.ui.component.UIComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 属性面板，用于显示和编辑对象属性、工具属性和图层管理
 */
public class PropertyPanel implements UIComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/PropertyPanel");
    
    private final ToolOptionsPanel toolOptionsPanel;
    private final LayerPanel layerPanel;
    private final HistoryPanel historyPanel;
    private final AppState appState;
    private final EventBus eventBus;
    private final ToolManager toolManager;
    private boolean initialized = false;
    
    // 状态信息
    private String currentToolName = "选择";
    private float zoom = 100.0f;
    private float opacity = 100.0f;
    private int selectedCount = 0;
    private String activeLayer;
    private String status = "就绪";
    
    // 事件监听器
    private final EventListener toolChangedListener;
    private final EventListener statusMessageListener;

    public PropertyPanel() {
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
        this.toolManager = ToolManager.getInstance();
        this.toolOptionsPanel = new ToolOptionsPanel();
        this.layerPanel = new LayerPanel();
        this.historyPanel = new HistoryPanel(AppState.getInstance().getCommandHistory());
        
        // 初始化事件监听器
        this.toolChangedListener = event -> {
            if (event instanceof ToolChangedEvent) {
                updateStatus();
            }
        };
        
        this.statusMessageListener = event -> {
            if (event instanceof StatusMessageEvent statusEvent) {
                this.status = statusEvent.getMessage();
                LOGGER.debug("PropertyPanel: 收到状态消息: {}", statusEvent.getMessage());
            }
        };
        
        // 注册事件监听器
        this.eventBus.subscribe(ToolChangedEvent.class, toolChangedListener);
        this.eventBus.subscribe(StatusMessageEvent.class, statusMessageListener);

        // 初始化通用状态
        // 通用状态
        ImString nameBuffer = new ImString(64);

        // 初始化图层状态
        // 图层相关状态
        // 新建图层时的名称输入
        ImString newLayerName = new ImString(64);  // 只设置缓冲区大小，不设置初始值
        // 编辑图层名称时的缓冲区
        ImString layerNameBuffer = new ImString(64);

        // 初始化方块状态
        // 方块相关状态
        int selectedBlockId = 0;
        ImString blockSearch = new ImString(256);
    }

    @Override
    public void init() {
        if (initialized) {
            MasterPlannerMod.LOGGER.debug("PropertyPanel已经初始化，跳过初始化流程");
            return;
        }

        try {
            MasterPlannerMod.LOGGER.info("正在初始化PropertyPanel...");
            
            // 初始化LayerPanel
            layerPanel.init();
            
            initialized = true;
            MasterPlannerMod.LOGGER.info("PropertyPanel初始化完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("PropertyPanel初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void render() {
        if (!initialized) {
            MasterPlannerMod.LOGGER.debug("PropertyPanel未初始化，跳过渲染");
            return;
        }

        try {
            // 设置元素之间的间距
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);

            // ====== 工具部分 ======
            renderToolSection();

            // ====== 图层部分 ======
            renderLayerSection();

            // ====== 历史记录部分 ======
            renderHistorySection();

            // ====== 状态属性部分 ======
            renderStatusSection();

            // 恢复样式
            ImGui.popStyleVar(2);

        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("PropertyPanel渲染失败: {}", e.getMessage(), e);
        }
    }

    private void renderToolSection() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置标题颜色
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        
        if (ImGui.collapsingHeader("工具属性", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 恢复标题颜色
            ImGui.popStyleColor(4);
            toolOptionsPanel.render();
        } else {
            ImGui.popStyleColor(4);
        }
    }

    private void renderLayerSection() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置标题颜色
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        
        if (ImGui.collapsingHeader("图层", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.popStyleColor(4);
            layerPanel.render();
        } else {
            ImGui.popStyleColor(4);
        }
    }

    private void renderHistorySection() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置标题颜色
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        
        if (ImGui.collapsingHeader("历史记录", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.popStyleColor(4);
            historyPanel.render();
        } else {
            ImGui.popStyleColor(4);
        }
    }

    private void renderStatusSection() {
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
                        
                        // 缩放比例
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("缩放");
                        ImGui.tableNextColumn();
                        ImGui.text(String.format("%.0f%%", zoom));
                        
                        // 透明度
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("透明度");
                        ImGui.tableNextColumn();
                        ImGui.text(String.format("%.0f%%", opacity));
                        
                        // 已选中数量
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("已选中");
                        ImGui.tableNextColumn();
                        ImGui.text(String.valueOf(selectedCount));
                        
                        // 当前图层
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text("当前图层");
                        ImGui.tableNextColumn();
                        ImGui.text(activeLayer != null ? activeLayer : "无");
                        
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
            
            // 更新缩放比例和透明度
            zoom = appState.getZoom();
            opacity = appState.getOpacity();
        } catch (Exception e) {
            LOGGER.error("Error updating status", e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            MasterPlannerMod.LOGGER.debug("正在关闭PropertyPanel...");
            
            // 取消注册事件监听器
            if (eventBus != null) {
                if (toolChangedListener != null) {
                    eventBus.unsubscribe(ToolChangedEvent.class, toolChangedListener);
                }
                if (statusMessageListener != null) {
                    eventBus.unsubscribe(StatusMessageEvent.class, statusMessageListener);
                }
            }
            
            layerPanel.close();
            historyPanel.close();
            MasterPlannerMod.LOGGER.debug("PropertyPanel关闭完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("PropertyPanel关闭失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}