package com.plot.infrastructure.event.block;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Client-side world renderer for ghost blocks created by {@link GhostBlockManager}.
 * Uses vanilla debug box drawing (visible through terrain) plus block particles as fallback.
 */
public final class GhostBlockWorldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GhostBlockWorldRenderer");
    private static volatile boolean initialized;
    private static final int MAX_RENDER_PER_FRAME = 4_000;
    private static final int BATCH_DRAW_SIZE = 256;
    /** 略高于地表绘制，避免顶视时与地形面片深度冲突。 */
    private static final float BOX_Y_INSET = 0.02f;
    private static final boolean PARTICLE_FALLBACK = true;
    private static final int MAX_PARTICLE_BLOCKS = 128;
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

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(GhostBlockWorldRenderer::render);
        LOGGER.info("GhostBlockWorldRenderer registered on BEFORE_DEBUG_RENDER");
    }

    private static void render(WorldRenderContext context) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null) {
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

            MatrixStack matrices = context.matrices();
            if (matrices == null) {
                logMissingContext("matrices=null");
                return;
            }

            Vec3d cameraPos = resolveCameraPos(context, client);
            float alpha = Math.clamp(ghostBlockManager.getOpacity(), 0.35f, 0.95f);
            float r = 0.1f;
            float g = 1.0f;
            float b = 1.0f;

            int rendered = 0;
            GhostBlockManager.GhostBlock firstRendered = null;
            int particleCount = 0;
            long nowForParticles = System.currentTimeMillis();
            boolean emitParticlesThisFrame = PARTICLE_FALLBACK && (nowForParticles - lastParticleEmitMs >= 100L);

            try (BufferAllocator allocator = new BufferAllocator(16384)) {
                VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);

                matrices.push();
                try {
                    matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                    for (GhostBlockManager.GhostBlock block : blocks) {
                        if (block == null || !block.isVisible() || block.getPosition() == null) {
                            continue;
                        }

                        double wx = block.getPosition().x;
                        double wy = block.getHeight() + BOX_Y_INSET;
                        double wz = block.getPosition().y;

                        DebugRenderer.drawBox(
                                matrices,
                                consumers,
                                wx, wy, wz,
                                wx + 1.0, wy + 1.0, wz + 1.0,
                                r, g, b, alpha
                        );

                        if (firstRendered == null) {
                            firstRendered = block;
                        }

                        rendered++;
                        if (rendered % BATCH_DRAW_SIZE == 0) {
                            consumers.draw();
                        }
                        if (rendered >= MAX_RENDER_PER_FRAME) {
                            break;
                        }
                    }

                    consumers.draw();
                } finally {
                    matrices.pop();
                }
            }

            if (emitParticlesThisFrame && client.particleManager != null) {
                var markerEffect = new BlockStateParticleEffect(
                        ParticleTypes.BLOCK,
                        Blocks.CYAN_STAINED_GLASS.getDefaultState()
                );
                int limit = Math.min(blocks.size(), MAX_PARTICLE_BLOCKS);
                for (int i = 0; i < limit; i++) {
                    GhostBlockManager.GhostBlock block = blocks.get(i);
                    if (block == null || !block.isVisible() || block.getPosition() == null) {
                        continue;
                    }
                    BlockPos pos = BlockPos.ofFloored(block.getPosition().x, block.getHeight(), block.getPosition().y);
                    client.particleManager.addParticle(
                            markerEffect,
                            pos.getX() + 0.5,
                            pos.getY() + 1.02,
                            pos.getZ() + 0.5,
                            0.0,
                            0.0,
                            0.0
                    );
                    particleCount++;
                }
            }

            if (emitParticlesThisFrame && particleCount > 0) {
                lastParticleEmitMs = nowForParticles;
            }

            logRenderDiagnostics(blocks, rendered, firstRendered, client, cameraPos);
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastRenderErrorLogMs > 1500L) {
                LOGGER.error("GhostBlockWorldRenderer 渲染异常（已拦截，避免崩溃）: {}", t, t);
                lastRenderErrorLogMs = now;
            }
        }
    }

    private static Vec3d resolveCameraPos(WorldRenderContext context, MinecraftClient client) {
        try {
            var worldState = context.worldState();
            if (worldState != null) {
                CameraRenderState camera = worldState.cameraRenderState;
                if (camera != null && camera.initialized && camera.pos != null) {
                    return camera.pos;
                }
            }
        } catch (Exception ignored) {
            // fall through to player position
        }
        return new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    private static void logRenderDiagnostics(
            List<GhostBlockManager.GhostBlock> blocks,
            int rendered,
            GhostBlockManager.GhostBlock firstRendered,
            MinecraftClient client,
            Vec3d cameraPos
    ) {
        long now = System.currentTimeMillis();
        if (firstRendered == null || rendered <= 0 || now - lastRenderDiagLogMs <= 3000L) {
            return;
        }

        double dx = firstRendered.getPosition().x - client.player.getX();
        double dz = firstRendered.getPosition().y - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        LOGGER.info(
                "Ghost renderer active: visible={}, rendered={}, first=({},{},{}), player=({},{},{}), camera=({},{},{}), horizontalDistance={}",
                blocks.size(),
                rendered,
                firstRendered.getPosition().x,
                firstRendered.getHeight(),
                firstRendered.getPosition().y,
                client.player.getX(),
                client.player.getY(),
                client.player.getZ(),
                cameraPos.x,
                cameraPos.y,
                cameraPos.z,
                String.format("%.2f", dist)
        );
        lastRenderDiagLogMs = now;
    }

    private static void logMissingContext(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastMissingContextWarnMs > 3000L) {
            LOGGER.warn("Ghost rendering skipped: {}", reason);
            lastMissingContextWarnMs = now;
        }
    }
}
