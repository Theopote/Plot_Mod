package com.plot.ui.panel.tool.renderer;

import com.plot.ui.tools.impl.modify.RotateTool;
import com.plot.ui.tools.impl.modify.strategy.RotateStrategy;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import com.plot.PlotMod;

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
            PlotMod.LOGGER.debug("RotateToolOptionRenderer: 开始渲染旋转工具选项");
            
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
                PlotMod.LOGGER.debug("角度步长已更新为: {}°", angleStep[0]);
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
                PlotMod.LOGGER.debug("角度吸附已更新为: {}", snapToAngle.get());
            }
            
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(5);
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用后旋转时会自动吸附到指定角度");
            }
            
            height += ImGui.getFrameHeightWithSpacing();

            
            // 渲染角度步长输入弹窗
            renderAngleStepInputPopup();
            
            PlotMod.LOGGER.debug("RotateToolOptionRenderer: 渲染完成，高度: {}", height);
            
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
                
                PlotMod.LOGGER.debug("RotateToolOptionRenderer: 同步状态完成，角度步长={}°，角度吸附={}", 
                    angleStep[0], snapToAngle.get());
            } else {
                PlotMod.LOGGER.debug("RotateToolOptionRenderer: 无法获取工具状态，使用默认值");
            }
        } catch (Exception e) {
            PlotMod.LOGGER.warn("RotateToolOptionRenderer: 同步工具状态失败: {}", e.getMessage());
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
            com.plot.core.state.AppState appState = com.plot.core.state.AppState.getInstance();
            if (appState == null) {
                PlotMod.LOGGER.warn("AppState实例不存在");
                return null;
            }
            
            com.plot.api.tool.ITool currentTool = appState.getCurrentTool();
            if (currentTool instanceof RotateTool) {
                return (RotateTool) currentTool;
            }
            
            // 如果当前工具不是RotateTool，尝试通过工具ID查找
            if (currentTool != null) {
                com.plot.core.tool.ToolManager toolManager = com.plot.core.tool.ToolManager.getInstance();
                com.plot.api.tool.ITool rotateTool = toolManager.getTool("rotate");
                if (rotateTool instanceof RotateTool) {
                    return (RotateTool) rotateTool;
                }
            }
            
            PlotMod.LOGGER.debug("当前工具不是RotateTool: {}", 
                currentTool != null ? currentTool.getClass().getSimpleName() : "null");
            return null;
        } catch (Exception e) {
            PlotMod.LOGGER.warn("获取RotateTool失败: {}", e.getMessage());
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
                PlotMod.LOGGER.debug("角度步长已设置为预设值: {}°", presetValue);
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
    
    private void applyAngleStepValue(int value, String source) {
        int clampedValue = Math.max(1, Math.min(90, value));
        angleStep[0] = clampedValue;
        updateToolConfig(CONFIG_KEY_ANGLE_STEP, String.valueOf(clampedValue));
        PlotMod.LOGGER.debug("角度步长已通过{}更新为: {}°", source, clampedValue);
    }

    /**
     * 渲染角度步长输入弹窗
     */
    private void renderAngleStepInputPopup() {
        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0, ImGuiCond.Appearing);

            int popupFlags = ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoSavedSettings;

            if (ImGui.beginPopupModal("angle_step_input", popupFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("rotate_angle_input")) {
                        ImGui.closeCurrentPopup();
                        return;
                    }

                    DialogLayoutHelper.beginSection("角度步长");
                    DialogLayoutHelper.helpText("请输入 1 - 90 之间的整数，回车可直接确认，双击滑块也可再次打开此面板。");
                    DialogLayoutHelper.endSection();

                    ImInt tempInput = new ImInt(angleStep[0]);
                    ImGui.pushItemWidth(-1);
                    boolean inputChanged = ImGui.inputInt("##angle_input", tempInput, 1, 5);
                    ImGui.popItemWidth();

                    if (inputChanged) {
                        int newValue = Math.max(1, Math.min(90, tempInput.get()));
                        tempInput.set(newValue);
                        applyAngleStepValue(newValue, "输入");
                    }

                    boolean confirmWithEnter = DialogLayoutHelper.isConfirmShortcutPressed();
                    boolean cancelWithEsc = DialogLayoutHelper.isCancelShortcutPressed();

                    if (cancelWithEsc) {
                        ImGui.closeCurrentPopup();
                    }

                    DialogLayoutHelper.beginFooter();
                    DialogLayoutHelper.FooterResult action =
                            DialogLayoutHelper.footerConfirmCancelCentered("取消", "确定", DialogStyleManager.getContentWidth());

                    if (action.confirmClicked() || confirmWithEnter) {
                        applyAngleStepValue(tempInput.get(), action.confirmClicked() ? "确定按钮" : "回车键");
                        ImGui.closeCurrentPopup();
                    }
                    if (action.cancelClicked()) {
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
}
