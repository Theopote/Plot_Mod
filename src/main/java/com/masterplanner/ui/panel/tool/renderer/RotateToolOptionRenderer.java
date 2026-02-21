package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.RotateTool;
import com.masterplanner.ui.tools.impl.modify.strategy.RotateStrategy;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import com.masterplanner.MasterPlannerMod;

/**
 * 旋转工具属性面板渲染器
 */
public class RotateToolOptionRenderer extends AbstractToolOptionRenderer {
    
    // 配置键常量
    private static final String CONFIG_KEY_ANGLE_STEP = "angleStep";
    private static final String CONFIG_KEY_SNAP_ANGLE = "snapAngle";
    // 已移除：复制模式和增强吸附的显式开关，复制通过 Ctrl 临时开启
    
    // 当前配置状态 - 使用ImBoolean以支持ImGui的引用传递
    private final int[] angleStep = {15}; // 改为整数数组
    private final ImBoolean snapToAngle = new ImBoolean(true);
    
    // 预设角度步长值
    private static final int[] PRESET_ANGLE_STEPS = {15, 30, 45, 60, 90};
    
    public RotateToolOptionRenderer() {
        super("rotate");
    }

    @Override
    public float render() {
        // 获取当前旋转工具实例
        RotateTool rotateTool = getCurrentTool();
        if (rotateTool == null) {
            // 如果当前工具不是RotateTool，显示提示信息
            ImGui.text("请选择旋转工具");
            return ImGui.getFrameHeightWithSpacing();
        }
        
        // 同步工具状态到UI
        syncToolState();
        
        float height = 0;
        ImGui.pushID("rotate_options");
        
        try {
            MasterPlannerMod.LOGGER.debug("RotateToolOptionRenderer: 开始渲染旋转工具选项");
            
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 角度步长设置
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("角度步长");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            
            // 使用整数滑动条
            if (ImGui.sliderInt("##angle_step", angleStep, 1, 90, "%d°")) {
                updateToolConfig(CONFIG_KEY_ANGLE_STEP, String.valueOf(angleStep[0]));
                MasterPlannerMod.LOGGER.debug("角度步长已更新为: {}°", angleStep[0]);
            }
            
            // 双击输入功能
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                ImGui.openPopup("angle_step_input");
            }
            
            ImGui.popItemWidth();
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("按住Shift键时的角度对齐步长（1°-90°）\n双击可手动输入数值");
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 预设值按钮
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("预设值");
            
            ImGui.tableNextColumn();
            renderPresetButtons(new float[]{height});
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 角度吸附开关
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("角度吸附");
            
            ImGui.tableNextColumn();
            // 设置复选框样式，参考其他工具的实现
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
            ImGui.pushStyleColor(ImGuiCol.CheckMark, currentTheme.accent);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 4.0f);
            
            if (ImGui.checkbox("##snap_angle", snapToAngle)) {
                updateToolConfig(CONFIG_KEY_SNAP_ANGLE, String.valueOf(snapToAngle.get()));
                MasterPlannerMod.LOGGER.debug("角度吸附已更新为: {}", snapToAngle.get());
            }
            
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(5);
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用后旋转时会自动吸附到指定角度");
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 使用说明
            renderUsageInstructions();
            
            // 快捷键提示
            renderShortcutTips();
            
            // 渲染角度步长输入弹窗
            renderAngleStepInputPopup();
            
            MasterPlannerMod.LOGGER.debug("RotateToolOptionRenderer: 渲染完成，高度: {}", height);
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 同步工具状态到UI
     */
    private void syncToolState() {
        try {
            RotateTool tool = getCurrentTool();
            if (tool != null && tool.getRotateStrategy() != null) {
                RotateStrategy strategy = tool.getRotateStrategy();
                
                // 同步角度步长
                double angleStepDegrees = Math.toDegrees(strategy.getRotateConstraints().getAngleStep());
                angleStep[0] = (int) Math.max(1, Math.min(90, Math.round(angleStepDegrees)));
                
                // 同步角度吸附状态
                snapToAngle.set(strategy.isSnapToAngleEnabledByUI());
                
                MasterPlannerMod.LOGGER.debug("RotateToolOptionRenderer: 同步状态完成，角度步长={}°，角度吸附={}", 
                    angleStep[0], snapToAngle.get());
            } else {
                MasterPlannerMod.LOGGER.debug("RotateToolOptionRenderer: 无法获取工具状态，使用默认值");
            }
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.warn("RotateToolOptionRenderer: 同步工具状态失败: {}", e.getMessage());
            // 如果获取工具状态失败，使用默认值
            // 这确保了UI不会因为工具状态获取失败而崩溃
        }
    }
    
