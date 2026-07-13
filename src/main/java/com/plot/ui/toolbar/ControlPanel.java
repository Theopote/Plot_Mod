package com.plot.ui.toolbar;

import com.plot.utils.PlotI18n;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.infrastructure.event.tool.ToolEvent;
import com.plot.infrastructure.event.command.UndoEvent;
import com.plot.infrastructure.event.command.RedoEvent;
import com.plot.ui.component.UIComponent;
import com.plot.ui.component.ControlPanelIcons;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.dialog.LineToBlockSettingsDialog;
import com.plot.ui.dialog.ProjectionSettingsDialog;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.toolbar.group.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ControlPanel");
    
    // 控制面板来源标识
    public static final String SOURCE = "control_panel";

    // 核心组件引用
    private final AppState appState;
    private final EventBus eventBus;
    
    // 工具组列表 - 组件化设计的核心
    private final List<ToolbarGroup> toolGroups;
    
    // 对话框组件（保持原有功能）
    private final ProjectionSettingsDialog projectionSettingsDialog;
    private final LineToBlockSettingsDialog lineToBlockSettingsDialog;
    
    // 警告对话框状态
    private String warningMessage = "";
    private volatile boolean warningPopupPending = false;

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
        groups.add(new PlotLogo());
        groups.add(new FileToolsGroup(appState, eventBus));
        groups.add(new ToolSettingsGroup(appState, eventBus));
        groups.add(new BlockOperationGroup(appState, eventBus, lineToBlockSettingsDialog, projectionSettingsDialog));
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
                renderToolGroupsSingleRow();
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
     * 渲染所有工具组 - 不再分组，按顺序排列在同一行（自动换行）
     */
    private void renderToolGroups() {
        // 获取内容区域的边界（考虑窗口内边距）
        float contentMinX = ImGui.getWindowContentRegionMinX();
        float contentMaxX = ImGui.getWindowContentRegionMaxX();
        float contentWidth = contentMaxX - contentMinX;
        
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
        // 所有组之间使用统一的ITEM_SPACING间距，不再考虑分组和分隔符
        List<Float> groupWidths = new ArrayList<>();
        for (ToolbarGroup group : enabledGroups) {
            groupWidths.add(group.getGroupWidth());
        }
        
        // 统一使用ITEM_SPACING作为所有组之间的间距
        List<Float> groupSpacings = new ArrayList<>();
        for (int i = 0; i < enabledGroups.size() - 1; i++) {
            groupSpacings.add(UILayout.Toolbar.ITEM_SPACING);
        }
        groupSpacings.add(0.0f); // 最后一个组没有后续间距
        
        // 计算每行可以放置的组（响应式布局，使用内容区域宽度）
        List<List<Integer>> rows = calculateRows(groupWidths, groupSpacings, contentWidth);
        
        // 顶部留白
        ImGui.dummy(0.0f, UILayout.Toolbar.BUTTON_PADDING);

        // 使用自然流布局推进Y，避免绝对Y定位与滚动状态冲突导致回弹
        for (List<Integer> row : rows) {
            ImGui.setCursorPosX(contentMinX);

            // 渲染这一行的所有组
            for (int i = 0; i < row.size(); i++) {
                int groupIndex = row.get(i);
                ToolbarGroup group = enabledGroups.get(groupIndex);

                try {
                    // 渲染工具组
                    group.render();

                    // 如果不是这一行的最后一个，添加统一间距（不再考虑分组和分隔符）
                    if (i < row.size() - 1) {
                        ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error rendering tool group: {}", group.getGroupName(), e);
                }
            }

        }
    }

    /**
     * 单行渲染工具组（顶部工具栏模式）
     */
    private void renderToolGroupsSingleRow() {
        float contentMinX = ImGui.getWindowContentRegionMinX();
        float contentMaxX = ImGui.getWindowContentRegionMaxX();
        float contentMinY = ImGui.getWindowContentRegionMinY();
        float contentMaxY = ImGui.getWindowContentRegionMaxY();
        float availableHeight = Math.max(0.0f, contentMaxY - contentMinY);
        float cursorY = contentMinY + Math.max(0.0f, (availableHeight - UILayout.Toolbar.BUTTON_SIZE) * 0.5f);

        List<ToolbarGroup> enabledGroups = new ArrayList<>();
        for (ToolbarGroup group : toolGroups) {
            if (group.isEnabled()) {
                enabledGroups.add(group);
            }
        }

        if (enabledGroups.isEmpty()) {
            return;
        }

        ImGui.setCursorPos(contentMinX, cursorY);
        for (int i = 0; i < enabledGroups.size(); i++) {
            ToolbarGroup group = enabledGroups.get(i);
            try {
                if (group instanceof ControlSlidersGroup slidersGroup) {
                    slidersGroup.renderCompactSingleRow(UILayout.Toolbar.BUTTON_SIZE);
                } else {
                    group.render();
                }
                if (i < enabledGroups.size() - 1) {
                    ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
                }
            } catch (Exception e) {
                LOGGER.error("Error rendering tool group: {}", group.getGroupName(), e);
            }
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
     * 在所有 Dock 窗口渲染完成后再显示模态弹窗。
     */
    public void renderDeferredModals() {
        if (projectionSettingsDialog != null) {
            projectionSettingsDialog.render();
        }
        if (lineToBlockSettingsDialog != null) {
            lineToBlockSettingsDialog.render();
        }
        com.plot.ui.dialog.SettingsAndHelpDialog.getInstance().render();
        renderWarningDialog();
    }

    /**
     * 渲染所有对话框（非模态部分已移至 {@link #renderDeferredModals()}）
     */
    private void renderDialogs() {
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
        warningMessage = PlotI18n.localizeMessage(message == null ? "" : message);
        warningPopupPending = true;
    }
    
    /**
     * 渲染警告对话框
     */
    private void renderWarningDialog() {
        if (warningPopupPending) {
            ImGui.openPopup("##warning_dialog");
            warningPopupPending = false;
        }

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0, ImGuiCond.Appearing);

            int popupFlags = ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoSavedSettings;

            if (ImGui.beginPopupModal("##warning_dialog", popupFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("control_warning")) {
                        ImGui.closeCurrentPopup();
                        return;
                    }

                    DialogLayoutHelper.warningText(warningMessage);

                    if (DialogLayoutHelper.isConfirmShortcutPressed() || DialogLayoutHelper.isCancelShortcutPressed()) {
                        ImGui.closeCurrentPopup();
                    }

                    DialogLayoutHelper.beginFooter();
                    if (DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())) {
                        ImGui.closeCurrentPopup();
                    }
                } finally {
                    ImGui.endPopup();
                }
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
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
