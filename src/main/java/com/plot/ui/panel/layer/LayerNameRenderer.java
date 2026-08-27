package com.plot.ui.panel.layer;

import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.utils.PlotI18n;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * 图层名称渲染器
 * 负责渲染和处理图层名称的显示和编辑
 */
public class LayerNameRenderer {
    private static final Logger LOGGER = LogManager.getLogger(LayerNameRenderer.class);

    private static final float BORDER_THICKNESS = 1.0f;
    private static final float ACTIVE_MARKER_WIDTH = 3.0f;
    private static final float TEXT_PADDING_X = 4.0f;
    private static final long DOUBLE_CLICK_THRESHOLD = 500;

    private final ImString nameBuffer = new ImString(1024);
    private String currentEditingLayerId = null;
    private boolean setFocus = false;
    private boolean isEditing = false;
    /** 忽略编辑开始当帧的点击，避免立刻结束编辑 */
    private int ignoreOutsideClickFrames = 0;

    private long lastClickTime = 0;
    private String lastClickedLayerId = null;

    private final LayerManager layerManager;
    private final Consumer<String> showWarningDialog;

    public LayerNameRenderer(
            LayerManager layerManager,
            Consumer<String> showWarningDialog) {
        this.layerManager = layerManager;
        this.showWarningDialog = showWarningDialog;
    }

    /**
     * 渲染图层名称，支持双击编辑
     */
    public void render(Layer layer, float width, float height, boolean isActive, boolean isSelected) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

        if (layer == null) {
            LOGGER.warn("尝试渲染空图层名称");
            return;
        }

        if (width <= 0.0f || height <= 0.0f) {
            LOGGER.warn("图层名称渲染尺寸无效: width={}, height={}, 图层: {}", width, height, layer.getName());
            return;
        }

        float originalX = ImGui.getCursorPosX();
        float originalY = ImGui.getCursorPosY();

        float scrollY = ImGui.getScrollY();
        float windowX = ImGui.getWindowPosX();
        float windowY = ImGui.getWindowPosY();

        // 绘制边框与活动标记（不依赖控件，避免抢占输入框交互）
        ImGui.getWindowDrawList().addRect(
                originalX + windowX,
                originalY + windowY - scrollY,
                originalX + windowX + width,
                originalY + windowY - scrollY + height,
                ImGui.getColorU32(ImGuiCol.Border),
                0.0f,
                0,
                BORDER_THICKNESS
        );

        if (isActive) {
            ImGui.getWindowDrawList().addRectFilled(
                    originalX + windowX,
                    originalY + windowY - scrollY,
                    originalX + windowX + ACTIVE_MARKER_WIDTH,
                    originalY + windowY - scrollY + height,
                    theme.accent
            );
        }

        float textPaddingLeft = TEXT_PADDING_X + (isActive ? ACTIVE_MARKER_WIDTH : 0);
        float availableTextWidth = Math.max(0.0f, width - TEXT_PADDING_X - textPaddingLeft);

        if (isEditing && layer.getId().equals(currentEditingLayerId)) {
            handleEditing(layer, originalX, originalY, width, height, textPaddingLeft, availableTextWidth);
        } else {
            renderDisplayName(layer, originalX, originalY, width, height,
                    textPaddingLeft, availableTextWidth, isSelected, theme);
        }

