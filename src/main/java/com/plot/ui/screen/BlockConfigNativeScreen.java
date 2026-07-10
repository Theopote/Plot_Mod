package com.plot.ui.screen;

import com.plot.ui.component.BlockIconRenderer;
import com.plot.ui.dialog.BlockConfigDialog.BlockCategoryManager.BlockCategory;
import com.plot.ui.dialog.BlockConfigDialog.BlockConfigManager;
import com.plot.ui.imgui.GuiOverlayRenderer;
import com.plot.ui.imgui.ImGuiRenderer;
import com.plot.utils.PlotI18n;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 原生方块配置面板（3D 物品图标稳定路径）。
 * <p>
 * 布局结构（左右分栏）：
 *   ┌──────────────────────────────────────────┐
 *   │              标题栏（含关闭×按钮）           │
 *   ├────────┬─────────────────────────────────┤
 *   │        │  🔍 搜索栏                        │
 *   │ 分类   ├─────────────────────────────────┤
 *   │ 侧边   │  方块网格（GRID_COLS × GRID_ROWS） │
 *   │ 栏     ├─────────────────────────────────┤
 *   │        │  ← 上页   1/N   下页 →           │
 *   ├────────┴─────────────────────────────────┤
 *   │  调色盘  ·  左键添加  ·  右键移除  ·  左键换位 │
 *   │  [ ][ ][ ][ ][ ][ ][ ][ ][ ][ ][ ][ ]   │
 *   │  已选 N/12  ·  [满时红字提示]               │
 *   ├──────────────────────────────────────────┤
 *   │                     ✓ 应用  取消  🗑 清空  │
 *   └──────────────────────────────────────────┘
 * <p>
 * 优化要点：
 *  1. 左侧分类侧边栏宽度随最宽标签文字自适应；
 *  2. 搜索栏实时过滤方块（支持名称 / 命名空间 ID 模糊匹配）；
 *  3. 调色盘操作提示文字、已选计数及满员红字提示；
 *  4. 网格中已在调色盘的方块显示高亮边框+对勾标记；
 *  5. 分页控件始终占位（单页时置灰），避免布局跳变；
 *  6. 修复 drawMainButton 文字居中、isInside 边界判断；
 *  7. 鼠标滚轮翻页支持；
 *  8. 标题栏右侧 × 关闭按钮。
 */
public class BlockConfigNativeScreen extends Screen {

    // ── 调色盘 ──────────────────────────────────────────────────────────────
    private static final int MAX_PALETTE_SLOTS = 14;

    // ── 网格 ────────────────────────────────────────────────────────────────
    private static final int GRID_COLS = 12;
    private static final int GRID_ROWS = 7;
    private static final int PAGE_SIZE  = GRID_COLS * GRID_ROWS;

    // ── 间距 ────────────────────────────────────────────────────────────────
    private static final int SLOT_GAP     = 1;   // 格子间间距
    private static final int MARGIN       = 3;   // 面板内边距
    private static final int SECTION_GAP  = 3;   // 区块之间的垂直间隔

    // ── 分类侧边栏 ──────────────────────────────────────────────────────────
    private static final int SIDEBAR_MIN_W  = 40;  // 侧边栏最小宽度
    private static final int TAB_TEXT_PAD_X = 8;   // 标签左右文字留白
    private static final int TAB_H          = 14;  // 每个分类按钮的高度（略微减小）
    private static final int TAB_GAP        = 1;   // 分类按钮间间距

    // ── 搜索栏 ──────────────────────────────────────────────────────────────
    private static final int SEARCH_H     = 14;  // 搜索栏高度

    // ── 标题栏 ──────────────────────────────────────────────────────────────
    private static final int TITLE_HEIGHT = 12;  // 略微减小

    // ── 分页 / 底部按钮 ──────────────────────────────────────────────────────
    private static final int BTN_H        = 12;  // 略微减小
    private static final int BTN_MIN_W    = 34;  // 按钮最小宽度，保证大于文字宽度
    private static final int BTN_TEXT_PAD_X = 8; // 按钮左右文字留白
    private static final int BTN_GAP      = 2;
    private static final int BOTTOM_MARGIN = 4;
    private static final int PAGER_TEXT_GAP = 4;   // 页码文字与两侧按钮的间距
    private static final float PAGER_SCROLL_SPEED = 28f; // 页码滚动速度（像素/秒）
    private static final long PAGER_SCROLL_PAUSE_MS = 1500L; // 滚动到端点后的停顿时间

    // ── 颜色常量 ─────────────────────────────────────────────────────────────
    private static final int COLOR_PANEL_BG      = 0xEE1A1A1A;
    private static final int COLOR_TITLE_BG      = 0xFF3A3A3A;
    private static final int COLOR_TITLE_BORDER  = 0xFF606060;
    private static final int COLOR_SIDEBAR_BG    = 0xFF222222;
    private static final int COLOR_TAB_ACTIVE    = 0xFF3D6FA0;
    private static final int COLOR_TAB_HOVER     = 0xFF3A3A3A;
    private static final int COLOR_TAB_NORMAL    = 0xFF2A2A2A;
    private static final int COLOR_TAB_BORDER    = 0xFF505050;
    private static final int COLOR_SLOT_NORMAL   = 0xFF323232;
    private static final int COLOR_SLOT_HOVER    = 0xFF464646;
    private static final int COLOR_SLOT_SELECTED = 0xFF2A4A28; // 已在调色盘
    private static final int COLOR_SLOT_BORDER   = 0xFF5C5C5C;
    private static final int COLOR_SEL_BORDER    = 0xFF5CA85A; // 已选边框
    private static final int COLOR_PALETTE_BG    = 0xFF2A2A2A;
    private static final int COLOR_PALETTE_HOVER = 0xFF444444;
    private static final int COLOR_PALETTE_EMPTY = 0xFF1E1E1E;
    private static final int COLOR_PALETTE_BORDER= 0xFF686868;
    private static final int COLOR_SEARCH_BORDER = 0xFF555555;
    private static final int COLOR_SEARCH_ACTIVE = 0xFF4A6FA5;
    private static final int COLOR_DIVIDER       = 0xFF505050;
    private static final int COLOR_TEXT_NORMAL   = 0xFFE0E0E0;
    private static final int COLOR_TEXT_MUTED    = 0xFF888888;
    private static final int COLOR_TEXT_HINT     = 0xFF606060;
    private static final int COLOR_TEXT_FULL     = 0xFFCC4444;
    private static final int COLOR_BTN_APPLY     = 0xFF2E7D32;
    private static final int COLOR_BTN_CANCEL    = 0xFF555555;
    private static final int COLOR_BTN_CLEAR     = 0xFF7A2020;
    private static final int COLOR_PAGER_ACTIVE  = 0xFF383838;
    private static final int COLOR_PAGER_DISABLED= 0xFF252525;
    private static final int COLOR_CLOSE_HOVER   = 0xFF882222;

