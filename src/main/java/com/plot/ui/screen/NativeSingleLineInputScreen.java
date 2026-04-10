package com.plot.ui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * 原生单行文本输入界面，用于稳定接收 Windows IME/中文输入。
 */
public class NativeSingleLineInputScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 118;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final Screen parent;
    private final int maxLength;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;

    private TextFieldWidget inputField;
    private String initialValue;
    private boolean completed;

    private int panelX;
    private int panelY;
    private int confirmX;
    private int cancelX;
    private int buttonY;
    private int buttonWidth;

    public NativeSingleLineInputScreen(Screen parent, Text title, String initialValue, int maxLength,
                                       Consumer<String> onConfirm, Runnable onCancel) {
        super(title);
        this.parent = parent;
        this.initialValue = initialValue != null ? initialValue : "";
        this.maxLength = Math.max(1, maxLength);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldX = panelX + 16;
        int fieldY = panelY + 42;
        int fieldWidth = PANEL_WIDTH - 32;

        inputField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, 18, this.title);
        inputField.setMaxLength(maxLength);
        inputField.setText(initialValue);
        inputField.setFocused(true);
        addDrawableChild(inputField);
        setFocused(inputField);

        buttonWidth = Math.max(84, this.textRenderer.getWidth("确定") + 24);
        int totalWidth = buttonWidth * 2 + BUTTON_GAP;
        confirmX = panelX + PANEL_WIDTH - 16 - totalWidth;
        cancelX = confirmX + buttonWidth + BUTTON_GAP;
        buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - 14;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEE1E1E1E);
        drawBorder(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF5A5A5A);

        context.drawTextWithShadow(this.textRenderer, this.title, panelX + 16, panelY + 14, 0xFFE8E8E8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("支持中文输入"), panelX + 16, panelY + 28, 0xFF9AA0A6);

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

        if (inputField != null) {
            inputField.setFocused(isInside(mouseX, mouseY, inputField.getX(), inputField.getY(), inputField.getWidth(), inputField.getHeight()));
            if (inputField.isFocused()) {
                this.setFocused(inputField);
            }
        }

        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (inputField != null && inputField.isFocused()) {
            if (inputField.charTyped(charInput)) {
                return true;
            }
            String committed = charInput.asString();
            if (!committed.isEmpty()) {
                inputField.write(committed);
                return true;
            }
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();

        if (keyCode == 257 || keyCode == 335) {
            confirmAndClose();
            return true;
        }
        if (keyCode == 256) {
            cancelAndClose();
            return true;
        }

        if (inputField != null && inputField.keyPressed(keyInput)) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        cancelAndClose();
    }

    private void confirmAndClose() {
        if (completed) {
            return;
        }
        completed = true;
        restoreParent();
        if (onConfirm != null) {
            onConfirm.accept(inputField != null ? inputField.getText() : initialValue);
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