        ImGui.setCursorPos(originalX + width, originalY);
    }

    private void renderDisplayName(
            Layer layer,
            float originalX,
            float originalY,
            float width,
            float height,
            float textPaddingLeft,
            float availableTextWidth,
            boolean isSelected,
            UITheme.ThemeColors theme) {
        ImGui.setCursorPos(originalX, originalY);
        ImGui.invisibleButton("##layername_area_" + layer.getId(), width, height);
        boolean isHovered = ImGui.isItemHovered();

        int textColor;
        if (layer.isLocked()) {
            textColor = ImGui.getColorU32(ImGuiCol.TextDisabled);
        } else if (isSelected) {
            textColor = theme.text;
        } else {
            textColor = ImGui.getColorU32(ImGuiCol.Text);
        }

        float textHeight = ImGui.getTextLineHeight();
        float textY = originalY + (height - textHeight) * 0.5f;
        float textX = originalX + textPaddingLeft;
        float textScreenX = textX + ImGui.getWindowPosX();
        float textScreenY = textY + ImGui.getWindowPosY() - ImGui.getScrollY();

        String displayName = PlotI18n.layerDisplayName(layer.getName());
        if (ImGui.calcTextSize(displayName).x > availableTextWidth) {
            displayName = truncateWithEllipsis(displayName, availableTextWidth);
        }

        ImGui.getWindowDrawList().addText(textScreenX, textScreenY, textColor, displayName);

        if (isHovered && !layer.isLocked()) {
            if (ImGui.isMouseClicked(0)) {
                long currentTime = System.currentTimeMillis();
                String layerId = layer.getId();
                if (lastClickedLayerId != null
                        && lastClickedLayerId.equals(layerId)
                        && (currentTime - lastClickTime) <= DOUBLE_CLICK_THRESHOLD) {
                    startEditing(layer);
                    lastClickTime = 0;
                    lastClickedLayerId = null;
                } else {
                    lastClickTime = currentTime;
                    lastClickedLayerId = layerId;
                }
            }

            if (ImGui.isMouseDoubleClicked(0)) {
                startEditing(layer);
            }
        }
    }

    /**
     * 在名称区域内直接渲染输入框，避免嵌套 beginChild 被父行高度裁剪导致无法编辑。
     */
    private void handleEditing(
            Layer layer,
            float x,
            float y,
            float width,
            float height,
            float textPaddingLeft,
            float availableTextWidth) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

        float screenX = x + ImGui.getWindowPosX();
        float screenY = y + ImGui.getWindowPosY() - ImGui.getScrollY();

        ImGui.getWindowDrawList().addRectFilled(
                screenX + 1,
                screenY + 1,
                screenX + width - 1,
                screenY + height - 1,
                theme.buttonHovered
        );
        ImGui.getWindowDrawList().addRect(
                screenX + 1,
                screenY + 1,
                screenX + width - 1,
                screenY + height - 1,
                theme.accent,
                0.0f,
                0,
                1.5f
        );

        float framePadY = Math.max(0.0f, (height - ImGui.getFontSize()) * 0.5f);

        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, theme.buttonSelected);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, textPaddingLeft, framePadY);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0.0f);

        try {
            ImGui.setCursorPos(x, y);
            ImGui.setNextItemWidth(width);

            if (setFocus) {
                ImGui.setKeyboardFocusHere();
                setFocus = false;
            }

            boolean enterPressed = ImGui.inputText(
                    "##edit_" + layer.getId(),
                    nameBuffer,
                    ImGuiInputTextFlags.EnterReturnsTrue
                            | ImGuiInputTextFlags.AutoSelectAll
                            | ImGuiInputTextFlags.CharsNoBlank
            );

            boolean inputActive = ImGui.isItemActive();
            boolean inputHovered = ImGui.isItemHovered();

            if (ignoreOutsideClickFrames > 0) {
                ignoreOutsideClickFrames--;
            }

            boolean finished = false;
            boolean canceled = false;

            if (enterPressed || ImGui.isKeyPressed(ImGuiKey.Enter)) {
                finished = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                canceled = true;
            } else if (ignoreOutsideClickFrames == 0
                    && ImGui.isMouseClicked(0)
                    && !inputHovered
                    && !inputActive) {
                finished = true;
            }

            if (finished) {
                applyNameChange(layer);
            } else if (canceled) {
                cancelEditing(layer.getId());
            }
        } finally {
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(5);
        }

        // 占位，保持名称区域宽度，避免后续控件错位
        ImGui.setCursorPos(x, y);
        ImGui.dummy(width, height);
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void startEditing(Layer layer) {
        if (layer == null) {
            LOGGER.warn("尝试编辑空图层");
            return;
        }

        if (layer.isLocked()) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.locked_no_rename"));
            return;
        }

        if (isEditing && currentEditingLayerId != null && !currentEditingLayerId.equals(layer.getId())) {
            cancelEditing(currentEditingLayerId);
        }

        currentEditingLayerId = layer.getId();
        isEditing = true;
        nameBuffer.clear();
        nameBuffer.set(layer.getName());
        setFocus = true;
        // 双击/菜单触发当帧及随后一两帧忽略“点外部结束”，防止立刻退出
        ignoreOutsideClickFrames = 2;
        LOGGER.info("开始编辑图层名称: '{}'", layer.getName());
    }

    private void applyNameChange(Layer layer) {
        String newName = nameBuffer.get().trim();

        if (newName.isEmpty()) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.name_empty"));
            nameBuffer.set(layer.getName());
            return;
        }

        int nameLength = calculateDisplayLength(newName);
        if (nameLength > 50) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.name_too_long"));
            nameBuffer.set(layer.getName());
            return;
        }

        if (!newName.equals(layer.getName()) && layerManager.isNameExists(newName)) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.name_exists"));
            nameBuffer.set(layer.getName());
            return;
        }

        if (!newName.equals(layer.getName())) {
            String oldName = layer.getName();
            layerManager.updateLayerProperty(layer, "name", newName);
            LayerEditHistory.commitProperty(layer.getId(), "name", oldName, newName);
            LOGGER.info("更新图层名称: '{}' -> '{}'", oldName, newName);
        }

        cancelEditing(layer.getId());
    }

    private int calculateDisplayLength(String text) {
        if (text == null) {
            return 0;
        }

        int length = 0;
        for (char c : text.toCharArray()) {
            if (c > 127) {
                length += 2;
            } else {
                length += 1;
            }
        }
        return length;
    }

    private void cancelEditing(String layerId) {
        currentEditingLayerId = null;
        isEditing = false;
        setFocus = false;
        ignoreOutsideClickFrames = 0;
        nameBuffer.clear();
        LOGGER.debug("取消编辑图层: {}", layerId);
    }

    private String truncateWithEllipsis(String text, float maxWidth) {
        if (ImGui.calcTextSize(text).x <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int length = text.length();

        while (length > 0) {
            String truncated = text.substring(0, length) + ellipsis;
            if (ImGui.calcTextSize(truncated).x <= maxWidth) {
                return truncated;
            }
            length--;
        }

        return ellipsis;
    }
}
