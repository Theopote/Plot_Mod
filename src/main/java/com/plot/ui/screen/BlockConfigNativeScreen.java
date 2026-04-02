package com.plot.ui.screen;

import com.plot.ui.component.BlockIconRenderer;
import com.plot.ui.dialog.BlockConfigDialog.BlockCategoryManager.BlockCategory;
import com.plot.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 原生方块配置面板（3D 物品图标稳定路径）。
 *
 * 该 Screen 只负责方块选择交互，最终仍回写 CompactBlockConfigDialog 的调色盘与事件流。
 */
public class BlockConfigNativeScreen extends Screen {
    private static final int MAX_PALETTE_SLOTS = 12;
    private static final int GRID_COLS = 12;
    private static final int GRID_ROWS = 6;
    private static final int PAGE_SIZE = GRID_COLS * GRID_ROWS;

    private static final int SLOT_GAP = 1;
    private static final int MARGIN = 3; //边距

    // 面板元素高度和间距
    private static final int TITLE_HEIGHT = 14;      // 标题栏高度
    private static final int TITLE_GAP = 2;          // 标题栏与分类按钮间距
    private static final int CATEGORY_HEIGHT = 14;   // 分类按钮高度
    private static final int CATEGORY_GAP = 2;       // 分类按钮与方块区间距
    private static final int PALETTE_LABEL_GAP = 2;  // "调色盘"文字与槽位间距
    private static final int PALETTE_GAP = 6;        // 调色盘与底部按钮间距
    private static final int BOTTOM_MARGIN = 4;      // 底部边距
    private final CompactBlockConfigDialog bridge;
    private final Screen parent;

    private BlockCategory currentCategory = BlockCategory.BUILDING_BLOCKS;
    private List<Block> categoryBlocks = Collections.emptyList();
    private final List<Block> palette = new ArrayList<>();

    private int page = 0;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int slotSize;
    private int slotInset;

    private int gridX;
    private int gridY;
    private int pagerY;
    private int pagerPrevX;
    private int pagerNextX;

    private int paletteX;
    private int paletteY;

    private int btnApplyX;
    private int btnCancelX;
    private int btnClearX;
    private int btnY;
    private static final int BTN_W = 70;
    private static final int BTN_H = 14;
    private static final int BUTTON_GAP = 6;
    private static final int PAGER_BTN_W = 40;
    private static final int PAGER_GAP = 2;