    // ── 状态 ─────────────────────────────────────────────────────────────────
    private final BlockConfigManager configManager;
    private final Screen parent;

    private BlockCategory currentCategory = BlockCategory.BUILDING_BLOCKS;
    /** 当前分类经过搜索过滤后的方块列表 */
    private List<Block> filteredBlocks = Collections.emptyList();
    /** 当前分类全量方块列表（未过滤），用于搜索时基础数据 */
    private List<Block> rawCategoryBlocks = Collections.emptyList();

    private final List<Block> palette = new ArrayList<>();
    private int page = 0;

    // ── 搜索 ─────────────────────────────────────────────────────────────────
    private TextFieldWidget searchBox;

    // ── 拖拽排序（调色盘） ────────────────────────────────────────────────────
    /** 正在拖拽的调色盘槽位索引，-1 表示无 */
    private int dragIndex = -1;
    /** 拖拽时当前悬停的目标槽位索引，用于预览交换位置 */
    private int dragHoverSlot = -1;

    // ── 分类侧边栏滚动 ────────────────────────────────────────────────────────────
    /** 侧边栏垂直滚动位置（像素）。为负时表示向上滚了一些 */
    private int sidebarScroll = 0;

    // ── 分页文字滚动 ──────────────────────────────────────────────────────────────
    private float pagerScrollOffset = 0f;
    private long pagerScrollPauseUntil = 0L;
    private boolean pagerScrollIncreasing = true;
    private String pagerScrollTextKey = "";

    // ── 布局坐标（init() 中计算） ──────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int slotSize, slotInset;

    // 侧边栏区域
    private int sidebarW, sidebarX, sidebarY, sidebarH;
    // 右侧内容区
    private int contentX, contentW;
    // 搜索栏
    private int searchX, searchY;
    // 网格
    private int gridX, gridY;
    // 分页
    private int pagerY, pagerPrevX, pagerPrevW, pagerNextX, pagerNextW;
    private int pagerTextAreaX, pagerTextAreaW;
    // 调色盘
    private int paletteAreaY;   // 从分隔线开始的 Y
    private int paletteX, paletteY;
    private int paletteCountY;  // 计数文字 Y
    // 底部按钮
    private int btnY, btnApplyX, btnCancelX, btnClearX;
    private int btnApplyW, btnCancelW, btnClearW;
    // 标题栏关闭按钮
    private int closeBtnX, closeBtnY, closeBtnW, closeBtnH;