    /**
     * 获取当前工具实例
     */
    private RotateTool getCurrentTool() {
        try {
            // 通过AppState获取当前激活的工具
            com.masterplanner.core.state.AppState appState = com.masterplanner.core.state.AppState.getInstance();
            if (appState == null) {
                MasterPlannerMod.LOGGER.warn("AppState实例不存在");
                return null;
            }
            
            com.masterplanner.api.tool.ITool currentTool = appState.getCurrentTool();
            if (currentTool instanceof RotateTool) {
                return (RotateTool) currentTool;
            }
            
            // 如果当前工具不是RotateTool，尝试通过工具ID查找
            if (currentTool != null) {
                com.masterplanner.core.tool.ToolManager toolManager = com.masterplanner.core.tool.ToolManager.getInstance();
                com.masterplanner.api.tool.ITool rotateTool = toolManager.getTool("rotate");
                if (rotateTool instanceof RotateTool) {
                    return (RotateTool) rotateTool;
                }
            }
            
            MasterPlannerMod.LOGGER.debug("当前工具不是RotateTool: {}", 
                currentTool != null ? currentTool.getClass().getSimpleName() : "null");
            return null;
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.warn("获取RotateTool失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void initialize() {
        // 初始化时同步工具状态
        syncToolState();
    }

    @Override
    public void cleanup() {
        // 清理资源
    }
    
    /**
     * 渲染使用说明
     */
    private void renderUsageInstructions() {
        if (ImGui.collapsingHeader("使用说明", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("旋转工具使用步骤：");
            ImGui.spacing();
            
            ImGui.bulletText("1. 使用选择工具选择要旋转的图形");
            ImGui.bulletText("2. 切换到旋转工具");
            ImGui.bulletText("3. 第一次点击：设置旋转中心点");
            ImGui.bulletText("4. 第二次点击：设置参考点（确定基准角度）");
            ImGui.bulletText("5. 移动鼠标旋转图形，第三次点击完成旋转");
            
            ImGui.spacing();
            ImGui.textWrapped("旋转模式说明：");
            ImGui.bulletText("三点旋转：通过中心点、参考点、目标点精确控制旋转");
            ImGui.bulletText("旋转中心：始终是第一次点击的位置");
            ImGui.bulletText("参考点：用于确定旋转的基准角度");
            ImGui.bulletText("目标点：确定最终的旋转角度");
            
            ImGui.spacing();
            ImGui.textWrapped("角度约束说明：");
            ImGui.bulletText("角度步长：按住Shift键时的角度对齐步长");
            ImGui.bulletText("角度吸附：启用后旋转时会自动对齐到指定角度");
        }
    }
    
    /**
     * 渲染预设值按钮
     */
    private void renderPresetButtons(float[] height) {
        // 获取当前主题
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        float buttonWidth = (ImGui.getContentRegionAvailX() - 8.0f) / 5.0f; // 5个按钮，减小间距
        float buttonHeight = ImGui.getFrameHeight();
        
        // 临时减少按钮间距
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2.0f, 0.0f);
        
        for (int i = 0; i < PRESET_ANGLE_STEPS.length; i++) {
            if (i > 0) ImGui.sameLine();
            
            int presetValue = PRESET_ANGLE_STEPS[i];
            String buttonLabel = presetValue + "°";
            
            // 为每个按钮单独设置样式，避免样式栈问题
            if (angleStep[0] == presetValue) {
                // 当前选中的按钮使用高亮样式
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.foreground);
            } else {
                // 普通按钮使用默认样式
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.controlBackground);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
                ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
            }
            
            ImGui.pushItemWidth(buttonWidth);
            if (ImGui.button(buttonLabel + "##preset_" + presetValue, buttonWidth, buttonHeight)) {
                angleStep[0] = presetValue;
                updateToolConfig(CONFIG_KEY_ANGLE_STEP, String.valueOf(angleStep[0]));
                MasterPlannerMod.LOGGER.debug("角度步长已设置为预设值: {}°", presetValue);
            }
            ImGui.popItemWidth();
            
            // 恢复样式
            ImGui.popStyleColor(4);
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("设置为 " + presetValue + "° 角度步长");
            }
        }
        
