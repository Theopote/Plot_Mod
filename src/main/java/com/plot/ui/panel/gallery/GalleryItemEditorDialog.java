package com.plot.ui.panel.gallery;

import com.plot.core.gallery.GalleryItem;
import com.plot.core.gallery.GalleryRepository;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.ui.component.Icons;
import com.plot.ui.component.UIComponent;
import com.plot.ui.component.UIUtils;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import com.plot.PlotMod;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存选中图形到图库。
 */
public class GalleryItemEditorDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryItemEditorDialog");
    private static final String SAVE_DIALOG_TITLE = "gallery_save##GallerySavePopup";

    private boolean visible;
    private boolean popupOpenRequested;
    private List<Shape> pendingSelection = List.of();
    private String defaultCategory = "BUILDING";

    private final ImString nameInput = new ImString(64);
    private final ImString descriptionInput = new ImString(256);
    private final ImString categoryInput = new ImString(32);

    public void showSave(List<Shape> selectedShapes, String defaultCategory) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            publishStatus("status.plot.gallery.save_no_selection");
            return;
        }
        pendingSelection = List.copyOf(selectedShapes);
        this.defaultCategory = defaultCategory != null ? defaultCategory : "BUILDING";
        resetInputs("", "", this.defaultCategory);
        visible = true;
        popupOpenRequested = true;
    }

    public void render() {
        if (!visible) {
            return;
        }
        if (popupOpenRequested) {
            ImGui.openPopup(SAVE_DIALOG_TITLE);
            popupOpenRequested = false;
        }
        renderDialog(SAVE_DIALOG_TITLE, PlotI18n.tr("dialog.plot.gallery_save_title"));
    }

    private void renderDialog(String popupId, String title) {
        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        float dialogWidth = DialogStyleManager.DialogWidth.STANDARD.value;
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(dialogWidth, 0.0f, ImGuiCond.Always);

        try {
            int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoScrollbar;
            if (ImGui.beginPopupModal(popupId, windowFlags)) {
                try {
                    if (DialogStyleManager.renderTopRightCloseButton("gallery_editor")) {
                        closePopup();
                        ImGui.endPopup();
                        return;
                    }
                    renderContent(title, dialogWidth);
                } catch (Exception e) {
                    LOGGER.error("渲染图库保存对话框失败", e);
                    closePopup();
                }
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void renderContent(String title, float dialogWidth) {
        float contentWidth = dialogWidth - DialogStyleManager.PANEL_PADDING * 2.0f;

        DialogLayoutHelper.beginSection(title);
        DialogLayoutHelper.helpText(PlotI18n.tr("dialog.plot.gallery_save_hint", pendingSelection.size()));
        DialogLayoutHelper.endSection();

        ImGui.text(PlotI18n.tr("dialog.plot.gallery_field_name"));
        ImGui.setNextItemWidth(contentWidth);
        ImGui.inputText("##gallery_name", nameInput);

        ImGui.text(PlotI18n.tr("dialog.plot.gallery_field_description"));
        ImGui.inputTextMultiline("##gallery_description", descriptionInput, contentWidth, 72.0f);

        ImGui.text(PlotI18n.tr("dialog.plot.gallery_field_category"));
        ImGui.setNextItemWidth(contentWidth);
        ImGui.inputText("##gallery_category", categoryInput);

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
            publishStatus("status.plot.gallery.save_name_required");
            return;
        }
        String description = descriptionInput.get().trim();
        String category = categoryInput.get().trim();
        if (category.isEmpty()) {
            category = defaultCategory;
        }

        GalleryRepository.getInstance().saveFromSelection(pendingSelection, name, description, category);
        publishStatus("status.plot.gallery.save_success", name);
        closePopup();
    }

    private void resetInputs(String name, String description, String category) {
        nameInput.set(name != null ? name : "");
        descriptionInput.set(description != null ? description : "");
        categoryInput.set(category != null ? category : "");
    }

    private void closePopup() {
        visible = false;
        pendingSelection = List.of();
        ImGui.closeCurrentPopup();
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
