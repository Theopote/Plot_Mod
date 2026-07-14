package com.plot.ui.panel.gallery;

import com.plot.core.gallery.GalleryRepository;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 图库自定义分类删除确认对话框。
 */
public class GalleryCategoryDeleteDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryCategoryDeleteDialog");
    private static final String DIALOG_TITLE = "gallery_delete_category##GalleryDeleteCategoryPopup";

    private final GalleryRepository repository = GalleryRepository.getInstance();

    private boolean visible;
    private boolean popupOpenRequested;
    private String pendingCategoryId;
    private Consumer<String> onDeleted;

    public void show(String categoryId, Consumer<String> onDeleted) {
        if (categoryId == null || categoryId.isBlank()) {
            return;
        }
        pendingCategoryId = categoryId;
        this.onDeleted = onDeleted;
        visible = true;
        popupOpenRequested = true;
    }

    public void render() {
        if (!visible || pendingCategoryId == null) {
            return;
        }
        if (popupOpenRequested) {
            ImGui.openPopup(DIALOG_TITLE);
            popupOpenRequested = false;
        }

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0.0f, ImGuiCond.Appearing);

        try {
            int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize;
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("gallery_delete_category")) {
                        closePopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderContent();
                } catch (Exception e) {
                    LOGGER.error("渲染图库分类删除对话框失败", e);
                    closePopup();
                }
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void renderContent() {
        ImGui.textWrapped(PlotI18n.tr("dialog.plot.gallery_delete_category_confirm", pendingCategoryId));
        DialogLayoutHelper.warningText(PlotI18n.tr("dialog.plot.gallery_delete_category_warning"));

        DialogLayoutHelper.beginFooter();
        DialogLayoutHelper.FooterResult action = DialogLayoutHelper.footerConfirmCancelCentered(
            PlotI18n.tr("button.plot.cancel"),
            PlotI18n.tr("button.plot.delete"),
            DialogStyleManager.getContentWidth());

        if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
            if (repository.deleteCustomCategory(pendingCategoryId)) {
                publishStatus("status.plot.gallery.category_deleted", pendingCategoryId);
                if (onDeleted != null) {
                    onDeleted.accept(pendingCategoryId);
                }
            }
            closePopup();
        }
        if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
            closePopup();
        }
    }

    private void closePopup() {
        visible = false;
        pendingCategoryId = null;
        onDeleted = null;
        ImGui.closeCurrentPopup();
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
