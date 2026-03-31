package com.plot.ui.panel.tool.renderer;

import com.plot.core.geometry.shapes.SpiralType;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.tools.impl.drawing.SpiralTool;
import com.plot.ui.theme.UITheme;
import com.plot.ui.theme.ThemeManager;
import com.plot.utils.ImGuiUtils;
import net.minecraft.util.Identifier;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 螺旋线工具选项渲染器 - 重构版本
 * 
 * <p>采用单一数据源模式，直接从SpiralTool获取状态，消除复杂的同步逻辑</p>
 * 
 * @author Plot Team
 * @version 2.0 - 单一数据源重构版本
 */
public class SpiralToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralToolOptionRenderer.class);
    
    // ====== 配置键常量 ======
    
    private static final String CONFIG_KEY_TYPE = "type";
    private static final String CONFIG_KEY_SPACING = "spacing";
    private static final String CONFIG_KEY_GROWTH_FACTOR = "growthFactor";
    private static final String CONFIG_KEY_SIDES = "sides";
    private static final String CONFIG_KEY_SHARP_EDGED = "sharpEdged";
    private static final String CONFIG_KEY_EXPANSION_RATE = "expansionRate";
    private static final String CONFIG_KEY_SPIRAL_COEFFICIENT = "spiralCoefficient";
    private static final String CONFIG_KEY_CLOCKWISE = "clockwise";
    
    // ====== 图标资源 ======
    
    private final int spiralLinearIconId;
    private final int spiralDecayIconId;
    private final int spiralSemicircleIconId;
    private final int spiralFermatIconId;
    private final int spiralFibonacciIconId;
    private final int spiralPolygonIconId;
    
    // ====== 临时UI状态 ======
    
    // 注意：这些数组仅用于ImGui API要求，不存储实际状态
    private final float[] tempSpacingArray = {0.0f};
    private final float[] tempGrowthFactorArray = {0.0f};
    private final int[] tempSidesArray = {6};
    private final float[] tempStartRadiusArray = {0.0f};
    private final float[] tempTurnsArray = {3.0f};
    private final float[] tempExpansionRateArray = {0.0f};
    private final float[] tempSpiralCoefficientArray = {0.5f};
    private final ImBoolean tempSharpEdged = new ImBoolean(false);
    private final ImBoolean tempClockwise = new ImBoolean(true);
    
    // ====== 图标加载错误处理 ======
    
    private static final String DEFAULT_ICON_PATH = "textures/gui/tooloptionspanel/default.png";
    
    /**
     * 安全加载纹理图标，包含错误处理和回退机制
     */
    private int loadTexture(String path) {
        try {
            return ImGuiUtils.getTextureId(Identifier.of("plot", path));
        } catch (Exception e) {
            LOGGER.error("加载图标失败: {}, 使用默认图标", path, e);
            try {
                return ImGuiUtils.getTextureId(Identifier.of("plot", DEFAULT_ICON_PATH));
            } catch (Exception fallbackException) {
                LOGGER.error("默认图标也加载失败: {}", DEFAULT_ICON_PATH, fallbackException);
                return -1; // 返回无效纹理ID
            }
        }
    }
    
    // ====== 构造函数 ======
    
    public SpiralToolOptionRenderer() {
        super("spiral"); // 调用父类构造函数，传入螺旋线工具的ID
        
        // 加载图标资源
        this.spiralLinearIconId = loadTexture("textures/gui/tooloptionspanel/spiral_linear.png");
        this.spiralDecayIconId = loadTexture("textures/gui/tooloptionspanel/spiral_decay.png");
        this.spiralSemicircleIconId = loadTexture("textures/gui/tooloptionspanel/spiral_semicircle.png");
        this.spiralFermatIconId = loadTexture("textures/gui/tooloptionspanel/spiral_fermat.png");
        this.spiralFibonacciIconId = loadTexture("textures/gui/tooloptionspanel/spiral_fibonacci.png");
        this.spiralPolygonIconId = loadTexture("textures/gui/tooloptionspanel/spiral_polygon.png");
        
        LOGGER.debug("SpiralToolOptionRenderer: 初始化完成，图标资源加载成功");
    }
    
    // ====== 私有方法 ======
    
    /**
     * 渲染尖角样式复选框
     */
    private void renderSharpEdgedCheckbox(UITheme.ThemeColors theme, float[] height) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("尖角样式");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        
        // 设置复选框样式，参考OffsetTool的实现
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 4.0f);
        
        if (ImGui.checkbox("##sharpEdged", tempSharpEdged)) {
            updateToolConfig(CONFIG_KEY_SHARP_EDGED, String.valueOf(tempSharpEdged.get()));
        }
        
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(5);
        ImGui.popItemWidth();
        height[0] += ImGui.getFrameHeightWithSpacing();
    }

    /**
     * 通用浮点数滑块渲染方法
     */
    private void renderFloatSlider(String label, String configKey, float[] value, float min, float max, String format, float[] heightAccumulator) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        if (ImGui.sliderFloat("##" + configKey, value, min, max, format)) {
            updateToolConfig(configKey, String.valueOf(value[0]));
        }
        ImGui.popItemWidth();
        heightAccumulator[0] += ImGui.getFrameHeightWithSpacing();
    }
    
    /**
     * 通用整数滑块渲染方法
     */
    private void renderIntSlider(int[] value, float[] heightAccumulator) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("边数");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        if (ImGui.sliderInt("##" + SpiralToolOptionRenderer.CONFIG_KEY_SIDES, value, 3, 32, "%d")) {
            updateToolConfig(SpiralToolOptionRenderer.CONFIG_KEY_SIDES, String.valueOf(value[0]));
        }
        ImGui.popItemWidth();
        heightAccumulator[0] += ImGui.getFrameHeightWithSpacing();
    }
    
    /**
     * 通用复选框渲染方法
     */
    private void renderCheckbox(ImBoolean value, float[] heightAccumulator) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("逆时针");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        
        // 设置复选框样式，参考OffsetTool的实现
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, currentTheme.accent);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 4.0f);
        
        if (ImGui.checkbox("##" + SpiralToolOptionRenderer.CONFIG_KEY_CLOCKWISE, value)) {
            // 复选框勾选表示逆时针，所以需要取反
            boolean clockwiseValue = !value.get();
            updateToolConfig(SpiralToolOptionRenderer.CONFIG_KEY_CLOCKWISE, String.valueOf(clockwiseValue));
        }
        
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(5);
        ImGui.popItemWidth();
        heightAccumulator[0] += ImGui.getFrameHeightWithSpacing();
    }
    
    /**
     * 获取当前螺旋线工具实例
     */
    private SpiralTool getCurrentSpiralTool() {
        var appState = AppState.getInstance();
        if (appState == null) {
            LOGGER.warn("AppState实例不存在");
            return null;
        }
        
        var currentTool = appState.getCurrentTool();
        if (!(currentTool instanceof SpiralTool)) {
            LOGGER.debug("当前工具不是SpiralTool: {}", currentTool != null ? currentTool.getClass().getSimpleName() : "null");
            return null;
        }
        
        return (SpiralTool) currentTool;
    }
    
    /**
     * 同步临时数组状态
     */
    private void syncTempArraysFromTool(SpiralTool spiralTool) {
        if (spiralTool == null) return;
        
        float oldGrowthFactor = tempGrowthFactorArray[0];
        tempSpacingArray[0] = spiralTool.getSpacing();
        tempGrowthFactorArray[0] = spiralTool.getGrowthFactor();
        tempSidesArray[0] = spiralTool.getSides();
        tempStartRadiusArray[0] = spiralTool.getStartRadius();
        tempTurnsArray[0] = spiralTool.getTurns();
        tempExpansionRateArray[0] = spiralTool.getExpansionRate();
        tempSpiralCoefficientArray[0] = spiralTool.getSpiralCoefficient();
        tempSharpEdged.set(spiralTool.isSharpEdged());
        // 复选框显示"逆时针"，所以需要取反：顺时针时复选框不勾选，逆时针时复选框勾选
        tempClockwise.set(!spiralTool.isClockwise());
        
        // 添加调试日志，检查生长因子是否发生变化
        if (Math.abs(oldGrowthFactor - tempGrowthFactorArray[0]) > 0.001f) {
            LOGGER.debug("SpiralToolOptionRenderer: 生长因子同步更新 {} -> {}", 
                oldGrowthFactor, tempGrowthFactorArray[0]);
        }
        
        LOGGER.debug("已从SpiralTool同步临时数组状态，生长因子={}, 螺距={}", tempGrowthFactorArray[0], tempSpacingArray[0]);
    }
    
    // ====== 公共方法 ======
    
    @Override
    public float render() {
        // 获取当前螺旋线工具实例
        SpiralTool spiralTool = getCurrentSpiralTool();
        if (spiralTool == null) {
            // 如果当前工具不是SpiralTool，显示提示信息
            ImGui.text("请选择螺旋线工具");
            return ImGui.getFrameHeightWithSpacing();
        }
        
        // 同步临时数组状态
        syncTempArraysFromTool(spiralTool);
        
        float[] height = {0};
        
        // 渲染螺旋类型选择
        renderSpiralTypeSelector(spiralTool, height);
        
        // 根据当前类型渲染特定选项
        SpiralType currentType = spiralTool.getCurrentType();
        switch (currentType) {
            case LINEAR -> renderLinearSpiralOptions(height);
            case LOGARITHMIC -> renderLogarithmicSpiralOptions(spiralTool, height);
            case SEMICIRCLE -> renderSemicircleSpiralOptions(spiralTool, height);
            case FERMAT -> renderFermatSpiralOptions(spiralTool, height);
            case FIBONACCI -> renderFibonacciSpiralOptions(spiralTool, height);
            case POLYGON -> renderPolygonSpiralOptions(spiralTool, height);
        }
        
        // 渲染通用选项
        renderCommonOptions(spiralTool, height);
        
        return height[0];
    }
    
    /**
     * 渲染螺旋类型选择器
     */
    private void renderSpiralTypeSelector(SpiralTool spiralTool, float[] height) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("螺旋类型");
        
        // 获取当前主题
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
        
        // 设置按钮颜色样式
        ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        
        // 设置边框样式
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        
        ImGui.tableNextColumn();
        float firstButtonX = ImGui.getCursorPosX();
        
        // 定义螺旋类型按钮
        SpiralType[] types = {
            SpiralType.LINEAR, 
            SpiralType.LOGARITHMIC, 
            SpiralType.SEMICIRCLE, 
            SpiralType.FERMAT, 
            SpiralType.FIBONACCI, 
            SpiralType.POLYGON
        };
        
        int[] icons = {
            spiralLinearIconId, 
            spiralDecayIconId, 
            spiralSemicircleIconId, 
            spiralFermatIconId, 
            spiralFibonacciIconId, 
            spiralPolygonIconId
        };
        
        SpiralType currentType = spiralTool.getCurrentType();
        
        // 渲染6个螺旋类型按钮
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                if (i % 3 == 0) {
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    ImGui.tableNextColumn();
                } else {
                    ImGui.sameLine(0, BUTTON_SPACING);
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING) * (i % 3));
                }
            }
            
            boolean isSelected = currentType == types[i];
            
            // 为选中的按钮应用特殊样式
            if (isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
            }
            
            ImGui.pushID("spiral_type_" + i);
            boolean clicked;
            try {
                // 检查纹理ID是否有效
                if (icons[i] != -1) {
                    clicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                } else {
                    // 纹理加载失败，使用文本按钮作为回退
                    clicked = ImGui.button(types[i].getDisplayName());
                }
            } catch (Exception e) {
                LOGGER.warn("渲染螺旋类型按钮失败: {}", e.getMessage());
                // 使用文本按钮作为回退
                clicked = ImGui.button(types[i].getDisplayName());
            }
            ImGui.popID();
            
            if (clicked && !isSelected) {
                LOGGER.info("SpiralToolOptionRenderer: 用户点击螺旋类型按钮，切换 {} -> {}", currentType, types[i]);
                updateToolConfig(CONFIG_KEY_TYPE, types[i].name());
            }
            
            // 恢复按钮样式（如果之前设置了选中样式）
            if (isSelected) {
                ImGui.popStyleColor(4);
            }
            
            // 添加工具提示
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(types[i].getDisplayName());
            }
        }
        
        // 恢复样式
        ImGui.popStyleVar();
        ImGui.popStyleColor(4);
        ImGui.popStyleVar();
        
        height[0] += (BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2) * 2;
    }
    
    /**
     * 渲染线性螺旋选项
     */
    private void renderLinearSpiralOptions(float[] height) {
        // 线性螺旋的使用方法由ToolOptionsPanel统一展示；这里仅显示状态信息
        renderCurrentParametersInfo(height);
    }
    
    /**
     * 渲染当前参数信息（只读显示）
     */
    private void renderCurrentParametersInfo(float[] height) {
        SpiralTool spiralTool = getCurrentSpiralTool();
        if (spiralTool == null) return;
        
        // 获取控制点信息
        var controlPoints = spiralTool.getControlPoints();
        if (!controlPoints.isEmpty()) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("当前状态");
            
            ImGui.tableNextColumn();
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - 10);
            var theme = ThemeManager.getInstance().getCurrentTheme();
            
            if (!controlPoints.isEmpty()) {
                ImGui.textColored(theme.successText, "✓ 已确定中心点");
            }
            if (controlPoints.size() >= 2) {
                ImGui.textColored(theme.successText, "✓ 已确定螺旋起点");
            }
            if (controlPoints.size() >= 3) {
                ImGui.textColored(theme.successText, "✓ 已确定螺距");
            }
            if (controlPoints.size() >= 4) {
                ImGui.textColored(theme.successText, "✓ 已确定最外圈");
            }
            
            ImGui.popTextWrapPos();
            height[0] += ImGui.getTextLineHeight() * controlPoints.size() + ImGui.getStyle().getItemSpacing().y;
        }
    }
    
    /**
     * 渲染对数螺旋选项
     */
    private void renderLogarithmicSpiralOptions(SpiralTool spiralTool, float[] height) {
        // 对数螺旋的起始半径由用户点击的第一点和第二点之间的距离决定，不需要滑动条
        renderFloatSlider("生长因子", CONFIG_KEY_GROWTH_FACTOR, tempGrowthFactorArray, 0.1f, 0.99f, "%.2f", height);
        renderCheckbox(tempClockwise, height);
    }
    
    /**
     * 渲染半圆螺旋选项
     */
    private void renderSemicircleSpiralOptions(SpiralTool spiralTool, float[] height) {
        // 半圆螺旋的起始半径由用户点击的第一点和第二点之间的距离决定，不需要滑动条
        renderFloatSlider("扩张率", CONFIG_KEY_EXPANSION_RATE, tempExpansionRateArray, 0.0f, 5.0f, "%.2f", height);
        renderCheckbox(tempClockwise, height);
    }
    
    /**
     * 渲染费马螺旋选项
     */
    private void renderFermatSpiralOptions(SpiralTool spiralTool, float[] height) {
        renderFloatSlider("螺旋系数", CONFIG_KEY_SPIRAL_COEFFICIENT, tempSpiralCoefficientArray, 0.5f, 8.0f, "%.2f", height);
        renderCheckbox(tempClockwise, height);
    }
    
    /**
     * 渲染斐波那契螺旋选项
     */
    private void renderFibonacciSpiralOptions(SpiralTool spiralTool, float[] height) {
        // 斐波那契螺旋的起始半径由用户点击的第一点和第二点之间的距离决定，不需要滑动条
        renderFloatSlider("螺旋系数", CONFIG_KEY_SPIRAL_COEFFICIENT, tempSpiralCoefficientArray, 0.1f, 5.0f, "%.2f", height);
        renderCheckbox(tempClockwise, height);
    }
    
    /**
     * 渲染多边形螺旋选项
     */
    private void renderPolygonSpiralOptions(SpiralTool spiralTool, float[] height) {
        renderIntSlider(tempSidesArray, height);
        // 多边形螺旋的起始半径由用户第二次点击定义，工具面板不显示起始半径滑动条
        renderCheckbox(tempClockwise, height);
    }
    
    /**
     * 渲染通用选项
     */
    private void renderCommonOptions(SpiralTool spiralTool, float[] height) {
        renderSharpEdgedCheckbox(ThemeManager.getInstance().getCurrentTheme(), height);
        
        // 仅在需要螺距参数的类型下显示滑块：线性与多边形
        SpiralType currentType = spiralTool.getCurrentType();
        if (currentType == SpiralType.LINEAR || currentType == SpiralType.POLYGON) {
            renderFloatSlider("螺距", CONFIG_KEY_SPACING, tempSpacingArray, 10.0f, 200.0f, "%.1f", height);
        }
    }
    
    @Override
    protected void updateToolConfig(String key, String value) {
        LOGGER.debug("SpiralToolOptionRenderer: 更新配置 key={}, value={}", key, value);
        
        try {
            // 发送配置更新事件
            EventBus.getInstance().publish(new ToolConfigEvent("spiral", key, null, value));
        } catch (Exception e) {
            LOGGER.error("发送配置更新事件失败: key={}, value={}, error={}", key, value, e.getMessage(), e);
        }
    }
    
    @Override
    public void initialize() {
        LOGGER.debug("SpiralToolOptionRenderer: 初始化完成");
    }
    
    @Override
    public void cleanup() {
        // 清理纹理资源
        try {
            if (spiralLinearIconId != -1) {
                ImGuiUtils.deleteTexture(spiralLinearIconId);
            }
            if (spiralDecayIconId != -1) {
                ImGuiUtils.deleteTexture(spiralDecayIconId);
            }
            if (spiralSemicircleIconId != -1) {
                ImGuiUtils.deleteTexture(spiralSemicircleIconId);
            }
            if (spiralFermatIconId != -1) {
                ImGuiUtils.deleteTexture(spiralFermatIconId);
            }
            if (spiralFibonacciIconId != -1) {
                ImGuiUtils.deleteTexture(spiralFibonacciIconId);
            }
            if (spiralPolygonIconId != -1) {
                ImGuiUtils.deleteTexture(spiralPolygonIconId);
            }
            LOGGER.debug("SpiralToolOptionRenderer: 纹理资源清理完成");
        } catch (Exception e) {
            LOGGER.warn("清理纹理资源时发生错误: {}", e.getMessage());
        }
        
        LOGGER.debug("SpiralToolOptionRenderer: 清理完成");
    }
} 