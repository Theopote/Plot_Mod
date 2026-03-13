package com.masterplanner.ui.panel;

import com.masterplanner.core.tool.ToolManager;
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
import com.masterplanner.MasterPlannerMod;
import com.masterplanner.ui.component.UIComponent;

/**
 * 属性面板，用于显示和编辑对象属性、工具属性和图层管理
 */
public class PropertyPanel implements UIComponent {

    private final ToolOptionsPanel toolOptionsPanel;
    private final LayerPanel layerPanel;
    private final HistoryPanel historyPanel;
    private final StatusPanel statusPanel;
    private final EventBus eventBus;
    private boolean initialized = false;
    
    // 事件监听器
    private final EventListener toolChangedListener;

    public PropertyPanel() {
        AppState appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
        ToolManager toolManager = ToolManager.getInstance();
        this.toolOptionsPanel = new ToolOptionsPanel();
        this.layerPanel = new LayerPanel();
        this.historyPanel = new HistoryPanel(AppState.getInstance().getCommandHistory());
        this.statusPanel = new StatusPanel();
        
        // 初始化事件监听器（用于其他面板的更新）
        this.toolChangedListener = event -> {
            if (event instanceof ToolChangedEvent) {
                // 状态更新已由StatusPanel自己处理
            }
        };
        
        // 注册事件监听器
        this.eventBus.subscribe(ToolChangedEvent.class, toolChangedListener);

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
            
            // 初始化子面板
            layerPanel.init();
            statusPanel.init();
            
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
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.tabActive);
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
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.tabActive);
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
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.tabActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        
        if (ImGui.collapsingHeader("历史记录", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.popStyleColor(4);
            historyPanel.render();
        } else {
            ImGui.popStyleColor(4);
        }
    }

    private void renderStatusSection() {
        statusPanel.render();
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
            }
            
            layerPanel.close();
            historyPanel.close();
            statusPanel.close();
            MasterPlannerMod.LOGGER.debug("PropertyPanel关闭完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("PropertyPanel关闭失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}