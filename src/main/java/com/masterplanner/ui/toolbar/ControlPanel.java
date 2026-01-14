package com.masterplanner.ui.toolbar;

import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.Events;
import com.masterplanner.infrastructure.event.tool.ToolEvent;
import com.masterplanner.infrastructure.event.command.UndoEvent;
import com.masterplanner.infrastructure.event.command.RedoEvent;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog;
import com.masterplanner.ui.dialog.LineToBlockSettingsDialog;
import com.masterplanner.ui.dialog.ProjectionSettingsDialog;
import com.masterplanner.ui.layout.UILayout;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.toolbar.group.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制面板组件 - 组件化重构版本
 * <p>
 * 重构说明：
 * - 采用组件化架构，将原来的巨大类拆分为独立的工具组
 * - 保持API兼容性，对外接口不变
 * - 代码从1200+行减少到300行左右
 * - 提高可维护性和可扩展性
 */
public class ControlPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ControlPanel");
    
    // 控制面板来源标识
    public static final String SOURCE = "control_panel";

    // 核心组件引用
    private final AppState appState;
    private final EventBus eventBus;
    
    // 工具组列表 - 组件化设计的核心
    private final List<ToolbarGroup> toolGroups;
    
    // 对话框组件（保持原有功能）
    private final CompactBlockConfigDialog blockConfigDialog;
    private final ProjectionSettingsDialog projectionSettingsDialog;
    private final LineToBlockSettingsDialog lineToBlockSettingsDialog;
    
    // 警告对话框状态
    private String warningMessage = "";

    /**
     * 构造函数 - 组件化初始化
     */
    public ControlPanel() {
        LOGGER.info("Initializing ControlPanel with component architecture...");
        
        try {
            this.appState = AppState.getInstance();
            this.eventBus = EventBus.getInstance();
            
            // 加载图标资源
            ControlPanelIcons.loadTextures();
            
            // 先初始化对话框组件
            this.blockConfigDialog = CompactBlockConfigDialog.createSafely(
                appState, eventBus, this::showWarningDialog);
            this.projectionSettingsDialog = ProjectionSettingsDialog.getInstance();
            this.lineToBlockSettingsDialog = LineToBlockSettingsDialog.getInstance();
            
            // 然后初始化工具组列表 - 组件化设计的核心
            this.toolGroups = initializeToolGroups();
            
            // 注册事件监听器
            registerEventListeners();
            
            LOGGER.info("ControlPanel initialized successfully with {} tool groups", toolGroups.size());
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize ControlPanel", e);
            throw new RuntimeException("Failed to initialize ControlPanel", e);
        }
    }
    
    /**
     * 初始化工具组 - 展示组件化设计
     */
    private List<ToolbarGroup> initializeToolGroups() {
        List<ToolbarGroup> groups = new ArrayList<>();
        
        // 按顺序添加各个工具组
        groups.add(new MasterPlannerLogo());
        groups.add(new FileToolsGroup(appState, eventBus));
        groups.add(new ToolSettingsGroup(appState, eventBus, blockConfigDialog, lineToBlockSettingsDialog, projectionSettingsDialog));
        groups.add(new ViewToolsGroup(eventBus));
        groups.add(new ControlSlidersGroup(appState, eventBus));
        
        LOGGER.debug("Initialized {} tool groups", groups.size());
        return groups;
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        // 订阅工具事件
        eventBus.subscribe(ToolEvent.class, event -> {
            if (event instanceof ToolEvent toolEvent) {
                handleToolEvent(toolEvent);
            }
        });
        
        // 订阅警告事件
        eventBus.subscribe(Events.WarningEvent.class, event -> {
            if (event instanceof Events.WarningEvent warningEvent) {
                showWarningDialog(warningEvent.getMessage());
            }
        });
    }

    /**
     * 渲染控制面板 - 组件化版本
     * 相比原来的复杂渲染逻辑，现在非常简洁
     */
    @Override
    public void render() {
        // 兼容旧模式：ControlPanel 自己开窗口（固定布局时代）
        try {
            setupControlPanelWindow();
            renderInCurrentWindow();
        } catch (Exception e) {
            LOGGER.error("Error rendering ControlPanel", e);
        } finally {
            cleanupControlPanelWindow();
        }
    }

    /**
     * DockSpace/外部布局模式：由外部 begin()/end() 管理窗口，这里只渲染内容。
     */
    public void renderInCurrentWindow() {
        try {
            // 注意：这里不能 begin()/end()，否则会创建一个额外的"##ControlPanel"窗口导致内容跑丢
            // WindowPadding 已在 renderDockedControlPanel 中设置，这里不需要重复设置
            // 只设置必要的样式，避免覆盖外部设置的样式
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,
                UILayout.Toolbar.ITEM_SPACING,
                UILayout.Toolbar.ITEM_SPACING);
            try {
                renderToolGroups();
                renderFixedComponents();
                renderDialogs();
            } finally {
                ImGui.popStyleVar(2);
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering ControlPanel contents", e);
        }
    }
    
    /**
     * 设置控制面板窗口
     */
    private void setupControlPanelWindow() {
        // 设置控制面板样式
        ImGui.pushStyleColor(ImGuiCol.ChildBg,
            ThemeManager.getInstance().getCurrentTheme().panelBackground);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 
            UILayout.Toolbar.BUTTON_PADDING, 
            UILayout.Toolbar.BUTTON_PADDING);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 
            UILayout.Toolbar.ITEM_SPACING, 
            UILayout.Toolbar.ITEM_SPACING);
        
        ImGui.begin("##ControlPanel",
                ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoBringToFrontOnFocus);
    }
    
    /**
     * 渲染所有工具组 - 响应式自动换行布局
     */
    private void renderToolGroups() {
        // 获取窗口宽度和高度
        float windowWidth = ImGui.getWindowWidth();
        float windowHeight = ImGui.getWindowHeight();
        float buttonHeight = UILayout.Toolbar.BUTTON_SIZE;
        
        // 计算可用的工具组（过滤掉禁用的）
        List<ToolbarGroup> enabledGroups = new ArrayList<>();
        for (ToolbarGroup group : toolGroups) {
            if (group.isEnabled()) {
                enabledGroups.add(group);
            }
        }
        
        if (enabledGroups.isEmpty()) {
            return;
        }
        
        // 计算每个组的实际宽度（不包括间距）
        List<Float> groupWidths = new ArrayList<>();
        List<Float> groupSpacings = new ArrayList<>(); // 存储每个组后面的间距
        for (int i = 0; i < enabledGroups.size(); i++) {
            ToolbarGroup group = enabledGroups.get(i);
            groupWidths.add(group.getGroupWidth());
            // 计算这个组后面的间距（如果不是最后一个）
            if (i < enabledGroups.size() - 1) {
                float spacing = group.needsSeparator() ? 
                    UILayout.Toolbar.GROUP_SPACING : UILayout.Toolbar.ITEM_SPACING;
                groupSpacings.add(spacing);
            } else {
                groupSpacings.add(0.0f); // 最后一个组没有后续间距
            }
        }
        
        // 计算每行可以放置的组（响应式布局）
        List<List<Integer>> rows = calculateRows(groupWidths, groupSpacings, windowWidth);
        
        // 计算行数，用于垂直居中
        int totalRows = rows.size();
        float totalHeight = totalRows * buttonHeight + (totalRows - 1) * UILayout.Toolbar.ITEM_SPACING;
        float startY = (windowHeight - totalHeight) / 2.0f;
        
        // 渲染每一行
        float currentY = startY;
        for (List<Integer> row : rows) {
            ImGui.setCursorPos(0, currentY);
            
            // 渲染这一行的所有组
            for (int i = 0; i < row.size(); i++) {
                int groupIndex = row.get(i);
                ToolbarGroup group = enabledGroups.get(groupIndex);
                
                try {
                    // 渲染工具组
                    group.render();
                    
                    // 如果不是这一行的最后一个，添加间距
                    if (i < row.size() - 1) {
                        float spacing = group.needsSeparator() ? 
                            UILayout.Toolbar.GROUP_SPACING : UILayout.Toolbar.ITEM_SPACING;
                        ImGui.sameLine(0, spacing);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error rendering tool group: {}", group.getGroupName(), e);
                }
            }
            
            // 移动到下一行
            currentY += buttonHeight + UILayout.Toolbar.ITEM_SPACING;
        }
    }
    
    /**
     * 计算响应式行布局
     * @param groupWidths 每个组的实际宽度
     * @param groupSpacings 每个组后面的间距
     * @param availableWidth 可用宽度
     * @return 每行包含的组索引列表
     */
    private List<List<Integer>> calculateRows(List<Float> groupWidths, List<Float> groupSpacings, float availableWidth) {
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> currentRow = new ArrayList<>();
        float currentRowWidth = 0;
        
        for (int i = 0; i < groupWidths.size(); i++) {
            float groupWidth = groupWidths.get(i);
            float spacing = groupSpacings.get(i);
            float totalWidth = groupWidth + spacing;
            
            // 如果当前行加上这个组会超出宽度，开始新行
            // 但至少要放一个组，即使它稍微超出
            if (!currentRow.isEmpty() && currentRowWidth + totalWidth > availableWidth) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
                currentRowWidth = 0;
            }
            
            // 添加当前组到行
            currentRow.add(i);
            currentRowWidth += totalWidth;
        }
        
        // 添加最后一行
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        
        return rows;
    }
    
    /**
     * 渲染固定组件（已移除，SystemPanel 现在是独立面板）
     */
    private void renderFixedComponents() {
        // SystemPanel 现在是独立的面板，不再在这里渲染
    }
    
    /**
     * 渲染所有对话框
     */
    private void renderDialogs() {
        if (blockConfigDialog != null) blockConfigDialog.render();
        if (projectionSettingsDialog != null) projectionSettingsDialog.render();
        if (lineToBlockSettingsDialog != null) lineToBlockSettingsDialog.render();
        // 新增：设置与帮助对话框
        com.masterplanner.ui.dialog.SettingsAndHelpDialog.getInstance().render();
        renderWarningDialog();
    }
    
    /**
     * 清理控制面板窗口
     */
    private void cleanupControlPanelWindow() {
        ImGui.end();
        ImGui.popStyleVar(3);  // 弹出3个样式变量
        ImGui.popStyleColor(); // 弹出1个颜色
    }


    /**
     * 处理工具事件
     */
    public void handleToolEvent(ToolEvent event) {
        if (!event.getSource().equals(SOURCE)) {
            return;
        }

        LOGGER.debug("处理工具事件: type={}, toolId={}", event.getToolEventType(), event.getToolId());
        
        try {
            switch (event.getToolEventType()) {
                case TOOL_FILE -> handleFileEvent(event.getToolId());
                case TOOL_EDIT -> handleEditEvent(event.getToolId());
                case TOOL_VIEW -> handleViewEvent(event.getToolId());
                case TOOL_SETTINGS -> handleSettingsEvent(event.getToolId());
                default -> LOGGER.warn("未知的工具事件类型: {}", event.getToolEventType());
            }
        } catch (Exception e) {
            LOGGER.error("处理工具事件时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理文件相关事件
     */
    private void handleFileEvent(String toolId) {
        LOGGER.debug("处理文件事件: {}", toolId);
        
        switch (toolId) {
            case "undo" -> eventBus.publish(new UndoEvent());
            case "redo" -> eventBus.publish(new RedoEvent());
            default -> LOGGER.warn("未知的文件工具ID: {}", toolId);
        }
    }

    /**
     * 处理编辑相关事件
     */
    private void handleEditEvent(String toolId) {
        // 编辑事件现在由左侧工具栏工具直接处理
        LOGGER.debug("编辑事件由组件直接处理: {}", toolId);
    }

    /**
     * 处理视图相关事件
     */
    private void handleViewEvent(String toolId) {
        // 视图事件现在由 ViewToolsGroup 直接处理
        LOGGER.debug("视图事件由组件直接处理: {}", toolId);
    }

    /**
     * 处理设置相关事件
     */
    private void handleSettingsEvent(String toolId) {
        // 设置事件现在由 ToolSettingsGroup 直接处理
        LOGGER.debug("设置事件由组件直接处理: {}", toolId);
    }

    /**
     * 显示警告对话框
     */
    private void showWarningDialog(String message) {
        ImGui.openPopup("##warning_dialog");
        warningMessage = message;
    }
    
    /**
     * 渲染警告对话框
     */
    private void renderWarningDialog() {
        if (ImGui.beginPopupModal("##warning_dialog", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(warningMessage);
            ImGui.separator();
            
            float buttonWidth = 120;
            ImGui.setCursorPosX((ImGui.getWindowWidth() - buttonWidth) * 0.5f);
            
            if (ImGui.button("确定", buttonWidth, 0) || 
                ImGui.isKeyPressed(ImGuiKey.Enter) || 
                ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
        }
    }

    @Override
    public void init() {
        try {
            LOGGER.debug("Initializing ControlPanel components...");
            toolGroups.forEach(ToolbarGroup::init);
            LOGGER.debug("ControlPanel components initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize ControlPanel components", e);
            throw new RuntimeException("Failed to initialize ControlPanel components", e);
        }
    }

    @Override
    public void close() throws Exception {
        LOGGER.debug("Disposing ControlPanel resources...");
        for (ToolbarGroup group : toolGroups) {
            try {
                group.close();
            } catch (Exception e) {
                LOGGER.error("Error closing tool group: {}", group.getGroupName(), e);
            }
        }
        ControlPanelIcons.dispose();
    }
}
