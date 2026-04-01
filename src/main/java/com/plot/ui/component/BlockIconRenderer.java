package com.plot.ui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import java.util.List;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * 方块图标渲染器：
 * - 使用 ItemRenderer 的 GUI 变换渲染得到原版风格 3D 图标
 * - 使用 OpenGL FBO 离屏生成纹理，直接提供给 ImGui.image / addImage
 */
public final class BlockIconRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BlockIconRenderer");
    private static volatile boolean loggedRenderMethodHint = false;
    private static volatile boolean loggedDrawContextMethods = false;

    private static final BlockIconRenderer INSTANCE = new BlockIconRenderer(64);
    /** 每帧离屏生成数量上限；过小会导致首次打开面板长时间占位图 */
    public static final int DEFAULT_RENDER_BUDGET = 16;
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final int textureSize;

    /** 1.21.x：ItemRenderState 内部层字段为 private，需反射读取 renderLayer/glint/tints/transform */
    private static volatile boolean itemRenderStateReflectReady;
    private static Field fItemStateLayerCount;
    private static Field fItemStateLayers;
    private static Field fLayerRenderLayer;
    private static Field fLayerGlint;
    private static Field fLayerTints;
    private static Field fLayerTransform;
    private static Field fLayerSpecialModel;

    private static final int MAX_ICON_RENDER_ATTEMPTS = 3;

    private final Map<Block, Integer> textureCache = new HashMap<>();
    private final Queue<Block> pendingQueue = new ArrayDeque<>();
    private final Set<Block> queuedSet = new HashSet<>();
    /** 多次失败后不再入队，避免刷屏 WARN */
    private final Set<Block> permanentlyFailed = new HashSet<>();
    private final Map<Block, Integer> renderFailureCount = new HashMap<>();
    private int placeholderTextureId = -1;
    private boolean closed = false;

    // 兼容旧接口：其他代码仍会读取方块 -> ItemStack
    private static final Map<Block, ItemStack> ITEM_STACK_CACHE = new ConcurrentHashMap<>();

    public static BlockIconRenderer getInstance() {
        return INSTANCE;
    }

    public BlockIconRenderer(int textureSize) {
        this.textureSize = textureSize;
    }

    public int getTextureId(Block block) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        if (block == null) {
            return getPlaceholderTextureId();
        }

        Integer cached = textureCache.get(block);
        if (cached != null) {
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

    public int renderNow(Block block) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        if (block == null) {
            return getPlaceholderTextureId();
        }
        Integer cached = textureCache.get(block);
        if (cached != null) {
            return cached;
        }
        if (permanentlyFailed.contains(block)) {
            return getPlaceholderTextureId();
        }

        // 1.21.10：DiffuseLighting 实现 AutoCloseable；显式 finally 与 processQueue 一致
        DiffuseLighting lighting = new DiffuseLighting();
        try {
            lighting.setShaderLights(DiffuseLighting.Type.ITEMS_3D);
            int texId = renderBlockToTextureCore(block);
            textureCache.put(block, texId);
            queuedSet.remove(block);
            pendingQueue.remove(block);
            return texId;
        } finally {
            lighting.close();
        }
    }

    public void processQueue() {
        processQueue(DEFAULT_RENDER_BUDGET);
    }

    public void processQueue(int maxPerFrame) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        // 整批共用一个 DiffuseLighting，避免每格图标 new 一次导致 Dynamic Transforms UBO 等状态剧烈扩容（见日志）
        DiffuseLighting lighting = new DiffuseLighting();
        try {
            lighting.setShaderLights(DiffuseLighting.Type.ITEMS_3D);
            int count = 0;
            while (!pendingQueue.isEmpty() && count < maxPerFrame) {
                Block block = pendingQueue.poll();
                if (block != null && !textureCache.containsKey(block)) {
                    try {
                        int texId = renderBlockToTextureCore(block);
                        textureCache.put(block, texId);
                        renderFailureCount.remove(block);
                    } catch (Throwable t) {
                        textureCache.remove(block);
                        int fails = renderFailureCount.merge(block, 1, Integer::sum);
                        if (fails >= MAX_ICON_RENDER_ATTEMPTS) {
                            permanentlyFailed.add(block);
                            renderFailureCount.remove(block);
                            textureCache.put(block, getPlaceholderTextureId());
                            LOGGER.warn("方块图标渲染已放弃（{}次），使用占位图: {} — {}", MAX_ICON_RENDER_ATTEMPTS, Registries.BLOCK.getId(block), t.getMessage());
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("放弃原因", t);
                            }
                        } else {
                            LOGGER.warn("渲染方块图标失败 ({}/{}): {} — {}", fails, MAX_ICON_RENDER_ATTEMPTS, Registries.BLOCK.getId(block), t.getMessage());
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("渲染方块图标失败（堆栈）", t);
                            }
                        }
                    } finally {
                        queuedSet.remove(block);
                    }
                }
                count++;
            }
        } finally {
            lighting.close();
        }
    }

    public void preload(Iterable<Block> blocks) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        for (Block block : blocks) {
            if (block == null || textureCache.containsKey(block) || queuedSet.contains(block) || permanentlyFailed.contains(block)) {
                continue;
            }
            queuedSet.add(block);
            pendingQueue.offer(block);
        }
    }

    public void invalidate(Block block) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();

        Integer texId = textureCache.remove(block);
        if (texId != null && texId > 0 && texId != placeholderTextureId) {
            GL11.glDeleteTextures(texId);
        }
        queuedSet.remove(block);
        pendingQueue.remove(block);
        permanentlyFailed.remove(block);
        renderFailureCount.remove(block);
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
            placeholderTextureId = -1;
        }
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

    /**
     * FBO 离屏绘制；漫反射光照须由调用方通过 {@link DiffuseLighting} 已设置好（例如 {@code processQueue} 整批一次）。
     */
    private int renderBlockToTextureCore(Block block) {
        Item item = block.asItem();
        if (item == null || item == Items.AIR) {
            return getPlaceholderTextureId();
        }

        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            return getPlaceholderTextureId();
        }

        GLStateSnapshot snapshot = GLStateSnapshot.capture();
        int colorTexId = 0;
        int depthRboId = 0;
        int fboId = 0;

        try {
            colorTexId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, textureSize, textureSize, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12Compat.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12Compat.GL_CLAMP_TO_EDGE);

            depthRboId = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRboId);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, textureSize, textureSize);

            fboId = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexId, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRboId);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(status));
            }

            GL11.glViewport(0, 0, textureSize, textureSize);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 严格 3D：只走 GUI 3D item 模型层渲染；不再降级到 2D sprite。
            renderGuiItemIntoCurrentFbo(stack, block);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexId);
            return colorTexId;
        } catch (Throwable t) {
            if (colorTexId > 0) {
                GL11.glDeleteTextures(colorTexId);
            }
            throw new RuntimeException("3D block icon render failed for: " + block, t);
        } finally {
            if (fboId > 0) GL30.glDeleteFramebuffers(fboId);
            if (depthRboId > 0) GL30.glDeleteRenderbuffers(depthRboId);
            snapshot.restore();
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // 3D-only 模式下不再需要 atlas 纹理 id 反射

    private int getPlaceholderTextureId() {
        if (placeholderTextureId > 0) return placeholderTextureId;

        final int size = Math.max(16, textureSize);
        final int tile = Math.max(2, size / 8);
        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * 4);

        int colorA = 0xFF3A3A3A;
        int colorB = 0xFF2E2E2E;
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
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return placeholderTextureId;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("BlockIconRenderer already closed");
        }
    }

    private static final class GLStateSnapshot {
        private final int[] viewport = new int[4];
        private final int[] framebufferBinding = new int[1];
        private final int[] textureBinding2D = new int[1];
        private final float[] clearColor = new float[4];
        private final int[] depthFunc = new int[1];
        private final int[] blendSrcRgb = new int[1];
        private final int[] blendDstRgb = new int[1];
        private final int[] blendSrcAlpha = new int[1];
        private final int[] blendDstAlpha = new int[1];
        private final boolean depthTestEnabled;
        private final boolean blendEnabled;
        private final boolean scissorEnabled;

        private GLStateSnapshot() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL30.GL_FRAMEBUFFER_BINDING, framebufferBinding);
            GL11.glGetIntegerv(GL11.GL_TEXTURE_BINDING_2D, textureBinding2D);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            GL11.glGetIntegerv(GL11.GL_DEPTH_FUNC, depthFunc);
            GL11.glGetIntegerv(GL14.GL_BLEND_SRC_RGB, blendSrcRgb);
            GL11.glGetIntegerv(GL14.GL_BLEND_DST_RGB, blendDstRgb);
            GL11.glGetIntegerv(GL14.GL_BLEND_SRC_ALPHA, blendSrcAlpha);
            GL11.glGetIntegerv(GL14.GL_BLEND_DST_ALPHA, blendDstAlpha);
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
            GL14.glBlendFuncSeparate(blendSrcRgb[0], blendDstRgb[0], blendSrcAlpha[0], blendDstAlpha[0]);
            if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static final class GL12Compat {
        private static final int GL_CLAMP_TO_EDGE = 0x812F;
    }

    /**
     * 1.21.10+：物品渲染走 {@link ItemModelManager} 填充 {@link ItemRenderState}，
     * 再调用静态 {@link ItemRenderer#renderItem}；并设置 {@link DiffuseLighting} 以匹配物品栏光照。
     */
    private void renderGuiItemIntoCurrentFbo(ItemStack stack, Block block) {
        ensureItemRenderStateReflect();
        if (!itemRenderStateReflectReady) {
            if (!loggedRenderMethodHint) {
                loggedRenderMethodHint = true;
                LOGGER.warn("ItemRenderState 反射初始化失败，无法离屏渲染 GUI 物品模型。");
            }
            throw new IllegalStateException("ItemRenderState reflection not ready");
        }

        ItemRenderState state = new ItemRenderState();
        int seed = Registries.BLOCK.getId(block).hashCode();
        client.getItemModelManager().clearAndUpdate(state, stack, ItemDisplayContext.GUI, client.world, null, seed);

        if (state.isEmpty()) {
            throw new IllegalStateException("ItemRenderState is empty for " + Registries.BLOCK.getId(block));
        }

        int layerCount;
        ItemRenderState.LayerRenderState[] layers;
        try {
            layerCount = fItemStateLayerCount.getInt(state);
            layers = (ItemRenderState.LayerRenderState[]) fItemStateLayers.get(state);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.translate(textureSize / 2.0f, textureSize / 2.0f, 100.0f);
        matrices.scale(16.0f, -16.0f, 16.0f);

        // Yarn 1.21.10：VertexConsumerProvider.immediate(BufferAllocator)，无 BufferBuilder(int) 构造。
        // BufferBuilder 需 (BufferAllocator, DrawMode, VertexFormat)；此处用独立 allocator 避免污染主场景缓冲。
        boolean drewAnyLayer = false;
        try (BufferAllocator iconBufferAllocator = BufferAllocator.fixedSized(1 << 16)) {
            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(iconBufferAllocator);

            for (int i = 0; i < layerCount && layers != null && i < layers.length; i++) {
                ItemRenderState.LayerRenderState layer = layers[i];
                if (layer == null) {
                    continue;
                }
                Object special;
                List<BakedQuad> quads;
                RenderLayer renderLayer;
                ItemRenderState.Glint glint;
                int[] tints;
                Transformation transform;
                try {
                    special = fLayerSpecialModel.get(layer);
                    quads = layer.getQuads();
                    renderLayer = (RenderLayer) fLayerRenderLayer.get(layer);
                    glint = (ItemRenderState.Glint) fLayerGlint.get(layer);
                    tints = (int[]) fLayerTints.get(layer);
                    transform = (Transformation) fLayerTransform.get(layer);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (special != null) {
                    continue;
                }
                if (quads == null || quads.isEmpty()) {
                    continue;
                }
                if (renderLayer == null) {
                    continue;
                }
                if (glint == null) {
                    glint = ItemRenderState.Glint.NONE;
                }
                if (tints == null) {
                    tints = new int[] { ItemRenderer.NO_TINT };
                }
                int light = FULL_BRIGHT;
                int overlay = OverlayTexture.DEFAULT_UV;

                matrices.push();
                try {
                    if (transform != null) {
                        transform.apply(false, matrices.peek());
                    }
                    ItemRenderer.renderItem(ItemDisplayContext.GUI, matrices, immediate, light, overlay, tints, quads, renderLayer, glint);
                    drewAnyLayer = true;
                } finally {
                    matrices.pop();
                }
            }

            if (!drewAnyLayer) {
                throw new IllegalStateException("No drawable item layers (possibly special model only): " + Registries.BLOCK.getId(block));
            }

            immediate.draw();
        }
        matrices.pop();
    }

    /**
     * specialModel 方块（床、头颅、旗帜等）无 BakedQuad 层时的降级：用 {@link DrawContext#drawItemWithoutEntity} 走与物品栏相同的 GUI 物品绘制。
     */
    private void renderParticleSpriteIntoCurrentFbo(ItemStack stack) {
        GuiRenderState guiState = getGuiRenderStateReflect();
        DrawContext context = new DrawContext(client, guiState);
        context.getMatrices().pushMatrix();
        try {
            float scale = textureSize / 16.0F;
            float pad = (textureSize - 16.0F * scale) * 0.5F;
            context.getMatrices().translate(pad, pad);
            context.getMatrices().scale(scale, scale);
            context.drawItemWithoutEntity(stack, 0, 0);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private static GuiRenderState getGuiRenderStateReflect() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            GameRenderer gr = c.gameRenderer;
            Field f = GameRenderer.class.getDeclaredField("guiState");
            f.setAccessible(true);
            return (GuiRenderState) f.get(gr);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取 GameRenderer.guiState（DrawContext 需要）", e);
        }
    }

    private static void ensureItemRenderStateReflect() {
        if (itemRenderStateReflectReady) {
            return;
        }
        synchronized (BlockIconRenderer.class) {
            if (itemRenderStateReflectReady) {
                return;
            }
            try {
                fItemStateLayerCount = ItemRenderState.class.getDeclaredField("layerCount");
                fItemStateLayers = ItemRenderState.class.getDeclaredField("layers");
                Class<?> layerClass = ItemRenderState.LayerRenderState.class;
                fLayerRenderLayer = layerClass.getDeclaredField("renderLayer");
                fLayerGlint = layerClass.getDeclaredField("glint");
                fLayerTints = layerClass.getDeclaredField("tints");
                fLayerTransform = layerClass.getDeclaredField("transform");
                fLayerSpecialModel = layerClass.getDeclaredField("specialModelType");
                for (Field f : new Field[] {
                    fItemStateLayerCount, fItemStateLayers, fLayerRenderLayer, fLayerGlint, fLayerTints,
                    fLayerTransform, fLayerSpecialModel
                }) {
                    f.setAccessible(true);
                }
                itemRenderStateReflectReady = true;
            } catch (Throwable t) {
                LOGGER.error("BlockIconRenderer: ItemRenderState 反射字段初始化失败", t);
            }
        }
    }

    // ------------------ 兼容旧接口 ------------------

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
                LOGGER.warn("获取方块物品失败: {} / {}", Registries.BLOCK.getId(b), t.getMessage());
                return ItemStack.EMPTY;
            }
        });
    }

    public static boolean hasValidIcon(Block block) {
        return !getItemStackForBlock(block).isEmpty();
    }

    public static void drawItem(DrawContext context, ItemStack stack, int x, int y) {
        if (context == null || stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) {
            return;
        }
        try {
            // 在 ImGui 之后的 overlay 阶段，渲染状态可能不满足 drawItem 可见性要求
            // 这里做一层最小保护，避免“调用成功但实际不可见”。
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColorMask(true, true, true, true);

            // 优先走已确认存在的显式重载，避免反射路径命中到“调用成功但无输出”的分支
            if (drawItemDirectCompat(context, stack, x, y)) {
                return;
            }

            // 兜底：尝试把物品绘制抬高到前景层，避免被同帧其他内容覆盖
            try {
                Method getMatrices = context.getClass().getMethod("getMatrices");
                Object matrices = getMatrices.invoke(context);
                if (matrices != null) {
                    Method push = matrices.getClass().getMethod("push");
                    Method pop = matrices.getClass().getMethod("pop");
                    Method translate = matrices.getClass().getMethod("translate", float.class, float.class, float.class);
                    push.invoke(matrices);
                    try {
                        translate.invoke(matrices, 0.0f, 0.0f, 200.0f);
                        context.drawItem(stack, x, y);
                    } finally {
                        pop.invoke(matrices);
                    }
                    return;
                }
            } catch (Throwable ignored) {
                // 反射失败走普通路径
            }

            context.drawItem(stack, x, y);
        } catch (Throwable t) {
            LOGGER.debug("drawItem failed: {}", t.getMessage());
        }
    }

    private static boolean drawItemDirectCompat(DrawContext context, ItemStack stack, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();

        try {
            if (mc != null && mc.player != null) {
                context.drawItem(mc.player, stack, x, y, 0);
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            context.drawItem(stack, x, y, 0);
            return true;
        } catch (Throwable ignored) {}

        try {
            context.drawItem(stack, x, y);
            return true;
        } catch (Throwable ignored) {}

        if (!loggedDrawContextMethods) {
            loggedDrawContextMethods = true;
            try {
                Method[] methods = context.getClass().getMethods();
                StringBuilder sb = new StringBuilder();
                for (Method m : methods) {
                    String n = m.getName();
                    if (n.startsWith("drawItem")) {
                        sb.append(n).append('(');
                        Class<?>[] p = m.getParameterTypes();
                        for (int i = 0; i < p.length; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(p[i].getSimpleName());
                        }
                        sb.append(")\n");
                    }
                }
                LOGGER.warn("DrawContext drawItem* methods:\n{}", sb);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static String getCacheStats() {
        return "IconTex缓存: " + INSTANCE.textureCache.size() + ", ItemStack缓存: " + ITEM_STACK_CACHE.size() + ", 待渲染: " + INSTANCE.getPendingCount();
    }

    public static boolean isInitialized() {
        return true;
    }

    public static void initialize() {
        // 保留兼容入口
    }

    /**
     * 预热常用方块的 ItemStack 缓存，并在渲染线程上离屏生成对应图标纹理（须由 {@link RenderSystem#assertOnRenderThread()} 满足时调用）。
     */
    public static void preloadCommonBlocks() {
        Block[] common = new Block[] {
            Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.SAND
        };
        for (Block b : common) {
            getItemStackForBlock(b);
        }
        BlockIconRenderer r = getInstance();
        r.preload(Arrays.asList(common));
        r.processQueue(common.length);
    }
}
