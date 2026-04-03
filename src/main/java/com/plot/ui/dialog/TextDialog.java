package com.plot.ui.dialog;

import com.plot.api.graphics.ITextStyle;
import com.plot.core.graphics.style.TextAlignment;
import com.plot.core.graphics.style.TextStyle;

import java.awt.Color;
import java.awt.Frame;

/**
 * 文字对话框（ImGui 版本适配层）。
 *
 * 该类保留原有 TextDialog 的主要 API，但内部不再使用 Swing/JDialog，
 * 而是委托给 TextInputDialog 在 ImGui 渲染循环中显示。
 */
public class TextDialog {
    private static final String DEFAULT_FONT_FAMILY = "Arial";
    private static final float DEFAULT_FONT_SIZE = 100.0f;
    private static final float DEFAULT_LINE_HEIGHT = TextStyle.DEFAULT_LINE_HEIGHT;
    private static final Color DEFAULT_COLOR = TextStyle.DEFAULT_COLOR;

    private boolean confirmed = false;
    private String inputText = "";
    private TextStyle textStyle;

    private float initialFontSize = DEFAULT_FONT_SIZE;
    private boolean initialBold = false;
    private boolean initialItalic = false;
    private TextAlignment.Horizontal initialHAlign = TextAlignment.Horizontal.LEFT;
    private TextAlignment.Vertical initialVAlign = TextAlignment.Vertical.TOP;
    private float initialLineHeight = DEFAULT_LINE_HEIGHT;
    private Color initialColor = DEFAULT_COLOR;

    public TextDialog(Frame owner) {
        // 保留构造签名以兼容旧调用方；owner 在 ImGui 版本中不使用。
    }

    /**
     * 触发对话框显示。ImGui 版本为非阻塞：返回后由渲染线程显示并回填结果。
     */
    public void setVisible(boolean visible) {
        if (!visible) {
            return;
        }

        confirmed = false;
        inputText = "";
        textStyle = null;

        TextInputDialog.TextInputPreset preset = new TextInputDialog.TextInputPreset(
                initialFontSize,
                initialBold,
                initialItalic,
                initialHAlign,
                initialVAlign,
                initialLineHeight
        );

        TextInputDialog.getInstance().scheduleOpen(
                "",
                preset,
                result -> {
                    if (result == null) {
                        confirmed = false;
                        return;
                    }
                    String text = result.text();
                    if (text == null || text.isEmpty()) {
                        confirmed = false;
                        return;
                    }
                    inputText = text;
                    textStyle = new TextStyle.Builder()
                            .fontFamily(DEFAULT_FONT_FAMILY)
                            .fontSize(result.fontSize())
                            .bold(result.bold())
                            .italic(result.italic())
                            .horizontalAlignment(result.hAlign())
                            .verticalAlignment(result.vAlign())
                            .lineHeight(result.lineHeight())
                            .color(initialColor)
                            .build();
                    confirmed = true;
                },
                () -> confirmed = false
        );
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getInputText() {
        return inputText;
    }

    public ITextStyle getTextStyle() {
        if (!confirmed) {
            return null;
        }
        return textStyle;
    }

    public void setInitialFontSize(float fontSize) {
        this.initialFontSize = clamp(fontSize, 100.0f, 200.0f);
    }

    public void setInitialBold(boolean bold) {
        this.initialBold = bold;
    }

    public void setInitialItalic(boolean italic) {
        this.initialItalic = italic;
    }

    public void setInitialHorizontalAlignment(TextAlignment.Horizontal hAlign) {
        if (hAlign != null) {
            this.initialHAlign = hAlign;
        }
    }

    public void setInitialVerticalAlignment(TextAlignment.Vertical vAlign) {
        if (vAlign != null) {
            this.initialVAlign = vAlign;
        }
    }

    public void setInitialLineHeight(float lineHeight) {
        this.initialLineHeight = clamp(lineHeight, 0.5f, 3.0f);
    }

    public void setInitialColor(Color color) {
        if (color != null) {
            this.initialColor = color;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
