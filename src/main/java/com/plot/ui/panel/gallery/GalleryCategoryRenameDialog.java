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
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 图库自定义分类重命名对话框。
 */
public class GalleryCategoryRenameDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryCategoryRenameDialog");
    private static final String DIALOG_TITLE = "gallery_rename_category##GalleryRenameCategoryPopup";

    private final GalleryRepository repository = GalleryRepository.getInstance();
    private final ImString nameInput = new ImString(64);

    private boolean visible;
    private boolean popupOpenRequested;
    private String pendingCategoryId;
    private Consumer<String> onRenamed;

    public void show(String categoryId, Consumer<String> onRenamed) {
        if (categoryId == null || categoryId.isBlank()) {
            return;
        }
        pendingCategoryId = categoryId;
        this.onRenamed = onRenamed;
        nameInput.set(categoryId);
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
        float dialogWidth = DialogStyleManager.DialogWidth.COMPACT.value;
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(dialogWidth, 0.0f, ImGuiCond.Always);

        try {
            int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoScrollbar;
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("gallery_rename_category")) {
                        closePopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderContent(dialogWidth);
                } catch (Exception e) {
                    LOGGER.error("渲染图库分类重命名对话框失败", e);
                    closePopup();
                }
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void renderContent(float dialogWidth) {
        float contentWidth = dialogWidth - DialogStyleManager.PANEL_PADDING * 2.0f;

        DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.gallery_rename_category_title"));
        DialogLayoutHelper.endSection();

        ImGui.text(PlotI18n.tr("dialog.plot.gallery_field_category"));
        ImGui.setNextItemWidth(contentWidth);
        ImGui.inputText("##gallery_category_rename", nameInput);

        DialogLayoutHelper.beginFooter();
        DialogLayoutHelper.FooterResult action = DialogLayoutHelper.footerConfirmCancelCentered(
            PlotI18n.tr("button.plot.cancel"),
            PlotI18n.tr("button.plot.confirm"),
            contentWidth);

        if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
            confirm();
        }
        if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
            closePopup();
        }
    }

    private void confirm() {
        String newName = nameInput.get().trim();
        if (newName.isEmpty()) {
            publishStatus("status.plot.gallery.category_name_required");
            return;
        }
        if (repository.isCategoryNameTaken(newName, pendingCategoryId)) {
            publishStatus("status.plot.gallery.category_name_taken");
            return;
        }
        if (!repository.renameCustomCategory(pendingCategoryId, newName)) {
            publishStatus("status.plot.gallery.category_name_taken");
            return;
        }
        publishStatus("status.plot.gallery.category_renamed", newName);
        if (onRenamed != null) {
            onRenamed.accept(newName);
        }
        closePopup();
    }

    private void closePopup() {
        visible = false;
        pendingCategoryId = null;
        onRenamed = null;
        ImGui.closeCurrentPopup();
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
