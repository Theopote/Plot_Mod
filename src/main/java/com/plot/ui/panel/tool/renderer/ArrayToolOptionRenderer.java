package com.plot.ui.panel.tool.renderer;

import com.plot.utils.PlotI18n;
import com.plot.ui.tools.impl.modify.ArrayTool;
import com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阵列工具属性面板渲染器
 * 
 * <p>提供阵列工具的配置选项，采用单向数据流设计：</p>
 * <ul>
 *   <li>无状态设计：UI不维护内部状态，直接从工具获取最新状态</li>
 *   <li>单一事实来源：所有状态都存储在ArrayTool中</li>
 *   <li>强封装：UI不直接访问策略对象，只通过ArrayTool的公共API</li>
 *   <li>支持矩形阵列、环形阵列、路径阵列三种类型</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 3.0 - 单向数据流版本
 */
public class ArrayToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayToolOptionRenderer.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_ARRAY_TYPE = "arrayType";
    private static final String CONFIG_KEY_ROW_COUNT = "rowCount";
    private static final String CONFIG_KEY_COLUMN_COUNT = "columnCount";
    // 已由 rowSpacing/columnSpacing 替代；环形参数由交互/数量推导
    // 保留常量占位以便向后兼容（不再使用）
    // 移除未使用的旧键常量，避免混淆
    
    // 阵列类型常量
    private static final String ARRAY_TYPE_RECTANGULAR = "RECTANGULAR";
    private static final String ARRAY_TYPE_CIRCULAR = "CIRCULAR";
    private static final String ARRAY_TYPE_PATH = "PATH";
    
    // 图标ID
    private final int rectangularArrayIconId;
    private final int circularArrayIconId;
    private final int pathArrayIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    public ArrayToolOptionRenderer() {
        super("array");
        
        // 加载图标
        this.rectangularArrayIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/array_rectangular.png"));
        this.circularArrayIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/array_circular.png"));
        this.pathArrayIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/array_path.png"));
        this.resourcesInitialized = true;
        
        // 添加图标加载调试信息
        LOGGER.debug("阵列工具图标加载完成 - 矩形: {}, 环形: {}, 路径: {}", 
            rectangularArrayIconId, circularArrayIconId, pathArrayIconId);
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("array_options");
        
        try {
            // 从ToolOptionsPanel传入的工具参数获取ArrayTool
            ArrayTool currentTool = getCurrentToolFromContext();
            if (currentTool == null) {
                LOGGER.debug("当前工具不是ArrayTool，跳过渲染");
                return 0;
            }

            LOGGER.debug("开始渲染阵列工具选项面板，当前工具: {}", currentTool.getClass().getSimpleName());
            LOGGER.debug("当前阵列类型: {}", currentTool.getArrayType());
            LOGGER.debug("可用区域: {}x{}", ImGui.getContentRegionAvail().x, ImGui.getContentRegionAvail().y);

            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // === 阵列类型选择 ===
            height += renderArrayTypeSelection(currentTool, currentTheme);
            LOGGER.debug("阵列类型选择渲染完成，高度: {}", height);
            
            // === 根据阵列类型显示参数 ===
            // 获取当前阵列类型（兼容新旧策略）
            String currentArrayTypeName = getCurrentArrayTypeName(currentTool);
            switch (currentArrayTypeName) {
                case "RECTANGULAR" -> {
                    height += renderRectangularArrayOptions(currentTool);
                    LOGGER.debug("矩形阵列选项渲染完成，总高度: {}", height);
                }
                case "CIRCULAR" -> {
                    height += renderCircularArrayOptions(currentTool);
                    LOGGER.debug("环形阵列选项渲染完成，总高度: {}", height);
                }
                case "PATH" -> {
                    height += renderPathArrayOptions(currentTool);
                    LOGGER.debug("路径阵列选项渲染完成，总高度: {}", height);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("渲染阵列工具选项时发生错误: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        LOGGER.debug("阵列工具选项面板渲染完成，总高度: {}", height);
        return height;
    }
    
    /**
     * 渲染阵列类型选择
     */
    private float renderArrayTypeSelection(ArrayTool currentTool, UITheme.ThemeColors currentTheme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.array_type"));
        
        ImGui.tableNextColumn();
        
        // 简化按钮布局，不使用复杂的定位
        LOGGER.debug("开始渲染阵列类型按钮");
        LOGGER.debug("图标ID - 矩形: {}, 环形: {}, 路径: {}", 
            rectangularArrayIconId, circularArrayIconId, pathArrayIconId);
        
        // 检查图标是否加载成功
        boolean iconsLoaded = rectangularArrayIconId > 0 && circularArrayIconId > 0 && pathArrayIconId > 0;
        LOGGER.debug("图标加载状态: {}", iconsLoaded);
        
        if (iconsLoaded) {
            // 使用图标按钮
            // 获取当前阵列类型名称（兼容新旧策略）
            String currentArrayTypeName = getCurrentArrayTypeName(currentTool);
            
            // 矩形阵列按钮
            boolean isRectangularSelected = "RECTANGULAR".equals(currentArrayTypeName);
            pushButtonStyle(currentTheme, isRectangularSelected);
            ImGui.pushID("rectangular_array");
            boolean rectangularClicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(rectangularArrayIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.array.rectangular"));
            }
            ImGui.popStyleColor(4);
            LOGGER.debug("矩形阵列按钮渲染完成，选中: {}, 点击: {}", isRectangularSelected, rectangularClicked);
            
            ImGui.sameLine();
            
            // 环形阵列按钮
            boolean isCircularSelected = "CIRCULAR".equals(currentArrayTypeName);
            pushButtonStyle(currentTheme, isCircularSelected);
            ImGui.pushID("circular_array");
            boolean circularClicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(circularArrayIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.array.polar"));
            }
            ImGui.popStyleColor(4);
            LOGGER.debug("环形阵列按钮渲染完成，选中: {}, 点击: {}", isCircularSelected, circularClicked);
            
            ImGui.sameLine();
            
            // 路径阵列按钮
            boolean isPathSelected = "PATH".equals(currentArrayTypeName);
            pushButtonStyle(currentTheme, isPathSelected);
            ImGui.pushID("path_array");
            boolean pathClicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(pathArrayIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.array.path"));
            }
            ImGui.popStyleColor(4);
            LOGGER.debug("路径阵列按钮渲染完成，选中: {}, 点击: {}", isPathSelected, pathClicked);
            
            // 处理按钮点击事件
            if (rectangularClicked && !isRectangularSelected) {
                LOGGER.debug("矩形阵列按钮被点击，发送配置更新事件");
                updateToolConfig(CONFIG_KEY_ARRAY_TYPE, ARRAY_TYPE_RECTANGULAR);
                LOGGER.debug("status.plot.array.mode_rect");
            }
            if (circularClicked && !isCircularSelected) {
                LOGGER.debug("环形阵列按钮被点击，发送配置更新事件");
                updateToolConfig(CONFIG_KEY_ARRAY_TYPE, ARRAY_TYPE_CIRCULAR);
                LOGGER.debug("status.plot.array.mode_polar");
            }
            if (pathClicked && !isPathSelected) {
                LOGGER.debug("路径阵列按钮被点击，发送配置更新事件");
                updateToolConfig(CONFIG_KEY_ARRAY_TYPE, ARRAY_TYPE_PATH);
                LOGGER.debug("status.plot.array.mode_path");
            }
        } else {
            // 使用文本按钮作为备用方案
            LOGGER.warn("图标加载失败，使用文本按钮作为备用方案");
            
            // 获取当前阵列类型名称（兼容新旧策略）
            String currentArrayTypeName = getCurrentArrayTypeName(currentTool);
            
            boolean isRectangularSelected = "RECTANGULAR".equals(currentArrayTypeName);
            if (ImGui.button(PlotI18n.tr("array.plot.rectangular"), 80, 30)) {
                if (!isRectangularSelected) {
                    updateToolConfig(CONFIG_KEY_ARRAY_TYPE, ARRAY_TYPE_RECTANGULAR);
                    LOGGER.debug("status.plot.array.mode_rect");
                }
            }
            ImGui.sameLine();
            
            boolean isCircularSelected = "CIRCULAR".equals(currentArrayTypeName);
            if (ImGui.button(PlotI18n.tr("array.plot.polar"), 80, 30)) {
                if (!isCircularSelected) {
                    updateToolConfig(CONFIG_KEY_ARRAY_TYPE, ARRAY_TYPE_CIRCULAR);
                    LOGGER.debug("status.plot.array.mode_polar");
                }
            }
            ImGui.sameLine();
            
            boolean isPathSelected = "PATH".equals(currentArrayTypeName);
            if (ImGui.button(PlotI18n.tr("array.plot.path"), 80, 30)) {
                if (!isPathSelected) {
                    updateToolConfig(CONFIG_KEY_ARRAY_TYPE, ARRAY_TYPE_PATH);
                    LOGGER.debug("status.plot.array.mode_path");
                }
            }
        }
        
        height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
        LOGGER.debug("阵列类型选择渲染完成，高度: {}", height);
        
        return height;
    }
    
    /**
     * 渲染矩形阵列选项
     */
    private float renderRectangularArrayOptions(ArrayTool currentTool) {
        float height = 0;
        
        if (ImGui.treeNodeEx(PlotI18n.tr("array.plot.rectangular_params"), ImGuiTreeNodeFlags.DefaultOpen)) {
            height += 20;
            
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 从新策略获取参数值
            int[] rowCount = { getRowCountFromStrategy(currentTool) };
            int[] columnCount = { getColumnCountFromStrategy(currentTool) };
            float[] rowSpacing = { (float) getRowSpacingFromStrategy(currentTool) };
            float[] columnSpacing = { (float) getColumnSpacingFromStrategy(currentTool) };
            
            // 应用控件样式
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
            ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
            ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
            
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, currentTheme.grabRounding);
            
            // 行数
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.row_count"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderInt("##rowCount", rowCount, 1, 20)) {
                updateRowCountInStrategy(currentTool, rowCount[0]);
                LOGGER.debug("行数已更新为: {}", rowCount[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 列数
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.column_count"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderInt("##columnCount", columnCount, 1, 20)) {
                updateColumnCountInStrategy(currentTool, columnCount[0]);
                LOGGER.debug("列数已更新为: {}", columnCount[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 间距
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.row_spacing"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##rowSpacing", rowSpacing, 10.0f, 200.0f, "%.1f")) {
                updateRowSpacingInStrategy(currentTool, rowSpacing[0]);
                LOGGER.debug("行间距已更新为: {}", rowSpacing[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 列间距
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.column_spacing"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##columnSpacing", columnSpacing, 10.0f, 200.0f, "%.1f")) {
                updateColumnSpacingInStrategy(currentTool, columnSpacing[0]);
                LOGGER.debug("列间距已更新为: {}", columnSpacing[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(6);
            
            // 操作行：完成按钮（状态感知）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("");
            ImGui.tableNextColumn();
            
            boolean canConfirm = currentTool.canConfirmArray();
            if (!canConfirm) {
                int disabledColor = ThemeManager.getInstance().getCurrentTheme().disabledBackground;
                ImGui.pushStyleColor(ImGuiCol.Button, disabledColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, disabledColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, disabledColor);
            }
            
            if (ImGui.button(PlotI18n.tr("button.plot.done"), 80, 24)) {
                if (canConfirm) {
                    updateToolConfig("confirm", "true");
                }
            }
            
            if (!canConfirm) {
                ImGui.popStyleColor(3);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PlotI18n.tr("hint.plot.array.select_source_first"));
                }
            } else {
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PlotI18n.tr("hint.plot.array.confirm_rectangular"));
                }
            }
            
            height += ImGui.getFrameHeightWithSpacing();

            ImGui.treePop();
        }
        
        return height;
    }
    
    /**
     * 渲染环形阵列选项
     */
    private float renderCircularArrayOptions(ArrayTool currentTool) {
        float height = 0;
        
        if (ImGui.treeNodeEx(PlotI18n.tr("array.plot.polar_params"), ImGuiTreeNodeFlags.DefaultOpen)) {
            height += 20;
            
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 从新策略获取参数值
            int[] count = { getRowCountFromStrategy(currentTool) };
            float[] radius = { (float) getRadiusFromStrategy(currentTool) };
            
            // 应用控件样式
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
            ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
            ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
            
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, currentTheme.grabRounding);
            
            // 数量滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.count"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderInt("##count", count, 2, 36)) {
                updateRowCountInStrategy(currentTool, count[0]);
                LOGGER.debug("数量已更新为: {}", count[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 半径（可调）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.radius"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##radius", radius, 10.0f, 1000.0f, "%.1f")) {
                updateRadiusInStrategy(currentTool, radius[0]);
                LOGGER.debug("半径已更新为: {}", radius[0]);
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 显示角度间隔（只读）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.angle_interval"));
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            double angleStep = 360.0 / currentTool.getRowCount();
            ImGui.text(String.format("%.1f°", angleStep));
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(6);

            // 操作行：完成按钮（状态感知）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("");
            ImGui.tableNextColumn();
            
            boolean canConfirm = currentTool.canConfirmArray();
            if (!canConfirm) {
                int disabledColor = ThemeManager.getInstance().getCurrentTheme().disabledBackground;
                ImGui.pushStyleColor(ImGuiCol.Button, disabledColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, disabledColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, disabledColor);
            }
            
            if (ImGui.button(PlotI18n.tr("button.plot.done"), 80, 24)) {
                if (canConfirm) {
                    updateToolConfig("confirm", "true");
                }
            }
            
            if (!canConfirm) {
                ImGui.popStyleColor(3);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PlotI18n.tr("hint.plot.array.select_source_first"));
                }
            } else {
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PlotI18n.tr("hint.plot.array.confirm_polar"));
                }
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            ImGui.treePop();
        }
        
        return height;
    }
    
    /**
     * 渲染路径阵列选项
     */
    private float renderPathArrayOptions(ArrayTool currentTool) {
        float height = 0;
        // 不使用折叠：直接两行布局（路径由画布左键选择）
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();

        // 行1：提示
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("");
        ImGui.tableNextColumn();
        ImGui.textWrapped(PlotI18n.tr("hint.plot.array.path_instructions"));
        height += ImGui.getFrameHeightWithSpacing();

        // 行2：数量滑动条
        int[] count = { getPathCountFromStrategy(currentTool) };
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.point_count"));
        ImGui.tableNextColumn();
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, currentTheme.grabRounding);
        ImGui.pushItemWidth(-1);
        if (ImGui.sliderInt("##path_count", count, 2, 20)) {
            updatePathCountInStrategy(currentTool, count[0]);
            LOGGER.debug("数量已更新为: {}", count[0]);
        }
        ImGui.popItemWidth();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(6);
        height += ImGui.getFrameHeightWithSpacing();

        // 行3：路径长度（只读）
        double pathLength = getPathLengthFromStrategy(currentTool);
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.path_length"));
        ImGui.tableNextColumn();
        if (pathLength > 0.0) {
            ImGui.text(String.format("%.2f", pathLength));
        } else {
            ImGui.textDisabled(PlotI18n.tr("hint.plot.array.no_path_picked"));
        }
        height += ImGui.getFrameHeightWithSpacing();

        // 行4：等距步长（只读）
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.step_distance"));
        ImGui.tableNextColumn();
        if (pathLength > 0.0 && count[0] >= 2) {
            double step = pathLength / (count[0] - 1);
            ImGui.text(String.format("%.2f", step));
        } else {
            ImGui.textDisabled("--");
        }
        height += ImGui.getFrameHeightWithSpacing();

        // 行5：完成按钮（状态感知）
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("");
        ImGui.tableNextColumn();
        
        boolean canConfirm = currentTool.canConfirmArray();
        if (!canConfirm) {
            int disabledColor = ThemeManager.getInstance().getCurrentTheme().disabledBackground;
            ImGui.pushStyleColor(ImGuiCol.Button, disabledColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, disabledColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, disabledColor);
        }
        
        if (ImGui.button(PlotI18n.tr("button.plot.done"), 90, 26)) {
            if (canConfirm) {
                updateToolConfig("confirm", "true");
            }
        }
        
        if (!canConfirm) {
            ImGui.popStyleColor(3);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.array.select_source_and_path"));
            }
        } else {
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.array.confirm_path"));
            }
        }
        
        height += ImGui.getFrameHeightWithSpacing();

        return height;
    }

    private double getRadiusFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getRadius();
            }
            return currentTool.getRadius();
        } catch (Exception e) {
            LOGGER.warn("获取半径失败: {}", e.getMessage());
            return 100.0;
        }
    }

    private void updateRadiusInStrategy(ArrayTool currentTool, double radius) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                arrayStrategy.setRadius(radius);
            } else {
                updateToolConfig("radius", String.valueOf(radius));
            }
        } catch (Exception e) {
            LOGGER.warn("更新半径失败: {}", e.getMessage());
        }
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
     * 从策略获取行数
     */
    private int getRowCountFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getRowCount();
            }
            return currentTool.getRowCount();
        } catch (Exception e) {
            LOGGER.warn("获取行数失败: {}", e.getMessage());
            return 2;
        }
    }

    /**
     * 从策略获取列数
     */
    private int getColumnCountFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getColumnCount();
            }
            return currentTool.getColumnCount();
        } catch (Exception e) {
            LOGGER.warn("获取列数失败: {}", e.getMessage());
            return 2;
        }
    }

    /**
     * 从策略获取行间距
     */
    private double getRowSpacingFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getRowSpacing();
            }
            return currentTool.getRowSpacing();
        } catch (Exception e) {
            LOGGER.warn("获取行间距失败: {}", e.getMessage());
            return 50.0;
        }
    }

    /**
     * 从策略获取列间距
     */
    private double getColumnSpacingFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getColumnSpacing();
            }
            return currentTool.getColumnSpacing();
        } catch (Exception e) {
            LOGGER.warn("获取列间距失败: {}", e.getMessage());
            return 50.0;
        }
    }

    /**
     * 从策略获取路径数量
     */
    private int getPathCountFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getPathCount();
            }
            return currentTool.getRowCount(); // 回退到行数
        } catch (Exception e) {
            LOGGER.warn("获取路径数量失败: {}", e.getMessage());
            return 10;
        }
    }

    private double getPathLengthFromStrategy(ArrayTool currentTool) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getCurrentPathLength();
            }
        } catch (Exception e) {
            LOGGER.warn("获取路径长度失败: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * 更新策略中的行数
     */
    private void updateRowCountInStrategy(ArrayTool currentTool, int rowCount) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                arrayStrategy.setRowCount(rowCount);
            } else {
                updateToolConfig(CONFIG_KEY_ROW_COUNT, String.valueOf(rowCount));
            }
        } catch (Exception e) {
            LOGGER.warn("更新行数失败: {}", e.getMessage());
        }
    }

    /**
     * 更新策略中的列数
     */
    private void updateColumnCountInStrategy(ArrayTool currentTool, int columnCount) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                arrayStrategy.setColumnCount(columnCount);
            } else {
                updateToolConfig(CONFIG_KEY_COLUMN_COUNT, String.valueOf(columnCount));
            }
        } catch (Exception e) {
            LOGGER.warn("更新列数失败: {}", e.getMessage());
        }
    }

    /**
     * 更新策略中的行间距
     */
    private void updateRowSpacingInStrategy(ArrayTool currentTool, double rowSpacing) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                arrayStrategy.setRowSpacing(rowSpacing);
            } else {
                updateToolConfig("rowSpacing", String.valueOf(rowSpacing));
            }
        } catch (Exception e) {
            LOGGER.warn("更新行间距失败: {}", e.getMessage());
        }
    }

    /**
     * 更新策略中的列间距
     */
    private void updateColumnSpacingInStrategy(ArrayTool currentTool, double columnSpacing) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                arrayStrategy.setColumnSpacing(columnSpacing);
            } else {
                updateToolConfig("columnSpacing", String.valueOf(columnSpacing));
            }
        } catch (Exception e) {
            LOGGER.warn("更新列间距失败: {}", e.getMessage());
        }
    }

    /**
     * 更新策略中的路径数量
     */
    private void updatePathCountInStrategy(ArrayTool currentTool, int pathCount) {
        try {
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                arrayStrategy.setPathCount(pathCount);
            } else {
                updateToolConfig(CONFIG_KEY_ROW_COUNT, String.valueOf(pathCount));
            }
        } catch (Exception e) {
            LOGGER.warn("更新路径数量失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前阵列类型名称（兼容新旧策略）
     */
    private String getCurrentArrayTypeName(ArrayTool currentTool) {
        try {
            // 尝试从新的ArrayWithSelectionStrategy获取
            if (currentTool.getModifyStrategy() instanceof ArrayWithSelectionStrategy arrayStrategy) {
                return arrayStrategy.getArrayType().name();
            }
            
            // 回退到旧的ArrayStrategy
            if (currentTool.getArrayType() != null) {
                return currentTool.getArrayType().name();
            }
            
            // 默认返回矩形阵列
            return "RECTANGULAR";
        } catch (Exception e) {
            LOGGER.warn("获取阵列类型失败: {}", e.getMessage());
            return "RECTANGULAR";
        }
    }

    /**
     * 从上下文获取当前工具
     * 优先从ToolOptionsPanel传入的工具参数获取，如果没有则从ToolManager获取
     */
    private ArrayTool getCurrentToolFromContext() {
        try {
            // 使用AppState获取当前工具，与其他渲染器保持一致
            com.plot.core.state.AppState appState = com.plot.core.state.AppState.getInstance();
            com.plot.core.tool.BaseTool currentTool = appState.getCurrentTool();
            
            LOGGER.debug("AppState获取到的当前工具: {}", 
                currentTool != null ? currentTool.getClass().getSimpleName() : "null");
            
            if (currentTool instanceof ArrayTool) {
                LOGGER.debug("当前工具是ArrayTool，返回成功");
                return (ArrayTool) currentTool;
            } else {
                LOGGER.debug("当前工具不是ArrayTool，类型: {}", 
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
        LOGGER.debug("ArrayToolOptionRenderer 已初始化（单向数据流模式）");
    }
    
    @Override
    public void cleanup() {
        // 释放纹理资源
        if (resourcesInitialized) {
            try {
                ImGuiUtils.deleteTexture(rectangularArrayIconId);
                ImGuiUtils.deleteTexture(circularArrayIconId);
                ImGuiUtils.deleteTexture(pathArrayIconId);
                resourcesInitialized = false;
                LOGGER.debug("阵列工具选项渲染器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("释放阵列工具选项渲染器资源失败: {}", e.getMessage());
            }
        }
    }
} 