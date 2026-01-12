package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.TransformTool;
import com.masterplanner.ui.tools.impl.modify.enums.TransformMode;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 变换工具选项渲染器
 * 
 * <p>负责渲染变换工具的选项面板，包括：</p>
 * <ul>
 *   <li>变换模式选择（自由、水平、垂直）</li>
 *   <li>中心缩放开关</li>
 *   <li>旋转功能开关</li>
 *   <li>数值输入开关</li>
 *   <li>实时状态显示</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 变换工具选项渲染器
 */
public class TransformToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformToolOptionRenderer.class);
    
    // 渲染常量
    private static final float BUTTON_HEIGHT = 32.0f;
    
    // 颜色常量
    private static final int PRIMARY_COLOR = 0xFF4A90E2;
    
    // 工具引用
    private final TransformTool transformTool;
    
    // 状态缓存
    private String currentMode = TransformMode.FREE.getValue();
    private boolean centerScaleEnabled = false;
    private boolean rotationEnabled = true;
    private boolean numericInputEnabled = true;
    
    // 数值输入缓存
    private final float[] scaleXArray = {1.0f};
    private final float[] scaleYArray = {1.0f};
    private final float[] rotationAngleArray = {0.0f};
    private final float[] moveXArray = {0.0f};
    private final float[] moveYArray = {0.0f};
    
    // 精度设置
    private final float[] stepSizeArray = {1.0f};
    private boolean snapToGrid = false;
    private boolean maintainAspectRatio = true;
    
    // 高级选项
    private boolean showTransformCenter = false;
    private boolean showReferencePoints = false;
    
    // 模式选项
    private static final String[] MODE_OPTIONS = {
        TransformMode.FREE.getDisplayName(),
        TransformMode.HORIZONTAL.getDisplayName(),
        TransformMode.VERTICAL.getDisplayName()
    };
    
    private static final String[] MODE_VALUES = {
        TransformMode.FREE.getValue(),
        TransformMode.HORIZONTAL.getValue(),
        TransformMode.VERTICAL.getValue()
    };
    
    public TransformToolOptionRenderer(TransformTool transformTool) {
        super("transform");
        this.transformTool = transformTool;
        LOGGER.debug("变换工具选项渲染器已初始化");
    }
    
    /**
     * 渲染变换工具选项面板
     */
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("transform_options");
        
        try {
            if (transformTool == null) {
                LOGGER.warn("变换工具为空，无法渲染选项面板");
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.text("请选择变换工具");
                return ImGui.getFrameHeightWithSpacing();
            }
            
            // 更新状态缓存
            updateStateCache();
            
            // 渲染模式选择
            height += renderModeSelection();
            
            // 渲染功能开关
            height += renderFeatureToggles();
            
            // 渲染数值输入控件
            height += renderNumericInputs();
            
            // 渲染精度设置
            height += renderPrecisionSettings();
            
            // 渲染高级选项
            height += renderAdvancedOptions();
            
            // 渲染使用说明
            height += renderUsageInstructions();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染模式选择
     */
    private float renderModeSelection() {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("变换模式");
        
        ImGui.tableNextColumn();
        
        // 模式选择按钮组
        for (int i = 0; i < MODE_OPTIONS.length; i++) {
            boolean isSelected = MODE_VALUES[i].equals(currentMode);
            
            if (isSelected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PRIMARY_COLOR);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, PRIMARY_COLOR | 0x20000000);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, PRIMARY_COLOR | 0x40000000);
            }
            
            if (ImGui.button(MODE_OPTIONS[i], ImGui.getContentRegionAvailX() / MODE_OPTIONS.length - 4, BUTTON_HEIGHT)) {
                if (!isSelected) {
                    updateMode(MODE_VALUES[i]);
                }
            }
            
            if (isSelected) {
                ImGui.popStyleColor(3);
            }
            
            if (i < MODE_OPTIONS.length - 1) {
                ImGui.sameLine();
            }
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        
        // 模式描述
        String modeDescription = getModeDescription(currentMode);
        if (modeDescription != null) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.tableNextColumn();
            ImGui.textWrapped(modeDescription);
            height += ImGui.getFrameHeightWithSpacing();
        }
        
        return height;
    }
    
    /**
     * 渲染功能开关
     */
    private float renderFeatureToggles() {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("功能设置");
        
        ImGui.tableNextColumn();
        
        // 中心缩放开关
        boolean newCenterScale = ImGui.checkbox("中心缩放 (Alt)", centerScaleEnabled);
        if (newCenterScale != centerScaleEnabled) {
            centerScaleEnabled = newCenterScale;
            transformTool.setCenterScaleEnabled(centerScaleEnabled);
            LOGGER.debug("中心缩放已{}", centerScaleEnabled ? "启用" : "禁用");
        }
        
        ImGui.sameLine();
        
        // 旋转功能开关
        boolean newRotation = ImGui.checkbox("旋转功能", rotationEnabled);
        if (newRotation != rotationEnabled) {
            rotationEnabled = newRotation;
            transformTool.setRotationEnabled(rotationEnabled);
            LOGGER.debug("旋转功能已{}", rotationEnabled ? "启用" : "禁用");
        }
        
        ImGui.sameLine();
        
        // 数值输入开关
        boolean newNumericInput = ImGui.checkbox("数值输入", numericInputEnabled);
        if (newNumericInput != numericInputEnabled) {
            numericInputEnabled = newNumericInput;
            transformTool.setNumericInputEnabled(numericInputEnabled);
            LOGGER.debug("数值输入已{}", numericInputEnabled ? "启用" : "禁用");
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        return height;
    }
    
    /**
     * 渲染数值输入控件
     */
    private float renderNumericInputs() {
        if (!numericInputEnabled) {
            return 0;
        }
        
        float height = 0;
        
        // 缩放比例
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("缩放比例");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        
        if (ImGui.dragFloat("X##scale_x", scaleXArray, 0.01f, 0.1f, 10.0f, "%.2f")) {
            updateNumericInput("scale_x", scaleXArray[0]);
        }
        
        ImGui.sameLine();
        
        if (ImGui.dragFloat("Y##scale_y", scaleYArray, 0.01f, 0.1f, 10.0f, "%.2f")) {
            updateNumericInput("scale_y", scaleYArray[0]);
        }
        
        ImGui.popItemWidth();
        height += ImGui.getFrameHeightWithSpacing();
        
        // 旋转角度
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("旋转角度");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.3f);
        
        if (ImGui.dragFloat("##rotation", rotationAngleArray, 1.0f, -360.0f, 360.0f, "%.1f°")) {
            updateNumericInput("rotation", rotationAngleArray[0]);
        }
        
        ImGui.sameLine();
        
        // 预设角度按钮
        if (ImGui.button("0°", 40, 0)) {
            rotationAngleArray[0] = 0.0f;
            updateNumericInput("rotation", 0.0f);
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("90°", 40, 0)) {
            rotationAngleArray[0] = 90.0f;
            updateNumericInput("rotation", 90.0f);
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("180°", 40, 0)) {
            rotationAngleArray[0] = 180.0f;
            updateNumericInput("rotation", 180.0f);
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("270°", 40, 0)) {
            rotationAngleArray[0] = 270.0f;
            updateNumericInput("rotation", 270.0f);
        }
        
        ImGui.popItemWidth();
        height += ImGui.getFrameHeightWithSpacing();
        
        // 移动距离
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("移动距离");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        
        if (ImGui.dragFloat("X##move_x", moveXArray, 1.0f, -1000.0f, 1000.0f, "%.1f")) {
            updateNumericInput("move_x", moveXArray[0]);
        }
        
        ImGui.sameLine();
        
        if (ImGui.dragFloat("Y##move_y", moveYArray, 1.0f, -1000.0f, 1000.0f, "%.1f")) {
            updateNumericInput("move_y", moveYArray[0]);
        }
        
        ImGui.popItemWidth();
        height += ImGui.getFrameHeightWithSpacing();
        
        return height;
    }
    
    /**
     * 渲染精度设置
     */
    private float renderPrecisionSettings() {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("精度设置");
        
        ImGui.tableNextColumn();
        
        // 步长设置
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.3f);
        
        if (ImGui.dragFloat("步长##step_size", stepSizeArray, 0.1f, 0.1f, 100.0f, "%.1f")) {
            updateNumericInput("step_size", stepSizeArray[0]);
        }
        
        ImGui.sameLine();
        
        // 对齐网格
        boolean newSnapToGrid = ImGui.checkbox("对齐网格", snapToGrid);
        if (newSnapToGrid != snapToGrid) {
            snapToGrid = newSnapToGrid;
            updateBooleanInput("snap_to_grid", snapToGrid);
        }
        
        ImGui.sameLine();
        
        // 保持比例
        boolean newMaintainAspectRatio = ImGui.checkbox("保持比例", maintainAspectRatio);
        if (newMaintainAspectRatio != maintainAspectRatio) {
            maintainAspectRatio = newMaintainAspectRatio;
            updateBooleanInput("maintain_aspect_ratio", maintainAspectRatio);
        }
        
        ImGui.popItemWidth();
        height += ImGui.getFrameHeightWithSpacing();
        
        return height;
    }
    
    /**
     * 渲染高级选项
     */
    private float renderAdvancedOptions() {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("高级选项");
        
        ImGui.tableNextColumn();
        
        // 显示变换中心
        boolean newShowTransformCenter = ImGui.checkbox("显示变换中心", showTransformCenter);
        if (newShowTransformCenter != showTransformCenter) {
            showTransformCenter = newShowTransformCenter;
            updateBooleanInput("show_transform_center", showTransformCenter);
        }
        
        ImGui.sameLine();
        
        // 显示参考点
        boolean newShowReferencePoints = ImGui.checkbox("显示参考点", showReferencePoints);
        if (newShowReferencePoints != showReferencePoints) {
            showReferencePoints = newShowReferencePoints;
            updateBooleanInput("show_reference_points", showReferencePoints);
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        return height;
    }
    
    
    /**
     * 更新数值输入
     */
    private void updateNumericInput(String key, float value) {
        if (transformTool != null) {
            transformTool.updateConfig(key, String.valueOf(value));
            LOGGER.debug("数值输入已更新: {} = {}", key, value);
        }
    }
    
    /**
     * 更新布尔输入
     */
    private void updateBooleanInput(String key, boolean value) {
        if (transformTool != null) {
            transformTool.updateConfig(key, String.valueOf(value));
            LOGGER.debug("布尔输入已更新: {} = {}", key, value);
        }
    }
    
    /**
     * 更新状态缓存
     */
    private void updateStateCache() {
        currentMode = transformTool.getMode();
        centerScaleEnabled = transformTool.isCenterScaleEnabled();
        rotationEnabled = transformTool.isRotationEnabled();
        numericInputEnabled = transformTool.isNumericInputEnabled();
    }
    
    /**
     * 更新变换模式
     */
    private void updateMode(String mode) {
        if (mode != null && !mode.equals(currentMode)) {
            transformTool.updateConfig(TransformTool.CONFIG_KEY_MODE, mode);
            currentMode = mode;
            LOGGER.debug("变换模式已更新: {}", mode);
        }
    }

    /**
     * 获取模式描述
     */
    private String getModeDescription(String mode) {
        if (TransformMode.FREE.getValue().equals(mode)) {
            return "允许在所有方向进行自由变换";
        } else if (TransformMode.HORIZONTAL.getValue().equals(mode)) {
            return "仅在水平方向进行变换";
        } else if (TransformMode.VERTICAL.getValue().equals(mode)) {
            return "仅在垂直方向进行变换";
        }
        return null;
    }
    
    
    /**
     * 渲染使用说明
     */
    private float renderUsageInstructions() {
        float height = 0;
        
        if (ImGui.collapsingHeader("使用说明", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("变换工具使用步骤：");
            ImGui.spacing();
            
            ImGui.bulletText("1. 使用选择工具选择要变换的图形");
            ImGui.bulletText("2. 切换到变换工具");
            ImGui.bulletText("3. 拖拽包围盒的控制点进行变换");
            ImGui.bulletText("4. 使用模式按钮切换变换模式");
            ImGui.bulletText("5. 启用/禁用中心缩放、旋转等功能");
            ImGui.bulletText("6. 使用数值输入进行精确变换");
            
            ImGui.spacing();
            ImGui.textWrapped("变换模式说明：");
            ImGui.bulletText("自由变换：允许在所有方向进行变换");
            ImGui.bulletText("水平变换：只允许水平方向变换");
            ImGui.bulletText("垂直变换：只允许垂直方向变换");
            
            ImGui.spacing();
            ImGui.textWrapped("功能说明：");
            ImGui.bulletText("中心缩放：按住Alt键从中心点缩放");
            ImGui.bulletText("旋转功能：角点外侧拖拽进行旋转");
            ImGui.bulletText("数值输入：使用精确的数值进行变换");
            
            height += ImGui.getFrameHeightWithSpacing() * 8; // 估算高度
        }
        
        if (ImGui.collapsingHeader("快捷键", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textColored(0.9f, 0.6f, 0.3f, 1.0f, "快捷键提示：");
            ImGui.spacing();
            
            ImGui.bulletText("Alt: 中心缩放（拖拽时）");
            ImGui.bulletText("Shift: 保持比例缩放");
            ImGui.bulletText("Ctrl: 精确对齐");
            ImGui.bulletText("右键: 切换模式 / 完成操作");
            ImGui.bulletText("Esc: 取消操作 / 返回选择");
            
            ImGui.spacing();
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "提示：");
            ImGui.textWrapped("""
                    • 变换时会显示实时预览效果
                    • 数值输入提供精确的变换控制
                    • 精度设置可以控制变换的步长和对齐""");
            
            height += ImGui.getFrameHeightWithSpacing() * 6; // 估算高度
        }
        
        return height;
    }
    
    /**
     * 初始化工具选项
     */
    @Override
    public void initialize() {
        LOGGER.debug("初始化变换工具选项");
        updateStateCache();
    }
    
    /**
     * 清理资源
     */
    @Override
    public void cleanup() {
        LOGGER.debug("变换工具选项渲染器资源已清理");
    }
    
}