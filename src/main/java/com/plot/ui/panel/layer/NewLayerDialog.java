package com.plot.ui.panel.layer;

import com.plot.core.graphics.style.LineStyle;
import com.plot.core.layer.LayerManager;
import com.plot.api.model.ILayer;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
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
    private static final String DIALOG_TITLE = "新建图层";
    private static final String LAYER_NAME_PATTERN = "[a-zA-Z0-9_\u4E00-\u9FFF]+";
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
        if (!name.matches(LAYER_NAME_PATTERN)) {
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
            CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
            encoder.encode(CharBuffer.wrap(text));

            String cleaned = text.replace("\uFFFD", "").trim();
            String normalized = cleaned.replaceAll("[^a-zA-Z0-9_\u4E00-\u9FFF]", "");
            if (normalized.length() > MAX_NAME_LENGTH) {
                return normalized.substring(0, MAX_NAME_LENGTH);
            }
            return normalized;
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

        float totalWidth = DialogStyleManager.DialogWidth.STANDARD.value;

        // 交由 ImGui 根据当前字体/DPI 自适应高度，避免手写行数导致裁剪或留白过多
        ImGui.setNextWindowSize(totalWidth, 0.0f, ImGuiCond.Appearing);

        if (ImGui.isPopupOpen(DIALOG_TITLE)) {
            ImGui.setNextWindowPos(
                    ImGui.getMainViewport().getCenter().x - totalWidth/2,
                    ImGui.getMainViewport().getCenter().y - 100,
                    ImGuiCond.Appearing
            );
        }

        int windowFlags = ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoSavedSettings |
                ImGuiWindowFlags.NoScrollbar |
                ImGuiWindowFlags.AlwaysAutoResize;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                if (DialogStyleManager.renderTopRightCloseButton("new_layer")) {
                    hide();
                    ImGui.closeCurrentPopup();
                    ImGui.endPopup();
                    return;
                }

                UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

                DialogLayoutHelper.beginSection("基础信息");
                if (DialogLayoutHelper.beginForm("##new_layer_form")) {
                    DialogLayoutHelper.formRowLabel("名称");
                    if (ImGui.isWindowAppearing()) {
                        ImGui.setKeyboardFocusHere();
                    }

                    if (nameInputInvalid) {
                        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
                        ImGui.pushStyleColor(ImGuiCol.Border, theme.errorText);
                    }

                    if (ImGui.inputText("##new_layer_name", layerName,
                            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll |
                                    ImGuiInputTextFlags.CharsNoBlank)) {
                        String currentInput = layerName.get();
                        LOGGER.debug("输入完成 - 当前输入: '{}', 字节长度: {}, 字符长度: {}",
                                currentInput, currentInput.getBytes().length, currentInput.length());
                        nameInputInvalid = false;
                        ImGui.setKeyboardFocusHere(1);
                    }

                    if (nameInputInvalid) {
                        ImGui.popStyleColor();
                        ImGui.popStyleVar();
                        DialogLayoutHelper.errorText("请输入合法的图层名称（中文、字母、数字或下划线）。");
                    }

                    DialogLayoutHelper.formRowLabel("颜色");
                    ImGui.colorEdit4("##new_layer_color", layerColor);

                    DialogLayoutHelper.formRowLabel("线型");
                    if (ImGui.beginCombo("##new_layer_line_type", lineType.toString())) {
                        for (LineStyle.LineType type : LineStyle.LineType.values()) {
                            if (ImGui.selectable(type.toString(), type == lineType)) {
                                lineType = type;
                            }
                        }
                        ImGui.endCombo();
                    }

                    DialogLayoutHelper.formRowLabel("线宽");
                    float[] tempLineWidth = {lineWidth};
                    if (ImGui.dragFloat("##new_layer_line_width", tempLineWidth,
                            0.1f, 0.1f, 5.0f, "%.1f")) {
                        lineWidth = tempLineWidth[0];
                    }

                    DialogLayoutHelper.endForm();
                }
                DialogLayoutHelper.endSection();

                DialogLayoutHelper.beginFooter();
                DialogLayoutHelper.FooterResult action =
                        DialogLayoutHelper.footerConfirmCancelRight("取消", "创建", DialogStyleManager.getContentWidth());

                if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
                    createNewLayer();
                }

                if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
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