    // 分类标签的换行布局信息
    private static class CategoryTabLayout {
        BlockCategory category;
        int x, y, w, h;
        CategoryTabLayout(BlockCategory category, int x, int y, int w, int h) {
            this.category = category;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
    private List<CategoryTabLayout> categoryTabLayouts = new ArrayList<>();
    private int categoryTabsHeight = CATEGORY_HEIGHT; // 分类标签实际占用的高度

    public BlockConfigNativeScreen(CompactBlockConfigDialog bridge, Screen parent) {
        super(Text.of("方块配置"));
        this.bridge = bridge;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // 先加载当前分类，确保分页信息可用于布局计算。
        reloadCategory();

        panelH = Math.min(760, this.height - 24);
        panelY = (this.height - panelH) / 2;

        // 从面板高度反推 slotSize
        int byHeight = (panelH - 220) / GRID_ROWS;
        slotSize = Math.max(18, Math.min(36, byHeight));
        slotInset = Math.max(1, (slotSize - 16) / 2);

        // 面板宽度 = 12 x slotSize + 11 x SLOT_GAP + 2 x MARGIN
        panelW = GRID_COLS * slotSize + (GRID_COLS - 1) * SLOT_GAP + 2 * MARGIN;
        panelW = Math.min(panelW, this.width - 24);
        panelX = (this.width - panelW) / 2;

        // 从上到下布局各个元素
        int currentY = panelY;
        
        // 1. 标题栏："方块配置"
        int titleY = currentY;
        currentY += TITLE_HEIGHT + TITLE_GAP;
        
        // 2. 分类标签按钮（需要先计算换行布局）
        int categoryY = currentY;
        calculateCategoryTabsLayout(categoryY);
        currentY += categoryTabsHeight + CATEGORY_GAP;
        
        // 3. 方块展示区域
        gridX = panelX + MARGIN;
        gridY = currentY;
        int gridHeight = GRID_ROWS * slotSize + (GRID_ROWS - 1) * SLOT_GAP;
        currentY += gridHeight + PAGER_GAP;

        // 方块展示区下方分页控件（仅多页时显示）。
        pagerY = currentY;
        pagerPrevX = gridX;
        pagerNextX = pagerPrevX + PAGER_BTN_W + BUTTON_GAP;
        currentY += BTN_H + PALETTE_LABEL_GAP;
        
        // 4. "调色盘"标签
        int paletteLabelY = currentY;
        currentY += 8 + PALETTE_LABEL_GAP;
        
        // 5. 调色盘槽位
        paletteX = panelX + MARGIN;
        paletteY = currentY;
        currentY += slotSize + PALETTE_GAP;
        
        // 6. 底部按钮
        btnY = panelY + panelH - BTN_H - BOTTOM_MARGIN;
        btnApplyX = panelX + panelW - (BTN_W * 3 + BUTTON_GAP * 2 + MARGIN);
        btnCancelX = btnApplyX + BTN_W + BUTTON_GAP;
        btnClearX = btnCancelX + BTN_W + BUTTON_GAP;

        if (bridge != null) {
            palette.clear();
            palette.addAll(bridge.getPaletteBlocksSnapshot());
        }
    }

    private void reloadCategory() {
        if (bridge == null) {
            categoryBlocks = Collections.emptyList();
        } else {
            categoryBlocks = bridge.getBlocksForCategory(currentCategory);
        }

        int maxPage = Math.max(0, (categoryBlocks.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 避免默认 Screen 背景二次触发 blur（1.21.x 每帧仅允许一次）。
        context.fill(0, 0, this.width, this.height, 0x90000000);

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE61F1F1F);
        drawBorder(context, panelX, panelY, panelW, panelH, 0xFF4A4A4A);
        renderTitleBar(context);

        renderCategoryTabs(context, mouseX, mouseY);
        renderGrid(context, mouseX, mouseY);
        renderPalette(context, mouseX, mouseY);
        renderPagerAndButtons(context, mouseX, mouseY);
        renderHoverTooltip(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no-op: 当前 Screen 自己绘制半透明背景，禁止重复 blur。
    }

    private void renderTitleBar(DrawContext context) {
        int x = panelX;
        int y = panelY;
        int w = panelW;

        context.fill(x, y, x + w, y + TITLE_HEIGHT, 0xFF4A4A4A);
        drawBorder(context, x, y, w, TITLE_HEIGHT, 0xFF666666);

        int textX = x + (w - this.textRenderer.getWidth(this.title)) / 2;
        int textY = y + (TITLE_HEIGHT - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, this.title, textX, textY, 0xFFFFFFFF, false);
    }

    /**
     * 计算分类标签的换行布局
     */
    private void calculateCategoryTabsLayout(int startY) {
        categoryTabLayouts.clear();
        List<BlockCategory> categories = bridge != null ? bridge.getAvailableCategories() : List.of(BlockCategory.values());
        
        int x = panelX + MARGIN;
        int y = startY;
        int maxY = y;
        int panelRight = panelX + panelW - MARGIN;
        
        for (BlockCategory category : categories) {
            String text = category.getDisplayName();
            int w = this.textRenderer.getWidth(text) + 4; // 文字宽度 + 2px 左右内边距
            
            // 检查是否需要换行
            if (x + w > panelRight && x > panelX + MARGIN) {
                x = panelX + MARGIN;
                y += CATEGORY_HEIGHT + BUTTON_GAP;
            }
            
            categoryTabLayouts.add(new CategoryTabLayout(category, x, y, w, CATEGORY_HEIGHT));
            maxY = Math.max(maxY, y + CATEGORY_HEIGHT);
            x += w + BUTTON_GAP;
        }
        
        categoryTabsHeight = maxY - startY;
    }

    private void renderCategoryTabs(DrawContext context, int mouseX, int mouseY) {
        for (CategoryTabLayout layout : categoryTabLayouts) {
            BlockCategory category = layout.category;
            int x = layout.x;
            int y = layout.y;
            int w = layout.w;
            int h = layout.h;
            
            String text = category.getDisplayName();
            boolean active = category == currentCategory;
            boolean hover = isInside(mouseX, mouseY, x, y, w, h);

            int bg = active ? 0xFF4A6FA5 : (hover ? 0xFF3B3B3B : 0xFF2D2D2D);
            context.fill(x, y, x + w, y + h, bg);
            drawBorder(context, x, y, w, h, 0xFF5A5A5A);
            int textX = x + (w - this.textRenderer.getWidth(text)) / 2;
            int textY = y + (h - this.textRenderer.fontHeight) / 2;
            context.drawText(this.textRenderer, text, textX, textY, 0xFFFFFFFF, false);
        }
    }

    private void renderGrid(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;

            int x = gridX + col * (slotSize + SLOT_GAP);
            int y = gridY + row * (slotSize + SLOT_GAP);

            boolean hover = isInside(mouseX, mouseY, x, y, slotSize, slotSize);
            context.fill(x, y, x + slotSize, y + slotSize, hover ? 0xFF464646 : 0xFF343434);
            drawBorder(context, x, y, slotSize, slotSize, 0xFF616161);

            if (idx >= categoryBlocks.size()) {
                continue;
            }

            Block block = categoryBlocks.get(idx);
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (!stack.isEmpty()) {
                BlockIconRenderer.tryDrawItem(context, stack, x + slotInset, y + slotInset);
            }
        }
    }

    private void renderPalette(DrawContext context, int mouseX, int mouseY) {
        int labelY = paletteY - 8 - PALETTE_LABEL_GAP;
        int lineY = labelY - 2;
        context.fill(panelX + MARGIN, lineY, panelX + panelW - MARGIN, lineY + 1, 0xFF5A5A5A);
        context.drawText(this.textRenderer, Text.of("调色盘"), paletteX, labelY, 0xFFE6E6E6, false);

        for (int i = 0; i < MAX_PALETTE_SLOTS; i++) {
            int x = paletteX + i * (slotSize + SLOT_GAP);
            int y = paletteY;
            boolean hover = isInside(mouseX, mouseY, x, y, slotSize, slotSize);

            context.fill(x, y, x + slotSize, y + slotSize, hover ? 0xFF4A4A4A : 0xFF2F2F2F);
            drawBorder(context, x, y, slotSize, slotSize, 0xFF707070);

            if (i >= palette.size()) {
                continue;
            }

            ItemStack stack = BlockIconRenderer.getItemStackForBlock(palette.get(i));
            if (!stack.isEmpty()) {
                BlockIconRenderer.tryDrawItem(context, stack, x + slotInset, y + slotInset);
            }
        }
    }

    private void renderPagerAndButtons(DrawContext context, int mouseX, int mouseY) {
        int totalPages = Math.max(1, (categoryBlocks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (totalPages > 1) {
            String pageText = String.format("第 %d/%d 页", page + 1, totalPages);
            context.drawText(this.textRenderer, pageText, pagerNextX + PAGER_BTN_W + BUTTON_GAP, pagerY + 3, 0xFFD6D6D6, false);
            drawMiniButton(context, pagerPrevX, pagerY, PAGER_BTN_W, BTN_H, "上一页", mouseX, mouseY);
            drawMiniButton(context, pagerNextX, pagerY, PAGER_BTN_W, BTN_H, "下一页", mouseX, mouseY);
        }

        drawMainButton(context, btnApplyX, btnY, BTN_W, BTN_H, "应用", 0xFF2E7D32, mouseX, mouseY);
        drawMainButton(context, btnCancelX, btnY, BTN_W, BTN_H, "取消", 0xFF616161, mouseX, mouseY);
        drawMainButton(context, btnClearX, btnY, BTN_W, BTN_H, "清空", 0xFF8E2424, mouseX, mouseY);
    }

    private void drawMiniButton(DrawContext context, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, hover ? 0xFF4A4A4A : 0xFF333333);
        drawBorder(context, x, y, w, h, 0xFF777777);
        context.drawText(this.textRenderer, text, x + 5, y + 3, 0xFFFFFFFF, false);
    }

    private void drawMainButton(DrawContext context, int x, int y, int w, int h, String text, int baseColor, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        int bg = hover ? brighten(baseColor) : baseColor;
        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, 0xFF919191);
        context.drawText(this.textRenderer, text, x + 24, y + 3, 0xFFFFFFFF, false);
    }

    private void renderHoverTooltip(DrawContext context, int mouseX, int mouseY) {
        Block hovered = getHoveredGridBlock(mouseX, mouseY);
        if (hovered == null) {
            hovered = getHoveredPaletteBlock(mouseX, mouseY);
        }
        if (hovered == null) {
            return;
        }

        List<Text> lines = List.of(
                hovered.getName(),
                Text.literal(Registries.BLOCK.getId(hovered).toString())
        );
        context.drawTooltip(this.textRenderer, lines, mouseX + 10, mouseY + 10);
    }

    private Block getHoveredGridBlock(int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= categoryBlocks.size()) {
                continue;
            }
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridX + col * (slotSize + SLOT_GAP);
            int y = gridY + row * (slotSize + SLOT_GAP);
            if (isInside(mouseX, mouseY, x, y, slotSize, slotSize)) {
                return categoryBlocks.get(idx);
            }
        }
        return null;
    }

