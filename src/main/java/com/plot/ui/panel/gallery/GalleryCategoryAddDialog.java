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
 * 图库自定义分类新增对话框。
 */
public class GalleryCategoryAddDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryCategoryAddDialog");
    private static final String DIALOG_TITLE = "gallery_add_category##GalleryAddCategoryPopup";

    private final GalleryRepository repository = GalleryRepository.getInstance();
    private final ImString nameInput = new ImString(64);

    private boolean visible;
    private boolean popupOpenRequested;
    private Consumer<String> onAdded;

    public void show(Consumer<String> onAdded) {
        this.onAdded = onAdded;
        nameInput.set(suggestDefaultName());
        visible = true;
        popupOpenRequested = true;
    }

    public void render() {
        if (!visible) {
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
                    if (DialogStyleManager.renderTopRightCloseButton("gallery_add_category")) {
                        closePopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderContent(dialogWidth);
                } catch (Exception e) {
                    LOGGER.error("渲染图库分类新增对话框失败", e);
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

        DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.gallery_add_category_title"));
        DialogLayoutHelper.endSection();

        ImGui.text(PlotI18n.tr("dialog.plot.gallery_field_category"));
        ImGui.setNextItemWidth(contentWidth);
        ImGui.inputText("##gallery_category_add", nameInput);

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
        String name = nameInput.get().trim();
        if (name.isEmpty()) {
            publishStatus("status.plot.gallery.category_name_required");
            return;
        }
        if (repository.isCategoryNameTaken(name, null)) {
            publishStatus("status.plot.gallery.category_name_taken");
            return;
        }
        repository.addCustomCategory(name);
        publishStatus("status.plot.gallery.category_added", name);
        if (onAdded != null) {
            onAdded.accept(name);
        }
        closePopup();
    }

    private String suggestDefaultName() {
        int index = repository.getCustomCategories().size() + 1;
        String name = PlotI18n.tr("gallery.plot.category.default_name", index);
        int suffix = index;
        while (repository.isCategoryNameTaken(name, null)) {
            name = PlotI18n.tr("gallery.plot.category.default_name", ++suffix);
        }
        return name;
    }

    private void closePopup() {
        visible = false;
        onAdded = null;
        ImGui.closeCurrentPopup();
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
