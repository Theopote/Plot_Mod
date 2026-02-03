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
 * GuiOverlayRenderer - 在ImGui之后的安全覆盖渲染
 * 
 * 使用经过验证的 BlockIconRenderer.drawBlock() / drawItem() 进行渲染
 * BlockIconRenderer 已经完整处理了所有 GL 状态管理和 AIR 兜底
 * 
 * 这个类只负责：
 * 1. 队列管理（缓冲要绘制的图标）
 * 2. 在合适的时机调用 BlockIconRenderer 进行绘制
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

    /**
     * 队列一个方块（带缩放）
     * 注意：缩放在 BlockIconRenderer 的 pose translate 中处理，这里只用于后续计算
     */
    public static void queueBlockItem(Block block, float x, float y, float scale) {
        if (block == null) {
            LOGGER.warn("queueBlockItem: block为null，跳过");
            return;
        }

        try {
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);

            // BlockIconRenderer 已经处理 AIR，这里只是额外检查
            if (stack == null || stack.isEmpty()) {
                LOGGER.debug("⚠️  方块 {} 无有效 Item 形式，跳过队列", 
                            net.minecraft.registry.Registries.BLOCK.getId(block));
                return;
            }

            // 将 ImGui 的屏幕坐标转换为 Minecraft framebuffer/GUI 坐标
            // 原因：ImGui 使用 display coordinates（window size），而 DrawContext.drawItem
            // 期望 framebuffer (或经过缩放的 GUI) 坐标。这里使用 Minecraft 的 window
            // 信息计算缩放因子进行转换，避免坐标/速度不一致导致图标漂移或被遮挡。
            int intX;
            int intY;
            try {
                var mc = net.minecraft.client.MinecraftClient.getInstance();
                if (mc != null && mc.getWindow() != null) {
                    float fbW = Math.max(1, mc.getWindow().getFramebufferWidth());
                    float fbH = Math.max(1, mc.getWindow().getFramebufferHeight());
                    float winW = Math.max(1, mc.getWindow().getWidth());
                    float winH = Math.max(1, mc.getWindow().getHeight());
                    float sx = fbW / winW;
                    float sy = fbH / winH;
                    intX = Math.round(x * sx);
                    intY = Math.round(y * sy);
                } else {
                    intX = Math.round(x);
                    intY = Math.round(y);
                }
            } catch (Throwable t) {
                LOGGER.warn("queueBlockItem: 计算坐标缩放时出错，回退到原始坐标: {}", t.getMessage());
                intX = Math.round(x);
                intY = Math.round(y);
            }

            PENDING_ITEMS.add(new PendingItem(stack, intX, intY));
            LOGGER.debug("✓ 已队列方块: {} @ ({}, {})", 
                        net.minecraft.registry.Registries.BLOCK.getId(block), intX, intY);

        } catch (Exception e) {
            LOGGER.warn("queueBlockItem 异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 在一帧 ImGui 渲染完成后调用
     * 使用 BlockIconRenderer 的安全绘制方法进行渲染
     * 
     * @param context DrawContext（自动转换为 GuiGraphics 内部使用）
     */
    public static void flush(DrawContext context) {
        if (PENDING_ITEMS.isEmpty()) {
            return;
        }

        if (context == null) {
            LOGGER.warn("flush: DrawContext 为null，跳过渲染");
            PENDING_ITEMS.clear();
            return;
        }

        try {
            int itemCount = PENDING_ITEMS.size();
            LOGGER.info("🎨 GuiOverlayRenderer.flush: 开始渲染 {} 个物品", itemCount);

            // DrawContext 内部有 GuiGraphics，我们通过 getGuiGraphics() 获取
            // 注意：DrawContext 在 1.21+ 是 GuiGraphics 的包装
            // 直接使用 context 的方法来访问 GuiGraphics 的功能

            int successCount = 0;
            int skipCount = 0;

            for (int i = 0; i < itemCount; i++) {
                try {
                    PendingItem item = PENDING_ITEMS.get(i);
                    if (item == null || item.stack == null || item.stack.isEmpty()) {
                        LOGGER.debug("flush: 第 {} 个物品无效，跳过", i);
                        skipCount++;
                        continue;
                    }

                        // 使用统一的安全渲染入口，BlockIconRenderer 管理装饰和状态
                        com.masterplanner.ui.component.BlockIconRenderer.drawItem(context, item.stack, item.x, item.y);
                    successCount++;

                } catch (Throwable e) {
                    LOGGER.warn("flush: 第 {} 个物品渲染失败 - {}", i, e.getMessage());
                }
            }

            LOGGER.info("✓ GuiOverlayRenderer.flush: 完成 - 成功: {}, 跳过: {}", successCount, skipCount);

        } catch (Exception e) {
            LOGGER.error("GuiOverlayRenderer.flush 异常", e);
        } finally {
            PENDING_ITEMS.clear();
        }
    }
}

