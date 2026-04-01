package com.plot.ui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric 1.21.10 / Minecraft 1.21.10
 *
 * 离屏 3D 方块图标渲染器：
 * 1. 使用 OpenGL FBO 渲染到纹理
 * 2. 使用 Vanilla DrawContext.drawItemWithoutEntity 渲染 GUI 风格 3D 方块物品
 * 3. 输出 textureId，可直接给 ImGui.addImage / ImGui.image 使用
 *
 * 重点：
 * - 不依赖 ItemRenderState 私有字段反射
 * - 不走 overlay 队列方案
 * - 专门给 ImGui 方块选择面板使用
 */
public final class BlockIconRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BlockIconRenderer");

    public static final int DEFAULT_RENDER_BUDGET = 16;
    private static final int MAX_RENDER_ATTEMPTS = 3;

    private static final BlockIconRenderer INSTANCE = new BlockIconRenderer(64);

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final int textureSize;

    private final Map<Block, Integer> textureCache = new HashMap<>();
    private final Queue<Block> pendingQueue = new ArrayDeque<>();
    private final Set<Block> queuedSet = new HashSet<>();
    private final Set<Block> permanentlyFailed = new HashSet<>();
    private final Map<Block, Integer> renderFailureCount = new HashMap<>();

    private static final Map<Block, ItemStack> ITEM_STACK_CACHE = new ConcurrentHashMap<>();

    private int fboId = 0;
    private int depthRboId = 0;
    private int placeholderTextureId = 0;
    private boolean closed = false;

    public static BlockIconRenderer getInstance() {
        return INSTANCE;
    }

    public BlockIconRenderer(int textureSize) {
        this.textureSize = Math.max(16, textureSize);
    }

    public int getTextureId(Block block) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        if (block == null) {
            return getPlaceholderTextureId();
        }

        Integer cached = textureCache.get(block);
        if (cached != null && cached > 0) {
            return cached;
        }

        if (permanentlyFailed.contains(block)) {
            return getPlaceholderTextureId();
        }

        if (!queuedSet.contains(block)) {
            queuedSet.add(block);
            pendingQueue.offer(block);
        }

        return getPlaceholderTextureId();
    }

    public void processQueue(int maxPerFrame) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        ensureRenderTarget();

        int count = 0;
        while (!pendingQueue.isEmpty() && count < Math.max(1, maxPerFrame)) {
            Block block = pendingQueue.poll();
            if (block == null) {
                count++;
                continue;
            }

            try {
                if (!textureCache.containsKey(block)) {
                    int texId = renderBlockToTexture(block);
                    textureCache.put(block, texId);
                    renderFailureCount.remove(block);
                }
            } catch (Throwable t) {
                int fails = renderFailureCount.merge(block, 1, Integer::sum);
                if (fails >= MAX_RENDER_ATTEMPTS) {
                    permanentlyFailed.add(block);
                    textureCache.put(block, getPlaceholderTextureId());
                    renderFailureCount.remove(block);
                    LOGGER.warn("方块图标渲染失败并已放弃: {} - {}",
                            Registries.BLOCK.getId(block), t.getMessage());
                } else {
                    LOGGER.warn("方块图标渲染失败 ({}/{}): {} - {}",
                            fails, MAX_RENDER_ATTEMPTS,
                            Registries.BLOCK.getId(block), t.getMessage());
                }
            } finally {
                queuedSet.remove(block);
            }

            count++;
        }
    }

    public void preload(Iterable<Block> blocks) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        for (Block block : blocks) {
            if (block == null) continue;
            if (textureCache.containsKey(block)) continue;
            if (queuedSet.contains(block)) continue;
            if (permanentlyFailed.contains(block)) continue;

            queuedSet.add(block);
            pendingQueue.offer(block);
        }
    }

    public void invalidateAll() {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        for (Integer texId : textureCache.values()) {
            if (texId != null && texId > 0 && texId != placeholderTextureId) {
                GL11.glDeleteTextures(texId);
            }
        }

        textureCache.clear();
        pendingQueue.clear();
        queuedSet.clear();
        permanentlyFailed.clear();
        renderFailureCount.clear();

        if (placeholderTextureId > 0) {
            GL11.glDeleteTextures(placeholderTextureId);
            placeholderTextureId = 0;
        }

        destroyRenderTarget();
    }

    public int getPendingCount() {
        return pendingQueue.size();
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        invalidateAll();
        closed = true;
    }

    private int renderBlockToTexture(Block block) {
        ItemStack stack = getItemStackForBlock(block);
        if (stack.isEmpty()) {
            return getPlaceholderTextureId();
        }

        ensureRenderTarget();

        int colorTexId = createColorTexture();
        GLStateSnapshot snapshot = GLStateSnapshot.capture();

        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    colorTexId,
                    0
            );
            GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    depthRboId
            );

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(status));
            }

            GL11.glViewport(0, 0, textureSize, textureSize);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            drawVanillaGuiItemIntoCurrentFbo(stack);

            // 解绑颜色附件，避免后续误写
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    0,
                    0
            );

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexId);
            return colorTexId;
        } catch (Throwable t) {
            if (colorTexId > 0) {
                GL11.glDeleteTextures(colorTexId);
            }
            throw new RuntimeException("renderBlockToTexture failed: " + Registries.BLOCK.getId(block), t);
        } finally {
            snapshot.restore();
        }
    }

    private void drawVanillaGuiItemIntoCurrentFbo(ItemStack stack) {
        if (client == null) {
            throw new IllegalStateException("MinecraftClient is null");
        }

        GuiRenderState guiState = getGuiRenderStateReflect();
        DrawContext context = new DrawContext(client, guiState);

        Object matrices = context.getMatrices();
        float scale = textureSize / 20.0f;        // 64 -> 3.2
        float iconPx = 16.0f * scale;             // 约 51.2
        float offset = (textureSize - iconPx) * 0.5f;

        pushMatrixCompat(matrices);
        try {
            translateCompat(matrices, offset, offset);
            scaleCompat(matrices, scale, scale);

            if (!drawItemWithoutEntityCompat(context, stack, 0, 0)) {
                throw new IllegalStateException("DrawContext.drawItemWithoutEntity / drawItem 调用失败");
            }

            flushDrawContextCompat(context);
        } finally {
            popMatrixCompat(matrices);
        }
    }

    private void ensureRenderTarget() {
        if (fboId > 0 && depthRboId > 0) {
            return;
        }

        fboId = GL30.glGenFramebuffers();
        depthRboId = GL30.glGenRenderbuffers();

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRboId);
        GL30.glRenderbufferStorage(
                GL30.GL_RENDERBUFFER,
                GL14.GL_DEPTH_COMPONENT24,
                textureSize,
                textureSize
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                depthRboId
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
    }

    private void destroyRenderTarget() {
        if (fboId > 0) {
            GL30.glDeleteFramebuffers(fboId);
            fboId = 0;
        }
        if (depthRboId > 0) {
            GL30.glDeleteRenderbuffers(depthRboId);
            depthRboId = 0;
        }
    }

    private int createColorTexture() {
        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                textureSize,
                textureSize,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        return texId;
    }

    private int getPlaceholderTextureId() {
        if (placeholderTextureId > 0) {
            return placeholderTextureId;
        }

        final int size = Math.max(16, textureSize);
        final int tile = Math.max(2, size / 8);
        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * 4);

        int colorA = 0xFF3A3A3A;
        int colorB = 0xFF2A2A2A;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean useA = (((x / tile) + (y / tile)) & 1) == 0;
                int c = useA ? colorA : colorB;

                buf.put((byte) ((c >> 16) & 0xFF));
                buf.put((byte) ((c >> 8) & 0xFF));
                buf.put((byte) (c & 0xFF));
                buf.put((byte) ((c >> 24) & 0xFF));
            }
        }
        buf.flip();

        placeholderTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, placeholderTextureId);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                size,
                size,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buf
        );
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        return placeholderTextureId;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("BlockIconRenderer already closed");
        }
    }

    private static GuiRenderState getGuiRenderStateReflect() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            GameRenderer gameRenderer = client.gameRenderer;

            Field field = GameRenderer.class.getDeclaredField("guiState");
            field.setAccessible(true);
            return (GuiRenderState) field.get(gameRenderer);
        } catch (Throwable t) {
            throw new IllegalStateException("无法获取 GameRenderer.guiState", t);
        }
    }

    private static boolean drawItemWithoutEntityCompat(DrawContext context, ItemStack stack, int x, int y) {
        try {
            Method m = context.getClass().getMethod("drawItemWithoutEntity", ItemStack.class, int.class, int.class);
            m.invoke(context, stack, x, y);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Method m = context.getClass().getMethod("drawItem", ItemStack.class, int.class, int.class);
            m.invoke(context, stack, x, y);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) {
                Method m = context.getClass().getMethod("drawItem", net.minecraft.entity.LivingEntity.class, ItemStack.class, int.class, int.class, int.class);
                m.invoke(context, mc.player, stack, x, y, 0);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static void flushDrawContextCompat(DrawContext context) {
        // 不同映射/版本方法名可能不同，全部试一次
        for (String name : new String[]{"draw", "flush"}) {
            try {
                Method m = context.getClass().getMethod(name);
                m.invoke(context);
                return;
            } catch (Throwable ignored) {
            }
        }

        try {
            Method getVertexConsumers = context.getClass().getMethod("getVertexConsumers");
            Object vertexConsumers = getVertexConsumers.invoke(context);
            if (vertexConsumers != null) {
                Method draw = vertexConsumers.getClass().getMethod("draw");
                draw.invoke(vertexConsumers);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void pushMatrixCompat(Object matrices) {
        invokeNoArgs(matrices, "pushMatrix");
        invokeNoArgs(matrices, "push");
    }

    private static void popMatrixCompat(Object matrices) {
        invokeNoArgs(matrices, "popMatrix");
        invokeNoArgs(matrices, "pop");
    }

    private static void translateCompat(Object matrices, float x, float y) {
        if (invokeMethod(matrices, "translate", new Class[]{float.class, float.class}, new Object[]{x, y})) {
            return;
        }
        invokeMethod(matrices, "translate", new Class[]{float.class, float.class, float.class}, new Object[]{x, y, 0.0f});
    }

    private static void scaleCompat(Object matrices, float sx, float sy) {
        if (invokeMethod(matrices, "scale", new Class[]{float.class, float.class}, new Object[]{sx, sy})) {
            return;
        }
        invokeMethod(matrices, "scale", new Class[]{float.class, float.class, float.class}, new Object[]{sx, sy, 1.0f});
    }

    private static void invokeNoArgs(Object target, String methodName) {
        if (target == null) return;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.invoke(target);
        } catch (Throwable ignored) {
        }
    }

    private static boolean invokeMethod(Object target, String methodName, Class<?>[] types, Object[] args) {
        if (target == null) return false;
        try {
            Method m = target.getClass().getMethod(methodName, types);
            m.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class GLStateSnapshot {
        private final int[] viewport = new int[4];
        private final int[] framebufferBinding = new int[1];
        private final int[] textureBinding2D = new int[1];
        private final float[] clearColor = new float[4];
        private final int[] depthFunc = new int[1];
        private final boolean depthTestEnabled;
        private final boolean blendEnabled;
        private final boolean scissorEnabled;

        private GLStateSnapshot() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL30.GL_FRAMEBUFFER_BINDING, framebufferBinding);
            GL11.glGetIntegerv(GL11.GL_TEXTURE_BINDING_2D, textureBinding2D);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            GL11.glGetIntegerv(GL11.GL_DEPTH_FUNC, depthFunc);

            this.depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            this.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        }

        public static GLStateSnapshot capture() {
            return new GLStateSnapshot();
        }

        public void restore() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBinding[0]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding2D[0]);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL11.glDepthFunc(depthFunc[0]);

            if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);

            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);

            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    // ---------------- 兼容旧接口 ----------------

    public static ItemStack getItemStackForBlock(Block block) {
        if (block == null) return ItemStack.EMPTY;

        return ITEM_STACK_CACHE.computeIfAbsent(block, b -> {
            try {
                if (b == Blocks.WATER) return new ItemStack(Items.WATER_BUCKET);
                if (b == Blocks.LAVA) return new ItemStack(Items.LAVA_BUCKET);
                if (b == Blocks.POWDER_SNOW) return new ItemStack(Items.POWDER_SNOW_BUCKET);
                if (b == Blocks.AIR) return ItemStack.EMPTY;

                Item item = b.asItem();
                if (item == null || item == Items.AIR) return ItemStack.EMPTY;

                return new ItemStack(item);
            } catch (Throwable t) {
                LOGGER.warn("获取方块物品失败: {} - {}", Registries.BLOCK.getId(b), t.getMessage());
                return ItemStack.EMPTY;
            }
        });
    }

    /**
     * 兼容 GuiOverlayRenderer：在当前 DrawContext 上绘制一个物品图标。
     */
    public static void drawItem(DrawContext context, ItemStack stack, int x, int y) {
        if (context == null || stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) {
            return;
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) {
                context.drawItem(mc.player, stack, x, y, 0);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            context.drawItem(stack, x, y, 0);
            return;
        } catch (Throwable ignored) {
        }
        try {
            context.drawItem(stack, x, y);
        } catch (Throwable ignored) {
        }
    }

    public static String getCacheStats() {
        return "IconTex缓存: " + INSTANCE.textureCache.size()
                + ", ItemStack缓存: " + ITEM_STACK_CACHE.size()
                + ", 待渲染: " + INSTANCE.getPendingCount();
    }

    public static boolean isInitialized() {
        return true;
    }

    public static void initialize() {
        // 保留旧入口，兼容外部调用
    }

    public static void preloadCommonBlocks() {
        Block[] common = new Block[]{
                Blocks.STONE,
                Blocks.DIRT,
                Blocks.GRASS_BLOCK,
                Blocks.OAK_PLANKS,
                Blocks.COBBLESTONE,
                Blocks.SAND
        };

        for (Block b : common) {
            getItemStackForBlock(b);
        }

        BlockIconRenderer renderer = getInstance();
        renderer.preload(Arrays.asList(common));
        renderer.processQueue(common.length);
    }
}
