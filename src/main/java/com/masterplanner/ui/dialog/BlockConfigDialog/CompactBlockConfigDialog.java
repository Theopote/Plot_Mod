package com.masterplanner.ui.dialog.BlockConfigDialog;

import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.block.BlockConfigEvent;
import com.masterplanner.ui.component.BlockIconRenderer;
import com.masterplanner.ui.dialog.BlockConfigDialog.BlockCategoryManager.BlockCategory;
import com.masterplanner.utils.ImGuiUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import imgui.ImGui;
import imgui.flag.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.item.ItemStack;
// removed unused direct MC rendering imports; we now use GuiOverlayRenderer

/**
 * 紧凑型方块配置对话框
 * <p>
 * 这个类整合了原有的多个UI组件，提供了一个固定大小、高度优化的统一UI界面。
 * 它负责分类选择、方块展示、调色盘管理以及所有拖放交互。
 * <p>
 * 核心优势：
 * - 高内聚：所有相关功能集中在一个类中
 * - 低耦合：简化的状态管理，无需复杂的接口传递
 * - 维护性：修改UI或交互逻辑只需在一处进行
 * - 性能：减少对象创建和方法调用开销
 */
public class CompactBlockConfigDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/CompactBlockConfigDialog");

    // ============================================================================
    // 布局与样式常量
    // ============================================================================
    private static final int BLOCKS_PER_ROW = 12;
    private static final int DISPLAY_ROWS = 8; // [CORRECTED] 展示区显示8行
    private static final int DISPLAY_BLOCKS_PER_PAGE = BLOCKS_PER_ROW * DISPLAY_ROWS; // 96个方块/页
    private static final int MAX_PALETTE_SLOTS = 12; // [CORRECTED] 调色盘12个槽位，一行
    private static final float BLOCK_ICON_SIZE = 48.0f;  // [ENHANCED] 增大到48x48便于点击和显示
    private static final float BLOCK_SPACING = 4.0f;
    private static final float PADDING = 4.0f;

    // [REVISED] 统一所有间距，包括边距和方块间距
    // private static final float GRID_SPACING = 4.0f; // 未使用，保留注释说明

    // [CORRECTED] 精确计算窗口宽度 - 刚好容纳12个方块
    private static final float EXACT_DIALOG_WIDTH = (BLOCK_ICON_SIZE * BLOCKS_PER_ROW) +
                                                   (BLOCK_SPACING * (BLOCKS_PER_ROW - 1)) +
                                                   (PADDING * 4);

    // [CORRECTED] 计算窗口高度 - 容纳8行方块展示
    private static final float DISPLAY_AREA_HEIGHT = (BLOCK_ICON_SIZE * DISPLAY_ROWS) +
                                                     (BLOCK_SPACING * (DISPLAY_ROWS)) +
                                                     (PADDING * 2) + 50; // 预留翻页信息空间
    private static final float TITLE_BAR_HEIGHT = 20.0f;        // 自定义标题栏高度
    private static final float TOP_PANEL_HEIGHT = 88.0f;        // 顶部面板
    private static final float BOTTOM_PANEL_HEIGHT = 134.0f;     // 调色盘(一行) + 按钮 + 说明文字 (增高以容纳所有元素)
    private static final float COMPACT_DIALOG_HEIGHT = TITLE_BAR_HEIGHT + TOP_PANEL_HEIGHT + DISPLAY_AREA_HEIGHT +
                                                      BOTTOM_PANEL_HEIGHT + (PADDING * 3)+4;// 面板间间距也改为4

    // [CORRECTED] 使用计算出的尺寸
    private float currentDialogWidth = EXACT_DIALOG_WIDTH;
    private float currentDialogHeight = COMPACT_DIALOG_HEIGHT;

    // ============================================================================
    // 全局视觉样式与颜色常量 (Minecraft 主题)
    // ============================================================================

    // [NEW] Minecraft 风格主题颜色
    // private static final int THEME_COLOR_PRIMARY = ImGui.getColorU32(0.4f, 0.6f, 0.4f, 1.0f);    // 绿色主色调（未使用）
    private static final int THEME_COLOR_SECONDARY = ImGui.getColorU32(0.5f, 0.5f, 0.5f, 1.0f);  // 灰色辅助色
    // private static final int THEME_COLOR_ACCENT = ImGui.getColorU32(0.8f, 0.6f, 0.2f, 1.0f);     // 橙色强调色（未使用）

    // 交互颜色
    private static final int DROP_INDICATOR_COLOR = ImGui.getColorU32(0.1f, 1.0f, 0.2f, 0.9f);   // 绿色拖放指示
    private static final int DRAG_PLACEHOLDER_COLOR = ImGui.getColorU32(0.8f, 0.8f, 0.8f, 0.3f); // 灰色占位符
    private static final int SLOT_BORDER_COLOR = THEME_COLOR_SECONDARY;                            // 灰色边框

    // [NEW] 按钮样式颜色 - 灰色系（调暗）
    private static final int BUTTON_COLOR_NORMAL = ImGui.getColorU32(0.3f, 0.3f, 0.3f, 1.0f);      // 正常深灰色
    private static final int BUTTON_COLOR_HOVERED = ImGui.getColorU32(0.4f, 0.4f, 0.4f, 1.0f);     // 掠过中灰色
    private static final int BUTTON_COLOR_ACTIVE = ImGui.getColorU32(0.2f, 0.2f, 0.2f, 1.0f);      // 按下深灰色
    private static final int BUTTON_COLOR_SELECTED = ImGui.getColorU32(0.5f, 0.5f, 0.5f, 1.0f);    // 选中亮灰色

    // ============================================================================
    // 核心组件
    // ============================================================================
    private final EventBus eventBus;
    private final Consumer<String> showWarningDialog;
    private final BlockCategoryManager categoryManager;
    private final BlockSearchManager searchManager;
    private final BlockPreloadManager preloadManager;

    // ============================================================================
    // UI状态
    // ============================================================================
    private boolean isOpen = false;
    private BlockCategory currentCategory = BlockCategory.BUILDING_BLOCKS;
    private int displayPage = 0;
    private int blocksPerPage = DISPLAY_BLOCKS_PER_PAGE; // [FIXED] 初始化为正确的值

    // ============================================================================
    // 调色盘状态
    // ============================================================================
    private final List<Block> paletteBlocks = new ArrayList<>();

    // ============================================================================
    // 拖放状态
    // ============================================================================
    private boolean isDragging = false;
    private Block draggedBlock = null;
    private DragSource dragSource = DragSource.NONE;
    private int dragOriginIndex = -1;
    private int dropIndicatorIndex = -1;

    private enum DragSource { NONE, DISPLAY_AREA, PALETTE_AREA }

    // ============ 逐帧MC物品渲染队列（覆盖在ImGui之上） ============
    // 移除本地PendingItemDraw与队列，改为全局覆盖渲染器

    // ImGui 直绘方块图标缓存（textureId + uv）
    private final Map<Block, SpriteRenderData> spriteRenderCache = new HashMap<>();
    private final Map<Block, Integer> blockTextureCache = new HashMap<>();

    private static final class SpriteRenderData {
        final int textureId;
        final float u0;
        final float v0;
        final float u1;
        final float v1;

        SpriteRenderData(int textureId, float u0, float v0, float u1, float v1) {
            this.textureId = textureId;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }

    /**
     * [NEW] 全局访问管理器 - 用于向后兼容
     * 注意：如果外部系统可以直接持有 CompactBlockConfigDialog 实例，
     * 这个管理器可以被移除以简化API。
     */
    public static class BlockConfigManager {
        private static BlockConfigManager INSTANCE;
        private CompactBlockConfigDialog dialogInstance;

        public static BlockConfigManager getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new BlockConfigManager();
            }
            return INSTANCE;
        }

        private BlockConfigManager() {}

        public void registerDialog(CompactBlockConfigDialog dialog) {
            this.dialogInstance = dialog;
            LOGGER.debug("CompactBlockConfigDialog实例已注册到全局管理器");
        }

        // [MODIFIED] 使用 Collections.emptyList() 提升健壮性
        public List<String> getSelectedBlockIds() {
            return dialogInstance != null ? dialogInstance.getSelectedBlockIds() : Collections.emptyList();
        }

        public boolean hasSelectedBlocks() {
            return dialogInstance != null && dialogInstance.hasSelectedBlocks();
        }

        public int getSelectedBlockCount() {
            return dialogInstance != null ? dialogInstance.getSelectedBlockCount() : 0;
        }
    }

    // ============================================================================
    // 初始化
    // ============================================================================

    /**
     * 构造函数
     */
    public CompactBlockConfigDialog(AppState appState, EventBus eventBus, Consumer<String> showWarningDialog) {
        this.eventBus = eventBus;
        this.showWarningDialog = showWarningDialog;

        try {
            // 创建依赖组件 - 使用独立的try-catch块确保每个组件的错误不会影响其他组件
            LOGGER.debug("开始初始化CompactBlockConfigDialog依赖组件...");
            
            // 初始化分类管理器
            try {
                LOGGER.debug("准备创建 BlockCategoryManager...");
                this.categoryManager = new BlockCategoryManager(appState, eventBus, showWarningDialog);
                LOGGER.debug("BlockCategoryManager 初始化成功");
            } catch (Exception e) {
                LOGGER.error("BlockCategoryManager 初始化失败: {}", e.getMessage(), e);
                throw new RuntimeException("方块分类管理器初始化失败", e);
            }
            
            // 初始化搜索管理器
            try {
                this.searchManager = new BlockSearchManager(
                    new com.masterplanner.ui.dialog.BlockConfigDialog.BlockSearchService(categoryManager),
                    categoryManager,
                    currentCategory
                );
                LOGGER.debug("BlockSearchManager 初始化成功");
            } catch (Exception e) {
                LOGGER.error("BlockSearchManager 初始化失败: {}", e.getMessage(), e);
                throw new RuntimeException("方块搜索管理器初始化失败", e);
            }
            
            // 初始化预加载管理器
            try {
                this.preloadManager = new BlockPreloadManager(categoryManager);
                LOGGER.debug("BlockPreloadManager 初始化成功");
            } catch (Exception e) {
                LOGGER.error("BlockPreloadManager 初始化失败: {}", e.getMessage(), e);
                throw new RuntimeException("方块预加载管理器初始化失败", e);
            }

            // 注册到全局管理器
            try {
                BlockConfigManager.getInstance().registerDialog(this);
                LOGGER.debug("已注册到全局管理器");
            } catch (Exception e) {
                LOGGER.error("注册到全局管理器失败: {}", e.getMessage(), e);
                // 这个不是致命错误，只记录日志
            }

            LOGGER.info("CompactBlockConfigDialog 初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("CompactBlockConfigDialog 构造函数失败: {}", e.getMessage(), e);
            throw new RuntimeException("方块配置对话框初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 打开对话框
     */
    public void open() {
        try {
            LOGGER.info("=== 开始打开方块配置对话框 ===");
            
            if (isOpen) {
                LOGGER.debug("对话框已经打开，直接返回");
                return;
            }

            // 基础状态检查
            if (categoryManager == null) {
                throw new RuntimeException("categoryManager 未初始化");
            }
            if (searchManager == null) {
                throw new RuntimeException("searchManager 未初始化");
            }
            if (preloadManager == null) {
                throw new RuntimeException("preloadManager 未初始化");
            }
            
            // [NEW] Minecraft 客户端状态检查
            try {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null) {
                    throw new RuntimeException("Minecraft 客户端实例未初始化");
                }
                if (client.world == null) {
                    LOGGER.warn("Minecraft 世界未加载，某些功能可能受限");
                }
                LOGGER.debug("Minecraft 客户端状态检查通过");
            } catch (Exception e) {
                LOGGER.error("Minecraft 客户端状态检查失败: {}", e.getMessage(), e);
                throw new RuntimeException("Minecraft 客户端环境不可用: " + e.getMessage(), e);
            }

            isOpen = true;
            LOGGER.debug("设置对话框状态为已打开");

            // [MODIFIED] 验证窗口尺寸设置
            try {
                validateWindowSize();
                LOGGER.debug("窗口尺寸验证完成");
            } catch (Exception e) {
                LOGGER.error("窗口尺寸验证失败", e);
                throw new RuntimeException("窗口尺寸设置错误: " + e.getMessage(), e);
            }

            // 预加载常用方块（高优先级） — 新渲染器为无状态，直接请求预加载缓存
            try {
                LOGGER.debug("请求预加载常用方块缓存 (无状态渲染器)");
                preloadCommonBlocks();
                LOGGER.debug("常用方块预加载请求已提交");
            } catch (Exception e) {
                LOGGER.error("常用方块预加载失败: {}", e.getMessage(), e);
            }

            // 设置当前分类并预加载
            try {
                LOGGER.debug("设置当前分类并预加载...");
                setCurrentCategory(currentCategory, true);
                LOGGER.debug("分类设置完成");
            } catch (Exception e) {
                LOGGER.error("设置当前分类失败: {}", e.getMessage(), e);
                throw new RuntimeException("分类初始化失败: " + e.getMessage(), e);
            }

            LOGGER.info("=== 方块配置对话框已成功打开 (紧凑版) - 尺寸: {}x{} ===",
                       (int)currentDialogWidth, (int)currentDialogHeight);
                       
        } catch (Exception e) {
            LOGGER.error("=== 打开方块配置对话框失败 ===", e);
            isOpen = false; // 确保状态一致
            throw new RuntimeException("无法打开方块配置对话框: " + e.getMessage(), e);
        }
    }

        /**
     * [MODIFIED] 验证和调整窗口尺寸 - 保持精确对齐
     */
    private void validateWindowSize() {
        // [MODIFIED] 使用固定的精确尺寸，确保完美对齐
        currentDialogWidth = EXACT_DIALOG_WIDTH;
        currentDialogHeight = COMPACT_DIALOG_HEIGHT;

        LOGGER.debug("精确窗口尺寸: {}x{}, 展示区域高度: {}",
                    (int)currentDialogWidth, (int)currentDialogHeight,
                    (int)DISPLAY_AREA_HEIGHT);
    }

    /**
     * 关闭对话框
     */
    public void close() {
        isOpen = false;
        // [MODIFIED] 关闭时清理拖放状态，提升健壮性
        endDrag();
        LOGGER.info("方块配置对话框已关闭");
    }

    // ============================================================================
    // 主渲染循环
    // ============================================================================

    /**
     * 主渲染方法
     */
    public void render() {
        if (!isOpen) return;

        try {
            ImGui.setNextWindowSize(currentDialogWidth, currentDialogHeight, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowPos(
                    ImGui.getIO().getDisplaySizeX() / 2 - currentDialogWidth / 2,
                    ImGui.getIO().getDisplaySizeY() / 2 - currentDialogHeight / 2,
                    ImGuiCond.FirstUseEver
            );

            int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar;

            if (ImGui.begin("方块选择", windowFlags)) {
                // 手动绘制20像素高的标题栏
                renderCustomTitleBar();
                try {
                    handleGlobalShortcuts();
                    renderTopPanel();
                    renderMiddlePanel();
                    renderBottomPanel();
                    renderDragPreview();
                } catch (Exception e) {
                    LOGGER.error("渲染方块配置对话框UI时出错: {}", e.getMessage(), e);
                    ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "UI渲染错误: " + e.getMessage());
                }
            }
            ImGui.end();

            // [最终修复] 将渲染任务放在ImGui窗口渲染之后
            processBlockIconRendering();
            // 在ImGui结束后，用Minecraft的DrawContext覆盖绘制真实物品图标
            flushPendingItemDraws();

        } catch (Exception e) {
            LOGGER.error("渲染方块配置对话框窗口时发生严重错误: {}", e.getMessage(), e);
            isOpen = false;
        }
    }

    // ============================================================================
    // UI渲染方法
    // ============================================================================

    /**
     * 渲染自定义标题栏 - 20像素高度
     */
    private void renderCustomTitleBar() {
        float windowWidth = ImGui.getWindowWidth();
        
        // 获取窗口位置
        float windowX = ImGui.getWindowPos().x;
        float windowY = ImGui.getWindowPos().y;
        
        // 绘制标题栏背景
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            windowX, windowY,
            windowX + windowWidth, windowY + TITLE_BAR_HEIGHT,
            ImGui.getColorU32(0.2f, 0.2f, 0.2f, 1.0f) // 深灰色背景
        );
        
        // 绘制标题文本
        ImGui.setCursorPos(8.0f, 2.0f); // 设置文本位置
        ImGui.textColored(1.0f, 1.0f, 1.0f, 1.0f, "方块选择");
        
        // 为后续内容预留空间
        ImGui.setCursorPosY(TITLE_BAR_HEIGHT + 4.0f);
    }

    /**
     * 渲染顶部面板（分类选择器 + 搜索）
     */
    private void renderTopPanel() {
        int childFlags = ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginChild("TopPanel", -1, TOP_PANEL_HEIGHT, true, childFlags)) {
            // 设置统一的边距样式
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PADDING, PADDING);
            
            renderCategorySelector();
            ImGui.spacing();
            searchManager.renderSearchControls();
            
            ImGui.popStyleVar(1);
        }
        ImGui.endChild();
    }

    /**
     * 渲染中部面板（方块展示区）
     */
    private void renderMiddlePanel() {
        int childFlags = ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginChild("DisplayArea", -1, DISPLAY_AREA_HEIGHT, true, childFlags)) {
            // 设置统一的边距样式
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PADDING, PADDING);
            
            renderBlockDisplayArea();
            
            ImGui.popStyleVar(1);
        }
        ImGui.endChild();
    }

    /**
     * 渲染底部面板（调色盘 + 按钮）
     */
    private void renderBottomPanel() {
        int childFlags = ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginChild("BottomPanel", -1, BOTTOM_PANEL_HEIGHT, true, childFlags)) {
            // 设置统一的边距样式
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PADDING, PADDING);
            
            renderPaletteArea();
            ImGui.spacing();
            renderActionButtons();
            
            ImGui.popStyleVar(1);
        }
        ImGui.endChild();
    }

    /**
     * 渲染分类选择器
     */
    private void renderCategorySelector() {
        BlockCategory[] categories = BlockCategory.values();
        float buttonWidth = (currentDialogWidth - PADDING * 4
                - BLOCK_SPACING * 4) / 5;
        float buttonHeight = 24.0f; // 统一按钮高度

        for (int i = 0; i < categories.length; i++) {
            BlockCategory category = categories[i];
            boolean isSelected = (category == currentCategory);

            // 每行5个按钮，使用统一间距
            if (i > 0 && i % 5 != 0) {
                ImGui.sameLine(0, BLOCK_SPACING);
            }

            // [ENHANCED] 应用主题颜色
            if (isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, BUTTON_COLOR_SELECTED);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, BUTTON_COLOR_HOVERED);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, BUTTON_COLOR_ACTIVE);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, BUTTON_COLOR_NORMAL);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, BUTTON_COLOR_HOVERED);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, BUTTON_COLOR_ACTIVE);
            }

            if (ImGui.button(category.getDisplayName(), buttonWidth, buttonHeight)) {
                if (!isSelected) {
                    setCurrentCategory(category, false);
                }
            }

            ImGui.popStyleColor(3);

            // [NEW] 增强的悬停提示
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(category.getDisplayName());
                // [MODIFIED] 使用 getOrDefault 提升健壮性
                int blockCount = categoryManager.getCategorizedBlocks()
                    .getOrDefault(category, Collections.emptyList()).size();
                ImGui.text(String.format("包含 %d 个方块", blockCount));
                ImGui.endTooltip();
            }
        }
    }

    /**
     * 渲染方块展示区域
     */
    private void renderBlockDisplayArea() {
        // 获取要显示的方块列表
        List<Block> displayBlocks = getDisplayBlocks();

        // 处理鼠标中键翻页
        handlePaging(displayBlocks.size());

        // 渲染方块网格
        int renderedBlocks = renderBlockGrid(displayBlocks);

        // 渲染翻页信息
        renderPageInfo(displayBlocks.size());

        if (renderedBlocks == 0) {
            renderEmptyMessage();
        }
    }

    /**
     * [CORRECTED] 渲染方块网格 - 每行12个方块，8行布局
     */
    private int renderBlockGrid(List<Block> allBlocks) {
        if (allBlocks == null || allBlocks.isEmpty()) {
            LOGGER.debug("方块列表为空，跳过渲染");
            return 0;
        }

        // --- 过滤和分页逻辑保持不变 ---
        List<Block> validBlocks = allBlocks.stream()
                .filter(Objects::nonNull)
                .toList();

        if (validBlocks.isEmpty()) {
            LOGGER.debug("过滤后没有有效方块，跳过渲染");
            return 0;
        }

        int startIndex = displayPage * blocksPerPage;
        int endIndex = Math.min(startIndex + blocksPerPage, validBlocks.size());

        if (startIndex >= validBlocks.size()) {
            LOGGER.debug("起始索引超出范围，重置到第一页");
            displayPage = 0;
            startIndex = 0;
            endIndex = Math.min(blocksPerPage, validBlocks.size());
        }

        List<Block> pageBlocks = validBlocks.subList(startIndex, endIndex);

        // 在循环开始前获取当前的Y坐标作为我们布局的"基准线"
        // 这样可以确保我们的网格绘制在正确的位置，而不是跳到窗口顶部。
        float initialY = ImGui.getCursorPosY();

        // --- 核心改动：使用绝对定位进行渲染 ---
        for (int i = 0; i < pageBlocks.size(); i++) {
            Block block = pageBlocks.get(i);

            int row = i / BLOCKS_PER_ROW;
            int col = i % BLOCKS_PER_ROW;

            // X坐标的计算是正确的，因为它本来就应该是相对于窗口左侧的
            float posX = PADDING + col * (BLOCK_ICON_SIZE + BLOCK_SPACING);

            // [FIX] Y坐标的计算需要加上初始的Y位置
            // 新公式: Y = 初始Y + 顶部边距 + 行偏移
            float posY = initialY + PADDING + row * (BLOCK_ICON_SIZE + BLOCK_SPACING);

            // 使用 setCursorPos 进行绝对定位
            ImGui.setCursorPos(posX, posY);

            // 渲染方块槽位
            renderDisplayBlockSlot(block, i);
        }
        // --- 核心改动结束 ---

        return pageBlocks.size();
    }

    /**
     * 渲染展示区的单个方块槽位
     */
    private void renderDisplayBlockSlot(Block block, int index) {
        if (block == null) {
            LOGGER.warn("尝试渲染空方块槽位，索引: {}", index);
            return;
        }

        ImGui.pushID("display_" + index);

        try {
            // 获取槽位位置（在创建invisibleButton之前，这是正确的坐标）
            float slotX = ImGui.getCursorScreenPos().x;
            float slotY = ImGui.getCursorScreenPos().y;

            // [NEW] 分离交互逻辑
            handleDisplaySlotInteraction(block);

            // 渲染方块图标（ImGui背景 + 覆盖MC物品）
            // 使用之前获取的坐标，因为invisibleButton不会改变光标位置
            renderBlockIcon(block, slotX, slotY, ImGui.isItemHovered(), false);

            // [NEW] 添加鼠标悬停提示
            if (ImGui.isItemHovered()) {
                renderBlockTooltip(block);
            }

        } catch (Exception e) {
            LOGGER.error("渲染方块槽位时发生错误: {} (索引: {}) - {}", 
                        block.getTranslationKey(), index, e.getMessage(), e);
            
            // 渲染错误占位符
            try {
                float slotX = ImGui.getCursorScreenPos().x;
                float slotY = ImGui.getCursorScreenPos().y;
                var drawList = ImGui.getWindowDrawList();
                drawList.addRectFilled(slotX, slotY, slotX + BLOCK_ICON_SIZE, slotY + BLOCK_ICON_SIZE,
                                     ImGui.getColorU32(0.5f, 0.2f, 0.2f, 1.0f)); // 红色错误指示
                drawList.addRect(slotX, slotY, slotX + BLOCK_ICON_SIZE, slotY + BLOCK_ICON_SIZE, 
                               SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);
            } catch (Exception renderError) {
                LOGGER.error("渲染错误占位符时也出错: {}", renderError.getMessage());
            }
        } finally {
            ImGui.popID();
        }
    }

    /**
     * [CORRECTED] 渲染调色盘区域 - 单行布局
     */
    private void renderPaletteArea() {
        // 调色盘标题
        ImGui.text(String.format("调色盘: %d/%d", paletteBlocks.size(), MAX_PALETTE_SLOTS));

        if (!paletteBlocks.isEmpty()) {
            ImGui.sameLine();
            if (ImGui.smallButton("清空")) {
                paletteBlocks.clear();
                LOGGER.info("清空调色盘");
            }
        }

        // [NEW] 分离拖放处理逻辑
        handlePaletteDrop();

        // [CORRECTED] 渲染调色盘槽位 - 单行12个槽位
        // 记录起始Y用于在槽位行下方精确放置按钮/说明，避免受同一行布局影响产生额外间距
        float paletteStartY = ImGui.getCursorPosY();
        for (int i = 0; i < MAX_PALETTE_SLOTS; i++) {
            if (i > 0) {
                ImGui.sameLine(0, BLOCK_SPACING);
            }

            renderPaletteSlot(i);
        }

        // 渲染拖放指示器
        renderDropIndicator();

        // 将光标移到槽位下方，以便按钮和说明文本能够正确布局
        // 缩小此处间距以避免与按钮产生过大空隙（使用 PADDING 的一半，但保证最小为2px）
        float paletteGap = Math.max(2.0f, PADDING / 2.0f);
        ImGui.setCursorPosY(paletteStartY + BLOCK_ICON_SIZE + paletteGap);
    }

    /**
     * 渲染调色盘的单个槽位
     */
    private void renderPaletteSlot(int slotIndex) {
        ImGui.pushID("palette_" + slotIndex);

        try {
            Block blockInSlot = (slotIndex < paletteBlocks.size()) ? paletteBlocks.get(slotIndex) : null;

            // 获取槽位位置（在创建invisibleButton之前，这是正确的坐标）
            float slotX = ImGui.getCursorScreenPos().x;
            float slotY = ImGui.getCursorScreenPos().y;

            // [NEW] 分离交互逻辑
            handlePaletteSlotInteraction(slotIndex, blockInSlot);

            // 渲染槽位
            // 使用之前获取的坐标，因为invisibleButton不会改变光标位置
            boolean isPlaceholder = (isDragging && dragSource == DragSource.PALETTE_AREA && dragOriginIndex == slotIndex);
            renderBlockIcon(blockInSlot, slotX, slotY, ImGui.isItemHovered(), isPlaceholder);

            // [NEW] 添加鼠标悬停提示
            if (ImGui.isItemHovered() && blockInSlot != null) {
                renderPaletteBlockTooltip(blockInSlot, slotIndex);
            }

        } catch (Exception e) {
            LOGGER.error("渲染调色盘槽位时发生错误 (索引: {}) - {}", slotIndex, e.getMessage(), e);
            
            // 渲染错误占位符
            try {
                float slotX = ImGui.getCursorScreenPos().x;
                float slotY = ImGui.getCursorScreenPos().y;
                var drawList = ImGui.getWindowDrawList();
                drawList.addRectFilled(slotX, slotY, slotX + BLOCK_ICON_SIZE, slotY + BLOCK_ICON_SIZE,
                                     ImGui.getColorU32(0.5f, 0.2f, 0.2f, 1.0f)); // 红色错误指示
                drawList.addRect(slotX, slotY, slotX + BLOCK_ICON_SIZE, slotY + BLOCK_ICON_SIZE, 
                               SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);
            } catch (Exception renderError) {
                LOGGER.error("渲染调色盘错误占位符时也出错: {}", renderError.getMessage());
            }
        } finally {
            ImGui.popID();
        }
    }

    /**
     * [ENHANCED] 渲染操作按钮 - 动态宽度适配
     */
    private void renderActionButtons() {
        ImGui.setCursorPosY(ImGui.getCursorPosY() + PADDING);

        // [NEW] 计算动态按钮宽度
        String[] buttonTexts = {"应用选择", "取消", "重置"};
        // 使用统一间距
        float padding = PADDING * 2; // 使用统一内边距

        // 计算每个按钮的最佳宽度
        float[] buttonWidths = new float[buttonTexts.length];
        float totalTextWidth = 0;
        for (int i = 0; i < buttonTexts.length; i++) {
            buttonWidths[i] = Math.max(ImGui.calcTextSize(buttonTexts[i]).x + padding, 80.0f);
            totalTextWidth += buttonWidths[i];
        }

        float totalWidth = totalTextWidth + BLOCK_SPACING * (buttonTexts.length - 1);
        float startX = (currentDialogWidth - totalWidth) * 0.5f;

        // [NEW] 应用主题颜色样式
        ImGui.pushStyleColor(ImGuiCol.Button, BUTTON_COLOR_NORMAL);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, BUTTON_COLOR_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, BUTTON_COLOR_ACTIVE);

        ImGui.setCursorPosX(startX);

        // 应用按钮 - 使用强调色
        ImGui.pushStyleColor(ImGuiCol.Button, BUTTON_COLOR_SELECTED);
        if (ImGui.button("应用选择", buttonWidths[0], 24)) { // 统一按钮高度
            applyBlockSelection();
        }
        ImGui.popStyleColor();

        // 取消按钮
        ImGui.sameLine(0, BLOCK_SPACING);
        if (ImGui.button("取消", buttonWidths[1], 24)) { // 统一按钮高度
            close();
        }

        // 重置按钮
        ImGui.sameLine(0, BLOCK_SPACING);
        if (ImGui.button("重置", buttonWidths[2], 24)) { // 统一按钮高度
            clearPalette();
        }

        ImGui.popStyleColor(3);

        // [NEW] 添加快捷键提示
        String shortcutHint = "Enter=应用 | Esc=取消 | Ctrl+R=重置";
        float hintWidth = ImGui.calcTextSize(shortcutHint).x;
        ImGui.setCursorPosX((currentDialogWidth - hintWidth) * 0.5f);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + PADDING);
        ImGui.textDisabled(shortcutHint);
    }

    // ============================================================================
    // 渲染辅助方法
    // ============================================================================

    /**
     * [UPDATED] 渲染真实的Minecraft方块图标 - 使用纹理ID渲染
     */
    private void renderBlockIcon(Block block, float x, float y, boolean isHovered, boolean isPlaceholder) {
        var drawList = ImGui.getWindowDrawList();
        if (drawList == null) {
            LOGGER.warn("ImGui drawList 为 null，无法渲染方块图标");
            return;
        }

        try {
            if (isPlaceholder) {
                drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, DRAG_PLACEHOLDER_COLOR);
                drawList.addRect(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);
            } else if (block != null) {
                // 检查方块是否有有效图标
                boolean hasValidIcon = BlockIconRenderer.hasValidIcon(block);
                if (!hasValidIcon) {
                    // 无 Item 形态时再回退 2D 资源纹理；再失败则占位
                    if (!tryRenderBlockTextureFromResources(drawList, block, x, y, BLOCK_ICON_SIZE)) {
                        LOGGER.warn("方块 {} 无有效图标，显示占位符", Registries.BLOCK.getId(block));
                        drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, DRAG_PLACEHOLDER_COLOR);
                        drawList.addRect(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);
                    }
                    return;
                }

                // 绘制方块背景
                int backgroundColor = isHovered ? 0xFF555555 : 0xFF373737;
                drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, backgroundColor);
                drawList.addRect(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);

                // 优先 3D 物品渲染（与物品栏一致），2D 仅作为失败兜底
                float scale = BLOCK_ICON_SIZE / 16.0f;  // 48/16 = 3.0
                com.masterplanner.ui.imgui.GuiOverlayRenderer.queueBlockItem(block, x, y, scale);

                // 作为兜底（理论上不应频繁触发）
                if (LOGGER.isDebugEnabled() && !tryRenderBlockIconDirect(drawList, block, x, y, BLOCK_ICON_SIZE)) {
                    tryRenderBlockTextureFromResources(drawList, block, x, y, BLOCK_ICON_SIZE);
                }
                
            } else {
                // 只有在调试模式下才记录这个警告，避免日志污染
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("方块为 null，显示占位符");
                }
                drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, DRAG_PLACEHOLDER_COLOR);
                drawList.addRect(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);
            }
        } catch (Exception e) {
            LOGGER.error("渲染方块图标时发生错误: {}", block != null ? Registries.BLOCK.getId(block) : "null", e);
            // 显示错误占位符
            drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, 0xFFFF0000);
            drawList.addRect(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, SLOT_BORDER_COLOR, 0.0f, 0, 1.0f);
        }
    }

    private void flushPendingItemDraws() { /* 改为全局覆盖渲染，无需本地Flush */ }

    /**
     * 渲染拖放指示器
     */
    private void renderDropIndicator() {
        if (dropIndicatorIndex >= 0 && dropIndicatorIndex <= MAX_PALETTE_SLOTS) {
            float startY = ImGui.getCursorScreenPos().y - BLOCK_ICON_SIZE - PADDING;
            float indicatorX = getIndicatorX();

            // 绘制绿色竖线指示器
            ImGui.getWindowDrawList().addRectFilled(
                indicatorX, startY,
                indicatorX + 2, startY + BLOCK_ICON_SIZE,
                DROP_INDICATOR_COLOR
            );
        }
    }

    private float getIndicatorX() {
        float indicatorX;

        if (dropIndicatorIndex == 0) {
            indicatorX = ImGui.getCursorScreenPos().x - 3;
        } else if (dropIndicatorIndex >= paletteBlocks.size()) {
            float slotsWidth = paletteBlocks.size() * (BLOCK_ICON_SIZE + BLOCK_SPACING);
            indicatorX = ImGui.getCursorScreenPos().x + slotsWidth + 1;
        } else {
            indicatorX = ImGui.getCursorScreenPos().x + dropIndicatorIndex * (BLOCK_ICON_SIZE + BLOCK_SPACING) - BLOCK_SPACING / 2 - 1;
        }
        return indicatorX;
    }

    /**
     * [MODIFIED] 渲染拖动预览 - 使用 getBackgroundDrawList 避免遮挡
     */
    private void renderDragPreview() {
        if (isDragging && draggedBlock != null) {
            float mouseX = ImGui.getMousePos().x + 5;
            float mouseY = ImGui.getMousePos().y + 5;

            // 设置鼠标光标
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);

            // [MODIFIED] 使用 getBackgroundDrawList 在最顶层绘制，避免遮挡
            var bgDrawList = ImGui.getBackgroundDrawList();

            // 绘制阴影
            bgDrawList.addRectFilled(
                mouseX - 1, mouseY - 1,
                mouseX + BLOCK_ICON_SIZE + 3, mouseY + BLOCK_ICON_SIZE + 3,
                ImGui.getColorU32(0.0f, 0.0f, 0.0f, 0.5f)
            );

            // 渲染拖动的方块预览（需要手动渲染到背景层）
            renderDraggedBlockPreview(bgDrawList, draggedBlock, mouseX, mouseY);
        }
    }

    /**
     * [UPDATED] 渲染拖动方块预览到背景层 - 使用真实图标
     */
    private void renderDraggedBlockPreview(imgui.ImDrawList drawList, Block block, float x, float y) {
        try {
            // 1.21.11：BlockIconRenderer 的离屏纹理渲染暂时禁用，拖拽预览改用覆盖绘制真实物品图标
            drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, ImGui.getColorU32(0.15f, 0.15f, 0.15f, 0.6f));
            float scale = BLOCK_ICON_SIZE / 16.0f;  // [ENHANCED] 使用正确的缩放系数
            com.masterplanner.ui.imgui.GuiOverlayRenderer.queueBlockItem(block, x, y, scale);

            if (LOGGER.isDebugEnabled() && !tryRenderBlockIconDirect(drawList, block, x, y, BLOCK_ICON_SIZE)) {
                tryRenderBlockTextureFromResources(drawList, block, x, y, BLOCK_ICON_SIZE);
            }
        } catch (Exception e) {
            LOGGER.error("渲染拖动预览时发生异常: {} - {}", 
                        block != null ? Registries.BLOCK.getId(block) : "null", e.getMessage(), e);
            // 渲染错误占位符
            drawList.addRectFilled(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, ImGui.getColorU32(0.5f, 0.2f, 0.2f, 0.6f));
        }
        drawList.addRect(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 0.9f), 0.0f, 0, 2.0f);
    }

    private boolean tryRenderBlockTextureFromResources(imgui.ImDrawList drawList, Block block, float x, float y, float size) {
        if (drawList == null || block == null) {
            return false;
        }

        int textureId = getBlockTextureId(block);
        if (textureId <= 0) {
            return false;
        }

        float inset = Math.max(2.0f, size * 0.0833f);
        drawList.addImage(textureId, x + inset, y + inset, x + size - inset, y + size - inset);
        return true;
    }

    private int getBlockTextureId(Block block) {
        Integer cached = blockTextureCache.get(block);
        if (cached != null) {
            return cached;
        }

        Identifier blockId = Registries.BLOCK.getId(block);
        String namespace = blockId.getNamespace();
        String path = blockId.getPath();

        Identifier textureIdentifier = null;

        // 1) 优先用对应 Item 的贴图（和物品栏最一致）
        if (block.asItem() != Items.AIR) {
            Identifier itemId = Registries.ITEM.getId(block.asItem());
            if (itemId != null) {
                textureIdentifier = findFirstExistingTexture(itemId.getNamespace(), itemId.getPath());
            }
        }

        // 2) 方块ID直查
        if (textureIdentifier == null) {
            textureIdentifier = findFirstExistingTexture(namespace, path);
        }

        // 3) 解析 item model textures
        if (textureIdentifier == null && block.asItem() != Items.AIR) {
            Identifier itemId = Registries.ITEM.getId(block.asItem());
            textureIdentifier = findTextureFromModel(itemId, true);
        }

        // 4) 解析 block model textures
        if (textureIdentifier == null) {
            textureIdentifier = findTextureFromModel(blockId, false);
        }

        // 5) 从 blockstate -> model 递归解析贴图
        if (textureIdentifier == null) {
            textureIdentifier = findTextureFromBlockState(blockId);
        }

        // 6) 最终兜底，保证永远有图标可见
        if (textureIdentifier == null) {
            textureIdentifier = Identifier.of("minecraft", "textures/item/barrier.png");
        }

        int textureId = ImGuiUtils.getTextureId(textureIdentifier);

        blockTextureCache.put(block, textureId);
        return textureId;
    }

    private Identifier findFirstExistingTexture(String namespace, String blockPath) {
        String[] candidates = new String[] {
            "textures/item/" + blockPath + ".png",
            "textures/block/" + blockPath + ".png",
            "textures/block/" + blockPath + "_top.png",
            "textures/block/" + blockPath + "_side.png",
            "textures/block/" + blockPath + "_front.png",
            "textures/block/" + blockPath + "_0.png",
            "textures/block/" + blockPath + "_1.png",
            "textures/block/" + blockPath + "_stage0.png",
            "textures/block/" + blockPath + "_stage1.png"
        };

        for (String candidate : candidates) {
            Identifier textureId = Identifier.of(namespace, candidate);
            if (resourceExists(textureId)) {
                return textureId;
            }
        }

        return null;
    }

    private Identifier findTextureFromModel(Identifier id, boolean itemModel) {
        if (id == null) {
            return null;
        }

        String modelPath = (itemModel ? "item/" : "block/") + id.getPath();
        Identifier modelId = toModelFileIdentifier(modelPath, id.getNamespace());
        return resolveTextureFromModelFile(modelId, 0, new HashMap<>());
    }

    private Identifier findTextureFromBlockState(Identifier blockId) {
        if (blockId == null) {
            return null;
        }

        Identifier blockStateId = Identifier.of(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        JsonObject root = readJsonObject(blockStateId);
        if (root == null) {
            return null;
        }

        if (root.has("variants") && root.get("variants").isJsonObject()) {
            JsonObject variants = root.getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
                Identifier textureId = extractTextureFromModelElement(entry.getValue(), blockId.getNamespace());
                if (textureId != null) {
                    return textureId;
                }
            }
        }

        if (root.has("multipart") && root.get("multipart").isJsonArray()) {
            JsonArray multipart = root.getAsJsonArray("multipart");
            for (JsonElement partElement : multipart) {
                if (!partElement.isJsonObject()) {
                    continue;
                }
                JsonObject part = partElement.getAsJsonObject();
                if (!part.has("apply")) {
                    continue;
                }

                Identifier textureId = extractTextureFromModelElement(part.get("apply"), blockId.getNamespace());
                if (textureId != null) {
                    return textureId;
                }
            }
        }

        return null;
    }

    private Identifier extractTextureFromModelElement(JsonElement element, String defaultNamespace) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonObject()) {
            JsonObject modelObj = element.getAsJsonObject();
            if (modelObj.has("model") && modelObj.get("model").isJsonPrimitive()) {
                String modelRef = modelObj.get("model").getAsString();
                Identifier modelId = toModelFileIdentifier(modelRef, defaultNamespace);
                return resolveTextureFromModelFile(modelId, 0, new HashMap<>());
            }
            return null;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement sub : array) {
                Identifier texture = extractTextureFromModelElement(sub, defaultNamespace);
                if (texture != null) {
                    return texture;
                }
            }
        }

        return null;
    }

    private Identifier resolveTextureFromModelFile(Identifier modelFileId, int depth, Map<String, String> childTextureOverrides) {
        if (modelFileId == null || depth > 12) {
            return null;
        }

        JsonObject root = readJsonObject(modelFileId);
        if (root == null) {
            return null;
        }

        Map<String, String> merged = new HashMap<>(childTextureOverrides);

        if (root.has("textures") && root.get("textures").isJsonObject()) {
            JsonObject textures = root.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                merged.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
            }
        }

        String[] preferredKeys = new String[] { "layer0", "all", "side", "top", "front", "particle", "texture" };
        for (String key : preferredKeys) {
            String value = resolveTextureReference(merged.get(key), merged, new HashSet<>());
            Identifier textureId = toTextureIdentifier(value, modelFileId.getNamespace());
            if (textureId != null && resourceExists(textureId)) {
                return textureId;
            }
        }

        for (String valueRaw : merged.values()) {
            String value = resolveTextureReference(valueRaw, merged, new HashSet<>());
            Identifier textureId = toTextureIdentifier(value, modelFileId.getNamespace());
            if (textureId != null && resourceExists(textureId)) {
                return textureId;
            }
        }

        if (root.has("parent") && root.get("parent").isJsonPrimitive()) {
            String parentRef = root.get("parent").getAsString();
            Identifier parentModel = toModelFileIdentifier(parentRef, modelFileId.getNamespace());
            return resolveTextureFromModelFile(parentModel, depth + 1, merged);
        }

        return null;
    }

    private String resolveTextureReference(String value, Map<String, String> textures, Set<String> visited) {
        if (value == null || value.isBlank()) {
            return null;
        }

        if (!value.startsWith("#")) {
            return value;
        }

        String key = value.substring(1);
        if (!visited.add(key)) {
            return null;
        }

        String next = textures.get(key);
        if (next == null || next.isBlank()) {
            return null;
        }

        return resolveTextureReference(next, textures, visited);
    }

    private Identifier toModelFileIdentifier(String modelRef, String defaultNamespace) {
        if (modelRef == null || modelRef.isBlank()) {
            return null;
        }

        String namespace = defaultNamespace;
        String path = modelRef;

        int sep = modelRef.indexOf(':');
        if (sep > 0 && sep < modelRef.length() - 1) {
            namespace = modelRef.substring(0, sep);
            path = modelRef.substring(sep + 1);
        }

        if (path.startsWith("models/")) {
            path = path.substring("models/".length());
        }
        if (!path.endsWith(".json")) {
            path = path + ".json";
        }

        return Identifier.of(namespace, "models/" + path);
    }

    private JsonObject readJsonObject(Identifier resourceId) {
        if (resourceId == null) {
            return null;
        }

        ClassLoader classLoader = getClass().getClassLoader();
        String fullPath = "assets/" + resourceId.getNamespace() + "/" + resourceId.getPath();

        try (var stream = classLoader.getResourceAsStream(fullPath)) {
            if (stream == null) {
                return null;
            }

            JsonElement rootElement = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return rootElement.isJsonObject() ? rootElement.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Identifier toTextureIdentifier(String textureRef, String defaultNamespace) {
        if (textureRef == null || textureRef.isBlank() || textureRef.startsWith("#")) {
            return null;
        }

        String namespace = defaultNamespace;
        String path = textureRef;

        int sep = textureRef.indexOf(':');
        if (sep > 0 && sep < textureRef.length() - 1) {
            namespace = textureRef.substring(0, sep);
            path = textureRef.substring(sep + 1);
        }

        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }

        return Identifier.of(namespace, path);
    }

    private boolean resourceExists(Identifier id) {
        String fullPath = "assets/" + id.getNamespace() + "/" + id.getPath();
        try (var stream = getClass().getClassLoader().getResourceAsStream(fullPath)) {
            return stream != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryRenderBlockIconDirect(imgui.ImDrawList drawList, Block block, float x, float y, float size) {
        if (drawList == null || block == null) {
            return false;
        }

        try {
            SpriteRenderData data = getOrBuildSpriteRenderData(block);
            if (data == null || data.textureId <= 0) {
                return false;
            }

            float inset = Math.max(2.0f, size * 0.0833f);
            drawList.addImage(
                data.textureId,
                x + inset,
                y + inset,
                x + size - inset,
                y + size - inset,
                data.u0,
                data.v0,
                data.u1,
                data.v1
            );
            return true;
        } catch (Throwable t) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ImGui直绘方块图标失败: {} - {}", Registries.BLOCK.getId(block), t.getMessage());
            }
            return false;
        }
    }

    private SpriteRenderData getOrBuildSpriteRenderData(Block block) {
        SpriteRenderData cached = spriteRenderCache.get(block);
        if (cached != null) {
            return cached;
        }

        try {
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (stack == null || stack.isEmpty()) {
                return null;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return null;
            }

            Object itemRenderer = invokeNoArgs(client, "getItemRenderer");
            if (itemRenderer == null) {
                return null;
            }

            Object models = invokeNoArgs(itemRenderer, "getModels");
            if (models == null) {
                return null;
            }

            Object bakedModel = invokeGetModel(models, stack);
            if (bakedModel == null) {
                return null;
            }

            Object sprite = invokeNoArgs(bakedModel, "getParticleSprite");
            if (sprite == null) {
                return null;
            }

            float u0 = asFloat(invokeNoArgs(sprite, "getMinU"), 0.0f);
            float v0 = asFloat(invokeNoArgs(sprite, "getMinV"), 0.0f);
            float u1 = asFloat(invokeNoArgs(sprite, "getMaxU"), 1.0f);
            float v1 = asFloat(invokeNoArgs(sprite, "getMaxV"), 1.0f);

            Object atlasIdObject = invokeNoArgs(sprite, "getAtlasId");
            Identifier atlasId = atlasIdObject instanceof Identifier id
                    ? id
                    : Identifier.of("minecraft", "textures/atlas/blocks.png");

            Object textureManager = invokeNoArgs(client, "getTextureManager");
            if (textureManager == null) {
                return null;
            }

            Object atlasTexture = invokeOneArg(textureManager, "getTexture", atlasId);
            if (atlasTexture == null) {
                return null;
            }

            Object glId = invokeNoArgs(atlasTexture, "getGlId");
            if (!(glId instanceof Number idValue)) {
                return null;
            }

            SpriteRenderData built = new SpriteRenderData(idValue.intValue(), u0, v0, u1, v1);
            spriteRenderCache.put(block, built);
            return built;
        } catch (Throwable t) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("构建方块Sprite渲染数据失败: {} - {}", Registries.BLOCK.getId(block), t.getMessage());
            }
            return null;
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            var method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeOneArg(Object target, String methodName, Object arg) {
        if (target == null || arg == null) {
            return null;
        }

        try {
            for (var method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }

                Class<?> paramType = method.getParameterTypes()[0];
                if (paramType.isAssignableFrom(arg.getClass())) {
                    return method.invoke(target, arg);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }

        return null;
    }

    private static Object invokeGetModel(Object models, ItemStack stack) {
        if (models == null || stack == null) {
            return null;
        }

        try {
            for (var method : models.getClass().getMethods()) {
                if (!method.getName().equals("getModel") || method.getParameterCount() != 4) {
                    continue;
                }

                Object[] args = new Object[] { stack, null, null, 0 };
                return method.invoke(models, args);
            }
        } catch (Throwable ignored) {
            return null;
        }

        return null;
    }

    private static float asFloat(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    /**
     * 渲染空消息
     */
    private void renderEmptyMessage() {
        float textWidth = ImGui.calcTextSize("当前分类或搜索结果为空").x;
        float windowWidth = ImGui.getWindowWidth();
        ImGui.setCursorPosX((windowWidth - textWidth) * 0.5f);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + PADDING * 8); // 使用统一间距的倍数
        ImGui.textDisabled("当前分类或搜索结果为空");
    }

    /**
     * [ENHANCED] 渲染翻页信息 - 完整的分页控件
     */
    private void renderPageInfo(int totalBlocks) {
        if (totalBlocks <= blocksPerPage) {
            return; // 只有一页时不显示翻页信息
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) totalBlocks / blocksPerPage));

        ImGui.setCursorPosY(ImGui.getCursorPosY() + PADDING);

        // 计算布局
        float windowWidth = ImGui.getWindowWidth();
        float buttonWidth = 30.0f;
        // 使用统一间距

        // 分页按钮和信息
        String pageText = String.format("%d / %d", displayPage + 1, totalPages);
        float pageTextWidth = ImGui.calcTextSize(pageText).x;
        float totalControlWidth = buttonWidth * 2 + BLOCK_SPACING * 2 + pageTextWidth;

        // 提示文本
        String hintText = "滚轮翻页 | ← → 键翻页";
        float hintTextWidth = ImGui.calcTextSize(hintText).x;

        // 居中布局控件
        ImGui.setCursorPosX((windowWidth - totalControlWidth) * 0.5f);

        // 上一页按钮
        ImGui.pushStyleColor(ImGuiCol.Button, BUTTON_COLOR_NORMAL);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, BUTTON_COLOR_HOVERED);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, BUTTON_COLOR_ACTIVE);

        boolean canGoPrev = displayPage > 0;
        if (!canGoPrev) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);

        if (ImGui.button("◀", buttonWidth, 24.0f) && canGoPrev) {
            displayPage--;
            LOGGER.debug("分页按钮：切换到第 {} 页", displayPage + 1);
        }

        if (!canGoPrev) ImGui.popStyleVar();

        // 页码显示
        ImGui.sameLine();
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 3.0f); // 微调垂直对齐
        ImGui.text(pageText);
        ImGui.setCursorPosY(ImGui.getCursorPosY() - 3.0f);

        // 下一页按钮
        ImGui.sameLine();
        boolean canGoNext = displayPage < totalPages - 1;
        if (!canGoNext) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);

        if (ImGui.button("▶", buttonWidth, 24.0f) && canGoNext) {
            displayPage++;
            LOGGER.debug("分页按钮：切换到第 {} 页", displayPage + 1);
        }

        if (!canGoNext) ImGui.popStyleVar();
        ImGui.popStyleColor(3);

        // 操作提示
        ImGui.setCursorPosX((windowWidth - hintTextWidth) * 0.5f);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + PADDING);
        ImGui.textDisabled(hintText);
    }

    // ============================================================================
    // 交互逻辑处理方法
    // ============================================================================

    /**
     * [NEW] 处理全局快捷键
     */
    private void handleGlobalShortcuts() {
        // Enter 键 - 应用选择
        if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Enter))) {
            applyBlockSelection();
            return;
        }

        // Escape 键 - 取消/关闭
        if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Escape))) {
            close();
            return;
        }

        // Ctrl+R - 重置调色盘
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(82)) { // R key scancode
            clearPalette();
            LOGGER.debug("快捷键 Ctrl+R：重置调色盘");
            return;
        }

        // [NEW] 数字键快速选择分类 (1-9)
        if (!ImGui.getIO().getKeyCtrl() && !ImGui.getIO().getKeyAlt()) {
            // 检查数字键 1-9 (ASCII 49-57)
            for (int keyCode = 49; keyCode <= 57; keyCode++) {
                if (ImGui.isKeyPressed(keyCode)) {
                    int categoryIndex = keyCode - 49; // 1键对应索引0
                    BlockCategory[] categories = BlockCategory.values();
                    if (categoryIndex < categories.length) {
                        setCurrentCategory(categories[categoryIndex], false);
                        LOGGER.debug("快捷键 {}：切换到分类 {}", categoryIndex + 1,
                                   categories[categoryIndex].getDisplayName());
                    }
                    return;
                }
            }
        }
    }

    /**
     * [NEW] 处理展示区槽位交互
     */
    private void handleDisplaySlotInteraction(Block block) {
        try {
            // 创建不可见按钮用于交互
            if (ImGui.invisibleButton("block", BLOCK_ICON_SIZE, BLOCK_ICON_SIZE)) {
                // 单击添加到调色盘
                addToLastSlot(block);
                LOGGER.debug("点击方块: {}", Registries.BLOCK.getId(block));
            }

            // 处理拖动开始
            if (ImGui.isItemHovered() && ImGui.isMouseDragging(0) && !isDragging) {
                startDrag(block, DragSource.DISPLAY_AREA, -1);
            }
        } catch (Exception e) {
            LOGGER.error("处理方块槽位交互时发生错误: {} - {}", 
                        block != null ? block.getTranslationKey() : "null", e.getMessage(), e);
        }
    }

    /**
     * [NEW] 处理调色盘槽位交互
     */
    private void handlePaletteSlotInteraction(int slotIndex, Block blockInSlot) {
        // 创建不可见按钮用于交互
        if (ImGui.invisibleButton("slot", BLOCK_ICON_SIZE, BLOCK_ICON_SIZE)) {
            // 左键点击处理逻辑可以在这里添加
        }

        // 处理右键移除
        if (ImGui.isItemClicked(1) && blockInSlot != null) {
            removePaletteBlock(slotIndex);
        }

        // 处理拖动开始
        if (ImGui.isItemHovered() && ImGui.isMouseDragging(0) && blockInSlot != null && !isDragging) {
            startDrag(blockInSlot, DragSource.PALETTE_AREA, slotIndex);
        }
    }

    /**
     * [NEW] 处理调色盘拖放
     */
    private void handlePaletteDrop() {
        // 检查是否在调色盘区域上方
        float paletteStartY = ImGui.getCursorScreenPos().y;
        boolean isMouseOverPalette = ImGui.getMousePos().y >= paletteStartY &&
                                   ImGui.getMousePos().y <= paletteStartY + BLOCK_ICON_SIZE + PADDING;

        // 处理拖放到调色盘
        if (isDragging && !ImGui.isMouseDown(0)) {
            if (isMouseOverPalette) {
                int dropIndex = calculateDropIndex();
                handleDrop(dropIndex);
            }
            endDrag();
        } else if (isDragging && isMouseOverPalette) {
            int dropIndex = calculateDropIndex();
            setDropIndicator(dropIndex);
        } else {
            clearDropIndicator();
        }
    }

    /**
     * [ENHANCED] 处理翻页逻辑 - 简化鼠标滚轮检测
     */
    private void handlePaging(int totalBlocks) {
        // [CORRECTED] 每页显示96个方块 (8行 × 12列)
        blocksPerPage = DISPLAY_BLOCKS_PER_PAGE;

        int totalPages = Math.max(1, (int) Math.ceil((double) totalBlocks / blocksPerPage));

        // [ENHANCED] 支持鼠标滚轮和键盘翻页
        // 检查当前窗口是否被悬停（包括子窗口）
        if (ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) {
            // 鼠标滚轮翻页
            float mouseWheel = ImGui.getIO().getMouseWheel();
            if (mouseWheel != 0) {
                if (mouseWheel > 0) {
                    displayPage = Math.max(0, displayPage - 1);
                    LOGGER.debug("鼠标滚轮上滚，切换到第 {} 页", displayPage + 1);
                } else {
                    displayPage = Math.min(totalPages - 1, displayPage + 1);
                    LOGGER.debug("鼠标滚轮下滚，切换到第 {} 页", displayPage + 1);
                }
            }

            // [NEW] 键盘翻页支持
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.LeftArrow))) {
                if (displayPage > 0) {
                    displayPage--;
                    LOGGER.debug("左箭头键翻页，切换到第 {} 页", displayPage + 1);
                }
            }
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.RightArrow))) {
                if (displayPage < totalPages - 1) {
                    displayPage++;
                    LOGGER.debug("右箭头键翻页，切换到第 {} 页", displayPage + 1);
                }
            }
        }

        // 限制页面范围
        displayPage = Math.max(0, Math.min(totalPages - 1, displayPage));
    }

    // ============================================================================
    // 拖放逻辑处理
    // ============================================================================

    /**
     * 开始拖动
     */
    private void startDrag(Block block, DragSource source, int index) {
        isDragging = true;
        draggedBlock = block;
        dragSource = source;
        dragOriginIndex = index;
        LOGGER.debug("开始拖动方块: {}, 来源: {}, 索引: {}",
                    Registries.BLOCK.getId(block), source, index);
    }

    /**
     * 结束拖动
     */
    private void endDrag() {
        isDragging = false;
        draggedBlock = null;
        dragSource = DragSource.NONE;
        dragOriginIndex = -1;
        clearDropIndicator();
    }

    /**
     * 处理拖放
     */
    private void handleDrop(int dropIndex) {
        if (draggedBlock == null) return;

        try {
            if (dragSource == DragSource.DISPLAY_AREA) {
                // 从展示区拖到调色盘
                addToPalette(draggedBlock, dropIndex);
            } else if (dragSource == DragSource.PALETTE_AREA) {
                // 调色盘内部重排序
                moveInPalette(dragOriginIndex, dropIndex);
            }
        } catch (Exception e) {
            LOGGER.error("处理拖放时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 计算拖放位置索引
     */
    private int calculateDropIndex() {
        float mouseX = ImGui.getMousePos().x;
        float windowStartX = ImGui.getWindowPos().x;
        float relativeX = mouseX - windowStartX - PADDING;

        // 计算相对于调色盘的位置
        int index = Math.round(relativeX / (BLOCK_ICON_SIZE + BLOCK_SPACING));
        return Math.max(0, Math.min(index, paletteBlocks.size()));
    }

    /**
     * 设置拖放指示器
     */
    private void setDropIndicator(int index) {
        this.dropIndicatorIndex = index;
    }

    /**
     * 清除拖放指示器
     */
    private void clearDropIndicator() {
        this.dropIndicatorIndex = -1;
    }

    // ============================================================================
    // 数据操作逻辑
    // ============================================================================

    /**
     * [MODIFIED] 添加方块到调色盘指定位置 - 允许重复
     */
    private void addToPalette(Block block, int index) {
        if (block == null) {
            LOGGER.warn("尝试添加空方块到调色盘，忽略操作");
            return;
        }
        
        try {
            if (paletteBlocks.size() < MAX_PALETTE_SLOTS) {
                int insertIndex = Math.min(index, paletteBlocks.size());
                paletteBlocks.add(insertIndex, block);
                LOGGER.debug("添加方块到调色盘 (允许重复): {} 位置: {} - 当前数量: {}",
                           Registries.BLOCK.getId(block), insertIndex, paletteBlocks.size());
            } else {
                LOGGER.warn("调色盘已满 ({}/{}，无法添加方块: {}",
                          paletteBlocks.size(), MAX_PALETTE_SLOTS, Registries.BLOCK.getId(block));
            }
        } catch (Exception e) {
            LOGGER.error("添加方块到调色盘时发生错误: {} - {}", 
                        block.getTranslationKey(), e.getMessage(), e);
        }
    }

    /**
     * 添加方块到调色盘末尾
     */
    private void addToLastSlot(Block block) {
        addToPalette(block, paletteBlocks.size());
    }

    /**
     * [NEW] 从调色盘移除方块
     */
    private void removePaletteBlock(int index) {
        if (index >= 0 && index < paletteBlocks.size()) {
            Block removedBlock = paletteBlocks.remove(index);
            LOGGER.debug("从调色盘移除方块: {}", Registries.BLOCK.getId(removedBlock));
        }
    }

    /**
     * [NEW] 调色盘内部移动方块
     */
    private void moveInPalette(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= paletteBlocks.size()) return;

        Block block = paletteBlocks.remove(fromIndex);
        int insertIndex;
        if (fromIndex < toIndex) {
            insertIndex = Math.min(toIndex - 1, paletteBlocks.size());
        } else {
            insertIndex = Math.min(toIndex, paletteBlocks.size());
        }
        paletteBlocks.add(insertIndex, block);
        LOGGER.debug("调色盘内部移动方块: {} 从 {} 到 {}",
                   Registries.BLOCK.getId(block), fromIndex, insertIndex);
    }

    /**
     * 获取要显示的方块列表（只保留有物品的方块）
     */
    private List<Block> getDisplayBlocks() {
        try {
            // 原始分类/搜索结果
            List<Block> rawBlocks = searchManager.handleSearchLogic();
            if (rawBlocks == null) {
                LOGGER.warn("搜索管理器返回null方块列表，使用空列表");
                return Collections.emptyList();
            }
            
            // 只保留有物品的方块，并过滤null
            List<Block> filtered = new ArrayList<>();
            for (Block block : rawBlocks) {
                if (block == null) {
                    LOGGER.debug("跳过null方块");
                    continue;
                }
                
                try {
                    // 使用 BlockIconRenderer 检查是否有有效物品
                    ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
                    if (!stack.isEmpty()) {
                        filtered.add(block);
                    } else {
                        LOGGER.debug("过滤无物品方块: {} (物品: {})", 
                                   Registries.BLOCK.getId(block), 
                                   stack.getItem().getTranslationKey());
                    }
                } catch (Exception e) {
                    LOGGER.debug("检查方块物品时异常: {} - {}", block, e.getMessage());
                }
            }
            LOGGER.debug("过滤后剩余方块数量: {}/{}", filtered.size(), rawBlocks.size());
            return filtered;
        } catch (Exception e) {
            LOGGER.error("获取显示方块列表时出错: {}", e.getMessage(), e);
            // [MODIFIED] 使用 getOrDefault 提升健壮性
            List<Block> fallback = categoryManager.getCategorizedBlocks()
                .getOrDefault(currentCategory, Collections.emptyList());
            // 同样过滤无物品方块
            List<Block> filtered = new ArrayList<>();
            for (Block block : fallback) {
                if (block == null) {
                    continue;
                }
                try {
                    ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
                    if (!stack.isEmpty()) {
                        filtered.add(block);
                    }
                } catch (Exception ignore) {}
            }
            LOGGER.debug("回退过滤后剩余方块数量: {}/{}", filtered.size(), fallback.size());
            return filtered;
        }
    }

    /**
     * 设置当前分类
     */
    private void setCurrentCategory(BlockCategory category, boolean force) {
        try {
            LOGGER.debug("setCurrentCategory 被调用: category={}, force={}", category, force);
            
            if (!force && this.currentCategory == category) {
                LOGGER.debug("分类未改变且非强制更新，跳过");
                return;
            }

            if (category == null) {
                throw new IllegalArgumentException("category 不能为null");
            }

            this.currentCategory = category;
            this.displayPage = 0; // 重置到第一页
            LOGGER.debug("已设置当前分类为: {}, 重置页面为0", category.getDisplayName());

            // 更新搜索管理器
            try {
                if (searchManager != null) {
                    searchManager.setCurrentCategory(category);
                    LOGGER.debug("searchManager.setCurrentCategory 调用成功");
                } else {
                    LOGGER.warn("searchManager 为 null，跳过设置分类");
                }
            } catch (Exception e) {
                LOGGER.error("设置搜索管理器分类时出错: {}", e.getMessage(), e);
                throw new RuntimeException("搜索管理器分类设置失败: " + e.getMessage(), e);
            }

            // 预加载分类图标
            try {
                if (preloadManager != null) {
                    preloadManager.preloadCategoryIcons(category);
                    LOGGER.debug("preloadManager.preloadCategoryIcons 调用成功");
                } else {
                    LOGGER.warn("preloadManager 为 null，跳过预加载");
                }
            } catch (Exception e) {
                LOGGER.error("预加载分类图标时出错: {}", e.getMessage(), e);
                // 预加载失败不是致命错误，只记录日志
                LOGGER.warn("预加载图标失败，但不影响分类切换");
            }

            LOGGER.info("成功切换到分类: {}", category.getDisplayName());
            
        } catch (Exception e) {
            LOGGER.error("设置当前分类时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("设置分类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 应用方块选择
     */
    private void applyBlockSelection() {
        if (paletteBlocks.isEmpty()) {
            showWarningDialog.accept("调色盘为空，请至少选择一个方块。");
            return;
        }

        try {
            // 转换为ID列表
            List<String> blockIds = paletteBlocks.stream()
                .map(block -> Registries.BLOCK.getId(block).toString())
                .toList();

            // 发布事件
            String primaryBlockId = blockIds.getFirst();
            String blockIdsStr = String.join(",", blockIds);
            BlockConfigEvent event = new BlockConfigEvent(primaryBlockId, "selected_blocks", blockIdsStr);
            eventBus.publish(event);

            LOGGER.info("应用方块选择: {} 个方块", paletteBlocks.size());
            LOGGER.debug("选中的方块ID: {}", blockIds);

            // 关闭对话框
            close();

        } catch (Exception e) {
            LOGGER.error("应用方块选择时出错: {}", e.getMessage(), e);
            showWarningDialog.accept("应用方块选择时出错: " + e.getMessage());
        }
    }

    // ============================================================================
    // 公共API
    // ============================================================================

    /**
     * 获取选中的方块ID列表
     */
    public List<String> getSelectedBlockIds() {
        return paletteBlocks.stream()
            .map(block -> Registries.BLOCK.getId(block).toString())
            .toList();
    }

    /**
     * 检查是否有选中的方块
     */
    public boolean hasSelectedBlocks() {
        return !paletteBlocks.isEmpty();
    }

    /**
     * 获取选中方块数量
     */
    public int getSelectedBlockCount() {
        return paletteBlocks.size();
    }

    /**
     * 清空调色盘
     */
    public void clearPalette() {
        paletteBlocks.clear();
        LOGGER.info("清空调色盘");
    }

    // ============================================================================
    // 方块图标预加载支持
    // ============================================================================

    /**
     * 预加载常用方块图标
     */
    private void preloadCommonBlocks() {
        // 常用的基础建筑方块
        List<Block> commonBlocks = List.of(
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.GRASS_BLOCK,
            Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.WHITE_WOOL, Blocks.GLASS,
            Blocks.WATER, Blocks.LAVA, Blocks.SAND, Blocks.GRAVEL,
            Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK,
            Blocks.REDSTONE_BLOCK, Blocks.EMERALD_BLOCK, Blocks.NETHERITE_BLOCK
        );

        // 高优先级预加载：触发 ItemStack 缓存
        for (Block block : commonBlocks) {
            BlockIconRenderer.getItemStackForBlock(block);
        }

        LOGGER.debug("预加载了 {} 个常用方块图标 (缓存项)", commonBlocks.size());
    }

    /**
     * 在render方法中处理方块图标渲染队列
     * [CRITICAL FIX] 添加延迟初始化逻辑，解决OpenGL上下文无效时的崩溃问题
     */
    private void processBlockIconRendering() {
        // 新版 BlockIconRenderer 为无状态安全渲染器，不再需要延迟初始化或 renderTick
        // 保持此处为无操作占位，以便日后扩展队列处理逻辑
    }

        /**
     * [ENHANCED] 渲染方块悬停提示 - 显示详细信息
     */
    private void renderBlockTooltip(Block block) {
        if (block == null) {
            return;
        }

        try {
            ImGui.beginTooltip();
            
            // 显示方块名称（本地化）
            String blockName = block.getName().getString();
            ImGui.text(blockName);
            
            // 显示方块ID
            String blockId = Registries.BLOCK.getId(block).toString();
            ImGui.textDisabled(blockId);
            
            // 显示操作提示
            ImGui.separator();
            ImGui.textDisabled("左键: 添加到调色盘");
            ImGui.textDisabled("拖拽: 移动到调色盘");
            
            ImGui.endTooltip();
            
        } catch (Exception e) {
            LOGGER.error("渲染方块提示时发生错误: {} - {}", 
                        Registries.BLOCK.getId(block), e.getMessage(), e);
        }
    }

    /**
     * [ENHANCED] 渲染调色盘方块悬停提示 - 显示详细信息和移除操作
     */
    private void renderPaletteBlockTooltip(Block block, int slotIndex) {
        if (block == null) {
            return;
        }

        try {
            ImGui.beginTooltip();
            
            // 显示方块名称（本地化）
            String blockName = block.getName().getString();
            ImGui.text(blockName);
            
            // 显示方块ID
            String blockId = Registries.BLOCK.getId(block).toString();
            ImGui.textDisabled(blockId);
            
            // 显示位置信息
            ImGui.separator();
            ImGui.textDisabled("位置: 槽位 " + (slotIndex + 1));
            
            // 显示操作提示
            ImGui.separator();
            ImGui.textDisabled("拖拽: 重新排序");
            ImGui.textDisabled("右键: 移除方块");
            
            ImGui.endTooltip();
            
        } catch (Exception e) {
            LOGGER.error("渲染调色盘方块提示时发生错误: {} - {}", 
                        Registries.BLOCK.getId(block), e.getMessage(), e);
        }
    }

    /**
     * [NEW] 安全的工厂方法 - 逐步初始化组件以便调试
     */
    public static CompactBlockConfigDialog createSafely(AppState appState, EventBus eventBus, Consumer<String> showWarningDialog) {
        LOGGER.info("=== 开始安全创建 CompactBlockConfigDialog ===");
        
        try {
            // 第一步：验证基础参数
            if (appState == null) {
                throw new IllegalArgumentException("appState 不能为null");
            }
            if (eventBus == null) {
                throw new IllegalArgumentException("eventBus 不能为null");
            }
            if (showWarningDialog == null) {
                throw new IllegalArgumentException("showWarningDialog 不能为null");
            }
            LOGGER.debug("基础参数验证通过");
            
            // 第二步：检查 Minecraft 环境
            try {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null) {
                    throw new RuntimeException("Minecraft 客户端未初始化");
                }
                LOGGER.debug("Minecraft 环境检查通过");
            } catch (Exception e) {
                LOGGER.error("Minecraft 环境检查失败", e);
                throw new RuntimeException("Minecraft 环境不可用: " + e.getMessage(), e);
            }
            
            // 第三步：创建实例
            CompactBlockConfigDialog dialog = new CompactBlockConfigDialog(appState, eventBus, showWarningDialog);
            LOGGER.info("=== CompactBlockConfigDialog 安全创建成功 ===");
            return dialog;
            
        } catch (Exception e) {
            LOGGER.error("=== CompactBlockConfigDialog 安全创建失败 ===", e);
            throw new RuntimeException("无法安全创建方块配置对话框: " + e.getMessage(), e);
        }
    }
}
