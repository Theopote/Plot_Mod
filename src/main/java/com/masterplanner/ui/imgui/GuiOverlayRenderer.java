package com.masterplanner.ui.imgui;

import com.masterplanner.ui.component.BlockIconRenderer;
import com.masterplanner.ui.imgui.gl.ImGuiGLStateGuard;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
    private static volatile long lastCoordScaleLogMs;

    private GuiOverlayRenderer() {}

    private static final class PendingItem {
        final ItemStack stack;
        final int x;
        final int y;
        final float scale;

        PendingItem(ItemStack stack, int x, int y, float scale) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private static final List<PendingItem> PENDING_ITEMS = new ArrayList<>();
    private static volatile DrawContext pendingDrawContext;

    /**
     * Inject a DrawContext to be used later when flushing queued overlay items.
     * This is set from MasterPlannerScreen.render(...) and consumed from RenderSystemMixin
     * just before swapBuffers (after ImGui has rendered its OpenGL draw data).
     */
    public static void setPendingDrawContext(DrawContext context) {
        pendingDrawContext = context;
    }

    /**
     * Flush queued items using the pending DrawContext (if present).
     * If no context is available, clear the queue to avoid cross-frame accumulation.
     */
    public static void flushPending() {
        DrawContext context = pendingDrawContext;
        pendingDrawContext = null;

        if (context == null) {
            if (!PENDING_ITEMS.isEmpty()) {
                LOGGER.warn("flushPending: DrawContext is null, clearing pending queue ({} items)", PENDING_ITEMS.size());
                PENDING_ITEMS.clear();
            }
            return;
        }

        // Ensure we're drawing to the default framebuffer, with a correct viewport.
        // flipFrame() is late in the frame; prior passes may leave a non-default FBO bound.
        try {
            RenderSystem.assertOnRenderThread();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            MinecraftClient mc = MinecraftClient.getInstance();
            Window window = mc != null ? mc.getWindow() : null;
            if (window != null) {
                int fbW = Math.max(1, window.getFramebufferWidth());
                int fbH = Math.max(1, window.getFramebufferHeight());
                GL11.glViewport(0, 0, fbW, fbH);
            }
        } catch (Throwable t) {
            // Don't break swapBuffers on viewport/FBO issues; just continue.
        }

        try (ImGuiGLStateGuard ignored = ImGuiGLStateGuard.enter()) {
            flush(context);
        }
    }

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
            float usedScale = scale;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                Window window = mc != null ? mc.getWindow() : null;

                float guiScaleX = 1.0f;
                float guiScaleY = 1.0f;

                if (window != null) {
                    int scaledW = Math.max(1, window.getScaledWidth());
                    int scaledH = Math.max(1, window.getScaledHeight());
                    ImGuiIO io = ImGui.getIO();
                    float imW = Math.max(1.0f, io.getDisplaySizeX());
                    float imH = Math.max(1.0f, io.getDisplaySizeY());
                    guiScaleX = scaledW / imW;
                    guiScaleY = scaledH / imH;

                    long now = System.currentTimeMillis();
                    if (now - lastCoordScaleLogMs > 3000L) {
                        lastCoordScaleLogMs = now;
                        LOGGER.info("GuiOverlayRenderer: 坐标缩放因子 x={}, y={} (scaled={}x{}, imgui={}x{})",
                                guiScaleX, guiScaleY, scaledW, scaledH, imW, imH);
                    }
                }

                intX = Math.round(x * guiScaleX);
                intY = Math.round(y * guiScaleY);
            } catch (Throwable t) {
                LOGGER.warn("queueBlockItem: 计算坐标缩放时出错，回退到原始坐标: {}", t.getMessage());
                intX = Math.round(x);
                intY = Math.round(y);
            }

            PENDING_ITEMS.add(new PendingItem(stack, intX, intY, usedScale));
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

                    // 先保证“可见性”：使用 GUI 缩放坐标直接绘制（16x16）。
                    // 之前的矩阵缩放在不同 MC 版本/矩阵栈实现下可能会把平移也一起缩放，导致整体跑到屏幕外。
                    // 等确认图标可见后，再逐步恢复“放大到 slot”的矩阵缩放逻辑。
                    BlockIconRenderer.drawItem(context, item.stack, item.x, item.y);
                    successCount++;

                } catch (Throwable e) {
                    LOGGER.warn("flush: 第 {} 个物品渲染失败 - {}", i, e.getMessage());
                }
            }

            // Some MC versions require an explicit flush on DrawContext's internal buffers.
            // We call it reflectively to avoid hard-coding a specific signature across mappings/versions.
            tryFlushDrawContext(context);

            LOGGER.info("✓ GuiOverlayRenderer.flush: 完成 - 成功: {}, 跳过: {}", successCount, skipCount);

        } catch (Exception e) {
            LOGGER.error("GuiOverlayRenderer.flush 异常", e);
        } finally {
            PENDING_ITEMS.clear();
        }
    }

    private static void tryFlushDrawContext(DrawContext context) {
        try {
            var m = context.getClass().getMethod("draw");
            m.invoke(context);
        } catch (NoSuchMethodException ignored) {
            // Not available in this MC version/mapping.
        } catch (Throwable t) {
            // Avoid noisy logs; flushing is best-effort.
        }
    }
}
