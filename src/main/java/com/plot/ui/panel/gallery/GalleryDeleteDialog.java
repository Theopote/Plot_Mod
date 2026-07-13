package com.plot.ui.panel.gallery;

import com.plot.core.gallery.GalleryItem;
import com.plot.core.gallery.GalleryRepository;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图库条目删除确认对话框。
 */
public class GalleryDeleteDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryDeleteDialog");
    private static final String DIALOG_TITLE = "gallery_delete_confirm##GalleryDeletePopup";

    private boolean visible;
    private GalleryItem pendingItem;

    public void show(GalleryItem item) {
        if (item == null) {
            return;
        }
        pendingItem = item;
        visible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    public void render() {
        if (!visible || pendingItem == null) {
            return;
        }

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0.0f, ImGuiCond.Appearing);

        try {
            int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize;
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("gallery_delete")) {
                        closePopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderContent();
                } catch (Exception e) {
                    LOGGER.error("渲染图库删除对话框失败", e);
                    closePopup();
                }
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void renderContent() {
        ImGui.textWrapped(PlotI18n.tr("dialog.plot.gallery_delete_confirm", pendingItem.getDisplayName()));
        DialogLayoutHelper.warningText(PlotI18n.tr("dialog.plot.gallery_delete_warning"));

        DialogLayoutHelper.beginFooter();
        DialogLayoutHelper.FooterResult action = DialogLayoutHelper.footerConfirmCancelCentered(
            PlotI18n.tr("button.plot.cancel"),
            PlotI18n.tr("button.plot.delete"),
            DialogStyleManager.getContentWidth());

        if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
            GalleryRepository.getInstance().deleteItem(pendingItem.getId());
            closePopup();
        }
        if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
            closePopup();
        }
    }

    private void closePopup() {
        visible = false;
        pendingItem = null;
        ImGui.closeCurrentPopup();
    }
}