        // 恢复原始间距
        ImGui.popStyleVar();
        
        height[0] += buttonHeight + ImGui.getStyle().getItemSpacingY();
    }
    
    /**
     * 渲染角度步长输入弹窗
     */
    private void renderAngleStepInputPopup() {
        if (ImGui.beginPopupModal("angle_step_input", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("请输入角度步长值（1-90）：");
            ImGui.spacing();
            
            // 临时输入缓冲区
            ImInt tempInput = new ImInt(angleStep[0]);
            ImGui.pushItemWidth(100);
            
            // 使用inputInt但不自动关闭弹窗
            boolean inputChanged = ImGui.inputInt("##angle_input", tempInput, 1, 5);
            
            // 验证输入范围并更新值，但不关闭弹窗
            if (inputChanged) {
                int newValue = tempInput.get();
                if (newValue >= 1 && newValue <= 90) {
                    angleStep[0] = newValue;
                    updateToolConfig(CONFIG_KEY_ANGLE_STEP, String.valueOf(angleStep[0]));
                    MasterPlannerMod.LOGGER.debug("角度步长已通过输入更新为: {}°", newValue);
                } else {
                    // 如果输入值超出范围，重置为当前值
                    tempInput.set(angleStep[0]);
                }
            }
            
            ImGui.popItemWidth();
            
            ImGui.spacing();
            
            // 按钮行
            if (ImGui.button("确定", 80, 0)) {
                int finalValue = tempInput.get();
                if (finalValue >= 1 && finalValue <= 90) {
                    angleStep[0] = finalValue;
                    updateToolConfig(CONFIG_KEY_ANGLE_STEP, String.valueOf(angleStep[0]));
                    MasterPlannerMod.LOGGER.debug("角度步长已通过确定按钮更新为: {}°", finalValue);
                }
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            if (ImGui.button("取消", 80, 0)) {
                ImGui.closeCurrentPopup();
            }
            
            // 添加回车键支持
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Enter))) {
                int finalValue = tempInput.get();
                if (finalValue >= 1 && finalValue <= 90) {
                    angleStep[0] = finalValue;
                    updateToolConfig(CONFIG_KEY_ANGLE_STEP, String.valueOf(angleStep[0]));
                    MasterPlannerMod.LOGGER.debug("角度步长已通过回车键更新为: {}°", finalValue);
                }
                ImGui.closeCurrentPopup();
            }
            
            // 添加ESC键支持
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Escape))) {
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 渲染快捷键提示
     */
    private void renderShortcutTips() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        if (ImGui.collapsingHeader("快捷键", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textColored(currentTheme.warningText, "快捷键提示：");
            ImGui.spacing();
            
            ImGui.bulletText("Shift：启用角度约束（按步长对齐）");
            ImGui.bulletText("Ctrl：复制模式（保留原始图形）");
            ImGui.bulletText("Alt：禁用角度吸附（自由旋转）");
            ImGui.bulletText("右键：取消当前旋转操作");
            ImGui.bulletText("Esc：取消旋转操作");
            
            ImGui.spacing();
            ImGui.textColored(currentTheme.mutedText, "提示：");
            ImGui.textWrapped("""
                    • 旋转中心始终是第一次点击的位置
                    • 参考点用于确定旋转的基准角度
                    • 移动鼠标时可以看到旋转预览
                    • 按住 Ctrl 保留原始图形并创建旋转副本""");
        }
    }
} 