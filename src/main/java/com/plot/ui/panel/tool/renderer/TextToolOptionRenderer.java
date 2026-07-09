package com.plot.ui.panel.tool.renderer;

import com.plot.utils.PlotI18n;
import com.plot.PlotMod;
import com.plot.core.graphics.style.TextStyle;
import com.plot.core.graphics.style.TextAlignment;
import com.plot.ui.tools.impl.modify.TextTool;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.core.state.AppState;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;

import java.awt.Color;
import com.plot.core.log.LogManager;

/**
 * 增强文字工具选项渲染器 - 无状态架构，支持多行文字和高级配置
 * 
 * <p>优化版本 - 基于SpiralToolOptionRenderer的优秀设计模式</p>
 * <ul>
 *   <li>状态同步机制：确保UI与工具状态一致</li>
 *   <li>主题集成：提供一致的用户体验</li>
 *   <li>错误处理和回退机制：增强健壮性</li>
 *   <li>参数验证：防止无效配置</li>
 *   <li>改进的UI布局和样式</li>
 * </ul>
 */
public class TextToolOptionRenderer extends AbstractToolOptionRenderer {
    
    // 配置键常量
    private static final String CONFIG_KEY_FONT_SIZE = "fontSize";
    private static final String CONFIG_KEY_BOLD = "bold";
    private static final String CONFIG_KEY_ITALIC = "italic";
    private static final String CONFIG_KEY_USE_DIALOG = "useDialog";
    private static final String CONFIG_KEY_H_ALIGN = "horizontalAlignment";
    private static final String CONFIG_KEY_V_ALIGN = "verticalAlignment";
    private static final String CONFIG_KEY_LINE_HEIGHT = "lineHeight";
    
    // 工具ID常量
    private static final String TOOL_ID = "text";

    // 工具引用 - 单一数据源
    private final TextTool textTool; // 保留引用以兼容未来扩展（避免每次从AppState查找）

    // 临时状态变量 - 仅在渲染期间使用
    private float[] tempFontSize = new float[1];
    private final ImBoolean tempBold = new ImBoolean(false);
    private final ImBoolean tempItalic = new ImBoolean(false);
    private final ImBoolean tempUseDialog = new ImBoolean(true);
    private float[] tempLineHeight = new float[1];
    private TextAlignment.Horizontal tempHorizontalAlign = TextAlignment.Horizontal.LEFT;
    private TextAlignment.Vertical tempVerticalAlign = TextAlignment.Vertical.TOP;
    
    // 状态同步脏标记
    private boolean needsSync = true;
    
    // 错误状态
    private boolean hasError = false;
    private String errorMessage = "";

