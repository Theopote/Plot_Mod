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

    private static final int SLOT_SIZE = 20;
    private static final int SLOT_INSET = 2;
    private static final int SLOT_GAP = 4;

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

    private int gridX;
    private int gridY;

    private int paletteX;
    private int paletteY;

    private int btnApplyX;
    private int btnCancelX;
    private int btnClearX;
    private int btnY;
    private static final int BTN_W = 70;
    private static final int BTN_H = 20;

    public BlockConfigNativeScreen(CompactBlockConfigDialog bridge, Screen parent) {
        super(Text.of("方块配置（原生）"));
        this.bridge = bridge;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        panelW = Math.min(760, this.width - 24);
        panelH = Math.min(560, this.height - 24);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        gridX = panelX + 16;
        gridY = panelY + 62;

        paletteX = panelX + 16;
        paletteY = gridY + GRID_ROWS * (SLOT_SIZE + SLOT_GAP) + 26;

        btnY = panelY + panelH - 32;
        btnApplyX = panelX + panelW - (BTN_W * 3 + 16 + 12);
        btnCancelX = btnApplyX + BTN_W + 6;
        btnClearX = btnCancelX + BTN_W + 6;

        if (bridge != null) {
            palette.clear();
            palette.addAll(bridge.getPaletteBlocksSnapshot());
        }

        reloadCategory();
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
        context.drawText(this.textRenderer, this.title, panelX + 14, panelY + 12, 0xFFFFFFFF, false);

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

    private void renderCategoryTabs(DrawContext context, int mouseX, int mouseY) {
        List<BlockCategory> categories = bridge != null ? bridge.getAvailableCategories() : List.of(BlockCategory.values());

        int x = panelX + 14;
        int y = panelY + 30;
        int h = 18;

        for (BlockCategory category : categories) {
            String text = category.getDisplayName();
            int w = Math.max(46, this.textRenderer.getWidth(text) + 12);
            boolean active = category == currentCategory;
            boolean hover = isInside(mouseX, mouseY, x, y, w, h);

            int bg = active ? 0xFF4A6FA5 : (hover ? 0xFF3B3B3B : 0xFF2D2D2D);
            context.fill(x, y, x + w, y + h, bg);
            drawBorder(context, x, y, w, h, 0xFF5A5A5A);
            context.drawText(this.textRenderer, text, x + 6, y + 5, 0xFFFFFFFF, false);
            x += w + 6;
        }
    }

    private void renderGrid(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;

            int x = gridX + col * (SLOT_SIZE + SLOT_GAP);
            int y = gridY + row * (SLOT_SIZE + SLOT_GAP);

            boolean hover = isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hover ? 0xFF464646 : 0xFF343434);
            drawBorder(context, x, y, SLOT_SIZE, SLOT_SIZE, 0xFF616161);

            if (idx >= categoryBlocks.size()) {
                continue;
            }

            Block block = categoryBlocks.get(idx);
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (!stack.isEmpty()) {
                BlockIconRenderer.tryDrawItem(context, stack, x + SLOT_INSET, y + SLOT_INSET);
            }
        }
    }

    private void renderPalette(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, Text.of("调色盘"), paletteX, paletteY - 14, 0xFFE6E6E6, false);

        for (int i = 0; i < MAX_PALETTE_SLOTS; i++) {
            int x = paletteX + i * (SLOT_SIZE + SLOT_GAP);
            int y = paletteY;
            boolean hover = isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);

            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hover ? 0xFF4A4A4A : 0xFF2F2F2F);
            drawBorder(context, x, y, SLOT_SIZE, SLOT_SIZE, 0xFF707070);

            if (i >= palette.size()) {
                continue;
            }

            ItemStack stack = BlockIconRenderer.getItemStackForBlock(palette.get(i));
            if (!stack.isEmpty()) {
                BlockIconRenderer.tryDrawItem(context, stack, x + SLOT_INSET, y + SLOT_INSET);
            }
        }
    }

    private void renderPagerAndButtons(DrawContext context, int mouseX, int mouseY) {
        int totalPages = Math.max(1, (categoryBlocks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        String pageText = String.format("第 %d/%d 页", page + 1, totalPages);
        context.drawText(this.textRenderer, pageText, panelX + 16, btnY + 6, 0xFFD6D6D6, false);

        int prevX = panelX + 110;
        int nextX = panelX + 160;
        drawMiniButton(context, prevX, btnY, 40, BTN_H, "上一页", mouseX, mouseY);
        drawMiniButton(context, nextX, btnY, 40, BTN_H, "下一页", mouseX, mouseY);

        drawMainButton(context, btnApplyX, btnY, BTN_W, BTN_H, "应用", 0xFF2E7D32, mouseX, mouseY);
        drawMainButton(context, btnCancelX, btnY, BTN_W, BTN_H, "取消", 0xFF616161, mouseX, mouseY);
        drawMainButton(context, btnClearX, btnY, BTN_W, BTN_H, "清空", 0xFF8E2424, mouseX, mouseY);
    }

    private void drawMiniButton(DrawContext context, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, hover ? 0xFF4A4A4A : 0xFF333333);
        drawBorder(context, x, y, w, h, 0xFF777777);
        context.drawText(this.textRenderer, text, x + 5, y + 6, 0xFFFFFFFF, false);
    }

    private void drawMainButton(DrawContext context, int x, int y, int w, int h, String text, int baseColor, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        int bg = hover ? brighten(baseColor) : baseColor;
        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, 0xFF919191);
        context.drawText(this.textRenderer, text, x + 24, y + 6, 0xFFFFFFFF, false);
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
            int x = gridX + col * (SLOT_SIZE + SLOT_GAP);
            int y = gridY + row * (SLOT_SIZE + SLOT_GAP);
            if (isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                return categoryBlocks.get(idx);
            }
        }
        return null;
    }

    private Block getHoveredPaletteBlock(int mouseX, int mouseY) {
        for (int i = 0; i < palette.size(); i++) {
            int x = paletteX + i * (SLOT_SIZE + SLOT_GAP);
            int y = paletteY;
            if (isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
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

        if (isInside(mouseX, mouseY, panelX + 110, btnY, 40, BTN_H) && button == 0) {
            if (page > 0) {
                page--;
            }
            return true;
        }

        if (isInside(mouseX, mouseY, panelX + 160, btnY, 40, BTN_H) && button == 0) {
            int totalPages = Math.max(1, (categoryBlocks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
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
        List<BlockCategory> categories = bridge != null ? bridge.getAvailableCategories() : List.of(BlockCategory.values());
        int x = panelX + 14;
        int y = panelY + 30;
        int h = 18;

        for (BlockCategory category : categories) {
            String text = category.getDisplayName();
            int w = Math.max(46, this.textRenderer.getWidth(text) + 12);
            if (isInside(mouseX, mouseY, x, y, w, h)) {
                currentCategory = category;
                page = 0;
                reloadCategory();
                return true;
            }
            x += w + 6;
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
            int x = gridX + col * (SLOT_SIZE + SLOT_GAP);
            int y = gridY + row * (SLOT_SIZE + SLOT_GAP);
            if (!isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
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
            int x = paletteX + i * (SLOT_SIZE + SLOT_GAP);
            int y = paletteY;
            if (!isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
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
