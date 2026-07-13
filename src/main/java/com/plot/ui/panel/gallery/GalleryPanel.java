package com.plot.ui.panel.gallery;

import com.plot.core.gallery.GalleryItem;
import com.plot.core.gallery.GalleryRepository;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.component.UIComponent;
import com.plot.ui.component.UIUtils;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import com.plot.PlotMod;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 图库面板：预设建筑平面、保存选中图形、放置到画布、删除确认。
 */
public class GalleryPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryPanel");
    private static final String ADD_CATEGORY_POPUP_ID = "##plot_add_category_popup";

    private final AppState appState = AppState.getInstance();
    private final GalleryRepository repository = GalleryRepository.getInstance();
    private final GalleryPlaceSession placeSession = new GalleryPlaceSession();
    private final GalleryDeleteDialog deleteDialog = new GalleryDeleteDialog();
    private final GalleryItemEditorDialog editorDialog = new GalleryItemEditorDialog();

    private final ImString searchText = new ImString(256);
    private final ImString newCategoryName = new ImString(32);

    private String selectedCategory = CategoryType.BUILDING.name();
    private boolean initialized;

    private enum CategoryType {
        ALL("gallery.plot.category.all"),
        BUILDING("gallery.plot.category.building"),
        LANDSCAPE("gallery.plot.category.landscape"),
        SYMBOL("gallery.plot.category.symbol");

        private final String i18nKey;

        CategoryType(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String getDisplayName() {
            return PlotI18n.tr(i18nKey);
        }

        static CategoryType fromId(String id) {
            if (id == null) {
                return null;
            }
            try {
                return CategoryType.valueOf(id);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        static String getDisplayNameForId(String categoryId) {
            CategoryType preset = fromId(categoryId);
            return preset != null ? preset.getDisplayName() : categoryId;
        }
    }

    public GalleryPanel() {
    }

    @Override
    public void init() {
        if (initialized) {
            PlotMod.LOGGER.debug("GalleryPanel已经初始化，跳过初始化流程");
            return;
        }
        try {
            PlotMod.LOGGER.info("正在初始化GalleryPanel...");
            repository.load();
            initialized = true;
            PlotMod.LOGGER.info("GalleryPanel初始化完成");
        } catch (Exception e) {
            PlotMod.LOGGER.error("GalleryPanel初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void render() {
        if (!initialized) {
            PlotMod.LOGGER.debug("GalleryPanel未初始化，跳过渲染");
            return;
        }

        try {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8, 8);
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 8, 8);

            renderSearchBar();
            renderSaveToolbar();
            renderCategories();
            renderGalleryContent();
            renderPlaceHint();

            deleteDialog.render();
            editorDialog.render();
            placeSession.tick(appState);

            ImGui.popStyleVar(2);
        } catch (Exception e) {
            PlotMod.LOGGER.error("GalleryPanel渲染失败: {}", e.getMessage(), e);
        }
    }

    private void renderSearchBar() {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 8, 6);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Border, theme.inputBorder);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, theme.inputText);

        float availableWidth = ImGui.getContentRegionAvailX() - 16;
        ImGui.setNextItemWidth(availableWidth);
        UIUtils.iconInput("##search", Icons.SEARCH, PlotI18n.tr("panel.plot.gallery_search_placeholder"), searchText);

        ImGui.popStyleColor(5);
        ImGui.popStyleVar();
    }

    private void renderSaveToolbar() {
        ImGui.spacing();
        var theme = ThemeManager.getInstance().getCurrentTheme();
        int selectedCount = appState.getSelectedShapes().size();

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.buttonSelected);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
        if (ImGui.button(PlotI18n.tr("panel.plot.gallery_save_selection") + "##save_selection")) {
            editorDialog.showSave(appState.getSelectedShapes(), selectedCategory);
        }
        ImGui.popStyleColor(2);

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("panel.plot.gallery_save_selection_hint", selectedCount));
            ImGui.endTooltip();
        }

        if (placeSession.isActive() && placeSession.getPendingItem() != null) {
            ImGui.sameLine();
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.warningText);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonHovered);
            if (ImGui.button(PlotI18n.tr("button.plot.cancel") + "##cancel_place")) {
                placeSession.cancel();
            }
            ImGui.popStyleColor(2);
        }
    }

    private void renderPlaceHint() {
        if (!placeSession.isActive() || placeSession.getPendingItem() == null) {
            return;
        }
        ImGui.spacing();
        var theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.textColored(theme.warningText, PlotI18n.tr(
            "status.plot.gallery.place_active",
            placeSession.getPendingItem().getDisplayName()));
    }

    private void renderCategories() {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.spacing();

        List<String> presetCategoryIds = Arrays.stream(CategoryType.values())
            .map(Enum::name)
            .toList();
        List<String> allCategoryIds = new ArrayList<>(presetCategoryIds);
        allCategoryIds.addAll(repository.getCustomCategories());

        float availableWidth = ImGui.getContentRegionAvailX() - 16;
        float baseButtonWidth = Math.max(56.0f,
            Math.min(84.0f, (availableWidth / Math.max(1, Math.min(allCategoryIds.size(), 5))) - 4.0f));

        float startX = ImGui.getCursorPosX();
        float currentX = startX;
        String categoryToRemove = null;

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 12);

        for (String categoryId : allCategoryIds) {
            boolean isCustomCategory = repository.getCustomCategories().contains(categoryId);
            String categoryLabel = CategoryType.getDisplayNameForId(categoryId);
            float categoryButtonWidth = getCompactCategoryButtonWidth(
                categoryLabel, baseButtonWidth, availableWidth, isCustomCategory);

            if (currentX + categoryButtonWidth > startX + availableWidth) {
                ImGui.newLine();
                currentX = startX;
            }

            ImGui.setCursorPosX(currentX);
            boolean isPresetCategory = presetCategoryIds.contains(categoryId);

            int normalColor = isPresetCategory ? theme.tabNormal : theme.buttonNormal;
            int hoveredColor = isPresetCategory ? theme.tabHovered : theme.buttonHovered;
            int activeColor = isPresetCategory ? theme.tabActive : theme.buttonActive;

            if (categoryId.equals(selectedCategory)) {
                normalColor = theme.buttonSelected;
                hoveredColor = theme.buttonSelectedHovered;
                activeColor = theme.buttonSelectedActive;
            }

            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, normalColor);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, hoveredColor);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, activeColor);

            if (ImGui.button(categoryLabel + "##" + categoryId, categoryButtonWidth, 24)) {
                selectedCategory = categoryId;
            }

            if (isCustomCategory) {
                ImGui.sameLine(0, 0);
                ImGui.setCursorPosX(ImGui.getCursorPosX() - 20);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.panelBackground);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonHovered);
                if (ImGui.button("×##delete" + categoryId, 16, 16)) {
                    categoryToRemove = categoryId;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text(PlotI18n.tr("panel.plot.gallery_delete_category"));
                    ImGui.endTooltip();
                }
                ImGui.popStyleColor(2);
            }

            ImGui.popStyleColor(3);
            currentX += categoryButtonWidth + 4;
            if (currentX < startX + availableWidth) {
                ImGui.sameLine();
            }
        }

        ImGui.popStyleVar(2);

        if (categoryToRemove != null) {
            repository.removeCustomCategory(categoryToRemove);
            if (selectedCategory.equals(categoryToRemove)) {
                selectedCategory = CategoryType.ALL.name();
            }
        }

        float addButtonWidth = getCompactCategoryButtonWidth(
            PlotI18n.tr("panel.plot.gallery_add_category"), baseButtonWidth, availableWidth, false);
        if (currentX + addButtonWidth <= startX + availableWidth) {
            ImGui.setCursorPosX(currentX);
            renderAddCategoryButton(addButtonWidth);
        } else {
            ImGui.newLine();
            renderAddCategoryButton(addButtonWidth);
        }

        renderAddCategoryPopup();
        ImGui.newLine();
        ImGui.separator();
    }

    private float getCompactCategoryButtonWidth(String label, float baseWidth, float maxWidth, boolean reserveDeleteSpace) {
        float preferredWidth = ImGui.calcTextSize(label).x + (reserveDeleteSpace ? 36.0f : 24.0f);
        return Math.min(maxWidth, Math.max(baseWidth, preferredWidth));
    }

    private void renderAddCategoryButton(float buttonWidth) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 12);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.tabNormal);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.tabHovered);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, theme.tabActive);
        if (ImGui.button(PlotI18n.tr("panel.plot.gallery_add_category") + "##add_category", buttonWidth, 24)) {
            ImGui.openPopup(ADD_CATEGORY_POPUP_ID);
        }
        ImGui.popStyleColor(3);
        ImGui.popStyleVar(2);
    }

    private void renderGalleryContent() {
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ChildRounding, 0);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ChildBorderSize, 1);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, ThemeManager.getInstance().getCurrentTheme().panelBackground);

        if (ImGui.beginChild("##gallery_content", 0, 0, true, ImGuiWindowFlags.None)) {
            List<GalleryItem> items = getFilteredItems();
            if (!items.isEmpty()) {
                if (ImGui.beginTable("gallery_table", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg)) {
                    ImGui.tableSetupColumn(PlotI18n.tr("panel.plot.gallery_col_name"), ImGuiTableColumnFlags.WidthFixed, 120);
                    ImGui.tableSetupColumn(PlotI18n.tr("panel.plot.gallery_col_description"), ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn(PlotI18n.tr("panel.plot.gallery_col_actions"), ImGuiTableColumnFlags.WidthFixed, 160);
                    ImGui.tableHeadersRow();

                    for (GalleryItem item : items) {
                        ImGui.tableNextRow();
                        ImGui.tableNextColumn();
                        ImGui.text(item.getDisplayName());
                        ImGui.tableNextColumn();
                        ImGui.text(item.getDisplayDescription());
                        ImGui.tableNextColumn();
                        renderItemOperations(item);
                    }
                    ImGui.endTable();
                }
            } else {
                ImGui.text(PlotI18n.tr("panel.plot.gallery_no_items"));
            }
        }
        ImGui.endChild();

        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private void renderItemOperations(GalleryItem item) {
        float buttonWidth = 35;
        float buttonHeight = 20;
        var theme = ThemeManager.getInstance().getCurrentTheme();

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.buttonSelected);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
        if (ImGui.button("+" + "##place" + item.getId(), buttonWidth, buttonHeight)) {
            placeSession.begin(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("button.plot.place"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);

        ImGui.sameLine();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonHovered);
        if (ImGui.button("✎" + "##edit" + item.getId(), buttonWidth, buttonHeight) && !item.isPreset()) {
            editorDialog.showEdit(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(item.isPreset()
                ? PlotI18n.tr("panel.plot.gallery_preset_readonly")
                : PlotI18n.tr("button.plot.edit"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);

        ImGui.sameLine();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.errorText);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonHovered);
        if (ImGui.button("×" + "##delete" + item.getId(), buttonWidth, buttonHeight)) {
            deleteDialog.show(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("button.plot.delete"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);

        ImGui.sameLine();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, theme.mutedText);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, theme.buttonHovered);
        if (ImGui.button("↓" + "##import" + item.getId(), buttonWidth, buttonHeight)) {
            repository.placeAtViewportCenter(item, appState);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("panel.plot.gallery_open_on_canvas"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);
    }

    private List<GalleryItem> getFilteredItems() {
        List<GalleryItem> filteredItems = new ArrayList<>(repository.getItems());

        if (!CategoryType.ALL.name().equals(selectedCategory)) {
            filteredItems.removeIf(item -> !item.getCategory().equals(selectedCategory));
        }

        String query = searchText.get().trim().toLowerCase();
        if (!query.isEmpty()) {
            filteredItems.removeIf(item ->
                !item.getDisplayName().toLowerCase().contains(query)
                    && !item.getDisplayDescription().toLowerCase().contains(query));
        }

        return filteredItems;
    }

    private void renderAddCategoryPopup() {
        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            if (ImGui.beginPopup(ADD_CATEGORY_POPUP_ID)) {
                try {
                    DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.new_category"));
                    DialogLayoutHelper.helpText(PlotI18n.tr("dialog.plot.new_category_hint"));
                    DialogLayoutHelper.endSection();

                    ImGui.setNextItemWidth(-1.0f);
                    ImGui.inputText("##new_category", newCategoryName);

                    DialogLayoutHelper.beginFooter();
                    DialogLayoutHelper.FooterResult action = DialogLayoutHelper.footerConfirmCancelCentered(
                        PlotI18n.tr("button.plot.cancel"),
                        PlotI18n.tr("button.plot.confirm"),
                        DialogStyleManager.getContentWidth());

                    if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
                        String categoryName = newCategoryName.get().trim();
                        if (!categoryName.isEmpty() && !repository.getCustomCategories().contains(categoryName)) {
                            repository.addCustomCategory(categoryName);
                            newCategoryName.clear();
                        }
                        ImGui.closeCurrentPopup();
                    }

                    if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
                        newCategoryName.clear();
                        ImGui.closeCurrentPopup();
                    }
                } finally {
                    ImGui.endPopup();
                }
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    public void dispose() {
        LOGGER.debug("正在清理GalleryPanel资源...");
        try {
            placeSession.cancel();
            GalleryPlacementGuard.setActive(false);
            searchText.clear();
            newCategoryName.clear();
            initialized = false;
            selectedCategory = CategoryType.ALL.name();
            LOGGER.debug("GalleryPanel资源清理完成");
        } catch (Exception e) {
            LOGGER.error("清理GalleryPanel资源时发生错误", e);
        }
    }

    @Override
    public void close() {
        dispose();
    }
}