    // ── 分类侧边栏布局 ─────────────────────────────────────────────────────────
    private static class CategoryTabLayout {
        BlockCategory category;
        int x, y, w, h;
        CategoryTabLayout(BlockCategory category, int x, int y, int w, int h) {
            this.category = category; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    private final List<CategoryTabLayout> categoryTabLayouts = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    public BlockConfigNativeScreen(Screen parent) {
        super(Text.translatable("block.plot.title"));
        this.configManager = BlockConfigManager.getInstance();
        this.parent = parent;
    }

    // =========================================================================
    //  初始化 / 布局
    // =========================================================================

    @Override
    protected void init() {
        super.init();

        // 1. 加载当前分类原始数据
        reloadRawCategory();

        // 2. 确定 slotSize（先估算面板高度）
        int availH = Math.min(780, this.height - 20);
        // 预留：标题(14) + 搜索(14) + 分页(14) + 调色盘区(约60) + 按钮(14) + 各种间距(约60)
        int reservedH = TITLE_HEIGHT + SECTION_GAP
                + SEARCH_H + SECTION_GAP
                + BTN_H + SECTION_GAP   // 分页
                + 60                     // 调色盘 + 计数 + 按钮 + margins
                + BOTTOM_MARGIN;
        int gridAreaH = availH - reservedH;
        int byHeight  = gridAreaH / GRID_ROWS;
        slotSize  = Math.max(18, Math.min(36, byHeight));
        slotInset = Math.max(1, (slotSize - 16) / 2);

        // 3. 面板宽度：侧边栏（随最宽标签自适应）+ 网格
        sidebarW = computeSidebarWidth();
        int gridW = GRID_COLS * slotSize + (GRID_COLS - 1) * SLOT_GAP;
        panelW = MARGIN + sidebarW + MARGIN + gridW + MARGIN;
        panelW = Math.min(panelW, this.width - 16);

        // 4. 面板高度（自顶向下推算后确定）
        int gridH     = GRID_ROWS * slotSize + (GRID_ROWS - 1) * SLOT_GAP;
        int paletteRowH = slotSize;
        // 各区高度总和
        panelH = TITLE_HEIGHT + SECTION_GAP          // 标题
                + SEARCH_H + SECTION_GAP              // 搜索
                + gridH + SECTION_GAP                 // 网格
                + BTN_H + SECTION_GAP                 // 分页
                + 2 + SECTION_GAP                     // 分隔线
                + 9 + 2                               // 调色盘标签行（文字+提示）
                + paletteRowH + SECTION_GAP           // 调色盘槽位
                + 9 + SECTION_GAP                     // 计数行
                + BTN_H                               // 底部按钮
                + BOTTOM_MARGIN;
        panelH = Math.min(panelH, this.height - 16);

        // 5. 居中放置面板
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        // 6. 侧边栏 / 内容区 X 坐标
        sidebarX = panelX + MARGIN;
        contentX = sidebarX + sidebarW + MARGIN;
        contentW = panelW - MARGIN - sidebarW - MARGIN - MARGIN;
        // 实际内容区宽度与网格宽对齐
        // (contentW may be slightly wider if panelW was clamped; grid stays left-aligned)

        // 7. 从上往下推 Y 坐标
        int cy = panelY;

        // 标题栏
        cy += TITLE_HEIGHT + SECTION_GAP;

        // 侧边栏起始 Y
        sidebarY = cy;

        // 搜索栏
        searchX = contentX;
        searchY = cy;
        cy += SEARCH_H + SECTION_GAP;

        // 初始化搜索框 Widget
        if (searchBox == null) {
            searchBox = new TextFieldWidget(this.textRenderer, searchX + 13, searchY + 2, contentW - 16, SEARCH_H - 4, Text.translatable("block.plot.search"));
            searchBox.setMaxLength(128);
            searchBox.setPlaceholder(Text.translatable("block.plot.search_hint"));
            searchBox.setChangedListener(text -> applySearchFilter());
        } else {
            // 调整位置
            searchBox.setX(searchX + 13);
            searchBox.setY(searchY + 2);
            searchBox.setWidth(contentW - 16);
            searchBox.setChangedListener(text -> applySearchFilter());
        }
        searchBox.setDrawsBackground(false);
        addDrawableChild(searchBox);

        // 网格
        gridX = contentX;
        gridY = cy;
        cy += gridH + SECTION_GAP;

        // 分页（按钮宽度按文字自适应）
        pagerY      = cy;
        pagerPrevX  = contentX;
        pagerPrevW  = Math.max(BTN_MIN_W, this.textRenderer.getWidth(PlotI18n.tr("block.plot.page_prev")) + BTN_TEXT_PAD_X * 2);
        pagerNextW  = Math.max(BTN_MIN_W, this.textRenderer.getWidth(PlotI18n.tr("block.plot.page_next")) + BTN_TEXT_PAD_X * 2);
        pagerNextX     = contentX + contentW - pagerNextW;
        pagerTextAreaX = pagerPrevX + pagerPrevW + PAGER_TEXT_GAP;
        pagerTextAreaW = Math.max(0, pagerNextX - PAGER_TEXT_GAP - pagerTextAreaX);
        cy += BTN_H + SECTION_GAP;

        // 侧边栏总高度（覆盖搜索+网格+分页）
        sidebarH = cy - sidebarY - SECTION_GAP;

        // 分隔线 / 调色盘标签
        paletteAreaY = cy;
        cy += 2 + SECTION_GAP; // 分隔线本身占2px

        // 调色盘标签文字在 renderPalette 中相对 paletteY 计算：使用字体高度控制与槽位间距
        cy += this.textRenderer.fontHeight + 1;
        paletteX = panelX + MARGIN;
        paletteY = cy;
        cy += paletteRowH + SECTION_GAP;

        // 计数行
        paletteCountY = cy;

        // 底部按钮（宽度按文字自适应，保持按钮更紧凑）
        btnY = panelY + panelH - BOTTOM_MARGIN - BTN_H;
        btnApplyW  = Math.max(BTN_MIN_W, this.textRenderer.getWidth(PlotI18n.tr("block.plot.apply")) + BTN_TEXT_PAD_X * 2);
        btnCancelW = Math.max(BTN_MIN_W, this.textRenderer.getWidth(PlotI18n.tr("block.plot.cancel")) + BTN_TEXT_PAD_X * 2);
        btnClearW  = Math.max(BTN_MIN_W, this.textRenderer.getWidth(PlotI18n.tr("block.plot.clear")) + BTN_TEXT_PAD_X * 2);

        // 三个按钮右对齐
        int totalBtnW = btnApplyW + btnCancelW + btnClearW + BTN_GAP * 2;
        btnApplyX  = panelX + panelW - MARGIN - totalBtnW;
        btnCancelX = btnApplyX + btnApplyW + BTN_GAP;
        btnClearX  = btnCancelX + btnCancelW + BTN_GAP;

        // 标题栏关闭按钮
        closeBtnW = 18;
        closeBtnH = TITLE_HEIGHT - 2;
        closeBtnX = panelX + panelW - closeBtnW - 1;
        closeBtnY = panelY + 1;

        // 8. 计算侧边栏标签布局
        buildCategoryTabLayouts();

        // 9. 同步调色盘数据
        palette.clear();
        palette.addAll(configManager.getPaletteBlocksSnapshot());

        // 10. 应用搜索过滤
        applySearchFilter();
    }

    /**
     * 加载当前分类的全量方块列表，重置分页（不做搜索过滤）。
     */
    private void reloadRawCategory() {
        rawCategoryBlocks = configManager.getBlocksForCategory(currentCategory);
        page = 0;
    }

    /**
     * 根据搜索框内容过滤 rawCategoryBlocks → filteredBlocks，并修正分页。
     */
    private void applySearchFilter() {
        String q = searchBox.getText().trim().toLowerCase();
        if (q.isEmpty()) {
            filteredBlocks = rawCategoryBlocks;
        } else {
            filteredBlocks = rawCategoryBlocks.stream().filter(block -> {
                String name = block.getName().getString().toLowerCase();
                String id   = Registries.BLOCK.getId(block).toString().toLowerCase();
                return name.contains(q) || id.contains(q);
            }).collect(Collectors.toList());
        }
        int maxPage = Math.max(0, (filteredBlocks.size() + PAGE_SIZE - 1) / PAGE_SIZE - 1);
        if (page > maxPage) page = maxPage;
    }

    /**
     * 根据所有分类标签中最宽的文字计算侧边栏宽度。
     */
    private int computeSidebarWidth() {
        int maxTextW = 0;
        for (BlockCategory cat : configManager.getAvailableCategories()) {
            maxTextW = Math.max(maxTextW, this.textRenderer.getWidth(cat.getDisplayName()));
        }
        return Math.max(SIDEBAR_MIN_W, maxTextW + TAB_TEXT_PAD_X * 2);
    }

    /**
     * 计算分类侧边栏每个标签的位置（竖向排列，宽度与侧边栏一致），并应用滚动偏移。
     */
    private void buildCategoryTabLayouts() {
        categoryTabLayouts.clear();
        List<BlockCategory> categories = configManager.getAvailableCategories();

        int x = sidebarX;
        int y = sidebarY + sidebarScroll;  // 应用滚动偏移
        for (BlockCategory cat : categories) {
            categoryTabLayouts.add(new CategoryTabLayout(cat, x, y, sidebarW, TAB_H));
            y += TAB_H + TAB_GAP;
        }
    }

    /**
     * 计算分类侧边栏的总内容高度（不考虑滚动）。
     */
    private int getSidebarContentHeight() {
        List<BlockCategory> categories = configManager.getAvailableCategories();
        return categories.size() * (TAB_H + TAB_GAP);
    }

    // =========================================================================
    //  渲染入口
    // =========================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderParentBackdrop(context, mouseX, mouseY, delta);

        // 半透明全屏遮罩：保留 Plot 主界面可见，只做轻量压暗作为模态层
        context.fill(0, 0, this.width, this.height, 0x55000000);

        // 面板背景 + 外边框
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL_BG);
        drawBorder(context, panelX, panelY, panelW, panelH, 0xFF484848);

