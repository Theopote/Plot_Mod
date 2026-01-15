package com.masterplanner.ui.imgui;

import com.masterplanner.ui.component.BlockIconRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 在ImGui绘制之后覆盖渲染到屏幕上的GUI物品/图标渲染队列。
 * 目的：避免ImGui addImage纹理链路在某些环境无效导致的不可见问题，
 * 直接使用Minecraft的DrawContext渲染物品贴图，确保与物品栏样式一致。
 */
public final class GuiOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/GuiOverlayRenderer");

    private GuiOverlayRenderer() {}

    private static final class PendingItem {
        final ItemStack stack;
        final int x;
        final int y;
        PendingItem(ItemStack stack, int x, int y) {
            this.stack = stack;
            this.x = x;
            this.y = y;
        }
    }

    private static final List<PendingItem> PENDING_ITEMS = new ArrayList<>();

    public static void queueBlockItem(Block block, float x, float y) {
        if (block == null) {
            LOGGER.warn("queueBlockItem: block为null，跳过");
            return;
        }
        try {
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (stack == null || stack.isEmpty()) {
                LOGGER.warn("queueBlockItem: 方块 {} 的物品堆栈为空，跳过", net.minecraft.registry.Registries.BLOCK.getId(block));
                return;
            }
            PENDING_ITEMS.add(new PendingItem(stack, (int) x, (int) y));
            LOGGER.info("queueBlockItem: 已添加方块 {} 到渲染队列，坐标: ({}, {})", 
                        net.minecraft.registry.Registries.BLOCK.getId(block), (int)x, (int)y);
        } catch (Exception e) {
            LOGGER.warn("queueBlockItem 异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 在一帧ImGui渲染完成之后调用，覆盖绘制所有排队的物品。
     */
    public static void flush(DrawContext context) {
        if (PENDING_ITEMS.isEmpty()) {
            return;
        }
        if (context == null) {
            LOGGER.warn("GuiOverlayRenderer.flush: DrawContext为null，跳过渲染");
            PENDING_ITEMS.clear();
            return;
        }
        try {
            LOGGER.info("GuiOverlayRenderer.flush: 开始渲染 {} 个物品", PENDING_ITEMS.size());
            // 获取窗口尺寸用于验证坐标
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            int windowWidth = client != null && client.getWindow() != null ? client.getWindow().getScaledWidth() : 0;
            int windowHeight = client != null && client.getWindow() != null ? client.getWindow().getScaledHeight() : 0;
            
            if (windowWidth > 0 && windowHeight > 0) {
                LOGGER.info("GuiOverlayRenderer.flush: 窗口尺寸: {}x{}", windowWidth, windowHeight);
            }
            
            // 使用 drawItemWithoutEntity 直接渲染，避免实体相关的渲染问题
            for (PendingItem pi : PENDING_ITEMS) {
                // 检查坐标是否在合理范围内
                if (pi.x < 0 || pi.y < 0 || (windowWidth > 0 && pi.x > windowWidth) || (windowHeight > 0 && pi.y > windowHeight)) {
                    LOGGER.warn("GuiOverlayRenderer.flush: 坐标超出范围: ({}, {}), 窗口尺寸: {}x{}", 
                               pi.x, pi.y, windowWidth, windowHeight);
                }
                
                try {
                    // 直接使用 drawItemWithoutEntity，这个方法更适合 GUI 渲染
                    context.drawItemWithoutEntity(pi.stack, pi.x, pi.y);
                    LOGGER.debug("GuiOverlayRenderer.flush: 已渲染物品 {} 在坐标 ({}, {})", 
                                pi.stack.getItem().getTranslationKey(), pi.x, pi.y);
                } catch (Throwable t) {
                    // 如果 drawItemWithoutEntity 失败，尝试使用 drawItem
                    try {
                        context.drawItem(pi.stack, pi.x, pi.y);
                        LOGGER.debug("GuiOverlayRenderer.flush: 已使用drawItem渲染物品 {} 在坐标 ({}, {})", 
                                    pi.stack.getItem().getTranslationKey(), pi.x, pi.y);
                    } catch (Throwable ignore) {
                        LOGGER.warn("GuiOverlayRenderer.flush: 渲染物品 {} 在坐标 ({}, {}) 失败: {}", 
                                   pi.stack.getItem().getTranslationKey(), pi.x, pi.y, ignore.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("GuiOverlayRenderer.flush 失败", e);
        } finally {
            PENDING_ITEMS.clear();
        }
    }
}


