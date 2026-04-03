package com.plot.ui.panel.layer;

import com.plot.core.graphics.style.LineStyle;
import com.plot.core.layer.LayerManager;
import com.plot.api.model.ILayer;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.awt.Color;

public class NewLayerDialog {
    private static final Logger LOGGER = LogManager.getLogger("NewLayerDialog");

    // === 常量定义 ===
    // 使用DialogStyleManager中定义的统一间距常数
    private static final float SPACING = DialogStyleManager.ITEM_SPACING;
    private static final String DIALOG_TITLE = "新建图层";
    private static final int MAX_NAME_LENGTH = 32;  // 最大名称长度（字符）
    private static final int MAX_BUFFER_SIZE = 512; // ImString 缓冲区大小（字节，足够支持长中文输入）

    // === 状态字段 ===
    private boolean isVisible = false;          // 对话框是否可见
    private final ImString layerName;           // 图层名称输入缓冲区
    private final float[] layerColor =         // 图层颜色 (RGBA)
            {1.0f, 1.0f, 1.0f, 1.0f};
    private LineStyle.LineType lineType =      // 图层线型
            LineStyle.LineType.SOLID;
    private float lineWidth = 1.0f;            // 图层线宽（改为单值）
        private boolean nameInputInvalid = false;  // 名称输入是否合法（用于红色边框反馈）

    // === 依赖项 ===
    private final LayerManager layerManager;    // 图层管理器
    private final Consumer<String> showWarningDialog;  // 警告对话框回调

    public NewLayerDialog(
            LayerManager layerManager,
            Consumer<String> showWarningDialog) {
        this.layerManager = layerManager;
        this.showWarningDialog = showWarningDialog;
        this.layerName = new ImString(MAX_BUFFER_SIZE);
    }

    public void show() {
        isVisible = true;
        nameInputInvalid = false;
        ImGui.openPopup(DIALOG_TITLE);

        if (layerName.get().isEmpty()) {
            layerName.set(generateDefaultName());
        }
    }

    private String generateDefaultName() {
        int layerCount = layerManager.getLayers().size() + 1;
        String defaultName = "图层" + layerCount;
        int suffix = layerCount;
        synchronized (layerManager) {
            while (layerManager.isNameExists(defaultName)) {
                defaultName = "图层" + (++suffix);
            }
        }
        return defaultName;
    }

    private void createNewLayer() {
        String rawName = layerName.get();
        LOGGER.info("创建图层 - 原始名称: '{}', 字节长度: {}, 字符长度: {}",
                rawName, rawName.getBytes().length, rawName.length());

        String name = sanitizeChineseText(rawName).trim();
        LOGGER.info("创建图层 - 处理后名称: '{}', 字节长度: {}, 字符长度: {}",
                name, name.getBytes().length, name.length());

        // 验证名称
        if (name.isEmpty()) {
            showWarningDialog.accept("图层名称不能为空");
            nameInputInvalid = true;
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            showWarningDialog.accept("图层名称不能超过" + MAX_NAME_LENGTH + "个字符");
            nameInputInvalid = true;
            return;
        }
        if (!name.matches("[a-zA-Z0-9_\u4E00-\u9FFF]+")) {
            showWarningDialog.accept("图层名称只能包含中文、字母、数字或下划线");
            nameInputInvalid = true;
            return;
        }

        synchronized (layerManager) {
            if (layerManager.isNameExists(name)) {
                showWarningDialog.accept("图层名称已存在，请使用其他名称");
                nameInputInvalid = true;
                return;
            }

            // 创建图层
            LayerManager.LayerCreationResult result = layerManager.createLayer(name);

            if (result.isSuccess()) {
                ILayer createdLayer = result.getLayer();
                
                // 设置用户在对话框中选择的属性
                // 设置颜色
                Color layerColorObj = new Color(layerColor[0], layerColor[1], layerColor[2], layerColor[3]);
                createdLayer.setColor(layerColorObj);
                LOGGER.info("设置图层颜色: RGBA({}, {}, {}, {})", 
                           layerColor[0], layerColor[1], layerColor[2], layerColor[3]);
                
                // 设置线型和线宽
                LineStyle newLineStyle = new LineStyle(lineType, lineWidth);
                newLineStyle.setColor(layerColorObj);  // 确保线条颜色与图层颜色一致
                createdLayer.setLineStyle(newLineStyle);
                LOGGER.info("设置图层线型: {}, 线宽: {}", lineType, lineWidth);
                
                LOGGER.info("图层创建成功 - 名称: '{}', 字节长度: {}, 字符长度: {}",
                        createdLayer.getName(), createdLayer.getName().getBytes().length, createdLayer.getName().length());

                // 清空输入框
                layerName.set("");
                nameInputInvalid = false;

                // 关闭对话框
                hide();
                ImGui.closeCurrentPopup();
            } else {
                showWarningDialog.accept(result.getMessage());
            }
        }
    }

