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
        if (block == null) return;
        try {
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (stack == null || stack.isEmpty()) return;
            PENDING_ITEMS.add(new PendingItem(stack, (int) x, (int) y));
        } catch (Exception e) {
            LOGGER.debug("queueBlockItem 异常: {}", e.getMessage());
        }
    }

    /**
     * 在一帧ImGui渲染完成之后调用，覆盖绘制所有排队的物品。
     */
    public static void flush(DrawContext context) {
        if (PENDING_ITEMS.isEmpty() || context == null) return;
        try {
            for (PendingItem pi : PENDING_ITEMS) {
                try {
                    context.drawItem(pi.stack, pi.x, pi.y);
                } catch (Throwable t) {
                    try {
                        context.drawItemWithoutEntity(pi.stack, pi.x, pi.y);
                    } catch (Throwable ignore) {
                        // 忽略
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


