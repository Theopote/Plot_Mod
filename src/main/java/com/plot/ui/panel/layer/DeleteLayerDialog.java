package com.plot.ui.panel.layer;

import com.plot.api.model.ILayer;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.state.AppState;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DeleteLayerDialog {
    // === 常量定义 ===
    private static final String DIALOG_TITLE = "删除图层确认##DeleteLayerPopup";
    private static final float BUTTON_WIDTH = 120.0f;
    private static final float BUTTON_SPACING = 8.0f;

    // === 状态字段 ===
    private boolean isVisible = false;

    // === 依赖项 ===
    private final AppState appState;
    private final Set<ILayer> selectedLayers;
    private final Consumer<String> showWarningDialog;

    public DeleteLayerDialog(
            AppState appState,
            Set<ILayer> selectedLayers,
            Consumer<String> showWarningDialog) {
        this.appState = appState;
        this.selectedLayers = selectedLayers;
        this.showWarningDialog = showWarningDialog;
    }

    public void show() {
        // 检查是否只剩一个图层
        LayerManager layerManager = appState.getLayerManager();
        if (layerManager.getLayers().size() <= 1) {
            showWarningDialog.accept("不能删除仅有的图层");
            return;
        }
        
        // 检查是否有锁定的图层
        boolean hasLockedLayer = false;
        String lockedLayerName = "";
        for (ILayer layer : selectedLayers) {
            if (layer instanceof Layer && layer.isLocked()) {
                hasLockedLayer = true;
                lockedLayerName = layer.getName();
                break;
            }
        }
        
        if (hasLockedLayer) {
            showWarningDialog.accept("无法删除锁定的图层: " + lockedLayerName + "，请先解锁");
            return;
        }
        
        isVisible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    public void hide() {
        isVisible = false;
    }

    public void render() {
        if (!isVisible) {
            return;
        }

        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

        // 设置对话框位置（居中）
        ImGui.setNextWindowPos(
            ImGui.getWindowPosX() + ImGui.getWindowWidth() * 0.5f,
            ImGui.getWindowPosY() + ImGui.getWindowHeight() * 0.5f,
            ImGuiCond.Always,
            0.5f,
            0.5f
        );

        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
                try {
                    renderDialogContent();
                } catch (Exception e) {
                    hide();
                }
                ImGui.endPopup();
            }
        } finally {
            ImGui.popStyleColor(6);
        }

        if (isVisible) {
            ImGui.openPopup(DIALOG_TITLE);
        }
    }

    private void renderDialogContent() {
        if (!selectedLayers.isEmpty()) {
            // 显示确认信息
            if (selectedLayers.size() == 1) {
                ILayer layer = selectedLayers.iterator().next();
                ImGui.text("确定要删除图层 \"" + layer.getName() + "\" 吗？");
            } else {
                ImGui.text("确定要删除选中的 " + selectedLayers.size() + " 个图层吗？");
            }
            ImGui.text("此操作不可撤销。");
            ImGui.separator();

            // 按钮布局
            renderButtons();
        }
    }

    private void renderButtons() {
        // 确定按钮
        if (ImGui.button("确定", BUTTON_WIDTH, 0) || ImGui.isKeyPressed(ImGuiKey.Enter)) {
            try {
                // 再次检查是否可以删除
                LayerManager layerManager = appState.getLayerManager();
                int totalLayers = layerManager.getLayers().size();
                
                if (totalLayers <= selectedLayers.size()) {
                    showWarningDialog.accept("必须至少保留一个图层");
                    hide();
                    ImGui.closeCurrentPopup();
                    return;
                }
                
                deleteSelectedLayers();
                hide();
                ImGui.closeCurrentPopup();
            } catch (Exception e) {
                throw new RuntimeException("删除图层失败", e);
            }
        }

        ImGui.sameLine(0, BUTTON_SPACING);

        // 取消按钮
        if (ImGui.button("取消", BUTTON_WIDTH, 0) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            hide();
            ImGui.closeCurrentPopup();
        }
    }

    private void deleteSelectedLayers() {
        // 通过 AppState 获取 LayerManager
        LayerManager layerManager = appState.getLayerManager();
        
        // 再次检查是否有锁定的图层（以防在对话框打开期间图层被锁定）
        List<ILayer> layersToDelete = new ArrayList<>();
        for (ILayer layer : selectedLayers) {
            if (!(layer instanceof Layer) || !layer.isLocked()) {
                layersToDelete.add(layer);
            }
        }
        
        // 删除所有未锁定的选中图层
        for (ILayer layer : layersToDelete) {
            layerManager.removeLayer(layer);
        }
        
        // 清空选中状态
        selectedLayers.clear();

        // 如果还有其他图层，设置最后一个图层为活动图层
        List<ILayer> remainingLayers = layerManager.getLayers();
        if (!remainingLayers.isEmpty()) {
            ILayer lastLayer = remainingLayers.getLast();
            if (lastLayer instanceof Layer) {
                layerManager.setActiveLayer(lastLayer);
            }
        }
    }

    public boolean isVisible() {
        return isVisible;
    }
} 