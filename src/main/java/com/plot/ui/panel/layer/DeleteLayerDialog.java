package com.plot.ui.panel.layer;

import com.plot.api.model.ILayer;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.state.AppState;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import imgui.ImGui;
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

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();

        // 仅首次出现时居中，避免用户拖动后被下一帧重置位置
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0.0f, ImGuiCond.Appearing);

        try {
            int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize;
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("delete_layer")) {
                        hide();
                        ImGui.closeCurrentPopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderDialogContent();
                } catch (Exception e) {
                    hide();
                }
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }

        if (isVisible) {
            ImGui.openPopup(DIALOG_TITLE);
        }
    }

    private void renderDialogContent() {
        if (!selectedLayers.isEmpty()) {
            if (selectedLayers.size() == 1) {
                ILayer layer = selectedLayers.iterator().next();
                ImGui.textWrapped("确定要删除图层 \"" + layer.getName() + "\" 吗？");
            } else {
                ImGui.textWrapped("确定要删除选中的 " + selectedLayers.size() + " 个图层吗？");
            }

            DialogLayoutHelper.warningText("此操作不可撤销。");
            DialogLayoutHelper.beginFooter();
            renderButtons();
        }
    }

    private void renderButtons() {
        float contentWidth = DialogStyleManager.getContentWidth();
        DialogLayoutHelper.FooterResult action =
                DialogLayoutHelper.footerConfirmCancelCentered("取消", "删除", contentWidth);

        if (action.confirmClicked() || ImGui.isKeyPressed(ImGuiKey.Enter)) {
            try {
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

        if (action.cancelClicked() || ImGui.isKeyPressed(ImGuiKey.Escape)) {
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