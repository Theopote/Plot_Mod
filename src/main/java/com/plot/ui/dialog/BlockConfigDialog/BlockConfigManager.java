package com.plot.ui.dialog.BlockConfigDialog;

import com.plot.ui.component.BlockIconRenderer;
import com.plot.ui.dialog.BlockConfigDialog.BlockCategoryManager.BlockCategory;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 原生方块配置面板使用的全局状态管理器。
 * <p>
 * 负责：
 * 1. 提供分类后的方块列表；
 * 2. 持久保存当前调色盘选择；
 * 3. 为状态面板/线转方块逻辑提供统一查询入口。
 */
public final class BlockConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BlockConfigManager");
    private static final int MAX_PALETTE_SLOTS = 14;
    /** 打开配置面板时，空调色盘的默认方块 */
    public static final String DEFAULT_PALETTE_BLOCK_ID = "minecraft:light_blue_wool";
    private static volatile BlockConfigManager instance;

    private BlockCategoryManager categoryManager;
    private final List<Block> paletteBlocks = new ArrayList<>();

    private BlockConfigManager() {
    }

    private synchronized BlockCategoryManager categoryManager() {
        if (categoryManager == null) {
            categoryManager = new BlockCategoryManager(null, null, message -> {});
        }
        return categoryManager;
    }

    public static BlockConfigManager getInstance() {
        BlockConfigManager manager = instance;
        if (manager == null) {
            synchronized (BlockConfigManager.class) {
                manager = instance;
                if (manager == null) {
                    manager = new BlockConfigManager();
                    instance = manager;
                }
            }
        }
        return manager;
    }

    public List<BlockCategory> getAvailableCategories() {
        return List.of(BlockCategory.values());
    }

    public List<Block> getBlocksForCategory(BlockCategory category) {
        List<Block> blocks = categoryManager().getBlocksInCategory(category);
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Block> filtered = new ArrayList<>();
        for (Block block : blocks) {
            if (block == null) {
                continue;
            }
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (!stack.isEmpty()) {
                filtered.add(block);
            }
        }
        return filtered;
    }

    public synchronized List<Block> getPaletteBlocksSnapshot() {
        return new ArrayList<>(paletteBlocks);
    }

    /**
     * 若调色盘为空，放入默认方块（淡蓝色羊毛）。可被用户清空或替换。
     *
     * @return 是否写入了默认方块
     */
    public synchronized boolean ensureDefaultPaletteIfEmpty() {
        if (!paletteBlocks.isEmpty()) {
            return false;
        }
        Block block = resolveDefaultPaletteBlock();
        if (block == null) {
            LOGGER.warn("无法解析默认调色盘方块: {}", DEFAULT_PALETTE_BLOCK_ID);
            return false;
        }
        paletteBlocks.add(block);
        LOGGER.info("方块调色盘为空，已放入默认方块: {}", DEFAULT_PALETTE_BLOCK_ID);
        return true;
    }

    private static Block resolveDefaultPaletteBlock() {
        try {
            Block block = Registries.BLOCK.get(Identifier.of(DEFAULT_PALETTE_BLOCK_ID));
            if (block == null) {
                return null;
            }
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            return stack.isEmpty() ? null : block;
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void setPaletteFromBlockIds(List<String> blockIds) {
        paletteBlocks.clear();
        if (blockIds == null || blockIds.isEmpty()) {
            LOGGER.info("方块调色盘已清空");
            return;
        }

        for (String blockId : blockIds) {
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            if (paletteBlocks.size() >= MAX_PALETTE_SLOTS) {
                break;
            }
            try {
                Block block = Registries.BLOCK.get(Identifier.of(blockId));
                if (block == null) {
                    continue;
                }
                ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
                if (!stack.isEmpty()) {
                    paletteBlocks.add(block);
                }
            } catch (Exception ignored) {
                // skip invalid ids
            }
        }

        LOGGER.info("方块调色盘已更新: {} 个方块", paletteBlocks.size());
    }

    public synchronized void setPaletteBlocks(List<Block> blocks) {
        paletteBlocks.clear();
        if (blocks == null || blocks.isEmpty()) {
            LOGGER.info("方块调色盘已清空");
            return;
        }

        for (Block block : blocks) {
            if (block == null) {
                continue;
            }
            if (paletteBlocks.size() >= MAX_PALETTE_SLOTS) {
                break;
            }
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (!stack.isEmpty()) {
                paletteBlocks.add(block);
            }
        }

        LOGGER.info("方块调色盘已更新: {} 个方块", paletteBlocks.size());
    }

    public synchronized List<String> getSelectedBlockIds() {
        return paletteBlocks.stream()
                .map(block -> Registries.BLOCK.getId(block).toString())
                .toList();
    }

    public synchronized boolean hasSelectedBlocks() {
        return !paletteBlocks.isEmpty();
    }

    public synchronized int getSelectedBlockCount() {
        return paletteBlocks.size();
    }
}
