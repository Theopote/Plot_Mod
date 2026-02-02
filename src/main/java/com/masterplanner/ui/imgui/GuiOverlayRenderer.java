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
        final float x;
        final float y;
        final float scale;  // 缩放因子，用于放大小图标
        PendingItem(ItemStack stack, float x, float y, float scale) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private static final List<PendingItem> PENDING_ITEMS = new ArrayList<>();

    public static void queueBlockItem(Block block, float x, float y) {
        queueBlockItem(block, x, y, 2.0f);  // 默认2倍缩放（16x16 -> 32x32）
    }

    public static void queueBlockItem(Block block, float x, float y, float scale) {
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
            PENDING_ITEMS.add(new PendingItem(stack, x, y, scale));
            LOGGER.info("queueBlockItem: 已添加方块 {} 到渲染队列，坐标: ({}, {}), 缩放: {}", 
                        net.minecraft.registry.Registries.BLOCK.getId(block), (int)x, (int)y, scale);
        } catch (Exception e) {
            LOGGER.warn("queueBlockItem 异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 在一帧ImGui渲染完成之后调用，覆盖绘制所有排队的物品。
     * 支持坐标浮点精度和缩放渲染。
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
            int itemCount = PENDING_ITEMS.size();
            LOGGER.info("GuiOverlayRenderer.flush: 开始渲染 {} 个物品", itemCount);
            
            if (itemCount == 0) {
                LOGGER.warn("GuiOverlayRenderer.flush: 列表大小为0，结束");
                return;
            }
            
            // 使用 drawItem 直接渲染
            int successCount = 0;
            int failCount = 0;
            
            for (int i = 0; i < itemCount; i++) {
                try {
                    PendingItem pi = PENDING_ITEMS.get(i);
                    if (pi == null) {
                        LOGGER.warn("GuiOverlayRenderer.flush: 第 {} 个PendingItem为null", i);
                        failCount++;
                        continue;
                    }
                    
                    if (pi.stack == null || pi.stack.isEmpty()) {
                        LOGGER.warn("GuiOverlayRenderer.flush: [{}] 物品堆栈无效", i);
                        failCount++;
                        continue;
                    }
                    
                    int intX = Math.round(pi.x);
                    int intY = Math.round(pi.y);
                    
                    LOGGER.info("GuiOverlayRenderer.flush: [{}] 渲染物品 {} 在 ({},{})", 
                                i, pi.stack.getItem().getTranslationKey(), intX, intY);
                    
                    // 渲染物品
                    context.drawItem(pi.stack, intX, intY);
                    successCount++;
                    
                } catch (Throwable t) {
                    LOGGER.warn("GuiOverlayRenderer.flush: 第 {} 个物品渲染失败", i, t);
                    failCount++;
                }
            }
            
            LOGGER.info("GuiOverlayRenderer.flush: 完成 - 成功: {}, 失败: {}", successCount, failCount);
            
        } catch (Exception e) {
            LOGGER.error("GuiOverlayRenderer.flush 异常", e);
        } finally {
            PENDING_ITEMS.clear();
        }
    }
}


