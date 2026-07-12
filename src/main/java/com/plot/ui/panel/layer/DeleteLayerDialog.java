package com.plot.ui.panel.layer;

import com.plot.api.model.ILayer;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.state.AppState;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DeleteLayerDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/DeleteLayerDialog");
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
        String blockReason = getDeletionBlockReason();
        if (blockReason != null) {
            showWarningDialog.accept(blockReason);
            return;
        }

        isVisible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    public void hide() {
        isVisible = false;
    }

    private void closePopup() {
        hide();
        ImGui.closeCurrentPopup();
    }

    private String getDeletionBlockReason() {
        LayerManager layerManager = appState.getLayerManager();
        if (layerManager.getLayers().size() <= 1) {
            return PlotI18n.tr("layer.plot.cannot_delete_only");
        }

        if (selectedLayers == null || selectedLayers.isEmpty()) {
            return PlotI18n.tr("layer.plot.select_to_delete");
        }

        if (layerManager.getLayers().size() <= selectedLayers.size()) {
            return PlotI18n.tr("layer.plot.must_keep_one");
        }

        for (ILayer layer : selectedLayers) {
            if (layer instanceof Layer && layer.isLocked()) {
                return PlotI18n.tr("layer.plot.cannot_delete_locked", PlotI18n.layerDisplayName(layer.getName()));
            }
        }

        return null;
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
                        closePopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderDialogContent();
                } catch (Exception e) {
                    LOGGER.error("渲染删除图层确认对话框时发生错误", e);
                    closePopup();
                }
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }

    }

    private void renderDialogContent() {
        if (!selectedLayers.isEmpty()) {
            if (selectedLayers.size() == 1) {
                ILayer layer = selectedLayers.iterator().next();
                ImGui.textWrapped(PlotI18n.tr("layer.plot.delete_confirm_single", PlotI18n.layerDisplayName(layer.getName())));
            } else {
                ImGui.textWrapped(PlotI18n.tr("layer.plot.delete_confirm_multiple", selectedLayers.size()));
            }

            DialogLayoutHelper.warningText(PlotI18n.tr("dialog.plot.delete_layer_warning"));
            DialogLayoutHelper.beginFooter();
            renderButtons();
        }
    }

    private void renderButtons() {
        float contentWidth = DialogStyleManager.getContentWidth();
        DialogLayoutHelper.FooterResult action =
                DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.delete"), contentWidth);

        if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
            try {
                String blockReason = getDeletionBlockReason();
                if (blockReason != null) {
                    showWarningDialog.accept(blockReason);
                    closePopup();
                    return;
                }

                deleteSelectedLayers();
                closePopup();
            } catch (Exception e) {
                LOGGER.error("删除图层失败", e);
                showWarningDialog.accept(PlotI18n.tr("layer.plot.delete_failed"));
                closePopup();
            }
        }

        if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
            closePopup();
        }
    }

    private void deleteSelectedLayers() {
        LayerStructureSnapshot before = LayerStructureSnapshot.capture();

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

        LayerEditHistory.commitStructureEdit(
                before,
                LayerStructureSnapshot.capture(),
                "history.plot.layer_structure.delete");
    }

    public boolean isVisible() {
        return isVisible;
    }
} 