        renderTitleBar(context, mouseX, mouseY);
        renderSidebar(context, mouseX, mouseY);
        renderSearchDecoration(context);
        renderGrid(context, mouseX, mouseY);
        renderPager(context, mouseX, mouseY, delta);
        renderPalette(context, mouseX, mouseY);
        renderBottomButtons(context, mouseX, mouseY);
        renderHoverTooltip(context, mouseX, mouseY);

        if (dragIndex >= 0) {
            renderDraggedItem(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderParentBackdrop(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent == null || parent == this) {
            return;
        }

        try {
            if (parent instanceof PlotScreen plotScreen) {
                // 作为背景层补绘 Plot 的 ImGui，但禁止其处理真实鼠标输入，避免点击穿透。
                plotScreen.renderAsBackdrop(context, delta);
                ImGuiRenderer renderer = ImGuiRenderer.getInstance();
                if (renderer.isInitialized() && renderer.hasPendingDrawData()) {
                    renderer.renderPendingDrawData();
                }
                GuiOverlayRenderer.flushPending();
                return;
            }

            // 其他普通 Screen 则直接按常规背景方式绘制。
            parent.render(context, -10_000, -10_000, delta);
        } catch (Exception e) {
            // 背景渲染失败不应阻断当前面板显示。
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no-op：由 render() 自行绘制背景，避免 1.21.x 重复 blur。
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // =========================================================================
    //  各区域渲染
    // =========================================================================

    /** 标题栏（含右侧 × 关闭按钮）。 */
    private void renderTitleBar(DrawContext context, int mouseX, int mouseY) {
        context.fill(panelX, panelY, panelX + panelW, panelY + TITLE_HEIGHT, COLOR_TITLE_BG);
        drawBorder(context, panelX, panelY, panelW, TITLE_HEIGHT, COLOR_TITLE_BORDER);

        // 标题居中
        int tw = this.textRenderer.getWidth(this.title);
        int tx = panelX + (panelW - tw) / 2;
        int ty = panelY + (TITLE_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, this.title, tx, ty, COLOR_TEXT_NORMAL, false);

        // × 关闭按钮
        boolean closeHover = isInside(mouseX, mouseY, closeBtnX, closeBtnY, closeBtnW, closeBtnH);
        if (closeHover) {
            context.fill(closeBtnX, closeBtnY, closeBtnX + closeBtnW, closeBtnY + closeBtnH, COLOR_CLOSE_HOVER);
        }
        String closeLabel = "×";
        int clw = this.textRenderer.getWidth(closeLabel);
        context.drawText(this.textRenderer, closeLabel,
                closeBtnX + (closeBtnW - clw) / 2,
            closeBtnY + (closeBtnH - this.textRenderer.fontHeight) / 2 + 1,
                closeHover ? 0xFFFFAAAA : 0xFFAAAAAA, false);
    }

    /** 左侧分类侧边栏（支持滚动）。 */
    private void renderSidebar(DrawContext context, int mouseX, int mouseY) {
        // 侧边栏背景
        context.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, COLOR_SIDEBAR_BG);
        // 侧边栏边框
        drawBorder(context, sidebarX, sidebarY, sidebarW, sidebarH, 0xFF505050);

        // 在侧边栏区域内裁剪，避免分类项溢出
        // 为了兼容性，这里采用简单做法：检查 Y 是否在范围内即可

        for (CategoryTabLayout layout : categoryTabLayouts) {
            // 跳过超出侧边栏范围的标签（向上超出）
            if (layout.y + layout.h <= sidebarY) continue;
            // 跳过超出侧边栏范围的标签（向下超出）
            if (layout.y >= sidebarY + sidebarH) continue;

            int tabX = layout.x + 1;
            int tabW = layout.w - 2;

            boolean active = layout.category == currentCategory;
            boolean hover  = isInside(mouseX, mouseY, tabX, layout.y, tabW, layout.h);
            int bg = active ? COLOR_TAB_ACTIVE : (hover ? COLOR_TAB_HOVER : COLOR_TAB_NORMAL);

            context.fill(tabX, layout.y, tabX + tabW, layout.y + layout.h, bg);

            // 激活状态左侧高亮条
            if (active) {
                context.fill(tabX, layout.y, tabX + 2, layout.y + layout.h, 0xFF6AAAE8);
            }

            // 底部分隔线（非最后一项）
            context.fill(tabX + 2, layout.y + layout.h - 1,
                    tabX + tabW - 2, layout.y + layout.h, COLOR_TAB_BORDER);

            String text = layout.category.getDisplayName();
            int tw = this.textRenderer.getWidth(text);
            int tx = tabX + (tabW - tw) / 2;
            int ty = layout.y + (layout.h - this.textRenderer.fontHeight) / 2;
            context.drawText(this.textRenderer, text, tx, ty,
                    active ? 0xFFFFFFFF : (hover ? 0xFFCCCCCC : 0xFF999999), false);
        }
    }

    /** 搜索栏装饰：只绘制边框与图标，文本由 Widget 树渲染。 */
    private void renderSearchDecoration(DrawContext context) {
        int borderColor = searchBox.isFocused() ? COLOR_SEARCH_ACTIVE : COLOR_SEARCH_BORDER;
        drawBorder(context, searchX, searchY, contentW, SEARCH_H, borderColor);

        // 🔍 图标
        context.drawText(this.textRenderer, "🔍", searchX + 3, searchY + (SEARCH_H - this.textRenderer.fontHeight) / 2,
                0xFF666666, false);
    }

    /** 方块网格（高亮已选中方块）。 */
    private void renderGrid(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x   = gridX + col * (slotSize + SLOT_GAP);
            int y   = gridY + row * (slotSize + SLOT_GAP);

            boolean hover      = isInside(mouseX, mouseY, x, y, slotSize, slotSize);
            boolean hasBlock   = idx < filteredBlocks.size();
            boolean inPalette  = hasBlock && palette.contains(filteredBlocks.get(idx));

            // 背景色
            int bg = inPalette ? COLOR_SLOT_SELECTED
                    : (hover && hasBlock ? COLOR_SLOT_HOVER : COLOR_SLOT_NORMAL);
            context.fill(x, y, x + slotSize, y + slotSize, bg);

            // 边框（已选中用绿色边框）
            drawBorder(context, x, y, slotSize, slotSize,
                    inPalette ? COLOR_SEL_BORDER : COLOR_SLOT_BORDER);

            if (!hasBlock) continue;

            Block block = filteredBlocks.get(idx);
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (!stack.isEmpty()) {
                BlockIconRenderer.tryDrawItem(context, stack, x + slotInset, y + slotInset);
            }

            // 已在调色盘：右下角绘制小对勾
            if (inPalette) {
                int cx = x + slotSize - 5;
                int cy = y + slotSize - 5;
                context.fill(cx,     cy + 2, cx + 1, cy + 3, 0xFF80DD80);
                context.fill(cx + 1, cy + 3, cx + 2, cy + 4, 0xFF80DD80);
                context.fill(cx + 2, cy + 1, cx + 3, cy + 3, 0xFF80DD80);
                context.fill(cx + 3, cy,     cx + 4, cy + 2, 0xFF80DD80);
            }
        }
    }

    /** 分页控件（始终占位，无数据时置灰）。 */
    private void renderPager(DrawContext context, int mouseX, int mouseY, float delta) {
        int totalPages = getTotalPages();
        boolean canPrev = page > 0;
        boolean canNext = page < totalPages - 1;

        String pageText;
        int totalInCategory = rawCategoryBlocks.size();
        if (totalPages <= 1 && filteredBlocks.isEmpty() && !searchBox.getText().isEmpty()) {
            pageText = PlotI18n.tr("block.plot.no_results", totalInCategory);
        } else {
            pageText = PlotI18n.tr("block.plot.page_info", page + 1, totalPages, totalInCategory);
        }
        int textW = this.textRenderer.getWidth(pageText);
        int textColor = totalPages > 1 ? COLOR_TEXT_NORMAL : COLOR_TEXT_MUTED;

        updatePagerTextScroll(pageText, textW, pagerTextAreaW, delta);
        renderPagerText(context, pageText, textW, textColor);

        // 按钮绘制在文字之上，避免页码文字遮挡按钮
        drawPagerButton(context, pagerPrevX, pagerY, pagerPrevW, PlotI18n.tr("block.plot.page_prev"),
                mouseX, mouseY, canPrev);
        drawPagerButton(context, pagerNextX, pagerY, pagerNextW, PlotI18n.tr("block.plot.page_next"),
                mouseX, mouseY, canNext);
    }

    /**
     * 在两页按钮之间的裁剪区域内绘制页码文字；宽度不足时水平滚动。
     */
    private void renderPagerText(DrawContext context, String pageText, int textW, int textColor) {
        if (pagerTextAreaW <= 0) return;

        int textY = pagerY + (BTN_H - this.textRenderer.fontHeight) / 2;
        int textX;
        if (textW <= pagerTextAreaW) {
            textX = pagerTextAreaX + (pagerTextAreaW - textW) / 2;
        } else {
            textX = pagerTextAreaX - Math.round(pagerScrollOffset);
        }

        context.enableScissor(pagerTextAreaX, pagerY, pagerTextAreaX + pagerTextAreaW, pagerY + BTN_H);
        context.drawText(this.textRenderer, pageText, textX, textY, textColor, false);
        context.disableScissor();
    }

    private void updatePagerTextScroll(String pageText, int textW, int areaW, float delta) {
        if (!pageText.equals(pagerScrollTextKey)) {
            pagerScrollTextKey = pageText;
            pagerScrollOffset = 0f;
            pagerScrollIncreasing = true;
            pagerScrollPauseUntil = Util.getMeasuringTimeMs() + PAGER_SCROLL_PAUSE_MS;
        }
        if (textW <= areaW || areaW <= 0) {
            pagerScrollOffset = 0f;
            return;
        }

        float maxScroll = textW - areaW;
        long now = Util.getMeasuringTimeMs();
        if (now < pagerScrollPauseUntil) {
            return;
        }

        pagerScrollOffset += delta * PAGER_SCROLL_SPEED * (pagerScrollIncreasing ? 1f : -1f);
        if (pagerScrollOffset >= maxScroll) {
            pagerScrollOffset = maxScroll;
            pagerScrollIncreasing = false;
            pagerScrollPauseUntil = now + PAGER_SCROLL_PAUSE_MS;
        } else if (pagerScrollOffset <= 0f) {
            pagerScrollOffset = 0f;
            pagerScrollIncreasing = true;
            pagerScrollPauseUntil = now + PAGER_SCROLL_PAUSE_MS;
        }
    }

    private void drawPagerButton(DrawContext context, int x, int y, int w,
                                 String text, int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && isInside(mouseX, mouseY, x, y, w, BlockConfigNativeScreen.BTN_H);
        int bg = !enabled ? COLOR_PAGER_DISABLED : (hover ? brighten(COLOR_PAGER_ACTIVE) : COLOR_PAGER_ACTIVE);
        context.fill(x, y, x + w, y + BlockConfigNativeScreen.BTN_H, bg);
        drawBorder(context, x, y, w, BlockConfigNativeScreen.BTN_H, enabled ? 0xFF606060 : 0xFF3A3A3A);
        int tw = this.textRenderer.getWidth(text);
        int tx = x + (w - tw) / 2;
        int ty = y + (BlockConfigNativeScreen.BTN_H - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, text, tx, ty,
                enabled ? (hover ? 0xFFFFFFFF : 0xFFCCCCCC) : COLOR_TEXT_HINT, false);
    }

    /** 调色盘区（含标签/操作提示/槽位/计数）。 */
    private void renderPalette(DrawContext context, int mouseX, int mouseY) {
        // 分隔线
        context.fill(panelX + MARGIN, paletteAreaY,
                panelX + panelW - MARGIN, paletteAreaY + 1, COLOR_DIVIDER);

        // 标签文字
        int labelY = paletteAreaY + SECTION_GAP;
        context.drawText(this.textRenderer, Text.translatable("block.plot.palette"),
                paletteX, labelY, 0xFFDDDDDD, false);

        // 操作提示（右对齐）
        String hint = PlotI18n.tr("block.plot.palette_hint");
        int hintW = this.textRenderer.getWidth(hint);
        context.drawText(this.textRenderer, hint,
                panelX + panelW - MARGIN - hintW, labelY, COLOR_TEXT_HINT, false);

        // 槽位
        for (int i = 0; i < MAX_PALETTE_SLOTS; i++) {
            int x = paletteX + i * (slotSize + SLOT_GAP);
            int y = paletteY;
            boolean hover  = isInside(mouseX, mouseY, x, y, slotSize, slotSize);
            boolean filled = i < palette.size();
            boolean isDrag = (i == dragIndex);

            int bg = !filled ? COLOR_PALETTE_EMPTY
                    : (isDrag ? 0xFF505050 : (hover ? COLOR_PALETTE_HOVER : COLOR_PALETTE_BG));
            context.fill(x, y, x + slotSize, y + slotSize, bg);
            
            // 边框：拖拽源用蓝色，拖拽目标预览用绿色，普通边框
            int borderColor = COLOR_PALETTE_BORDER;
            if (isDrag) {
                borderColor = 0xFF8888FF;  // 蓝色 - 正在拖
            } else if (dragIndex >= 0 && dragHoverSlot == i && i != dragIndex) {
                borderColor = 0xFF88FF88;  // 绿色 - 拖拽目标预览
            }
            drawBorder(context, x, y, slotSize, slotSize, borderColor);

            if (!filled) {
                // 空槽位显示 +
                if (i == palette.size()) { // 第一个空槽位有提示
                    context.drawText(this.textRenderer, "+",
                            x + (slotSize - this.textRenderer.getWidth("+")) / 2,
                            y + (slotSize - this.textRenderer.fontHeight) / 2,
                            0xFF3A3A3A, false);
                }
                continue;
            }

            ItemStack stack = BlockIconRenderer.getItemStackForBlock(palette.get(i));
            if (!stack.isEmpty()) {
                BlockIconRenderer.tryDrawItem(context, stack, x + slotInset, y + slotInset);
            }
        }

        // 计数行
        int count = palette.size();
        boolean isFull = count >= MAX_PALETTE_SLOTS;
        String countText = PlotI18n.tr("block.plot.selected_count", count, MAX_PALETTE_SLOTS);
        context.drawText(this.textRenderer, countText,
                paletteX, paletteCountY,
                isFull ? COLOR_TEXT_FULL : COLOR_TEXT_MUTED, false);
        if (isFull) {
            String fullHint = PlotI18n.tr("block.plot.palette_full");
            int fw = this.textRenderer.getWidth(fullHint);
            context.drawText(this.textRenderer, fullHint,
                    panelX + panelW - MARGIN - fw, paletteCountY,
                    COLOR_TEXT_FULL, false);
        }
    }

    /** 底部操作按钮。 */
    private void renderBottomButtons(DrawContext context, int mouseX, int mouseY) {
        drawMainButton(context, btnApplyX,  btnY, btnApplyW, PlotI18n.tr("block.plot.apply"), COLOR_BTN_APPLY,  mouseX, mouseY);
        drawMainButton(context, btnCancelX, btnY, btnCancelW, PlotI18n.tr("block.plot.cancel"),   COLOR_BTN_CANCEL, mouseX, mouseY);
        drawMainButton(context, btnClearX,  btnY, btnClearW, PlotI18n.tr("block.plot.clear"),   COLOR_BTN_CLEAR,  mouseX, mouseY);
    }

    private void drawMainButton(DrawContext context, int x, int y, int w,
                                String text, int baseColor, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, BlockConfigNativeScreen.BTN_H);
        context.fill(x, y, x + w, y + BlockConfigNativeScreen.BTN_H, hover ? brighten(baseColor) : baseColor);
        drawBorder(context, x, y, w, BlockConfigNativeScreen.BTN_H, 0xFF888888);
        // 文字正确居中
        int tw = this.textRenderer.getWidth(text);
        int tx = x + (w - tw) / 2;
        int ty = y + (BlockConfigNativeScreen.BTN_H - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, text, tx, ty, 0xFFFFFFFF, false);
    }

