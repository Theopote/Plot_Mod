package com.plot.ui.toolbar;

import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.event.tool.ToolSelectedEvent;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.core.tool.BaseTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.api.tool.ITool;
import com.plot.ui.component.UIComponent;
import com.plot.ui.component.UIUtils;
import com.plot.ui.component.ToolPanelIcons;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import com.plot.utils.PlotI18n;
import com.plot.ui.layout.UILayout;
import com.plot.ui.tools.DrawingToolsModule;
import com.plot.core.command.CommandManager;
import com.plot.core.snap.SnapManager;

import java.util.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具面板，包含所有绘图和修改工具
 */
public class ToolPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ToolPanel");

    private final AppState appState;
    private final EventBus eventBus;
    // 移除未使用的字段
    
    private List<ToolGroup> toolGroups;
    private ToolManager toolManager;
    
    // 可折叠工具组状态管理
    private final Map<String, Boolean> groupCollapsedState;

    public ToolPanel() {
        try {
            LOGGER.info("Initializing ToolPanel...");
            
            // 初始化折叠状态管理
            this.groupCollapsedState = new HashMap<>();
            
            // 确保 ToolManager 已初始化
            try {
                this.toolManager = ToolManager.getInstance();
            } catch (IllegalStateException e) {
                LOGGER.warn("ToolManager未初始化，现在使用AppState进行初始化");
                ToolManager.initialize(AppState.getInstance());
                this.toolManager = ToolManager.getInstance();
            }
            
            if (this.toolManager == null) {
                throw new IllegalStateException(PlotI18n.error("error.plot.validation.tool_manager_init_failed"));
            }
            
            LOGGER.debug("Setting up tool groups...");
            this.toolGroups = initializeToolGroups();
            
            // 加载折叠状态配置
            loadGroupCollapsedStates();
            
            LOGGER.info("ToolPanel initialized successfully");
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize ToolPanel", e);
            throw e;
        }
        
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
    }
    
    public void render() {
        try {
            LOGGER.debug("Starting ToolPanel render...");

            // 兼容旧模式：工具面板自己开窗口（固定布局时代）
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,
                UILayout.Toolbar.TOOL_PANEL_PADDING,
                UILayout.Toolbar.TOOL_PANEL_PADDING);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,
                UILayout.Toolbar.LEFT_BUTTON_SPACING,
                UILayout.Toolbar.LEFT_BUTTON_SPACING);

            boolean toolPanelWin = ImGui.begin("##ToolPanel",
                ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoBringToFrontOnFocus);
            try {
                if (toolPanelWin) {
                    renderInCurrentWindow();
                }
            } finally {
                ImGui.end();
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering ToolPanel", e);
        } finally {
            // 确保样式栈被正确恢复
            ImGui.popStyleVar(3);
        }
    }

    /**
     * DockSpace/外部布局模式：由外部 begin()/end() 管理窗口，这里只渲染内容。
     */
    public void renderInCurrentWindow() {
        // 显式设置ItemSpacing，避免窗口缩放时被重置
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,
            UILayout.Toolbar.LEFT_BUTTON_SPACING,
            UILayout.Toolbar.LEFT_BUTTON_SPACING);
        try {
            if (ImGui.beginChild("##tool_panel_scroll", 0, 0, false, ImGuiWindowFlags.NoScrollbar)) {
                // 渲染工具按钮
                renderToolGroups();
                ImGui.dummy(1.0f, UILayout.Toolbar.BUTTON_PADDING + 12.0f);
            }
            ImGui.endChild();
        } finally {
            ImGui.popStyleVar();
        }
    }
    
    private void renderToolGroups() {
        float contentWidth = ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX();
        float contentMinX = ImGui.getWindowContentRegionMinX();

        // 顶部留白，并仅控制X位置，让Y由自然流布局推进
        ImGui.dummy(0.0f, UILayout.Toolbar.BUTTON_PADDING);
        ImGui.setCursorPosX(contentMinX);
        
        for (int i = 0; i < toolGroups.size(); i++) {
            ToolGroup group = toolGroups.get(i);
            
            // 增强的分组视觉效果
            if (i > 0 && group.needsSeparator) {
                renderGroupSeparator(contentWidth);
            }
            
            // 渲染工具组（可能包含折叠功能）
            renderToolGroupWithHeader(group, contentWidth, i);
        }
    }
    
    /**
     * 渲染增强的组分隔符
     * @param contentWidth 内容区域宽度
     */
    private void renderGroupSeparator(float contentWidth) {
        // 分隔符前后的间距各为 ITEM_SPACING / 2，总距离等于按钮之间的间距（ITEM_SPACING）
        // 这样按钮距离分割线的距离就等于按钮之间的间距
        float spacing = UILayout.Toolbar.ITEM_SPACING * 0.5f;
        ImGui.dummy(0, spacing);
        
        // 使用主题颜色绘制分隔线
        ImGui.pushStyleColor(ImGuiCol.Separator, 
            ThemeManager.getInstance().getCurrentTheme().buttonBorder);
        
        // 绘制带缩进的分隔线，增强视觉层次
        float indent = contentWidth * 0.1f;
        ImGui.setCursorPosX(ImGui.getWindowContentRegionMinX() + indent);
        ImGui.separator();
        
        ImGui.popStyleColor();
        ImGui.dummy(0, spacing);
    }
    
    /**
     * 渲染带标题的工具组（支持折叠功能）
     * @param group 工具组
     * @param contentWidth 内容区域宽度
     * @param groupIndex 组索引
     */
    private void renderToolGroupWithHeader(ToolGroup group, float contentWidth, int groupIndex) {
        String groupId = group.name + "_" + groupIndex;
        boolean isCollapsed = groupCollapsedState.getOrDefault(groupId, false);
        
        // 渲染可折叠的组标题（可选功能，默认关闭以保持简洁）
        if (shouldShowGroupHeaders()) {
            if (renderGroupHeader(group, groupId, isCollapsed, contentWidth)) {
                // 切换折叠状态
                groupCollapsedState.put(groupId, !isCollapsed);
                saveGroupCollapsedStates();
                isCollapsed = !isCollapsed;
            }
        }
        
        // 如果未折叠，渲染工具组内容
        if (!isCollapsed) {
            renderToolGroup(group, contentWidth);
        }
    }
    
    /**
     * 渲染组标题
     * @param group 工具组
     * @param groupId 组ID
     * @param isCollapsed 是否折叠
     * @param contentWidth 内容宽度
     * @return 是否点击了标题（切换折叠状态）
     */
    private boolean renderGroupHeader(ToolGroup group, String groupId, boolean isCollapsed, float contentWidth) {
        // 设置标题样式
        ImGui.pushStyleColor(ImGuiCol.Button, 
            ThemeManager.getInstance().getCurrentTheme().panelBackground);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 
            ThemeManager.getInstance().getCurrentTheme().buttonHovered);
        
        // 渲染折叠箭头和组名
        String arrow = isCollapsed ? "▶" : "▼";
        String headerText = arrow + " " + Text.translatable(group.name).getString();
        
        float headerHeight = 20.0f;
        boolean clicked = ImGui.button(headerText + "##" + groupId, contentWidth, headerHeight);
        
        ImGui.popStyleColor(2);
        
        if (!isCollapsed) {
            ImGui.dummy(0, 2.0f); // 小间距
        }
        
        return clicked;
    }
    
    /**
     * 判断是否显示组标题
     * 通过配置控制是否启用折叠功能
     */
    private boolean shouldShowGroupHeaders() {
        return ToolPanelConfig.ENABLE_COLLAPSIBLE_GROUPS;
    }
    
    private void renderToolGroup(ToolGroup group, float contentWidth) {
        // 使用动态流式布局替代固定双列布局
        renderFlowLayout(group, contentWidth);
    }
    
    /**
     * 动态流式布局渲染方法
     * 工具按钮会自动排列，当空间不足时自动换行
     * @param group 工具组
     * @param contentWidth 内容区域宽度
     */
    private void renderFlowLayout(ToolGroup group, float contentWidth) {
        float buttonSize = UILayout.Toolbar.LEFT_BUTTON_SIZE;
        float buttonSpacing = UILayout.Toolbar.LEFT_BUTTON_SPACING;
        float marginLeft = ImGui.getWindowContentRegionMinX();
        
        // 动态计算每行可容纳的按钮数量（响应式设计）
        int buttonsPerRow = calculateOptimalButtonsPerRow(contentWidth);
        
        for (int i = 0; i < group.tools.size(); i++) {
            // 计算当前行的起始索引
            int rowStart = (i / buttonsPerRow) * buttonsPerRow;
            int currentRowButtons = Math.min(buttonsPerRow, group.tools.size() - rowStart);
            
            // 如果是新行的开始，计算居中偏移
            if (i % buttonsPerRow == 0) {
                float rowWidth = currentRowButtons * buttonSize + (currentRowButtons - 1) * buttonSpacing;
                float centerOffset = Math.max(0, (contentWidth - rowWidth) * 0.5f);
                ImGui.setCursorPosX(marginLeft + centerOffset);
            }
            
            renderToolButton(group.tools.get(i));
            
            // 如果不是行末且不是最后一个按钮，继续在同一行
            // 显式指定间距，不依赖全局ItemSpacing，避免窗口缩放时间距变化
            if ((i + 1) % buttonsPerRow != 0 && i < group.tools.size() - 1) {
                ImGui.sameLine(0, UILayout.Toolbar.LEFT_BUTTON_SPACING);
            }
        }
        
        // 注意：组间距由分隔符处理，这里不添加额外间距
        // 如果下一个组有分隔符，分隔符会添加间距；如果没有分隔符，也不添加间距
        // 这样可以确保按钮距离分割线的距离等于按钮之间的间距（ITEM_SPACING）
    }
    
    /**
     * 根据可用宽度计算最优的每行按钮数量（响应式设计）
     * 完全根据可用宽度自动计算，不限制最大列数
     * @param contentWidth 内容区域宽度
     * @return 最优的每行按钮数量
     */
    private int calculateOptimalButtonsPerRow(float contentWidth) {
        float buttonSize = UILayout.Toolbar.LEFT_BUTTON_SIZE;
        float buttonSpacing = UILayout.Toolbar.LEFT_BUTTON_SPACING;
        
        // 基础计算：能容纳多少个按钮
        // 公式：contentWidth = n * buttonSize + (n-1) * buttonSpacing
        // 解：n = (contentWidth + buttonSpacing) / (buttonSize + buttonSpacing)
        // 使用 Math.floor 向下取整，确保不会超出可用宽度
        float calculated = (contentWidth + buttonSpacing) / (buttonSize + buttonSpacing);
        int maxPossible = Math.max(1, (int) Math.floor(calculated));
        
        // 响应式调整：根据宽度阈值和配置优化布局
        if (!ToolPanelConfig.ENABLE_RESPONSIVE_LAYOUT) {
            return 2; // 禁用响应式时固定双列
        }
        
        // 如果配置了最大列数限制，则应用限制
        if (ToolPanelConfig.MAX_COLUMNS > 0) {
            maxPossible = Math.min(maxPossible, ToolPanelConfig.MAX_COLUMNS);
        }
        
        // 超窄屏：至少保证单列
        if (contentWidth <= UILayout.Toolbar.SINGLE_COLUMN_THRESHOLD) {
            return 1;
        }
        
        // 验证计算：确保4个按钮能放下
        // 4个按钮需要：4 * 40 + 3 * 4 = 160 + 12 = 172
        // 面板宽度182，减去边框和内边距后，可用宽度应该 >= 172
        // 使用稍微宽松的阈值（171.5）来避免浮点精度问题
        float requiredWidthFor4 = 4 * buttonSize + 3 * buttonSpacing;
        if (contentWidth >= (requiredWidthFor4 - 0.5f) && maxPossible < 4) {
            return 4; // 强制返回4，因为面板宽度设计为能容纳4个按钮
        }
        
        // 其他情况：根据实际可用宽度计算，不限制最大列数
        return maxPossible;
    }
    
    private void renderToolButton(Tool tool) {
        if (tool == null || tool.icon == null) {
            LOGGER.warn("Attempted to render null tool or tool with null icon");
            return;
        }

        try {
            boolean isSelected = appState.getCurrentTool() != null && 
                appState.getCurrentTool().getId().equals(tool.id);
            
            // 添加调试日志
            LOGGER.debug("Rendering tool button: {} (id: {}), selected: {}", 
                tool.label, tool.id, isSelected);

            // 设置按钮边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.getInstance().getCurrentTheme().buttonBorder);

            try {

                String tooltip = PlotI18n.toolUsageHint(tool.id);
                
                if (UIUtils.imageButton(tool.icon, tooltip,
                    UILayout.Toolbar.LEFT_BUTTON_SIZE, isSelected)) {
                    LOGGER.debug("Tool button clicked: {}", tool.id);
                    handleToolSelect(tool.id);
                }
            } finally {
                ImGui.popStyleColor();
                ImGui.popStyleVar();
            }
            
        } catch (Exception e) {
            LOGGER.error("Error rendering tool button: {}", tool.label, e);
        }
    }

    private void handleToolSelect(String toolId) {
        LOGGER.debug("开始处理工具选择: toolId={}", toolId);
        
        try {
            ITool selectedTool = toolManager.getTool(toolId);
            if (selectedTool == null) {
                LOGGER.error("工具未找到: toolId={}, 尝试重新初始化绘图工具", toolId);
                
                // 使用新的DrawingToolsModule API重新初始化
                try {
                    SnapManager snapManager = SnapManager.getInstance();
                    CommandManager commandManager = CommandManager.getInstance();
                    DrawingToolsModule.initializeAndRegister(toolManager, appState, eventBus, snapManager, commandManager);
                } catch (Exception initException) {
                    LOGGER.error("重新初始化绘图工具失败: {}", initException.getMessage(), initException);
                    return;
                }
                selectedTool = toolManager.getTool(toolId);
                
                if (selectedTool == null) {
                    LOGGER.error("重新初始化后仍未找到工具: toolId={}", toolId);
                    return;
                }
            }
            
            LOGGER.debug("找到工具: toolId={}, toolClass={}", 
                toolId, selectedTool.getClass().getSimpleName());
            
            if (selectedTool instanceof BaseTool baseTool) {
                LOGGER.debug("准备设置当前工具: toolId={}", toolId);
                
                // 记录工具状态变化
                ITool oldTool = toolManager.getActiveTool();
                LOGGER.debug("当前工具: {}", oldTool != null ? oldTool.getId() : "null");
                
                // 修复：使用ToolManager.setActiveTool而不是AppState.setCurrentTool
                // 这样可以确保setupToolCanvasAndCamera被正确调用
                toolManager.setActiveTool(selectedTool);
                LOGGER.debug("新工具已通过ToolManager设置: {}", selectedTool.getId());
                
                // 同时更新AppState以保持兼容性
                appState.setCurrentTool(baseTool);
                LOGGER.debug("AppState也已更新: {}", baseTool.getId());
                
                // 发布工具选择事件
                eventBus.publish(new ToolSelectedEvent(toolId));
                LOGGER.debug("工具选择事件已发布: toolId={}", toolId);
            } else {
                LOGGER.error("工具类型错误: toolId={}, expectedType=BaseTool, actualType={}", 
                    toolId, selectedTool.getClass().getName());
            }
        } catch (Exception e) {
            LOGGER.error("处理工具选择时发生错误: toolId={}", toolId, e);
        }
    }

    @Override
    public void init() {
        try {
            LOGGER.debug("Initializing ToolPanel components...");
            
            // 确保 ToolManager 已初始化
            if (toolManager == null) {
                try {
                    this.toolManager = ToolManager.getInstance();
                } catch (IllegalStateException e) {
                    LOGGER.warn("ToolManager未初始化，现在使用AppState进行初始化");
                    ToolManager.initialize(appState);
                    this.toolManager = ToolManager.getInstance();
                }
                
                if (toolManager == null) {
                    throw new IllegalStateException(PlotI18n.error("error.plot.validation.tool_manager_init_failed"));
                }
            }
            
            // 初始化工具组
            if (toolGroups == null || toolGroups.isEmpty()) {
                LOGGER.debug("Initializing tool groups...");
                this.toolGroups = initializeToolGroups();
            }
            
            LOGGER.debug("ToolPanel components initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize ToolPanel components", e);
            throw new RuntimeException("Failed to initialize ToolPanel components", e);
        }
    }
    
    @Override
    public void close() throws Exception {
        LOGGER.debug("Disposing ToolPanel resources...");
        // 保存折叠状态
        saveGroupCollapsedStates();
        // 清理资源
    }
    
    /**
     * 加载工具组折叠状态
     */
    private void loadGroupCollapsedStates() {
        try {
            // 这里可以从配置文件或应用状态中加载
            // 暂时使用默认状态（所有组展开）
            for (ToolGroup group : toolGroups) {
                if (group != null) {
                    String groupId = group.name + "_" + toolGroups.indexOf(group);
                    groupCollapsedState.putIfAbsent(groupId, false);
                }
            }
            LOGGER.debug("Loaded group collapsed states");
        } catch (Exception e) {
            LOGGER.error("Failed to load group collapsed states", e);
        }
    }
    
    /**
     * 保存工具组折叠状态
     */
    private void saveGroupCollapsedStates() {
        try {
            // 这里可以保存到配置文件或应用状态中
            // 具体实现取决于配置管理器的API
            LOGGER.debug("Saved group collapsed states: {}", groupCollapsedState);
        } catch (Exception e) {
            LOGGER.error("Failed to save group collapsed states", e);
        }
    }
    
    private List<ToolGroup> initializeToolGroups() {
        List<ToolGroup> groups = new ArrayList<>();
        
        // 第一组：基础工具
        List<Tool> basicTools = Arrays.asList(
            new Tool(ToolPanelIcons.SELECT, "tool.plot.select", "select"),
            new Tool(ToolPanelIcons.ERASER, "tool.plot.eraser", "eraser")
        );
        groups.add(new ToolGroup("group.plot.basic_tools", basicTools, true));
        
        // 第二组：绘图工具
        List<Tool> drawTools = Arrays.asList(
            new Tool(ToolPanelIcons.LINE, "tool.plot.line", "line"),
            new Tool(ToolPanelIcons.FREEHAND, "tool.plot.freehand", "freedraw"),
            new Tool(ToolPanelIcons.CIRCLE, "tool.plot.circle", "circle"),
            new Tool(ToolPanelIcons.RECTANGLE, "tool.plot.rectangle", "rectangle"),
            new Tool(ToolPanelIcons.SPLINE, "tool.plot.spline", "spline"),
            new Tool(ToolPanelIcons.ELLIPSE, "tool.plot.ellipse", "ellipse"),
            new Tool(ToolPanelIcons.SEMICIRCLE, "tool.plot.semicircle", "semicircle"),
            new Tool(ToolPanelIcons.ARC, "tool.plot.arc", "arc"),
            new Tool(ToolPanelIcons.POLYLINE, "tool.plot.polyline", "polyline"),
            new Tool(ToolPanelIcons.POLYGON, "tool.plot.polygon", "polygon")
        );
        groups.add(new ToolGroup("group.plot.drawing_tools", drawTools, true));
        
        // 第三组：特殊图形
        List<Tool> specialTools = Arrays.asList(
            new Tool(ToolPanelIcons.STAR, "tool.plot.star", "star"),
            new Tool(ToolPanelIcons.SPIRAL, "tool.plot.spiral", "spiral"),
            new Tool(ToolPanelIcons.CATENARY, "tool.plot.catenary", "catenary"),
            new Tool(ToolPanelIcons.SINE_WAVE, "tool.plot.sine", "sine")
        );
        groups.add(new ToolGroup("group.plot.special_tools", specialTools, true));
        
        // 第四组：编辑工具（包含变换和修改功能）
        List<Tool> editTools = Arrays.asList(
            // 变换工具
            new Tool(ToolPanelIcons.MOVE, "tool.plot.move", "move"),
            new Tool(ToolPanelIcons.ROTATE, "tool.plot.rotate", "rotate"),
            new Tool(ToolPanelIcons.SCALE, "tool.plot.scale", "scale"),
            new Tool(ToolPanelIcons.MIRROR, "tool.plot.mirror", "mirror"),
            new Tool(ToolPanelIcons.ALIGN, "tool.plot.align", "align"),
            new Tool(ToolPanelIcons.ARRAY, "tool.plot.array", "array"),
            new Tool(ToolPanelIcons.OFFSET, "tool.plot.offset", "offset"),
            // 修改工具
            new Tool(ToolPanelIcons.BREAK, "tool.plot.break", "break"),
            new Tool(ToolPanelIcons.FILLET, "tool.plot.fillet", "fillet"),
            new Tool(ToolPanelIcons.CHAMFER, "tool.plot.chamfer", "chamfer"),
            new Tool(ToolPanelIcons.EXTEND, "tool.plot.extend", "extend"),
            new Tool(ToolPanelIcons.TRIM, "tool.plot.trim", "trim"),
            new Tool(ToolPanelIcons.TRANSFORM, "tool.plot.transform", "transform")
        );
        groups.add(new ToolGroup("group.plot.edit_tools", editTools, true));
        
        // 第五组：标注工具
        List<Tool> annotationTools = Arrays.asList(
                new Tool(ToolPanelIcons.TEXT, "tool.plot.text", "text"),
                new Tool(ToolPanelIcons.ANNOTATION, "tool.plot.annotation", "annotation")
        );
        groups.add(new ToolGroup("group.plot.annotation_tools", annotationTools, true));
        
        return groups;
    }
    
    private static class ToolGroup {
        final String name;
        final List<Tool> tools;
        final boolean needsSeparator;
        
        ToolGroup(String name, List<Tool> tools, boolean needsSeparator) {
            this.name = name;
            this.tools = tools;
            this.needsSeparator = needsSeparator;
        }
    }
    
    private static class Tool {
        final Identifier icon;
        final String label;
        final String id;
        
        Tool(Identifier icon, String label, String id) {
            this.icon = icon;
            this.label = label;
            this.id = id;
        }
    }
}
