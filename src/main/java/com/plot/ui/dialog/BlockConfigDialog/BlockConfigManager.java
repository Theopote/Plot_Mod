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
    private static final BlockConfigManager INSTANCE = new BlockConfigManager();

    private final BlockCategoryManager categoryManager;
    private final List<Block> paletteBlocks = new ArrayList<>();

    private BlockConfigManager() {
        this.categoryManager = new BlockCategoryManager(null, null, message -> {});
    }

    public static BlockConfigManager getInstance() {
        return INSTANCE;
    }

    public List<BlockCategory> getAvailableCategories() {
        return List.of(BlockCategory.values());
    }

    public List<Block> getBlocksForCategory(BlockCategory category) {
        List<Block> blocks = categoryManager.getBlocksInCategory(category);
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