    public TextToolOptionRenderer(TextTool textTool) {
        super(TOOL_ID);
        this.textTool = textTool;

        PlotMod.LOGGER.info("创建增强文字工具选项渲染器");
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("text_options");
        
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 仅在需要时同步工具状态到UI
            if (needsSync) {
                syncStateFromTool();
                needsSync = false;
            }
            
            // 显示错误信息（如果有）
            if (hasError) {
                height += renderErrorSection(currentTheme);
            }
            
            // 根据“使用对话框”设置决定面板内容
            if (tempUseDialog.get()) {
                // 对话框已包含样式与多行输入，这里仅保留最小化选项
                height += renderInputMethodSection(currentTheme);
                height += renderDialogInfo(currentTheme);
            } else {
                // 未使用对话框时，保留完整的面板快速设置
                height += renderFontSizeSection(currentTheme);
                height += renderStyleSection(currentTheme);
                height += renderAlignmentSection(currentTheme);
                height += renderLineHeightSection(currentTheme);
                height += renderInputMethodSection(currentTheme);
            }
            height += renderActionSection(currentTheme);

        } catch (Exception e) {
            PlotMod.LOGGER.error("TextToolOptionRenderer 渲染失败: {}", e.getMessage(), e);
            hasError = true;
            errorMessage = "渲染失败: " + e.getMessage();
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    /**
     * 对话框模式下的说明信息
     */
    private float renderDialogInfo(UITheme.ThemeColors theme) {
        float height = 0;
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.text_description"));

        ImGui.tableNextColumn();
        // 使用较淡的提示色
        ImGui.textColored(theme.mutedText,
                PlotI18n.tr("hint.plot.text.moved_to_dialog");

        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }

    /**
     * 从工具同步状态到UI
     */
    private void syncStateFromTool() {
        try {
            AppState appState = AppState.getInstance();
            if (appState == null) {
                return;
            }
            
            var currentTool = appState.getCurrentTool();
            if (!(currentTool instanceof TextTool actualTextTool)) {
                return;
            }
            
            // 使用实际的TextTool实例获取配置
            ToolConfiguration config = new ActualToolConfiguration(actualTextTool);
            
            // 同步状态到UI变量
            tempFontSize[0] = config.getFontSize();
            tempBold.set(config.isBold());
            tempItalic.set(config.isItalic());
            tempUseDialog.set(config.isUseDialog());
            tempLineHeight[0] = config.getLineHeight();
            tempHorizontalAlign = config.getHorizontalAlignment();
            tempVerticalAlign = config.getVerticalAlignment();
            
            // 颜色改为跟随当前图层，不在面板中配置
            
            hasError = false;
            errorMessage = "";
            
            PlotMod.LOGGER.debug("TextToolOptionRenderer: 从实际TextTool同步配置 - useDialog: {}", config.isUseDialog());
            
        } catch (Exception e) {
            PlotMod.LOGGER.error("同步文字工具状态失败: {}", e.getMessage());
            hasError = true;
            errorMessage = "状态同步失败: " + e.getMessage();
        }
    }
    
    /**
     * 渲染错误信息区域
     */
    private float renderErrorSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.tableNextColumn();
        
        // 设置错误文本颜色
        ImGui.pushStyleColor(ImGuiCol.Text, theme.errorText);
        ImGui.textWrapped(errorMessage);
        ImGui.popStyleColor();
        
        height += ImGui.getTextLineHeight() * 2 + ImGui.getStyle().getItemSpacing().y;
        return height;
    }
    
    /**
     * 渲染字体大小设置区域
     */
    private float renderFontSizeSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.font_size"));
        
        ImGui.tableNextColumn();
        
        // 使用通用浮点数步进器
        renderFloatStepper("font_size", tempFontSize[0], 2.0f, 
                          TextStyle.MIN_FONT_SIZE, TextStyle.MAX_FONT_SIZE,
                CONFIG_KEY_FONT_SIZE, theme);
        
        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }
    
    /**
     * 渲染样式设置区域
     */
    private float renderStyleSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.style"));
        
        ImGui.tableNextColumn();
        
        // 设置复选框样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        
        // 粗体复选框
        if (ImGui.checkbox(PlotI18n.tr("option.plot.text_bold") + "##bold", tempBold)) {
            updateToolConfig(CONFIG_KEY_BOLD, String.valueOf(tempBold.get()));
        }
        
        ImGui.sameLine();
        
        // 斜体复选框
        if (ImGui.checkbox(PlotI18n.tr("option.plot.text_italic") + "##italic", tempItalic)) {
            updateToolConfig(CONFIG_KEY_ITALIC, String.valueOf(tempItalic.get()));
        }
        
