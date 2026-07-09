package com.plot.ui.panel.gallery;

import com.plot.ui.component.Icons;
import com.plot.ui.component.UIComponent;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;

import static com.plot.ui.panel.gallery.GalleryPanel.GalleryItem.GalleryItemType.BLOCK;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.plot.ui.component.UIUtils;
import com.plot.PlotMod;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 图库面板，用于管理和使用预设图形
 */
public class GalleryPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryPanel");
    private static final String ADD_CATEGORY_POPUP_ID = "##plot_add_category_popup";

    private final ImString searchText;
    private String selectedCategory = CategoryType.BUILDING.name();
    private boolean initialized = false;

    // 添加自定义类别列表
    private final List<String> customCategories = new ArrayList<>();

    // 使用枚举表示分类
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

    private final GalleryManager galleryManager = new GalleryManager(); // 创建 GalleryManager 实例

    public GalleryPanel() {
        // 预留：后续可使用 AppState/EventBus 做图库选择事件派发
        AppState.getInstance();
        EventBus.getInstance();
        this.searchText = new ImString(256);
    }

    @Override
    public void init() {
        if (initialized) {
            PlotMod.LOGGER.debug("GalleryPanel已经初始化，跳过初始化流程");
            return;
        }

        try {
            PlotMod.LOGGER.info("正在初始化GalleryPanel...");

            // 异步加载图库项目
            CompletableFuture.runAsync(() -> {
                try {
                    galleryManager.loadGalleryItems();
                } catch (Exception e) {
                    PlotMod.LOGGER.error("加载图库项目失败: {}", e.getMessage(), e);
                }
            });

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
            // 设置内边距和间距
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8, 8);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);

            // 渲染搜索栏
            renderSearchBar();

            // 渲染分类
            renderCategories();

            // 渲染图库内容
            renderGalleryContent();

            // 恢复样式
            ImGui.popStyleVar(2);

        } catch (Exception e) {
            PlotMod.LOGGER.error("GalleryPanel渲染失败: {}", e.getMessage(), e);
        }
    }

    private void renderSearchBar() {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        // 添加样式设置
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8, 6);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.inputBorder);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.inputText);

        // 设置搜索框宽度为可用区域宽度减去边距
        float availableWidth = ImGui.getContentRegionAvailX() - 16; // 减去左右各8像素边距
        ImGui.setNextItemWidth(availableWidth);

        // 渲染搜索输入框
        if (UIUtils.iconInput("##search", Icons.SEARCH, PlotI18n.tr("panel.plot.gallery_search_placeholder"), searchText)) {
            // 处理搜索文本变化
            filterGalleryItems(searchText.get());
        }

        // 恢复样式
        ImGui.popStyleColor(5);
        ImGui.popStyleVar();
    }

    private void renderCategories() {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.spacing();

        // 获取所有类别（预设 + 自定义）
        List<String> presetCategoryIds = Arrays.stream(CategoryType.values())
                .map(Enum::name)
                .toList();
        List<String> allCategoryIds = new ArrayList<>(presetCategoryIds);
        allCategoryIds.addAll(customCategories);

        float availableWidth = ImGui.getContentRegionAvailX() - 16;
        float baseButtonWidth = Math.max(56.0f,
                Math.min(84.0f, (availableWidth / Math.max(1, Math.min(allCategoryIds.size(), 5))) - 4.0f));

        // 渲染所有类别按钮
        float startX = ImGui.getCursorPosX();
        float currentX = startX;

        String categoryToRemove = null;

        // 设置标签按钮样式
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 12);

        for (String categoryId : allCategoryIds) {
            boolean isCustomCategory = customCategories.contains(categoryId);
            String categoryLabel = CategoryType.getDisplayNameForId(categoryId);
            float categoryButtonWidth = getCompactCategoryButtonWidth(
                    categoryLabel, baseButtonWidth, availableWidth, isCustomCategory);

            // 检查是否需要换行
            if (currentX + categoryButtonWidth > startX + availableWidth) {
                ImGui.newLine();
                currentX = startX;
            }

            ImGui.setCursorPosX(currentX);

            // 设置标签颜色
            boolean isPresetCategory = presetCategoryIds.contains(categoryId);

            int normalColor = isPresetCategory ? theme.tabNormal : theme.buttonNormal;
            int hoveredColor = isPresetCategory ? theme.tabHovered : theme.buttonHovered;
            int activeColor = isPresetCategory ? theme.tabActive : theme.buttonActive;

            if (categoryId.equals(selectedCategory)) {
                normalColor = theme.buttonSelected;
                hoveredColor = theme.buttonSelectedHovered;
                activeColor = theme.buttonSelectedActive;
            }

            ImGui.pushStyleColor(ImGuiCol.Button, normalColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);

            // 渲染带文字的按钮
            if (ImGui.button(categoryLabel + "##" + categoryId, categoryButtonWidth, 24)) {
                selectedCategory = categoryId;
            }

            // 如果是自定义类别，添加删除按钮
            if (isCustomCategory) {
                ImGui.sameLine(0, 0);
                ImGui.setCursorPosX(ImGui.getCursorPosX() - 20);
                ImGui.pushStyleColor(ImGuiCol.Button, theme.panelBackground);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
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

        // 处理类别删除
        if (categoryToRemove != null) {
            customCategories.remove(categoryToRemove);
            if (selectedCategory.equals(categoryToRemove)) {
                selectedCategory = CategoryType.ALL.name();
            }
        }

        // 添加新类别的按钮
        float addButtonWidth = getCompactCategoryButtonWidth(PlotI18n.tr("panel.plot.gallery_add_category"), baseButtonWidth, availableWidth, false);
        if (currentX + addButtonWidth <= startX + availableWidth) {
            ImGui.setCursorPosX(currentX);
            renderAddCategoryButton(addButtonWidth);
        } else {
            ImGui.newLine();
            renderAddCategoryButton(addButtonWidth);
        }

        // 添加类别的弹出窗口
        renderAddCategoryPopup();

        ImGui.newLine();
        ImGui.separator();
    }

    private float getCompactCategoryButtonWidth(String label, float baseWidth, float maxWidth, boolean reserveDeleteSpace) {
        float preferredWidth = ImGui.calcTextSize(label).x + (reserveDeleteSpace ? 36.0f : 24.0f);
        return Math.min(maxWidth, Math.max(baseWidth, preferredWidth));
    }

    private void renderAddCategoryButton(float buttonWidth) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 12);
        
        ImGui.pushStyleColor(ImGuiCol.Button, ThemeManager.getInstance().getCurrentTheme().tabNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ThemeManager.getInstance().getCurrentTheme().tabHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ThemeManager.getInstance().getCurrentTheme().tabActive);
        
        if (ImGui.button(PlotI18n.tr("panel.plot.gallery_add_category") + "##add_category", buttonWidth, 24)) {
            ImGui.openPopup(ADD_CATEGORY_POPUP_ID);
        }
        
        ImGui.popStyleColor(3);
        ImGui.popStyleVar(2);
    }

    private void renderGalleryContent() {
        // 设置子窗口样式
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, ThemeManager.getInstance().getCurrentTheme().panelBackground);

        if (ImGui.beginChild("##gallery_content", 0, 0, true, ImGuiWindowFlags.None)) {
            List<GalleryItem> items = getFilteredItems();
            if (!items.isEmpty()) {
                // 设置表格列
                if (ImGui.beginTable("gallery_table", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg)) {
                    // 设置列宽
                    ImGui.tableSetupColumn(PlotI18n.tr("panel.plot.gallery_col_name"), ImGuiTableColumnFlags.WidthFixed, 120);
                    ImGui.tableSetupColumn(PlotI18n.tr("panel.plot.gallery_col_description"), ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn(PlotI18n.tr("panel.plot.gallery_col_actions"), ImGuiTableColumnFlags.WidthFixed, 160);
                    ImGui.tableHeadersRow();

                    // 渲染每一行
                    for (GalleryItem item : items) {
                        ImGui.tableNextRow();
                        
                        // 名称列
                        ImGui.tableNextColumn();
                        ImGui.text(item.name);
                        
                        // 描述列
                        ImGui.tableNextColumn();
                        ImGui.text(item.description);
                        
                        // 操作列
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
        
        // 放置按钮
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
        if (ImGui.button("+" + "##place" + item.id, buttonWidth, buttonHeight)) {
            handlePlaceItem(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("button.plot.place"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);
        
        // 编辑按钮
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        if (ImGui.button("✎" + "##edit" + item.id, buttonWidth, buttonHeight)) {
            handleEditItem(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("button.plot.edit"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);
        
        // 删除按钮
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Button, theme.errorText);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        if (ImGui.button("×" + "##delete" + item.id, buttonWidth, buttonHeight)) {
            handleDeleteItem(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("button.plot.delete"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);
        
        // 导入按钮
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Button, theme.mutedText);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        if (ImGui.button("↓" + "##import" + item.id, buttonWidth, buttonHeight)) {
            handleImportItem(item);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(PlotI18n.tr("button.plot.import"));
            ImGui.endTooltip();
        }
        ImGui.popStyleColor(2);
    }

    /**
     * 根据搜索文本过滤图库项目
     *
     * @param searchText 搜索文本
     */
    private void filterGalleryItems(String searchText) {
        try {
            LOGGER.debug("正在搜索: {}", searchText);

            // 这里应该实现实际的搜索逻辑
            // 例如：根据名称、描述或标签进行过滤

            // 更新UI以显示搜索结果
            // ...

            LOGGER.debug("搜索完成");
        } catch (Exception e) {
            LOGGER.error("搜索失败: {}", e.getMessage(), e);
        }
    }

    private void handlePlaceItem(GalleryItem item) {
        LOGGER.debug("放置图库项目: {}", item.id);
        // TODO: 实现放置逻辑
    }

    private void handleEditItem(GalleryItem item) {
        LOGGER.debug("编辑图库项目: {}", item.id);
        // TODO: 实现编辑逻辑
    }

    private void handleDeleteItem(GalleryItem item) {
        LOGGER.debug("删除图库项目: {}", item.id);
        // TODO: 实现删除逻辑，可能需要添加确认对话框
    }

    private void handleImportItem(GalleryItem item) {
        LOGGER.debug("导入图库项目: {}", item.id);
        // TODO: 实现导入逻辑
    }

    private List<GalleryItem> getFilteredItems() {
        List<GalleryItem> filteredItems = new ArrayList<>(galleryManager.galleryItems);

        if (!CategoryType.ALL.name().equals(selectedCategory)) {
            filteredItems.removeIf(item -> !item.getCategory().equals(selectedCategory));
        }

        if (!searchText.get().isEmpty()) {
            String searchStr = searchText.get().toLowerCase();
            filteredItems.removeIf(item -> 
                !item.getName().toLowerCase().contains(searchStr) &&
                !item.getDescription().toLowerCase().contains(searchStr) &&
                item.getTags().stream().noneMatch(tag -> tag.toLowerCase().contains(searchStr))
            );
        }

        return filteredItems;
    }

    // 添加新的方法：渲染添加类别的弹出窗口
    private final ImString newCategoryName = new ImString(32);
    
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
                    DialogLayoutHelper.FooterResult action =
                            DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth());

                    if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
                        String categoryName = newCategoryName.get().trim();
                        if (!categoryName.isEmpty() && !customCategories.contains(categoryName)) {
                            customCategories.add(categoryName);
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

    /**
     * 图库项目类，表示一个可以被选择和使用的预设图形
     */
    public static class GalleryItem {
        public enum GalleryItemType {
            OBJECT,  // 对象
            BLOCK    // 图块
        }

        final String id;           // 唯一标识符
        final String name;         // 显示名称
        final String description;  // 描述信息
        final String category;     // 所属分类
        private final GalleryItemType type;
        final List<String> tags;   // 标签

        GalleryItem(String id, String name, String description, String category, 
                    GalleryItemType type, List<String> tags) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.type = type;
            this.tags = tags;
        }

        GalleryItem(String id, String name, String description, String category, 
                    GalleryItemType type) {
            this(id, name, description, category, type, new ArrayList<>());
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getCategory() {
            return category;
        }

        public List<String> getTags() {
            return tags;
        }

        public GalleryItemType getType() {
            return type;
        }
    }

    public static class GalleryManager {
        private final List<GalleryItem> galleryItems = new ArrayList<>();
        private boolean initialized = false;

        public void loadGalleryItems() {
            try {
                if (initialized) {
                    LOGGER.debug("画廊项目已加载，跳过初始化");
                    return;
                }

                LOGGER.info("开始加载图库项目...");
                
                // 添加示例图库项目
                galleryItems.add(new GalleryItem(
                    "rect_block",
                    PlotI18n.tr("gallery.plot.item.rect_block.name"),
                    PlotI18n.tr("gallery.plot.item.rect_block.desc"),
                    CategoryType.BUILDING.name(),
                    BLOCK,
                    Arrays.asList("basic", "rectangle")
                ));

                galleryItems.add(new GalleryItem(
                    "circle_block",
                    PlotI18n.tr("gallery.plot.item.circle_block.name"),
                    PlotI18n.tr("gallery.plot.item.circle_block.desc"),
                    CategoryType.LANDSCAPE.name(),
                    BLOCK
                ));

                galleryItems.add(new GalleryItem(
                    "triangle_block",
                    PlotI18n.tr("gallery.plot.item.triangle_block.name"),
                    PlotI18n.tr("gallery.plot.item.triangle_block.desc"),
                    CategoryType.SYMBOL.name(),
                    BLOCK
                ));

                initialized = true;
                LOGGER.info("成功加载 {} 个图库项目", galleryItems.size());
            } catch (Exception e) {
                LOGGER.error("加载图库项目失败", e);
            }
        }

        public void dispose() {
            initialized = false;
            galleryItems.clear();
        }
    }

    public void dispose() {
        // 清理图库面板资源
        LOGGER.debug("正在清理GalleryPanel资源...");
        
        try {
            // 清理画廊管理器
            galleryManager.dispose();

            // 清理搜索文本
            if (searchText != null) {
                searchText.clear();
            }
            
            // 清理自定义类别
            customCategories.clear();

            // 重置状态
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