    private String sanitizeChineseText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            // 验证 UTF-8 编码
            CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
            encoder.encode(CharBuffer.wrap(text));
            // 移除 U+FFFD 和无效字符
            String cleaned = text.replace("\uFFFD", "").trim();
            if (cleaned.isEmpty()) {
                StringBuilder validChars = new StringBuilder();
                for (int i = 0; i < text.length() && validChars.length() < MAX_NAME_LENGTH; i++) {
                    char c = text.charAt(i);
                    // 中文字符范围
                    // 中文扩展A
                    // 中文扩展B
                    if (c != '\uFFFD' && (Character.isLetterOrDigit(c) || c >= 0x4E00 && c <= 0x9FFF || c >= 0x3400 && c <= 0x4DBF || c == '_' || c == '-' || c == ' ')) {
                        validChars.append(c);
                    }
                }
                return validChars.toString().trim();
            }
            return cleaned;
        } catch (CharacterCodingException e) {
            LOGGER.error("无效字符编码: {}", text, e);
            return "";
        }
    }

    private void hide() {
        isVisible = false;
        nameInputInvalid = false;
        layerName.clear();
        Arrays.fill(layerColor, 1.0f);
        lineType = LineStyle.LineType.SOLID;
        lineWidth = 1.0f;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void render() {
        if (!isVisible) return;

        float totalWidth = 300.0f;

        // 动态计算高度，避免固定高度在高 DPI/大字体下裁剪底部按钮
        float rowHeight = ImGui.getFrameHeightWithSpacing();
        float separatorHeight = ImGui.getStyle().getItemSpacingY() + 1.0f;
        float verticalPadding = DialogStyleManager.PANEL_PADDING * 2.0f;
        float dynamicHeight = verticalPadding + rowHeight * 6.0f + separatorHeight + DialogStyleManager.ITEM_SPACING;

        ImGui.setNextWindowSize(totalWidth, dynamicHeight, ImGuiCond.Always);

        if (ImGui.isPopupOpen(DIALOG_TITLE)) {
            ImGui.setNextWindowPos(
                    ImGui.getMainViewport().getCenter().x - totalWidth/2,
                    ImGui.getMainViewport().getCenter().y - 100,
                    ImGuiCond.Appearing
            );
        }

        int windowFlags = ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoSavedSettings |
            ImGuiWindowFlags.NoScrollbar;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                if (DialogStyleManager.renderTopRightCloseButton("new_layer")) {
                    hide();
                    ImGui.closeCurrentPopup();
                    ImGui.endPopup();
                    return;
                }

                float labelWidth = DialogStyleManager.LABEL_WIDTH;
                float controlWidth = DialogStyleManager.getControlWidth(labelWidth);

                // === 名称输入 ===
                UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
                ImGui.text("名称：");
                ImGui.sameLine(labelWidth);
                ImGui.setNextItemWidth(controlWidth);
                if (ImGui.isWindowAppearing()) {
                    ImGui.setKeyboardFocusHere();
                }

                if (nameInputInvalid) {
                    ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.Border, theme.errorText);
                }

                // 使用不带回调的 inputText 重载，验证中文输入
                if (ImGui.inputText("##new_layer_name", layerName,
                        ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll |
                                ImGuiInputTextFlags.CharsNoBlank)) {
                    String currentInput = layerName.get();
                    LOGGER.debug("输入完成 - 当前输入: '{}', 字节长度: {}, 字符长度: {}",
                            currentInput, currentInput.getBytes().length, currentInput.length());
                    nameInputInvalid = false;
                    ImGui.setKeyboardFocusHere(1); // 聚焦到"确定"按钮
                }

                if (nameInputInvalid) {
                    ImGui.popStyleColor();
                    ImGui.popStyleVar();
                }

                // === 颜色选择器 ===
                ImGui.text("颜色：");
                ImGui.sameLine(labelWidth);
                ImGui.setNextItemWidth(controlWidth);
                ImGui.colorEdit4("##new_layer_color", layerColor);

                // === 线型选择 ===
                ImGui.text("线型：");
                ImGui.sameLine(labelWidth);
                ImGui.setNextItemWidth(controlWidth);
                if (ImGui.beginCombo("##new_layer_line_type", lineType.toString())) {
                    for (LineStyle.LineType type : LineStyle.LineType.values()) {
                        if (ImGui.selectable(type.toString(), type == lineType)) {
                            lineType = type;
                        }
                    }
                    ImGui.endCombo();
                }

                // === 线宽输入 ===
                ImGui.text("线宽：");
                ImGui.sameLine(labelWidth);
                ImGui.setNextItemWidth(controlWidth);
                float[] tempLineWidth = {lineWidth}; // 临时数组以兼容 ImGui
                if (ImGui.dragFloat("##new_layer_line_width", tempLineWidth,
                        0.1f, 0.1f, 5.0f, "%.1f")) {
                    lineWidth = tempLineWidth[0];
                }

                ImGui.separator();

                // === 按钮区域 ===
                float buttonSpacing = DialogStyleManager.BUTTON_SPACING;
                float buttonWidth = DialogStyleManager.getTwoButtonWidth(controlWidth);

                DialogStyleManager.centerTwoButtons(buttonWidth);
                if (ImGui.button("确定", buttonWidth, 0) ||
                        ImGui.isKeyPressed(ImGuiKey.Enter)) {
                    createNewLayer();
                }

                ImGui.sameLine(0, buttonSpacing);
                if (ImGui.button("取消", buttonWidth, 0) ||
                        ImGui.isKeyPressed(ImGuiKey.Escape)) {
                    hide();
                    ImGui.closeCurrentPopup();
                }

                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }
}