    /** 悬停 Tooltip（方块名称 + 注册 ID）。 */
    private void renderHoverTooltip(DrawContext context, int mouseX, int mouseY) {
        // 拖拽时不显示 Tooltip
        if (dragIndex >= 0) return;
        
        Block hovered = getHoveredGridBlock(mouseX, mouseY);
        if (hovered == null) hovered = getHoveredPaletteBlock(mouseX, mouseY);
        if (hovered == null) return;

        List<Text> lines = List.of(
                hovered.getName(),
                Text.literal(Registries.BLOCK.getId(hovered).toString())
        );
        context.drawTooltip(this.textRenderer, lines, mouseX + 10, mouseY + 10);
    }

    /** 拖拽时在鼠标位置渲染浮动的方块图标。 */
    private void renderDraggedItem(DrawContext context, int mouseX, int mouseY) {
        if (dragIndex < 0 || dragIndex >= palette.size()) return;
        
        Block draggedBlock = palette.get(dragIndex);
        ItemStack draggedStack = BlockIconRenderer.getItemStackForBlock(draggedBlock);
        
        if (!draggedStack.isEmpty()) {
            // 在鼠标位置绘制方块，略微偏下右以避免遮挡光标
            int offsetX = 8;
            int offsetY = 8;
            BlockIconRenderer.tryDrawItem(context, draggedStack, mouseX + offsetX, mouseY + offsetY);
            
            // 方块周围加一个微妙的半透明蓝色矩形边框，强化视觉效果
            int w = slotSize;
            int h = slotSize;
            context.fill(mouseX + offsetX - 1, mouseY + offsetY - 1, 
                    mouseX + offsetX + w + 1, mouseY + offsetY + h + 1, 
                    0x448888FF);  // 半透明蓝色背景
        }
    }

