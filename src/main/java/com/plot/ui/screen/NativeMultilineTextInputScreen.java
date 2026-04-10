package com.plot.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 原生多行文本输入界面。
 * 使用多组 TextFieldWidget 代替 ImGui 文本框，以稳定支持中文输入。
 */
public class NativeMultilineTextInputScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 248;
    private static final int LINE_COUNT = 6;
    private static final int LINE_HEIGHT = 18;
    private static final int LINE_GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final Screen parent;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private final List<TextFieldWidget> lineFields = new ArrayList<>();
    private final String initialValue;

    private boolean completed;
    private int panelX;
    private int panelY;
    private int confirmX;
    private int cancelX;
    private int buttonY;
    private int buttonWidth;

    public NativeMultilineTextInputScreen(Screen parent, Text title, String initialValue,
                                          Consumer<String> onConfirm, Runnable onCancel) {
        super(title);
        this.parent = parent;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.initialValue = initialValue != null ? initialValue : "";
    }

    @Override
    protected void init() {
        super.init();
        lineFields.clear();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        String[] lines = initialValue.split("\\n", -1);
        int inputX = panelX + 18;
        int inputWidth = PANEL_WIDTH - 36;
        int startY = panelY + 42;
        for (int index = 0; index < LINE_COUNT; index++) {
            TextFieldWidget lineField = new TextFieldWidget(
                    this.textRenderer,
                    inputX,
                    startY + index * (LINE_HEIGHT + LINE_GAP),
                    inputWidth,
                    LINE_HEIGHT,
                    Text.literal("第" + (index + 1) + "行")
            );
            lineField.setMaxLength(256);
            if (index < lines.length) {
                lineField.setText(lines[index]);
            }
            addDrawableChild(lineField);
            lineFields.add(lineField);
        }

        if (!lineFields.isEmpty()) {
            lineFields.get(0).setFocused(true);
            setFocused(lineFields.get(0));
        }

        buttonWidth = Math.max(84, this.textRenderer.getWidth("确定") + 24);
        int totalWidth = buttonWidth * 2 + BUTTON_GAP;
        confirmX = panelX + PANEL_WIDTH - 18 - totalWidth;
        cancelX = confirmX + buttonWidth + BUTTON_GAP;
        buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - 16;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEE1E1E1E);
        drawBorder(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF5A5A5A);

        context.drawTextWithShadow(this.textRenderer, this.title, panelX + 18, panelY + 14, 0xFFE8E8E8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("支持中文输入，逐行输入；空白尾行会自动忽略"), panelX + 18, panelY + 28, 0xFF9AA0A6);

        drawButton(context, confirmX, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY, "确定", 0xFF2E7D32);
        drawButton(context, cancelX, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY, "取消", 0xFF555555);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no-op
    }

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (isInside(mouseX, mouseY, confirmX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            confirmAndClose();
            return true;
        }
        if (isInside(mouseX, mouseY, cancelX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            cancelAndClose();
            return true;
        }

        for (TextFieldWidget lineField : lineFields) {
            boolean focused = isInside(mouseX, mouseY, lineField.getX(), lineField.getY(), lineField.getWidth(), lineField.getHeight());
            lineField.setFocused(focused);
            if (focused) {
                setFocused(lineField);
            }
        }

        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        TextFieldWidget focusedField = getFocusedField();
        if (focusedField != null) {
            if (focusedField.charTyped(charInput)) {
                return true;
            }
            String committed = charInput.asString();
            if (!committed.isEmpty()) {
                focusedField.write(committed);
                return true;
            }
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();

        if (keyCode == 256) {
            cancelAndClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            return true;
        }

        TextFieldWidget focusedField = getFocusedField();
        if (focusedField != null && focusedField.keyPressed(keyInput)) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        cancelAndClose();
    }

    private TextFieldWidget getFocusedField() {
        for (TextFieldWidget lineField : lineFields) {
            if (lineField.isFocused()) {
                return lineField;
            }
        }
        return null;
    }

    private void confirmAndClose() {
        if (completed) {
            return;
        }
        completed = true;
        restoreParent();
        if (onConfirm != null) {
            onConfirm.accept(joinLines());
        }
    }

    private void cancelAndClose() {
        if (completed) {
            return;
        }
        completed = true;
        restoreParent();
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void restoreParent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            if (parent instanceof PlotScreen) {
                PlotScreenState.markSuppressNextPlotClick();
            }
            client.setScreen(parent);
        }
    }

    private String joinLines() {
        List<String> lines = new ArrayList<>(lineFields.size());
        for (TextFieldWidget lineField : lineFields) {
            lines.add(lineField.getText());
        }
        int lastNonEmpty = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (!lines.get(index).isEmpty()) {
                lastNonEmpty = index;
            }
        }
        if (lastNonEmpty < 0) {
            return "";
        }
        return String.join("\n", lines.subList(0, lastNonEmpty + 1));
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height,
                            int mouseX, int mouseY, String label, int baseColor) {
        boolean hover = isInside(mouseX, mouseY, x, y, width, height);
        int color = hover ? brighten(baseColor) : baseColor;
        context.fill(x, y, x + width, y + height, color);
        drawBorder(context, x, y, width, height, 0xFF808080);
        int textWidth = this.textRenderer.getWidth(label);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - this.textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(this.textRenderer, label, textX, textY, 0xFFFFFFFF);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int brighten(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 20);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + 20);
        int b = Math.min(255, (color & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }
}