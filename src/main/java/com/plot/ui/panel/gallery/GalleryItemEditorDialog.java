package com.plot.ui.panel.gallery;

import com.plot.core.gallery.GalleryItem;
import com.plot.core.gallery.GalleryRepository;
import com.plot.core.model.Shape;
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

import java.util.List;

/**
 * 保存选中图形到图库 / 编辑图库条目。
 */
public class GalleryItemEditorDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryItemEditorDialog");
    private static final String SAVE_DIALOG_TITLE = "gallery_save##GallerySavePopup";
    private static final String EDIT_DIALOG_TITLE = "gallery_edit##GalleryEditPopup";

    public enum Mode {
        SAVE,
        EDIT
    }

    private boolean visible;
    private Mode mode = Mode.SAVE;
    private GalleryItem editingItem;
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
        mode = Mode.SAVE;
        editingItem = null;
        pendingSelection = List.copyOf(selectedShapes);
        this.defaultCategory = defaultCategory != null ? defaultCategory : "BUILDING";
        resetInputs("", "", this.defaultCategory);
        visible = true;
        ImGui.openPopup(SAVE_DIALOG_TITLE);
    }

    public void showEdit(GalleryItem item) {
        if (item == null || item.isPreset()) {
            return;
        }
        mode = Mode.EDIT;
        editingItem = item;
        pendingSelection = List.of();
        resetInputs(item.getDisplayName(), item.getDisplayDescription(), item.getCategory());
        visible = true;
        ImGui.openPopup(EDIT_DIALOG_TITLE);
    }

    public void render() {
        if (!visible) {
            return;
        }
        if (mode == Mode.SAVE) {
            renderDialog(SAVE_DIALOG_TITLE, PlotI18n.tr("dialog.plot.gallery_save_title"));
        } else {
            renderDialog(EDIT_DIALOG_TITLE, PlotI18n.tr("dialog.plot.gallery_edit_title"));
        }
    }

    private void renderDialog(String popupId, String title) {
        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        float dialogWidth = DialogStyleManager.DialogWidth.STANDARD.value;
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        // 固定宽度；AlwaysAutoResize 会与 -1 宽输入框形成反馈，导致弹窗逐帧变窄
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
                    LOGGER.error("渲染图库编辑对话框失败", e);
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
        if (mode == Mode.SAVE) {
            DialogLayoutHelper.helpText(PlotI18n.tr("dialog.plot.gallery_save_hint", pendingSelection.size()));
        }
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

        GalleryRepository repository = GalleryRepository.getInstance();
        if (mode == Mode.SAVE) {
            repository.saveFromSelection(pendingSelection, name, description, category);
            publishStatus("status.plot.gallery.save_success", name);
        } else if (editingItem != null) {
            repository.updateItem(editingItem.getId(), name, description, category);
            publishStatus("status.plot.gallery.edit_success", name);
        }
        closePopup();
    }

    private void resetInputs(String name, String description, String category) {
        nameInput.set(name != null ? name : "");
        descriptionInput.set(description != null ? description : "");
        categoryInput.set(category != null ? category : "");
    }

    private void closePopup() {
        visible = false;
        editingItem = null;
        pendingSelection = List.of();
        ImGui.closeCurrentPopup();
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
