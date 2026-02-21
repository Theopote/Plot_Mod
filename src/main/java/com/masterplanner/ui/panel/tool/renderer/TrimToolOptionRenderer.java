package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.TrimTool;
import com.masterplanner.ui.tools.impl.modify.strategy.TrimWithSelectionStrategy;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修剪工具属性面板渲染器
 * 
 * <p>提供修剪工具的配置选项，采用单向数据流设计：</p>
 * <ul>
 *   <li>无状态设计：UI不维护内部状态，直接从工具获取最新状态</li>
 *   <li>单一事实来源：所有状态都存储在TrimTool中</li>
 *   <li>强封装：UI不直接访问策略对象，只通过TrimTool的公共API</li>
 *   <li>支持边界修剪和栅栏修剪两种模式</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 单向数据流版本
 */
public class TrimToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrimToolOptionRenderer.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_MODE = TrimTool.CONFIG_KEY_MODE;
    private static final String CONFIG_KEY_TOLERANCE = TrimTool.CONFIG_KEY_TOLERANCE;
    private static final String CONFIG_KEY_FENCE_TYPE = TrimTool.CONFIG_KEY_FENCE_TYPE;
    private static final String CONFIG_KEY_FENCE_POLYGON_SIDES = TrimTool.CONFIG_KEY_FENCE_POLYGON_SIDES;
    
    // 修剪模式常量
    private static final String TRIM_MODE_BOUNDARY = "BOUNDARY";
    private static final String TRIM_MODE_FENCE = "FENCE";
    private static final String[] FENCE_TYPE_LABELS = {"Polyline", "矩形", "圆形", "椭圆", "正多边形"};
    private static final TrimWithSelectionStrategy.FenceType[] FENCE_TYPE_VALUES = {
        TrimWithSelectionStrategy.FenceType.POLYLINE,
        TrimWithSelectionStrategy.FenceType.RECTANGLE,
        TrimWithSelectionStrategy.FenceType.CIRCLE,
        TrimWithSelectionStrategy.FenceType.ELLIPSE,
        TrimWithSelectionStrategy.FenceType.REGULAR_POLYGON
    };
    
    // 图标ID
    private final int boundaryTrimIconId;
    private final int fenceTrimIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    public TrimToolOptionRenderer() {
        super("trim");
        
        // 加载图标
        this.boundaryTrimIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/trim_boundary.png"));
        this.fenceTrimIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/trim_fence.png"));
        this.resourcesInitialized = true;
        
        // 添加图标加载调试信息
        LOGGER.debug("修剪工具图标加载完成 - 边界: {}, 栅栏: {}", 
            boundaryTrimIconId, fenceTrimIconId);
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("trim_options");
        
        try {
            // 从ToolOptionsPanel传入的工具参数获取TrimTool
            TrimTool currentTool = getCurrentToolFromContext();
            if (currentTool == null) {
                LOGGER.debug("当前工具不是TrimTool，跳过渲染");
                return 0;
            }

            LOGGER.debug("开始渲染修剪工具选项面板，当前工具: {}", currentTool.getClass().getSimpleName());
            LOGGER.debug("当前修剪模式: {}", currentTool.getTrimMode());
            LOGGER.debug("可用区域: {}x{}", ImGui.getContentRegionAvail().x, ImGui.getContentRegionAvail().y);

            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // === 修剪状态显示 ===
            height += renderTrimStatus(currentTool, currentTheme);
            LOGGER.debug("修剪状态显示渲染完成，高度: {}", height);
            
            // === 修剪模式选择 ===
            height += renderTrimModeSelection(currentTool, currentTheme);
            LOGGER.debug("修剪模式选择渲染完成，高度: {}", height);
            
            // === 修剪参数设置 ===
            height += renderTrimParameters(currentTool, currentTheme);
            LOGGER.debug("修剪参数渲染完成，总高度: {}", height);
            
            // 恢复原始的圆角设置
            ImGui.getStyle().setFrameRounding(originalRounding);
            
        } catch (Exception e) {
            LOGGER.error("渲染修剪工具选项时发生错误: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        LOGGER.debug("修剪工具选项面板渲染完成，总高度: {}", height);
        return height;
    }
    
    /**
     * 渲染修剪状态显示
     */
    private float renderTrimStatus(TrimTool currentTool, UITheme.ThemeColors currentTheme) {
        float height = 0;
        
        // 获取当前策略的状态信息
        if (currentTool.getModifyStrategy() instanceof TrimWithSelectionStrategy trimStrategy) {
            TrimWithSelectionStrategy.TrimState trimState = trimStrategy.getTrimState();
            TrimWithSelectionStrategy.TrimType trimType = trimStrategy.getTrimType();
            
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("当前状态");
            
            ImGui.tableNextColumn();
            
            // 设置状态指示器样式
            switch (trimState) {
                case SELECTING_BOUNDARIES, SELECTING_TARGETS -> {
                    ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.successText);
                    ImGui.text("选择模式");
                }
                case WAITING_TRIM_CLICK, DRAWING_FENCE, BOUNDARY_READY, FENCE_READY -> {
                    ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.warningText);
                    ImGui.text("修剪模式");
                }
                case PROCESSING -> {
                    ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.errorText);
                    ImGui.text("处理中");
                }
                default -> {
                    ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.mutedText);
                    ImGui.text("未知状态");
                }
            }
            ImGui.popStyleColor();
            
            // 显示详细状态
            ImGui.sameLine();
            ImGui.textColored(currentTheme.mutedText, " - " + trimState.getDescription());
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 显示操作提示
            int selectedCount = trimStrategy.getSelectedCount();
            if (selectedCount > 0) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.tableNextColumn();
                
                String itemType = switch (trimState) {
                    case SELECTING_BOUNDARIES, WAITING_TRIM_CLICK, BOUNDARY_READY -> "边界图形";
                    case SELECTING_TARGETS, DRAWING_FENCE, FENCE_READY -> "目标图形";
                    default -> "图形";
                };
                
                ImGui.textColored(currentTheme.successText, 
                    "已选择 " + selectedCount + " 个" + itemType);
                height += ImGui.getFrameHeightWithSpacing();
            }
            
            // 显示栅栏信息
            if (trimType == TrimWithSelectionStrategy.TrimType.FENCE) {
                int fencePointCount = trimStrategy.getFencePoints().size();
                if (fencePointCount > 0) {
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    ImGui.tableNextColumn();
                    ImGui.textColored(currentTheme.warningText, 
                        "栅栏点数: " + fencePointCount);
                    height += ImGui.getFrameHeightWithSpacing();
                }
            }
        }
        
        return height;
    }
    
    /**
     * 渲染修剪模式选择
     */
    private float renderTrimModeSelection(TrimTool currentTool, UITheme.ThemeColors currentTheme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("修剪模式");
        
        ImGui.tableNextColumn();
        
        // 检查图标是否加载成功
        boolean iconsLoaded = boundaryTrimIconId > 0 && fenceTrimIconId > 0;
        LOGGER.debug("图标加载状态: {}", iconsLoaded);
        
        if (iconsLoaded) {
            // 使用图标按钮
            // 边界修剪按钮
            boolean isBoundarySelected = currentTool.getTrimMode() == TrimWithSelectionStrategy.TrimMode.BOUNDARY;
            pushButtonStyle(currentTheme, isBoundarySelected);
            ImGui.pushID("boundary_trim");
            boolean boundaryClicked = ImGui.imageButton(boundaryTrimIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("边界修剪：选择边界图形，然后点击要修剪的图形一侧");
            }
            ImGui.popStyleColor(4);
            LOGGER.debug("边界修剪按钮渲染完成，选中: {}, 点击: {}", isBoundarySelected, boundaryClicked);
            
            ImGui.sameLine();
            
            // 栅栏修剪按钮
            boolean isFenceSelected = currentTool.getTrimMode() == TrimWithSelectionStrategy.TrimMode.FENCE;
            pushButtonStyle(currentTheme, isFenceSelected);
            ImGui.pushID("fence_trim");
            boolean fenceClicked = ImGui.imageButton(fenceTrimIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("栅栏修剪：定义栅栏线进行批量修剪");
            }
            ImGui.popStyleColor(4);
            LOGGER.debug("栅栏修剪按钮渲染完成，选中: {}, 点击: {}", isFenceSelected, fenceClicked);
            
            // 处理按钮点击事件
            if (boundaryClicked && !isBoundarySelected) {
                LOGGER.debug("边界修剪按钮被点击，发送配置更新事件");
                updateToolConfig(CONFIG_KEY_MODE, TRIM_MODE_BOUNDARY);
                LOGGER.debug("切换到边界修剪模式");
            }
            if (fenceClicked && !isFenceSelected) {
                LOGGER.debug("栅栏修剪按钮被点击，发送配置更新事件");
                updateToolConfig(CONFIG_KEY_MODE, TRIM_MODE_FENCE);
                LOGGER.debug("切换到栅栏修剪模式");
            }
        } else {
            // 使用文本按钮作为备用方案
            LOGGER.warn("图标加载失败，使用文本按钮作为备用方案");
            
            boolean isBoundarySelected = currentTool.getTrimMode() == TrimWithSelectionStrategy.TrimMode.BOUNDARY;
            if (ImGui.button("边界修剪", 80, 30)) {
                if (!isBoundarySelected) {
                    updateToolConfig(CONFIG_KEY_MODE, TRIM_MODE_BOUNDARY);
                    LOGGER.debug("切换到边界修剪模式");
                }
            }
            ImGui.sameLine();
            
            boolean isFenceSelected = currentTool.getTrimMode() == TrimWithSelectionStrategy.TrimMode.FENCE;
            if (ImGui.button("栅栏修剪", 80, 30)) {
                if (!isFenceSelected) {
                    updateToolConfig(CONFIG_KEY_MODE, TRIM_MODE_FENCE);
                    LOGGER.debug("切换到栅栏修剪模式");
                }
            }
        }
        ImGui.sameLine();
        ImGui.textDisabled("(按住Shift连续)");

        height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;

        if (currentTool.getTrimMode() == TrimWithSelectionStrategy.TrimMode.FENCE) {
            height += renderFenceTypeOptions(currentTool, currentTheme);
        }

        LOGGER.debug("修剪模式选择渲染完成，高度: {}", height);
        
        return height;
    }

    private float renderFenceTypeOptions(TrimTool currentTool, UITheme.ThemeColors currentTheme) {
        float height = 0.0f;

        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("栅栏类型");

        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);

        TrimWithSelectionStrategy.FenceType currentType = currentTool.getFenceType();
        String previewLabel = currentType.getDisplayName();

        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);

        if (ImGui.beginCombo("##fence_type", previewLabel)) {
            for (int i = 0; i < FENCE_TYPE_VALUES.length; i++) {
                boolean selected = FENCE_TYPE_VALUES[i] == currentType;
                if (ImGui.selectable(FENCE_TYPE_LABELS[i], selected)) {
                    updateToolConfig(CONFIG_KEY_FENCE_TYPE, FENCE_TYPE_VALUES[i].name());
                    LOGGER.debug("栅栏类型切换为: {}", FENCE_TYPE_VALUES[i]);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("设置栅栏修剪边界类型");
        }

        ImGui.popStyleVar();
        ImGui.popStyleColor(4);
        ImGui.popItemWidth();
        height += ImGui.getFrameHeightWithSpacing();

        if (currentType == TrimWithSelectionStrategy.FenceType.REGULAR_POLYGON) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("边数");

            ImGui.tableNextColumn();
            int[] sides = { currentTool.getFencePolygonSides() };
            ImGui.pushItemWidth(-1);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
            ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
            ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, currentTheme.grabRounding);

            if (ImGui.sliderInt("##fence_polygon_sides", sides, 3, 24)) {
                updateToolConfig(CONFIG_KEY_FENCE_POLYGON_SIDES, String.valueOf(sides[0]));
                LOGGER.debug("正多边形边数更新为: {}", sides[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("设置正多边形栅栏的边数");
            }

            ImGui.popStyleVar(2);
            ImGui.popStyleColor(6);
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
        }

        return height;
    }
    
    /**
     * 渲染修剪参数设置
     */
    private float renderTrimParameters(TrimTool currentTool, UITheme.ThemeColors currentTheme) {
        float height = 0;
        
        if (ImGui.treeNodeEx("修剪参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            height += 20;
            
            // 获取当前配置状态
            double currentTolerance = currentTool.getTrimTolerance();
            
            // 创建临时数组来传递给ImGui
            float[] tolerance = { (float) currentTolerance };
            
            // 应用控件样式
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
            ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
            ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
            
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, currentTheme.grabRounding);
            
            // 修剪容差
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("修剪容差");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##tolerance", tolerance, 1.0f, 20.0f, "%.1f")) {
                updateToolConfig(CONFIG_KEY_TOLERANCE, String.valueOf(tolerance[0]));
                LOGGER.debug("修剪容差已更新为: {}", tolerance[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("修剪操作的容差范围，影响修剪点的检测精度");
            }
            
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(6);
            
            ImGui.treePop();
        }
        
        return height;
    }

    /**
     * 渲染使用说明
     */
    private float renderUsageInstructions() {
        float height = 0;
        
        if (ImGui.treeNodeEx("使用说明", ImGuiTreeNodeFlags.DefaultOpen)) {
            height += 20;
            
            ImGui.textWrapped("1. 选择修剪模式（边界/栅栏）");
            height += 20;
            
            ImGui.textWrapped("2. 设置修剪参数（容差等）");
            height += 20;
            
            ImGui.textWrapped("3. 点击要修剪的图形位置");
            height += 20;
            
            ImGui.textWrapped("4. 根据模式完成修剪操作");
            height += 20;
            
            ImGui.textWrapped("5. 按ESC取消操作");
            height += 20;
            
            ImGui.treePop();
        }
        
        return height;
    }
    
    /**
     * 渲染快捷键提示
     */
    private float renderShortcutTips() {
        float height = 0;
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        if (ImGui.treeNodeEx("操作指南", ImGuiTreeNodeFlags.DefaultOpen)) {
            height += 20;
            
            ImGui.textColored(theme.warningText, "操作流程：");
            ImGui.spacing();
            
            ImGui.textColored(theme.warningText, "1. 选择边界图形");
            ImGui.bulletText("左键点选或框选用作修剪边界的图形");
            ImGui.bulletText("可以选择多个边界图形");
            
            ImGui.spacing();
            ImGui.textColored(theme.warningText, "2. 确认选择");
            ImGui.bulletText("右键完成边界选择，进入修剪模式");
            
            ImGui.spacing();
            ImGui.textColored(theme.warningText, "3. 执行修剪");
            ImGui.bulletText("边界修剪：左键点击要修剪图形的一侧");
            ImGui.bulletText("栅栏修剪：左键定义栅栏线，右键完成");
            
            ImGui.spacing();
            ImGui.textColored(theme.mutedText, "快捷键：");
            ImGui.bulletText("C：切换到边界修剪模式");
            ImGui.bulletText("F：切换到栅栏修剪模式");
            ImGui.bulletText("ESC：取消当前操作，返回选择模式");
            ImGui.bulletText("Shift：按住进行连续修剪");
            
            height += 160;
            
            ImGui.treePop();
        }
        
        return height;
    }
    
    /**
     * 设置按钮的样式
     */
    private void pushButtonStyle(UITheme.ThemeColors theme, boolean isSelected) {
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonActiveBorder);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        }
    }
    
    /**
     * 从上下文获取当前工具
     * 优先从ToolOptionsPanel传入的工具参数获取，如果没有则从ToolManager获取
     */
    private TrimTool getCurrentToolFromContext() {
        try {
            // 使用AppState获取当前工具，与其他渲染器保持一致
            com.masterplanner.core.state.AppState appState = com.masterplanner.core.state.AppState.getInstance();
            com.masterplanner.core.tool.BaseTool currentTool = appState.getCurrentTool();
            
            LOGGER.debug("AppState获取到的当前工具: {}", 
                currentTool != null ? currentTool.getClass().getSimpleName() : "null");
            
            if (currentTool instanceof TrimTool) {
                LOGGER.debug("当前工具是TrimTool，返回成功");
                return (TrimTool) currentTool;
            } else {
                LOGGER.debug("当前工具不是TrimTool，类型: {}", 
                    currentTool != null ? currentTool.getClass().getSimpleName() : "null");
            }
        } catch (Exception e) {
            LOGGER.warn("获取当前工具失败: {}", e.getMessage(), e);
        }
        return null;
    }
    
    @Override
    public void initialize() {
        // 不再需要同步状态，因为render总是获取最新状态
        LOGGER.debug("TrimToolOptionRenderer 已初始化（单向数据流模式）");
    }
    
    @Override
    public void cleanup() {
        // 释放纹理资源
        if (resourcesInitialized) {
            try {
                ImGuiUtils.deleteTexture(boundaryTrimIconId);
                ImGuiUtils.deleteTexture(fenceTrimIconId);
                resourcesInitialized = false;
                LOGGER.debug("修剪工具选项渲染器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("释放修剪工具选项渲染器资源失败: {}", e.getMessage());
            }
        }
    }
} 