    // =========================================================================
    //  命中检测辅助
    // =========================================================================

    private Block getHoveredGridBlock(int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= filteredBlocks.size()) continue;
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridX + col * (slotSize + SLOT_GAP);
            int y = gridY + row * (slotSize + SLOT_GAP);
            if (isInside(mouseX, mouseY, x, y, slotSize, slotSize)) return filteredBlocks.get(idx);
        }
        return null;
    }

    private Block getHoveredPaletteBlock(int mouseX, int mouseY) {
        for (int i = 0; i < palette.size(); i++) {
            int x = paletteX + i * (slotSize + SLOT_GAP);
            if (isInside(mouseX, mouseY, x, paletteY, slotSize, slotSize)) return palette.get(i);
        }
        return null;
    }

    private int getHoveredPaletteIndex(double mouseX, double mouseY) {
        for (int i = 0; i < MAX_PALETTE_SLOTS; i++) {
            int x = paletteX + i * (slotSize + SLOT_GAP);
            if (isInside(mouseX, mouseY, x, paletteY, slotSize, slotSize)) return i;
        }
        return -1;
    }

    // =========================================================================
    //  输入事件
    // =========================================================================

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();

        // 标题栏 × 关闭
        if (btn == 0 && isInside(mx, my, closeBtnX, closeBtnY, closeBtnW, closeBtnH)) {
            close();
            return true;
        }

        // 搜索栏焦点管理（同步 Screen 焦点与 Widget 焦点，保证输入法事件链稳定）
        if (isInside(mx, my, searchX, searchY, contentW, SEARCH_H)) {
            searchBox.setFocused(true);
            this.setFocused(searchBox);
        } else {
            searchBox.setFocused(false);
            if (this.getFocused() == searchBox) {
                this.setFocused(null);
            }
        }

        // 分类侧边栏
        if (handleCategoryClick(mx, my)) return true;

        // 网格
        if (handleGridClick(mx, my, btn)) return true;

        // 调色盘（拖拽起点）
        if (btn == 0 && isInside(mx, my, paletteX, paletteY,
                MAX_PALETTE_SLOTS * (slotSize + SLOT_GAP), slotSize)) {
            int idx = getHoveredPaletteIndex(mx, my);
            if (idx >= 0 && idx < palette.size()) {
                dragIndex = idx;
                return true;
            }
        }
        if (btn == 1) {
            // 右键直接移除
            int idx = getHoveredPaletteIndex(mx, my);
            if (idx >= 0 && idx < palette.size()) {
                palette.remove(idx);
                return true;
            }
        }

        // 分页
        int totalPages = getTotalPages();
        if (btn == 0 && isInside(mx, my, pagerPrevX, pagerY, pagerPrevW, BTN_H) && page > 0) {
            page--;
            return true;
        }
        if (btn == 0 && isInside(mx, my, pagerNextX, pagerY, pagerNextW, BTN_H) && page < totalPages - 1) {
            page++;
            return true;
        }

        // 底部按钮
        if (btn == 0 && isInside(mx, my, btnApplyX,  btnY, btnApplyW,  BTN_H)) { applyAndClose(); return true; }
        if (btn == 0 && isInside(mx, my, btnCancelX, btnY, btnCancelW, BTN_H)) { close();         return true; }
        if (btn == 0 && isInside(mx, my, btnClearX,  btnY, btnClearW,  BTN_H)) { palette.clear(); return true; }

        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // 拖拽释放：交换调色盘槽位
        if (button == 0 && dragIndex >= 0) {
            int targetIdx = getHoveredPaletteIndex(mouseX, mouseY);
            if (targetIdx >= 0 && targetIdx < palette.size() && targetIdx != dragIndex) {
                Collections.swap(palette, dragIndex, targetIdx);
            }
            dragIndex = -1;
            dragHoverSlot = -1;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == 0 && dragIndex >= 0) {
            dragHoverSlot = getHoveredPaletteIndex(click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 优先：鼠标在侧边栏上时滚动分类栏
        boolean overSidebar = mouseX >= sidebarX && mouseX < sidebarX + sidebarW
                && mouseY >= sidebarY && mouseY < sidebarY + sidebarH;
        
        if (overSidebar) {
            int contentHeight = getSidebarContentHeight();
            int maxScroll = Math.max(0, contentHeight - sidebarH);
            
            // 滚轮向上为正，向下为负；调整为：向上滚 = 向上看内容 = scroll 减少（更负）
            int newScroll = sidebarScroll - (int) (verticalAmount * TAB_H);
            sidebarScroll = Math.max(-maxScroll, Math.min(0, newScroll));
            
            // 重新计算分类标签布局以应用新的滚动位置
            buildCategoryTabLayouts();
            return true;
        }
        
        // 次级：鼠标在网格或内容区上方时，滚轮翻页
        boolean overGrid = mouseX >= contentX && mouseX < contentX + contentW
                && mouseY >= gridY    && mouseY < gridY + GRID_ROWS * (slotSize + SLOT_GAP);
        if (overGrid) {
            int totalPages = getTotalPages();
            if (verticalAmount < 0 && page < totalPages - 1) { page++; return true; }
            if (verticalAmount > 0 && page > 0)              { page--; return true; }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        // 优先交给搜索框，确保输入法上屏字符可被稳定接收。
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.charTyped(charInput)) {
                return true;
            }

            // IME 兜底：某些输入法场景下 charTyped 可能返回 false，
            // 但 asString 仍有已提交文本（如中文上屏），此时手动写入。
            String committed = charInput.asString();
            if (!committed.isEmpty()) {
                searchBox.write(committed);
                return true;
            }
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();

        // 搜索框聚焦时，保留 ESC 的清空/失焦行为。
        if (searchBox.isFocused()) {
            if (keyCode == 256 /* ESCAPE */) {
                if (!searchBox.getText().isEmpty()) {
                    searchBox.setText("");
                } else {
                    searchBox.setFocused(false);
                }
                return true;
            }
        }

        // 搜索框聚焦时优先交给它处理（Ctrl+A/C/V、方向键、退格等）。
        if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(keyInput)) {
            return true;
        }

        // 其余按键交给 Screen/widget 树分发。
        if (super.keyPressed(keyInput)) {
            return true;
        }

        // 全局 ESC 关闭（搜索框无焦点时）
        if (keyCode == 256 /* ESCAPE */ && !searchBox.isFocused()) {
            close();
            return true;
        }
        return false;
    }

    // =========================================================================
    //  点击处理（分类 / 网格）
    // =========================================================================

    private boolean handleCategoryClick(double mx, double my) {
        for (CategoryTabLayout layout : categoryTabLayouts) {
            if (layout.y + layout.h <= sidebarY || layout.y >= sidebarY + sidebarH) continue;
            int tabX = layout.x + 1;
            int tabW = layout.w - 2;
            if (isInside(mx, my, tabX, layout.y, tabW, layout.h)) {
                if (layout.category != currentCategory) {
                    currentCategory = layout.category;
                    page = 0;
                    searchBox.setText("");
                    reloadRawCategory();
                    applySearchFilter();
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleGridClick(double mx, double my, int button) {
        if (button != 0) return false;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= filteredBlocks.size()) continue;
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridX + col * (slotSize + SLOT_GAP);
            int y = gridY + row * (slotSize + SLOT_GAP);
            if (!isInside(mx, my, x, y, slotSize, slotSize)) continue;

            Block block = filteredBlocks.get(idx);
            if (palette.contains(block)) {
                // 已在调色盘，点击不重复添加（高亮已表示选中）
                return true;
            }
            if (palette.size() >= MAX_PALETTE_SLOTS) {
                // 满员：无操作（调色盘区域计数行会显示红字提示）
                return true;
            }
            palette.add(block);
            return true;
        }
        return false;
    }

    // =========================================================================
    //  生命周期
    // =========================================================================

    private void applyAndClose() {
        configManager.setPaletteBlocks(palette);
        close();
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            if (parent instanceof PlotScreen) {
                PlotScreenState.markSuppressNextPlotClick();
            }
            client.setScreen(parent);
        }
    }

    // =========================================================================
    //  工具方法
    // =========================================================================

    private int getTotalPages() {
        return Math.max(1, (filteredBlocks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /**
     * 边界判断：使用严格小于（< x+w）避免相邻格子在边界像素重叠触发。
     */
    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int brighten(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 20);
        int g = Math.min(255, ((color >>> 8)  & 0xFF) + 20);
        int b = Math.min(255, ( color         & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x,         y,         x + w,     y + 1,     color); // 上
        context.fill(x,         y + h - 1, x + w,     y + h,     color); // 下
        context.fill(x,         y,         x + 1,     y + h,     color); // 左
        context.fill(x + w - 1, y,         x + w,     y + h,     color); // 右
    }
}