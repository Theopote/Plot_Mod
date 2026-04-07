package com.plot.ui.dialog;

import com.plot.core.graphics.style.TextAlignment;
import com.plot.core.graphics.style.TextStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.function.Consumer;

/**
 * 纯 ImGui 实现的文字输入对话框（无 Swing 依赖，适用于 headless 环境）
 */
public class TextInputDialog {
    private static final String DIALOG_TITLE = "添加文字";

    private static final TextInputDialog INSTANCE = new TextInputDialog();
    
    /** 多行输入框最小可见行数 */
    private static final float MIN_INPUT_ROWS = 6.0f;

    public static TextInputDialog getInstance() {
        return INSTANCE;
    }

    private boolean visible = false;
    private final ImString textBuffer = new ImString(2048);
    private Consumer<TextInputResult> onConfirm;
    private Runnable onCancel;

    // 跨线程安全的延迟打开标记与参数（避免在非渲染线程调用 ImGui.openPopup）
    private volatile boolean pendingOpen = false;
    private String pendingPreset = "";
    private Consumer<TextInputResult> pendingOnConfirm;
    private Runnable pendingOnCancel;
    private TextInputPreset pendingStylePreset;

    // 样式参数（跟随 TextStyle 的默认值与范围）
    // 注意：字体大小滑动条范围为 100~200，所以初始值设为 100.0f
    private float fontSize = 100.0f;
    private boolean bold = false;
    private boolean italic = false;
    private TextAlignment.Horizontal hAlign = TextAlignment.Horizontal.LEFT;
    private TextAlignment.Vertical vAlign = TextAlignment.Vertical.TOP;
    private float lineHeight = TextStyle.DEFAULT_LINE_HEIGHT;

    private TextInputDialog() {}

