package com.plot.infrastructure.event.block;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Client-side world renderer for ghost blocks created by {@link GhostBlockManager}.
 * Uses vanilla debug box drawing so previews stay visible on MC 1.21.x render pipeline.
 */
public final class GhostBlockWorldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GhostBlockWorldRenderer");
    private static volatile boolean initialized;
    private static final int MAX_RENDER_PER_FRAME = 4_000;
    private static final int BATCH_DRAW_SIZE = 512;
    private static long lastMissingContextWarnMs = 0L;
    private static long lastRenderDiagLogMs = 0L;
    private static long lastRenderErrorLogMs = 0L;

    private GhostBlockWorldRenderer() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Debug-style overlays: draw line boxes after terrain/entities, before late debug pass.
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
            float alpha = Math.max(0.15f, Math.min(0.95f, ghostBlockManager.getOpacity()));
            float r = 0.2f;
            float g = 1.0f;
            float b = 1.0f;

            try (BufferAllocator allocator = new BufferAllocator(8192)) {
                VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);

                matrices.push();
                try {
                    matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                    int rendered = drawGhostBlockBoxes(blocks, matrices, consumers, r, g, b, alpha);
                    consumers.draw();
                    logRenderDiagnostics(blocks, rendered, client, cameraPos);
                } finally {
                    matrices.pop();
                }
            }
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

    private static int drawGhostBlockBoxes(
            List<GhostBlockManager.GhostBlock> blocks,
            MatrixStack matrices,
            VertexConsumerProvider.Immediate consumers,
            float r,
            float g,
            float b,
            float alpha
    ) {
        int rendered = 0;
        for (GhostBlockManager.GhostBlock block : blocks) {
            if (block == null || !block.isVisible() || block.getPosition() == null) {
                continue;
            }

            BlockPos pos = BlockPos.ofFloored(block.getPosition().x, block.getHeight(), block.getPosition().y);
            DebugRenderer.drawBlockBox(matrices, consumers, pos, r, g, b, alpha);

            rendered++;
            if (rendered % BATCH_DRAW_SIZE == 0) {
                consumers.draw();
            }
            if (rendered >= MAX_RENDER_PER_FRAME) {
                break;
            }
        }

        return rendered;
    }

    private static void logRenderDiagnostics(
            List<GhostBlockManager.GhostBlock> blocks,
            int rendered,
            MinecraftClient client,
            Vec3d cameraPos
    ) {
        long now = System.currentTimeMillis();
        if (rendered <= 0 || now - lastRenderDiagLogMs <= 3000L) {
            return;
        }

        GhostBlockManager.GhostBlock first = blocks.getFirst();
        double dx = first.getPosition().x - client.player.getX();
        double dz = first.getPosition().y - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        LOGGER.info(
                "Ghost renderer active: visible={}, rendered={}, first=({},{},{}), player=({},{},{}), camera=({},{},{}), horizontalDistance={}",
                blocks.size(),
                rendered,
                first.getPosition().x,
                first.getHeight(),
                first.getPosition().y,
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
