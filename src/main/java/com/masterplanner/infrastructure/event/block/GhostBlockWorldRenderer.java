package com.masterplanner.infrastructure.event.block;

import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Client-side world renderer for ghost blocks created by {@link GhostBlockManager}.
 * Renders lightweight line boxes so preview blocks are visible without mutating the world.
 */
public final class GhostBlockWorldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/GhostBlockWorldRenderer");
    private static volatile boolean initialized;
    private static final int MAX_RENDER_PER_FRAME = 20_000;
    private static final boolean X_RAY_PREVIEW = false;
    private static final boolean PARTICLE_FALLBACK = false;
    private static final int MAX_PARTICLE_BLOCKS = 64;
    private static long lastMissingContextWarnMs = 0L;
    private static long lastRenderDiagLogMs = 0L;
    private static long lastParticleEmitMs = 0L;
    private static long lastRenderErrorLogMs = 0L;

    private GhostBlockWorldRenderer() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        WorldRenderEvents.AFTER_ENTITIES.register(GhostBlockWorldRenderer::render);
        LOGGER.info("GhostBlockWorldRenderer registered");
    }

    private static void render(WorldRenderContext context) {
        try {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (!ghostBlockManager.isRenderingEnabled()) {
            return;
        }

        List<GhostBlockManager.GhostBlock> blocks = ghostBlockManager.getVisibleGhostBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();
        if (client.player == null) {
            long now = System.currentTimeMillis();
            if (now - lastMissingContextWarnMs > 3000L) {
                LOGGER.warn("Ghost rendering skipped: consumers={}, matrices={}, player={}",
                        true, true, client.player != null);
                lastMissingContextWarnMs = now;
            }
            return;
        }

        float alpha = Math.max(0.15f, Math.min(0.95f, ghostBlockManager.getOpacity()));
        float r = 0.2f;
        float g = 1.0f;
        float b = 1.0f;
        Vec3d cameraPos = context.worldState().cameraRenderState.pos;

        boolean prevBlend = false;
        boolean prevCull = false;
        boolean prevDepth = false;
        if (X_RAY_PREVIEW) {
            prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }

        matrices.push();

        VertexConsumer lines = consumers.getBuffer(RenderLayer.LINES);

        int rendered = 0;
        GhostBlockManager.GhostBlock firstRendered = null;
        int particleCount = 0;
        long nowForParticles = System.currentTimeMillis();
        boolean emitParticlesThisFrame = PARTICLE_FALLBACK && (nowForParticles - lastParticleEmitMs >= 120L);
        for (GhostBlockManager.GhostBlock block : blocks) {
            if (block == null || !block.isVisible() || block.getPosition() == null) {
                continue;
            }

            BlockPos pos = BlockPos.ofFloored(block.getPosition().x, block.getHeight(), block.getPosition().y);
            matrices.push();
            matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            try {
                client.getBlockRenderManager().renderBlockAsEntity(
                        block.getBlock().getDefaultState(),
                        matrices,
                        consumers,
                        0x00F000F0,
                        OverlayTexture.DEFAULT_UV
                );
            } catch (Exception renderEx) {
                LOGGER.warn("渲染幽灵方块模型失败: pos={}, type={}, err={}", pos.toShortString(), block.getBlockType(), renderEx.toString());
            }

            Box localBox = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).expand(0.0025);
            drawLineBox(lines, matrices.peek(), localBox, r, g, b, alpha);
            matrices.pop();

            if (firstRendered == null) {
                firstRendered = block;
            }

            if (emitParticlesThisFrame && particleCount < MAX_PARTICLE_BLOCKS && client.particleManager != null) {
                client.particleManager.addParticle(
                        ParticleTypes.END_ROD,
                        pos.getX() + 0.5,
                        pos.getY() + 1.05,
                        pos.getZ() + 0.5,
                        0.0,
                        0.01,
                        0.0
                );
                particleCount++;
            }

            rendered++;
            if (rendered >= MAX_RENDER_PER_FRAME) {
                break;
            }
        }

        if (emitParticlesThisFrame && particleCount > 0) {
            lastParticleEmitMs = nowForParticles;
        }

        long now = System.currentTimeMillis();
        if (firstRendered != null && now - lastRenderDiagLogMs > 3000L) {
            double dx = firstRendered.getPosition().x - client.player.getX();
            double dz = firstRendered.getPosition().y - client.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            LOGGER.info("Ghost renderer active: visible={}, rendered={}, first=({},{},{}), player=({},{},{}), distance={}",
                    blocks.size(), rendered,
                    firstRendered.getPosition().x, firstRendered.getHeight(), firstRendered.getPosition().y,
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    String.format("%.2f", dist));
            lastRenderDiagLogMs = now;
        }

        matrices.pop();

        if (X_RAY_PREVIEW) {
            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
        }
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastRenderErrorLogMs > 1500L) {
                LOGGER.error("GhostBlockWorldRenderer 渲染异常（已拦截，避免崩溃）: {}", t, t);
                lastRenderErrorLogMs = now;
            }
        }
    }

    private static void drawLineBox(VertexConsumer consumer, MatrixStack.Entry entry, Box box, float r, float g, float b, float a) {
        int ri = Math.max(0, Math.min(255, (int)(r * 255.0f)));
        int gi = Math.max(0, Math.min(255, (int)(g * 255.0f)));
        int bi = Math.max(0, Math.min(255, (int)(b * 255.0f)));
        int ai = Math.max(0, Math.min(255, (int)(a * 255.0f)));

        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        line(consumer, entry, x1, y1, z1, x2, y1, z1, ri, gi, bi, ai);
        line(consumer, entry, x2, y1, z1, x2, y1, z2, ri, gi, bi, ai);
        line(consumer, entry, x2, y1, z2, x1, y1, z2, ri, gi, bi, ai);
        line(consumer, entry, x1, y1, z2, x1, y1, z1, ri, gi, bi, ai);

        line(consumer, entry, x1, y2, z1, x2, y2, z1, ri, gi, bi, ai);
        line(consumer, entry, x2, y2, z1, x2, y2, z2, ri, gi, bi, ai);
        line(consumer, entry, x2, y2, z2, x1, y2, z2, ri, gi, bi, ai);
        line(consumer, entry, x1, y2, z2, x1, y2, z1, ri, gi, bi, ai);

        line(consumer, entry, x1, y1, z1, x1, y2, z1, ri, gi, bi, ai);
        line(consumer, entry, x2, y1, z1, x2, y2, z1, ri, gi, bi, ai);
        line(consumer, entry, x2, y1, z2, x2, y2, z2, ri, gi, bi, ai);
        line(consumer, entry, x1, y1, z2, x1, y2, z2, ri, gi, bi, ai);
    }

    private static void line(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int r,
            int g,
            int b,
            int a
    ) {
        float width = 1.5f;
        // 1.21.10: VertexConsumer 无 lineWidth，线宽由 RenderLayer.LINES 决定
        consumer.vertex(entry, (float)x1, (float)y1, (float)z1)
                .color(r, g, b, a)
                .normal(entry, 0.0f, 1.0f, 0.0f);
        consumer.vertex(entry, (float)x2, (float)y2, (float)z2)
                .color(r, g, b, a)
                .normal(entry, 0.0f, 1.0f, 0.0f);
    }

}