    public void open(String presetText, Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        // 渲染线程可直接打开
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.textBuffer.set(presetText != null ? presetText : "");
        applyPreset(TextInputPreset.defaults());
        this.visible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    public void open(String presetText, TextInputPreset preset,
                     Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.textBuffer.set(presetText != null ? presetText : "");
        applyPreset(preset);
        this.visible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    /**
     * 在任意线程请求打开（将在下一帧渲染时真正打开）
     */
    public void scheduleOpen(String presetText, Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.pendingPreset = presetText != null ? presetText : "";
        this.pendingOnConfirm = onConfirm;
        this.pendingOnCancel = onCancel;
        this.pendingStylePreset = TextInputPreset.defaults();
        this.pendingOpen = true;
    }

    public void scheduleOpen(String presetText, TextInputPreset preset,
                             Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.pendingPreset = presetText != null ? presetText : "";
        this.pendingOnConfirm = onConfirm;
        this.pendingOnCancel = onCancel;
        this.pendingStylePreset = preset;
        this.pendingOpen = true;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render() {
        // 若收到延迟打开请求，则在渲染线程执行真正的打开
        if (pendingOpen) {
            this.onConfirm = pendingOnConfirm;
            this.onCancel = pendingOnCancel;
            this.textBuffer.set(pendingPreset);
            applyPreset(pendingStylePreset);
            this.visible = true;
            this.pendingOpen = false;
            ImGui.openPopup(DIALOG_TITLE);
        }

        if (!visible) return;

        float width = DialogStyleManager.DialogWidth.WIDE.value;
        float inputHeight = Math.max(
                ImGui.getTextLineHeightWithSpacing() * MIN_INPUT_ROWS,
                ImGui.getFrameHeightWithSpacing() * 4.0f
        );

        ImGui.setNextWindowSize(width, 0.0f, ImGuiCond.Appearing);
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings |
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.AlwaysAutoResize;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("text_input")) {
                        cancelAndClose();
                        return;
                    }

                    float contentWidth = DialogStyleManager.getContentWidth();

                    DialogLayoutHelper.beginSection("文字内容");
                    DialogLayoutHelper.helpText("请输入文字内容（可多行）");
                    DialogLayoutHelper.DenseEditorStyleScope editorStyle = DialogLayoutHelper.pushDenseEditorStyle();
                    try {
                        if (ImGui.isWindowAppearing()) {
                            ImGui.setKeyboardFocusHere();
                        }
                        boolean multilineOk = true;
                        try {
                            ImGui.inputTextMultiline("##text_input", textBuffer, contentWidth, inputHeight,
                                    ImGuiInputTextFlags.AllowTabInput);
                        } catch (Throwable t) {
                            multilineOk = false;
                        }
                        if (!multilineOk) {
                            ImGui.inputText("##text_input", textBuffer, ImGuiInputTextFlags.CallbackHistory);
                        }
                    } finally {
                        DialogLayoutHelper.popDenseEditorStyle(editorStyle);
                    }
                    DialogLayoutHelper.endSection();

                    DialogLayoutHelper.beginSection("文字样式");
                    renderStyleSection();
                    DialogLayoutHelper.helpText("样式会应用到当前新建的文字对象。");
                    DialogLayoutHelper.endSection();

                    DialogLayoutHelper.beginFooter();
                    DialogLayoutHelper.FooterResult action =
                            DialogLayoutHelper.footerConfirmCancelRight("取消", "确定", contentWidth);
                    if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
                        cancelAndClose();
                    }
                    if (action.confirmClicked()) {
                        confirmAndClose();
                    }
                } finally {
                    ImGui.endPopup();
                }
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void renderStyleSection() {
        if (DialogLayoutHelper.beginForm("##text_style_form")) {
            DialogLayoutHelper.formRowLabel("字体大小");
            float[] sizeArr = new float[]{fontSize};
            if (ImGui.sliderFloat("##font_size", sizeArr, 100.0f, 200.0f, "%.1f")) {
                fontSize = sizeArr[0];
            }

            DialogLayoutHelper.formRowLabel("行高");
            float[] lineArr = new float[]{lineHeight};
            if (ImGui.sliderFloat("##line_height", lineArr, 0.5f, 3.0f, "%.2f")) {
                lineHeight = lineArr[0];
            }

            DialogLayoutHelper.formRowLabel("字形");
            if (ImGui.checkbox("粗体##bold", bold)) {
                bold = !bold;
            }
            ImGui.sameLine(0, DialogStyleManager.ITEM_SPACING_H);
            if (ImGui.checkbox("斜体##italic", italic)) {
                italic = !italic;
            }

            DialogLayoutHelper.formRowLabel("水平对齐");
            ImGui.setNextItemWidth(-1.0f);
            String currentH = hAlign.name();
            if (ImGui.beginCombo("##h_align", currentH)) {
                for (TextAlignment.Horizontal h : TextAlignment.Horizontal.values()) {
                    boolean selected = h == hAlign;
                    if (ImGui.selectable(h.name(), selected)) {
                        hAlign = h;
                    }
                    if (selected) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }

            DialogLayoutHelper.formRowLabel("垂直对齐");
            ImGui.setNextItemWidth(-1.0f);
            String currentV = vAlign.name();
            if (ImGui.beginCombo("##v_align", currentV)) {
                for (TextAlignment.Vertical v : TextAlignment.Vertical.values()) {
                    boolean selected = v == vAlign;
                    if (ImGui.selectable(v.name(), selected)) {
                        vAlign = v;
                    }
                    if (selected) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }

            DialogLayoutHelper.endForm();
        }
    }

    private void confirmAndClose() {
        String text = textBuffer.get();
        if (onConfirm != null) {
            onConfirm.accept(new TextInputResult(text, fontSize, bold, italic, hAlign, vAlign, lineHeight));
        }
        closeInternal();
        ImGui.closeCurrentPopup();
    }

    private void cancelAndClose() {
        if (onCancel != null) {
            onCancel.run();
        }
        closeInternal();
        ImGui.closeCurrentPopup();
    }

    private void closeInternal() {
        visible = false;
        onConfirm = null;
        onCancel = null;
        pendingStylePreset = null;
    }

    private void applyPreset(TextInputPreset preset) {
        TextInputPreset p = preset != null ? preset : TextInputPreset.defaults();
        this.fontSize = clamp(p.fontSize(), 100.0f, 200.0f);
        this.bold = p.bold();
        this.italic = p.italic();
        this.hAlign = p.hAlign() != null ? p.hAlign() : TextAlignment.Horizontal.LEFT;
        this.vAlign = p.vAlign() != null ? p.vAlign() : TextAlignment.Vertical.TOP;
        this.lineHeight = clamp(p.lineHeight(), 0.5f, 3.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TextInputResult(String text, float fontSize, boolean bold, boolean italic,
                                  TextAlignment.Horizontal hAlign, TextAlignment.Vertical vAlign, float lineHeight) {
    }

    public record TextInputPreset(float fontSize, boolean bold, boolean italic,
                                  TextAlignment.Horizontal hAlign, TextAlignment.Vertical vAlign, float lineHeight) {
        public static TextInputPreset defaults() {
            return new TextInputPreset(100.0f, false, false,
                    TextAlignment.Horizontal.LEFT, TextAlignment.Vertical.TOP,
                    TextStyle.DEFAULT_LINE_HEIGHT);
        }
    }
}


