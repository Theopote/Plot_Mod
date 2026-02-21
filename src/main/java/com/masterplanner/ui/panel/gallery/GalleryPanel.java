package com.masterplanner.ui.panel.gallery;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;

import static com.masterplanner.ui.panel.gallery.GalleryPanel.GalleryItem.GalleryItemType.BLOCK;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.MasterPlannerMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 图库面板，用于管理和使用预设图形
 */
public class GalleryPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/GalleryPanel");

    private final ImString searchText;
    private String selectedCategory = CategoryType.BUILDING.name;
    private boolean initialized = false;

    // 添加自定义类别列表
    private final List<String> customCategories = new ArrayList<>();

    // 使用枚举表示分类
    private enum CategoryType {
        ALL("全部"),
        BUILDING("建筑"),
        LANDSCAPE("景观"),
        SYMBOL("符号");

        public final String name;

        CategoryType(String name) {
            this.name = name;
        }
    }

    private final GalleryManager galleryManager = new GalleryManager(); // 创建 GalleryManager 实例

    public GalleryPanel() {
        // 预留：后续可使用 AppState/EventBus 做图库选择事件派发
        AppState.getInstance();
        EventBus.getInstance();
        this.searchText = new ImString(256);
        
        // 添加一些示例自定义类别
        customCategories.add("规划");
    }

    @Override
    public void init() {
        if (initialized) {
            MasterPlannerMod.LOGGER.debug("GalleryPanel已经初始化，跳过初始化流程");
            return;
        }

        try {
            MasterPlannerMod.LOGGER.info("正在初始化GalleryPanel...");

            // 异步加载图库项目
            CompletableFuture.runAsync(() -> {
                try {
                    galleryManager.loadGalleryItems();
                } catch (Exception e) {
                    MasterPlannerMod.LOGGER.error("加载图库项目失败: {}", e.getMessage(), e);
                }
            });

            initialized = true;
            MasterPlannerMod.LOGGER.info("GalleryPanel初始化完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("GalleryPanel初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void render() {
        if (!initialized) {
            MasterPlannerMod.LOGGER.debug("GalleryPanel未初始化，跳过渲染");
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
            MasterPlannerMod.LOGGER.error("GalleryPanel渲染失败: {}", e.getMessage(), e);
        }
    }

    private void renderSearchBar() {
        // 添加样式设置
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8, 6);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, ThemeManager.getInstance().getCurrentTheme().controlBackground);

        // 设置搜索框宽度为可用区域宽度减去边距
        float availableWidth = ImGui.getContentRegionAvailX() - 16; // 减去左右各8像素边距
        ImGui.setNextItemWidth(availableWidth);

        // 渲染搜索输入框
        if (UIUtils.iconInput("##search", Icons.SEARCH, "搜索...", searchText)) {
            // 处理搜索文本变化
            filterGalleryItems(searchText.get());
        }

        // 恢复样式
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }

    private void renderCategories() {
        ImGui.spacing();

        // 获取所有类别（预设 + 自定义）
        List<String> allCategories = new ArrayList<>();
        for (CategoryType category : CategoryType.values()) {
            allCategories.add(category.name);
        }
        allCategories.addAll(customCategories);

        // 计算每个按钮的宽度
        float availableWidth = ImGui.getContentRegionAvailX() - 16;
        float buttonWidth = Math.min(100, (availableWidth / allCategories.size()) - 4);
        
        // 渲染所有类别按钮
        float startX = ImGui.getCursorPosX();
        float currentX = startX;
        
        String categoryToRemove = null;

        // 设置标签按钮样式
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 12);
        
        for (String category : allCategories) {
            // 检查是否需要换行
            if (currentX + buttonWidth > startX + availableWidth) {
                ImGui.newLine();
                currentX = startX;
            }
            
            ImGui.setCursorPosX(currentX);
            
            // 设置标签颜色
            boolean isPresetCategory = Arrays.stream(CategoryType.values())
                    .anyMatch(c -> c.name.equals(category));
            
            // 修改颜色定义，使用已有的主题颜色
            int normalColor = isPresetCategory ? 
                    ThemeManager.getInstance().getCurrentTheme().buttonActive : // 预设类别使用更深的颜色
                    ThemeManager.getInstance().getCurrentTheme().buttonNormal;  // 自定义类别使用普通颜色
            
            // 如果是选中的类别，使用更深的颜色
            if (category.equals(selectedCategory)) {
                normalColor = ThemeManager.getInstance().getCurrentTheme().buttonActive;
            }
            
            ImGui.pushStyleColor(ImGuiCol.Button, normalColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ThemeManager.getInstance().getCurrentTheme().buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ThemeManager.getInstance().getCurrentTheme().buttonActive);
            
            // 渲染带文字的按钮
            if (ImGui.button(category + "##" + category, buttonWidth, 24)) {
                selectedCategory = category;
            }
            
            // 如果是自定义类别，添加删除按钮
            if (customCategories.contains(category)) {
                ImGui.sameLine(0, 0);
                ImGui.setCursorPosX(ImGui.getCursorPosX() - 20);
                ImGui.pushStyleColor(ImGuiCol.Button, 0);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ThemeManager.getInstance().getCurrentTheme().buttonHovered);
                if (ImGui.button("×##delete" + category, 16, 16)) {
                    categoryToRemove = category;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text("删除类别");
                    ImGui.endTooltip();
                }
                ImGui.popStyleColor(2);
            }
            
            ImGui.popStyleColor(3);

            currentX += buttonWidth + 4;
            
            if (currentX < startX + availableWidth) {
                ImGui.sameLine();
            }
        }
        
        ImGui.popStyleVar(2);

        // 处理类别删除
        if (categoryToRemove != null) {
            customCategories.remove(categoryToRemove);
            if (selectedCategory.equals(categoryToRemove)) {
                selectedCategory = CategoryType.ALL.name;
            }
        }

        // 添加新类别的按钮
        if (currentX + buttonWidth <= startX + availableWidth) {
            ImGui.setCursorPosX(currentX);
            renderAddCategoryButton(buttonWidth);
        } else {
            ImGui.newLine();
            renderAddCategoryButton(buttonWidth);
        }

        // 添加类别的弹出窗口
        renderAddCategoryPopup();

        ImGui.newLine();
        ImGui.separator();
    }

    private void renderAddCategoryButton(float buttonWidth) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 12);
        
        ImGui.pushStyleColor(ImGuiCol.Button, ThemeManager.getInstance().getCurrentTheme().buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ThemeManager.getInstance().getCurrentTheme().buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ThemeManager.getInstance().getCurrentTheme().buttonActive);
        
        if (ImGui.button("+ 添加类别##add_category", buttonWidth, 24)) {
            ImGui.openPopup("添加类别");
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
                    ImGui.tableSetupColumn("名称", ImGuiTableColumnFlags.WidthFixed, 120);
                    ImGui.tableSetupColumn("描述", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn("操作", ImGuiTableColumnFlags.WidthFixed, 160);
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
                ImGui.text("没有找到符合条件的图库项目");
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
            ImGui.text("放置");
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
            ImGui.text("编辑");
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
            ImGui.text("删除");
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
            ImGui.text("导入");
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

        if (!CategoryType.ALL.name.equals(selectedCategory)) {
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
        if (ImGui.beginPopup("添加类别")) {
            ImGui.text("输入新类别名称：");
            ImGui.inputText("##new_category", newCategoryName);
            
            if (ImGui.button("确定")) {
                String categoryName = newCategoryName.get().trim();
                if (!categoryName.isEmpty() && !customCategories.contains(categoryName)) {
                    customCategories.add(categoryName);
                    newCategoryName.clear();
                }
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("取消")) {
                newCategoryName.clear();
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
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
                    "矩形块", 
                    "基础矩形图块", 
                    CategoryType.BUILDING.name, 
                    BLOCK, 
                    Arrays.asList("基础", "矩形")
                ));
                
                galleryItems.add(new GalleryItem(
                    "circle_block", 
                    "圆形块", 
                    "基础圆形图块", 
                    CategoryType.LANDSCAPE.name, 
                    BLOCK
                ));
                
                galleryItems.add(new GalleryItem(
                    "triangle_block", 
                    "三角形", 
                    "三角形图块", 
                    CategoryType.SYMBOL.name, 
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
            selectedCategory = CategoryType.ALL.name;
            
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