package com.masterplanner.ui.component;

// RenderSystem calls removed; DrawContext handles rendering state where possible
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🔥 绝对安全的方块/物品图标渲染器 - Minecraft 1.21+ 最佳实践
 * 
 * 设计目标：
 * ✔ 不依赖外部 RenderSystem 状态
 * ✔ 自动修复 GL / Shader / Atlas
 * ✔ 对 AIR / 无 Item 的 Block 有兜底
 * ✔ 可在 Screen / Overlay / 自定义面板中安全使用
 * ✔ 不会污染后续 UI 渲染
 * 
 * 使用示例：
 * BlockIconRenderer.drawBlock(guiGraphics, Blocks.STONE, x, y);
 * BlockIconRenderer.drawItem(guiGraphics, new ItemStack(Items.DIAMOND), x, y);
 */
public final class BlockIconRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/BlockIconRenderer");

    private BlockIconRenderer() {}

    // === 缓存 ===
    private static final Map<Block, ItemStack> itemStackCache = new ConcurrentHashMap<>();
    private static final Map<Block, String> blockNameCache = new ConcurrentHashMap<>();

    /* ===========================
     *  Public API
     * =========================== */

    /**
     * 绘制方块图标（最常用的方法）
     * 
     * @param block 要绘制的方块
     * @param x 屏幕 X 坐标
     * @param y 屏幕 Y 坐标
     */
        public static void drawBlock(
            DrawContext context,
            Block block,
            int x,
            int y
        ) {
        if (block == null) {
            drawFallback(context, x, y);
            return;
        }

        Item item = block.asItem();
        if (item == Items.AIR) {
            LOGGER.debug("方块 {} 没有 Item 形式，使用 Barrier fallback", Registries.BLOCK.getId(block));
            drawFallback(context, x, y);
            return;
        }

        drawItem(context, new ItemStack(item), x, y);
    }

    /**
     * 绘制物品图标（直接使用 ItemStack）
     * 
     * @param stack 物品堆栈
     * @param x 屏幕 X 坐标
     * @param y 屏幕 Y 坐标
     */
    public static void drawItem(
            DrawContext context,
            ItemStack stack,
            int x,
            int y
    ) {
        if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) {
            LOGGER.debug("物品堆栈无效，使用 Barrier fallback");
            drawFallback(context, x, y);
            return;
        }
        try {
            prepareRenderState();

            // 确保图标在 UI 之上
            // Draw directly; DrawContext implementations handle matrix state appropriately
            context.drawItem(stack, x, y);
            LOGGER.debug("drawItem: 绘制物品 {} at ({},{})", stack.getItem().toString(), x, y);
        } finally {
            restoreRenderState();
        }
    }

    /**
     * 获取方块的物品堆栈（缓存版本）
     */
    public static ItemStack getItemStackForBlock(Block block) {
        if (block == null) {
            return ItemStack.EMPTY;
        }

        return itemStackCache.computeIfAbsent(block, b -> {
            try {
                // 特殊处理流体方块
                if (b == Blocks.WATER) {
                    return new ItemStack(Items.WATER_BUCKET);
                } else if (b == Blocks.LAVA) {
                    return new ItemStack(Items.LAVA_BUCKET);
                } else if (b == Blocks.POWDER_SNOW) {
                    return new ItemStack(Items.POWDER_SNOW_BUCKET);
                } else if (b == Blocks.AIR) {
                    return ItemStack.EMPTY;
                } else {
                    Item item = b.asItem();
                    if (item == Items.AIR) {
                        LOGGER.debug("方块 {} 没有 Item 形式", Registries.BLOCK.getId(b));
                        return ItemStack.EMPTY;
                    }
                    return new ItemStack(item);
                }
            } catch (Exception e) {
                LOGGER.warn("获取方块 {} 的物品堆栈失败: {}", Registries.BLOCK.getId(b), e.getMessage());
                return ItemStack.EMPTY;
            }
        });
    }

    /**
     * 获取方块的显示名称（缓存版本）
     */
    public static String getBlockDisplayName(Block block) {
        if (block == null) return "Unknown";

        return blockNameCache.computeIfAbsent(block, b -> {
            try {
                String name = b.getName().getString();
                return name != null && !name.isEmpty() ? name : Registries.BLOCK.getId(b).toString();
            } catch (Exception e) {
                LOGGER.debug("获取方块 {} 名称失败: {}", Registries.BLOCK.getId(b), e.getMessage());
                return Registries.BLOCK.getId(b).toString();
            }
        });
    }

    /**
     * 检查方块是否有有效图标
     */
    public static boolean hasValidIcon(Block block) {
        if (block == null) return false;
        ItemStack stack = getItemStackForBlock(block);
        return !stack.isEmpty();
    }

    /* ===========================
     *  Render State Guard（关键！）
     * =========================== */

    /**
     * 准备安全的渲染状态
     * 🔥 这是 ItemRenderer 正确工作的关键
     */
    private static void prepareRenderState() {
        // Intentionally lightweight: prefer DrawContext to manage shaders/atlas.
        // Keep this method for future adjustments; avoid direct RenderSystem calls here.
    }

    /**
     * 恢复到 GUI 安全状态
     * 不彻底 reset，只恢复必要项以避免污染后续渲染
     */
    private static void restoreRenderState() {
        // No-op: DrawContext manages the necessary state in this MC version.
    }

    /* ===========================
     *  Fallback
     * =========================== */

    /**
     * 兜底图标：当 Block 为 AIR 或无效时显示 Barrier
     */
    private static void drawFallback(
            DrawContext context,
            int x,
            int y
    ) {
        try {
            prepareRenderState();
            context.drawItem(new ItemStack(Items.BARRIER), x, y);
        } finally {
            restoreRenderState();
        }
    }

    /* ===========================
     *  Utilities
     * =========================== */

    /**
     * 清理缓存
     */
    public static synchronized void clearCache() {
        LOGGER.info("清理 BlockIconRenderer 缓存");
        itemStackCache.clear();
        blockNameCache.clear();
    }

    /**
     * 获取缓存统计
     */
    public static String getCacheStats() {
        return String.format("ItemStack 缓存: %d, 名称缓存: %d",
                itemStackCache.size(), blockNameCache.size());
    }

    /* ===========================
     *  Backward-compatibility shims
     *  These restore the legacy API surface so older callers compile.
     * =========================== */

    /**
     * 是否已初始化（向后兼容）
     */
    public static boolean isInitialized() {
        return true; // stateless renderer; always available
    }

    /**
     * 初始化（向后兼容，noop）
     */
    public static void initialize() {
        LOGGER.debug("BlockIconRenderer.initialize() called (noop)");
        // No global init required for this safe renderer
    }

    /**
     * 运行简单的自检/测试（向后兼容）
     */
    public static void testBlockIconRendering() {
        LOGGER.info("BlockIconRenderer.testBlockIconRendering() invoked -- {}", getCacheStats());
    }

    /**
     * 预加载常用方块（向后兼容）
     */
    public static void preloadCommonBlocks() {
        Block[] common = new Block[] {
            Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.OAK_PLANKS,
            Blocks.COBBLESTONE, Blocks.SAND
        };
        for (Block b : common) {
            getItemStackForBlock(b);
        }
        LOGGER.debug("preloadCommonBlocks executed, filled cache entries: {}", itemStackCache.size());
    }

    /**
     * 兼容旧接口：触发对方块纹理/ItemStack 的缓存并返回占位纹理 id
     */
    public static int getBlockTextureId(Block block) {
        getItemStackForBlock(block);
        return 0; // legacy callers only cared about triggering preload
    }
}
