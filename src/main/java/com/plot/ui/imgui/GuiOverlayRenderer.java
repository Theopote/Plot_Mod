package com.plot.ui.imgui;

import com.plot.ui.component.BlockIconRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import java.lang.reflect.Field;

/**
 * GuiOverlayRenderer - 在ImGui之后的安全覆盖渲染
 * <p>
 * 使用经过验证的 BlockIconRenderer.drawBlock() / drawItem() 进行渲染
 * BlockIconRenderer 已经完整处理了所有 GL 状态管理和 AIR 兜底
 * <p>
 * 这个类只负责：
 * 1. 队列管理（缓冲要绘制的图标）
 * 2. 在合适的时机调用 BlockIconRenderer 进行绘制
 */
public final class GuiOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GuiOverlayRenderer");
    private static volatile long lastDrawContextFlushWarnMs;
    private static volatile long lastDrawItemFailWarnMs;
    private static final boolean DEBUG_ITEM_PROBE = false;
    private static long lastOverlayScaleLogMs;

    private GuiOverlayRenderer() {}

    private static final class PendingItem {
        final ItemStack stack;
        final float x;
        final float y;
        final float scale;

        PendingItem(ItemStack stack, float x, float y, float scale) {
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
     * This is set from PlotScreen.render(...) and consumed from RenderSystemMixin
     * just before swapBuffers (after ImGui has rendered its OpenGL draw data).
     */
    public static void setPendingDrawContext(DrawContext context) {
        pendingDrawContext = context;
    }

    public static void queueItem(ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float safeScale = scale <= 0.0f ? 1.0f : scale;
        PENDING_ITEMS.add(new PendingItem(stack.copy(), x, y, safeScale));
    }

    /**
     * Flush queued items using the pending DrawContext (if present).
     * If no context is available, clear the queue to avoid cross-frame accumulation.
     */
    public static void flushPending() {
        DrawContext context = createOverlayDrawContext();
        DrawContext screenContext = pendingDrawContext;
        pendingDrawContext = null;

        if (context == null) {
            context = screenContext;
        }

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
            // 关键：避免被 ImGui / 其他 pass 遗留的 scissor 与深度状态裁掉 overlay 图标
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } catch (Throwable t) {
            // Don't break swapBuffers on viewport/FBO issues; just continue.
        }

        flush(context);
    }

    private static DrawContext createOverlayDrawContext() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.gameRenderer == null) {
                return null;
            }

            Field guiStateField = GameRenderer.class.getDeclaredField("guiState");
            guiStateField.setAccessible(true);
            Object state = guiStateField.get(mc.gameRenderer);
            if (!(state instanceof GuiRenderState guiState)) {
                return null;
            }

            return new DrawContext(mc, guiState);
        } catch (Throwable ignored) {
            return null;
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
            LOGGER.debug("GuiOverlayRenderer.flush: 开始渲染 {} 个物品", itemCount);

            float[] guiScale = computeGuiScale();
            float xScale = guiScale[0];
            float yScale = guiScale[1];

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

                    drawScaledItem(context, item, xScale, yScale);
                    successCount++;

                } catch (Throwable e) {
                    LOGGER.warn("flush: 第 {} 个物品渲染失败 - {}", i, e.getMessage());
                }
            }

            // Some MC versions require an explicit flush on DrawContext's internal buffers.
            // We call it reflectively to avoid hard-coding a specific signature across mappings/versions.
            tryFlushDrawContext(context);

            LOGGER.debug("GuiOverlayRenderer.flush: 完成 - 成功: {}, 跳过: {}", successCount, skipCount);

        } catch (Exception e) {
            LOGGER.error("GuiOverlayRenderer.flush 异常", e);
        } finally {
            PENDING_ITEMS.clear();
        }
    }

    private static void drawScaledItem(DrawContext context, PendingItem item, float xScale, float yScale) {
        float scale = item.scale <= 0.0f ? 1.0f : item.scale;
        float drawXf = item.x * xScale;
        float drawYf = item.y * yScale;
        int drawX = Math.round(drawXf);
        int drawY = Math.round(drawYf);
        long now = System.currentTimeMillis();
        if (now - lastOverlayScaleLogMs > 2000L) {
            lastOverlayScaleLogMs = now;
            LOGGER.info("Overlay图标坐标: pending={}, pos=({}, {}), scale={}",
                    PENDING_ITEMS.size(), drawX, drawY, scale);
        }
        if (DEBUG_ITEM_PROBE) {
            drawProbeRect(context, drawX, drawY, scale);
        }
        if (Math.abs(scale - 1.0f) < 0.0001f) {
            boolean ok = BlockIconRenderer.tryDrawItem(context, item.stack, drawX, drawY);
            if (!ok) {
                drawProbeRect(context, drawX, drawY, 1.0f);
                long failNow = System.currentTimeMillis();
                if (failNow - lastDrawItemFailWarnMs > 3000L) {
                    lastDrawItemFailWarnMs = failNow;
                    LOGGER.warn("Overlay drawItem 失败，已绘制探针方块: pos=({}, {})", drawX, drawY);
                }
            }
            return;
        }

        try {
            var getMatrices = context.getClass().getMethod("getMatrices");
            Object matrices = getMatrices.invoke(context);
            if (matrices == null) {
                BlockIconRenderer.tryDrawItem(context, item.stack, drawX, drawY);
                return;
            }

            var push = matrices.getClass().getMethod("push");
            var pop = matrices.getClass().getMethod("pop");
            var translate = matrices.getClass().getMethod("translate", float.class, float.class, float.class);
            var scaleMethod = matrices.getClass().getMethod("scale", float.class, float.class, float.class);

            push.invoke(matrices);
            try {
                translate.invoke(matrices, drawXf, drawYf, 0.0f);
                scaleMethod.invoke(matrices, scale, scale, 1.0f);
                boolean ok = BlockIconRenderer.tryDrawItem(context, item.stack, 0, 0);
                if (!ok) {
                    drawProbeRect(context, drawX, drawY, 1.0f);
                    long failNow = System.currentTimeMillis();
                    if (failNow - lastDrawItemFailWarnMs > 3000L) {
                        lastDrawItemFailWarnMs = failNow;
                        LOGGER.warn("Overlay 缩放 drawItem 失败，已绘制探针方块: pos=({}, {})", drawX, drawY);
                    }
                }
            } finally {
                pop.invoke(matrices);
            }
        } catch (Throwable t) {
            boolean ok = BlockIconRenderer.tryDrawItem(context, item.stack, drawX, drawY);
            if (!ok) {
                drawProbeRect(context, drawX, drawY, 1.0f);
                long failNow = System.currentTimeMillis();
                if (failNow - lastDrawItemFailWarnMs > 3000L) {
                    lastDrawItemFailWarnMs = failNow;
                    LOGGER.warn("Overlay 异常回退 drawItem 失败，已绘制探针方块: pos=({}, {})", drawX, drawY);
                }
            }
        }
    }

    private static float[] computeGuiScale() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return new float[]{1.0f, 1.0f};
        }

        Window window = mc.getWindow();
        float imguiW = 0.0f;
        float imguiH = 0.0f;

        try {
            imguiW = ImGui.getIO().getDisplaySizeX();
            imguiH = ImGui.getIO().getDisplaySizeY();
        } catch (Throwable ignored) {
        }

        int windowW;
        int windowH;

        if (imguiW > 1.0f && imguiH > 1.0f) {
            windowW = Math.round(imguiW);
            windowH = Math.round(imguiH);
        } else {
            windowW = 0;
            windowH = 0;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                GLFW.glfwGetWindowSize(window.getHandle(), w, h);
                windowW = Math.max(1, w.get(0));
                windowH = Math.max(1, h.get(0));
            } catch (Throwable ignored) {
            }
        }

        if (windowW <= 0 || windowH <= 0) {
            try {
                windowW = Math.max(1, window.getScaledWidth());
                windowH = Math.max(1, window.getScaledHeight());
            } catch (Throwable ignored) {
                windowW = 1;
                windowH = 1;
            }
        }

        int guiW = Math.max(1, window.getScaledWidth());
        int guiH = Math.max(1, window.getScaledHeight());

        return new float[]{(float) guiW / windowW, (float) guiH / windowH};
    }

    private static void drawProbeRect(DrawContext context, int x, int y, float scale) {
        try {
            int size = Math.max(8, Math.round(16.0f * scale));
            int x2 = x + size;
            int y2 = y + size;
            // 半透明紫色填充 + 亮青色边框，便于在任何背景下识别
            context.fill(x, y, x2, y2, 0x884000FF);
            context.fill(x, y, x2, y + 1, 0xFF00FFFF);
            context.fill(x, y2 - 1, x2, y2, 0xFF00FFFF);
            context.fill(x, y, x + 1, y2, 0xFF00FFFF);
            context.fill(x2 - 1, y, x2, y2, 0xFF00FFFF);
        } catch (Throwable ignored) {
        }
    }

    private static void tryFlushDrawContext(DrawContext context) {
        boolean anySucceeded = false;
        try {
            // 1) 1.21.10+ 延迟提交路径
            try {
                context.drawDeferredElements();
                anySucceeded = true;
            } catch (Throwable ignored) {
            }

            try {
                var m = context.getClass().getMethod("drawDeferredElements");
                m.invoke(context);
                anySucceeded = true;
            } catch (NoSuchMethodException ignored) {
            }

            // 2) 旧映射：DrawContext#draw()
            try {
                var m = context.getClass().getMethod("draw");
                m.invoke(context);
                anySucceeded = true;
            } catch (NoSuchMethodException ignored) {
            }

            // 3) DrawContext#getVertexConsumers().draw()
            try {
                var getVc = context.getClass().getMethod("getVertexConsumers");
                Object vc = getVc.invoke(context);
                if (vc != null) {
                    var draw = vc.getClass().getMethod("draw");
                    draw.invoke(vc);
                    anySucceeded = true;
                }
            } catch (NoSuchMethodException ignored) {
            }

            // 4) DrawContext#vertexConsumers field -> draw()
            try {
                var f = context.getClass().getDeclaredField("vertexConsumers");
                f.setAccessible(true);
                Object vc = f.get(context);
                if (vc != null) {
                    var draw = vc.getClass().getMethod("draw");
                    draw.invoke(vc);
                    anySucceeded = true;
                }
            } catch (NoSuchFieldException ignored) {
            }

            if (!anySucceeded) {
                long now = System.currentTimeMillis();
                if (now - lastDrawContextFlushWarnMs > 3000L) {
                    lastDrawContextFlushWarnMs = now;
                    LOGGER.warn("tryFlushDrawContext: 未找到可用提交方法");
                }
            }
        } catch (NoSuchMethodException ignored) {
            // Not available in this MC version/mapping.
        } catch (Throwable t) {
            // Avoid noisy logs; flushing is best-effort.
            long now = System.currentTimeMillis();
            if (now - lastDrawContextFlushWarnMs > 3000L) {
                lastDrawContextFlushWarnMs = now;
                LOGGER.warn("tryFlushDrawContext: best-effort flush failed: {}", t.getMessage());
            }
        }
    }
}