        // 恢复样式
        ImGui.popStyleVar();
        ImGui.popStyleColor(5);
        
        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }
    
    // 颜色选择面板已移除，颜色跟随图层
    
    /**
     * 渲染对齐设置区域
     */
    private float renderAlignmentSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.text_align"));
        
        ImGui.tableNextColumn();
        
        // 水平对齐下拉菜单
        renderEnumCombo("h_align", tempHorizontalAlign, 
                       TextAlignment.Horizontal.values(), CONFIG_KEY_H_ALIGN, theme);
        
        ImGui.sameLine();
        
        // 垂直对齐下拉菜单
        renderEnumCombo("v_align", tempVerticalAlign, 
                       TextAlignment.Vertical.values(), CONFIG_KEY_V_ALIGN, theme);
        
        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }
    
    /**
     * 渲染行高设置区域
     */
    private float renderLineHeightSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.text_line_height"));
        
        ImGui.tableNextColumn();
        
        // 使用通用浮点数步进器
        renderFloatStepper("line_height", tempLineHeight[0], 0.1f, 
                          0.5f, 3.0f, CONFIG_KEY_LINE_HEIGHT, theme);
        
        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }
    
    /**
     * 渲染输入方式设置区域
     */
    private float renderInputMethodSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.input_method"));
        
        ImGui.tableNextColumn();
        
        // 设置复选框样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        
        // 使用对话框选项
        if (ImGui.checkbox(PlotI18n.tr("option.plot.text_use_dialog") + "##use_dialog", tempUseDialog)) {
            LogManager.getInstance().debug("TextToolOptionRenderer: 用户切换对话框设置为 {}", tempUseDialog.get());
            updateToolConfig(CONFIG_KEY_USE_DIALOG, String.valueOf(tempUseDialog.get()));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.text.dialog_input"));
        }
        
        // 恢复样式
        ImGui.popStyleVar();
        ImGui.popStyleColor(5);
        
        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }
    
    /**
     * 渲染操作按钮区域
     */
    private float renderActionSection(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.text_operations"));
        
        ImGui.tableNextColumn();
        
        // 设置按钮样式
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 4.0f);
        
        // 重置大小按钮
        if (ImGui.button(PlotI18n.tr("button.plot.reset_size"), 80, 20)) {
            updateToolConfig(CONFIG_KEY_FONT_SIZE, String.valueOf(TextStyle.DEFAULT_FONT_SIZE));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.text.reset_font"));
        }
        
        ImGui.sameLine();
        
        // 转换为图形按钮
        if (ImGui.button(PlotI18n.tr("button.plot.convert_shape"), 80, 20)) {
            updateToolConfig("convertSelected", "true");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.text.convert_shape"));
        }
        
        // 恢复样式
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(4);
        
        height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
        return height;
    }

    /**
     * 通用浮点数步进器 - 带滑块和+/-按钮
     */
    private void renderFloatStepper(String label, float currentValue, float step,
                                    float min, float max, String configKey,
                                    UITheme.ThemeColors theme) {
        tempFontSize[0] = currentValue;
        
        // 设置滑块样式
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        
        // 滑块
        if (ImGui.sliderFloat("##" + label, tempFontSize, min, max, "%.1f")) {
            // 实时更新（仅在编辑完成后）
            if (ImGui.isItemDeactivatedAfterEdit()) {
                updateToolConfig(configKey, String.valueOf(tempFontSize[0]));
            }
        }
        
        // 恢复样式
        ImGui.popStyleColor(5);
        
        // 快速调整按钮
        ImGui.sameLine();
        if (ImGui.button("-##" + label, 20, 20)) {
            float newValue = Math.max(tempFontSize[0] - step, min);
            updateToolConfig(configKey, String.valueOf(newValue));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.text.decrease"));
        }
        
        ImGui.sameLine();
        if (ImGui.button("+##" + label, 20, 20)) {
            float newValue = Math.min(tempFontSize[0] + step, max);
            updateToolConfig(configKey, String.valueOf(newValue));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.text.increase"));
        }
    }
    
    /**
     * 通用枚举下拉菜单
     */
    private <T extends Enum<T>> void renderEnumCombo(String label, T currentValue, 
                                                     T[] allValues, String configKey, 
                                                     UITheme.ThemeColors theme) {
        // 设置下拉菜单样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        
        if (ImGui.beginCombo("##" + label, currentValue.name())) {
            for (T value : allValues) {
                boolean isSelected = currentValue == value;
                if (ImGui.selectable(value.name(), isSelected)) {
                    updateToolConfig(configKey, value.name());
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        // 恢复样式
        ImGui.popStyleColor(6);
    }
    
    /**
     * 更新工具配置，并标记需要同步
     */
    @Override
    protected void updateToolConfig(String key, String value) {
        PlotMod.LOGGER.debug("TextToolOptionRenderer.updateToolConfig: key={}, value={}", key, value);
        
        try {
            // 参数验证（颜色相关校验去除）
            switch (key) {
                case CONFIG_KEY_FONT_SIZE:
                    float fontSize = Float.parseFloat(value);
                    if (fontSize < TextStyle.MIN_FONT_SIZE || fontSize > TextStyle.MAX_FONT_SIZE) {
                        throw new IllegalArgumentException("字体大小必须在" + TextStyle.MIN_FONT_SIZE + "到" + TextStyle.MAX_FONT_SIZE + "之间");
                    }
                    break;
                case CONFIG_KEY_LINE_HEIGHT:
                    float lineHeight = Float.parseFloat(value);
                    if (lineHeight < 0.5f || lineHeight > 3.0f) {
                        throw new IllegalArgumentException("行高必须在0.5到3.0之间");
                    }
                    break;
            }
        } catch (IllegalArgumentException e) {
            PlotMod.LOGGER.error("无效配置: {} = {}, {}", key, value, e.getMessage());
            return; // 验证失败时不更新配置
        }
        
        // 验证通过后，调用基类方法发布事件
        super.updateToolConfig(key, value);
        needsSync = true; // 标记下次渲染需要同步
    }
    
    @Override
    public void initialize() {
        PlotMod.LOGGER.debug("初始化增强文字工具选项渲染器");
        
        // 重置所有控件值为默认值
        tempFontSize[0] = TextStyle.DEFAULT_FONT_SIZE;
        tempBold.set(false);
        tempItalic.set(false);
        tempUseDialog.set(true);
        tempLineHeight[0] = TextStyle.DEFAULT_LINE_HEIGHT;
        tempHorizontalAlign = TextAlignment.Horizontal.LEFT;
        tempVerticalAlign = TextAlignment.Vertical.TOP;
        
        // 颜色默认值已不需要，跟随图层
        
        // 状态同步现在每帧都会进行，无需特殊标记
        needsSync = true;
        
        // 发送默认配置（颜色不下发，跟随图层样式）
        updateToolConfig(CONFIG_KEY_FONT_SIZE, String.valueOf(tempFontSize[0]));
        updateToolConfig(CONFIG_KEY_BOLD, String.valueOf(tempBold.get()));
        updateToolConfig(CONFIG_KEY_ITALIC, String.valueOf(tempItalic.get()));
        updateToolConfig(CONFIG_KEY_USE_DIALOG, String.valueOf(tempUseDialog.get()));
        updateToolConfig(CONFIG_KEY_LINE_HEIGHT, String.valueOf(tempLineHeight[0]));
        updateToolConfig(CONFIG_KEY_H_ALIGN, tempHorizontalAlign.name());
        updateToolConfig(CONFIG_KEY_V_ALIGN, tempVerticalAlign.name());
    }
    
    @Override
    public void cleanup() {
        PlotMod.LOGGER.debug("清理增强文字工具选项渲染器");
    }
    
    /**
     * 工具配置接口 - 定义从TextTool获取配置的契约
     */
    public interface ToolConfiguration {
        float getFontSize();
        boolean isBold();
        boolean isItalic();
        Color getColor();
        boolean isUseDialog();
        TextAlignment.Horizontal getHorizontalAlignment();
        TextAlignment.Vertical getVerticalAlignment();
        float getLineHeight();
    }

    /**
     * 实际工具配置实现 - 从TextTool获取配置
     */
    private static class ActualToolConfiguration implements ToolConfiguration {
        private final TextTool textTool;

        public ActualToolConfiguration(TextTool textTool) {
            this.textTool = textTool;
        }

        @Override
        public float getFontSize() {
            return textTool.getFontSize();
        }

        @Override
        public boolean isBold() {
            return textTool.isBold();
        }

        @Override
        public boolean isItalic() {
            return textTool.isItalic();
        }

        @Override
        public Color getColor() {
            return textTool.getColor();
        }

        @Override
        public boolean isUseDialog() {
            return textTool.isUseDialog();
        }

        @Override
        public TextAlignment.Horizontal getHorizontalAlignment() {
            return textTool.getHorizontalAlignment();
        }

        @Override
        public TextAlignment.Vertical getVerticalAlignment() {
            return textTool.getVerticalAlignment();
        }

        @Override
        public float getLineHeight() {
            return textTool.getLineHeight();
        }
    }
} 