    private Block getHoveredPaletteBlock(int mouseX, int mouseY) {
        for (int i = 0; i < palette.size(); i++) {
            int x = paletteX + i * (slotSize + SLOT_GAP);
            int y = paletteY;
            if (isInside(mouseX, mouseY, x, y, slotSize, slotSize)) {
                return palette.get(i);
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (handleCategoryClick(mouseX, mouseY)) {
            return true;
        }

        if (handleGridClick(mouseX, mouseY, button)) {
            return true;
        }

        if (handlePaletteClick(mouseX, mouseY, button)) {
            return true;
        }

        int totalPages = Math.max(1, (categoryBlocks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (totalPages > 1 && isInside(mouseX, mouseY, pagerPrevX, pagerY, PAGER_BTN_W, BTN_H) && button == 0) {
            if (page > 0) {
                page--;
            }
            return true;
        }

        if (totalPages > 1 && isInside(mouseX, mouseY, pagerNextX, pagerY, PAGER_BTN_W, BTN_H) && button == 0) {
            if (page < totalPages - 1) {
                page++;
            }
            return true;
        }

        if (isInside(mouseX, mouseY, btnApplyX, btnY, BTN_W, BTN_H) && button == 0) {
            applyAndClose();
            return true;
        }

        if (isInside(mouseX, mouseY, btnCancelX, btnY, BTN_W, BTN_H) && button == 0) {
            close();
            return true;
        }

        if (isInside(mouseX, mouseY, btnClearX, btnY, BTN_W, BTN_H) && button == 0) {
            palette.clear();
            return true;
        }

        return super.mouseClicked(click, handled);
    }

    private boolean handleCategoryClick(double mouseX, double mouseY) {
        for (CategoryTabLayout layout : categoryTabLayouts) {
            int x = layout.x;
            int y = layout.y;
            int w = layout.w;
            int h = layout.h;
            if (isInside(mouseX, mouseY, x, y, w, h)) {
                currentCategory = layout.category;
                page = 0;
                reloadCategory();
                return true;
            }
        }
        return false;
    }

    private boolean handleGridClick(double mouseX, double mouseY, int button) {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= categoryBlocks.size()) {
                continue;
            }

            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridX + col * (slotSize + SLOT_GAP);
            int y = gridY + row * (slotSize + SLOT_GAP);
            if (!isInside(mouseX, mouseY, x, y, slotSize, slotSize)) {
                continue;
            }

            if (button != 0) {
                return true;
            }

            Block block = categoryBlocks.get(idx);
            if (palette.contains(block)) {
                return true;
            }
            if (palette.size() >= MAX_PALETTE_SLOTS) {
                return true;
            }
            palette.add(block);
            return true;
        }

        return false;
    }

    private boolean handlePaletteClick(double mouseX, double mouseY, int button) {
        for (int i = 0; i < palette.size(); i++) {
            int x = paletteX + i * (slotSize + SLOT_GAP);
            int y = paletteY;
            if (!isInside(mouseX, mouseY, x, y, slotSize, slotSize)) {
                continue;
            }

            if (button == 1) {
                palette.remove(i);
                return true;
            }

            if (button == 0) {
                Collections.swap(palette, i, Math.max(0, i - 1));
                return true;
            }
        }
        return false;
    }

    private void applyAndClose() {
        if (bridge != null) {
            bridge.setPaletteBlocksFromExternal(palette);
            bridge.applySelectionFromExternal();
        }
        close();
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int brighten(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 18);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + 18);
        int b = Math.min(255, (color & 0xFF) + 18);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}
