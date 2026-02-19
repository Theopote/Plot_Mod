package com.masterplanner.infrastructure.event.block;

import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Client-side world renderer for ghost blocks created by {@link GhostBlockManager}.
 * Renders lightweight line boxes so preview blocks are visible without mutating the world.
 */
public final class GhostBlockWorldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/GhostBlockWorldRenderer");
    private static volatile boolean initialized;
    private static final int MAX_RENDER_PER_FRAME = 20_000;

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
        if (consumers == null) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = context.worldState().cameraRenderState.pos;

        float alpha = Math.max(0.05f, Math.min(1.0f, ghostBlockManager.getOpacity()));
        float r = 0.25f;
        float g = 0.9f;
        float b = 1.0f;

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer lines = consumers.getBuffer(RenderLayers.linesTranslucent());
        MatrixStack.Entry entry = matrices.peek();

        int rendered = 0;
        for (GhostBlockManager.GhostBlock block : blocks) {
            if (block == null || !block.isVisible() || block.getPosition() == null) {
                continue;
            }

            BlockPos pos = BlockPos.ofFloored(block.getPosition().x, block.getHeight(), block.getPosition().y);
            Box box = new Box(pos).expand(0.0025);
            drawLineBox(lines, entry, box, r, g, b, alpha);

            rendered++;
            if (rendered >= MAX_RENDER_PER_FRAME) {
                break;
            }
        }

        matrices.pop();
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

        // Bottom rectangle
        line(consumer, entry, x1, y1, z1, x2, y1, z1, ri, gi, bi, ai);
        line(consumer, entry, x2, y1, z1, x2, y1, z2, ri, gi, bi, ai);
        line(consumer, entry, x2, y1, z2, x1, y1, z2, ri, gi, bi, ai);
        line(consumer, entry, x1, y1, z2, x1, y1, z1, ri, gi, bi, ai);

        // Top rectangle
        line(consumer, entry, x1, y2, z1, x2, y2, z1, ri, gi, bi, ai);
        line(consumer, entry, x2, y2, z1, x2, y2, z2, ri, gi, bi, ai);
        line(consumer, entry, x2, y2, z2, x1, y2, z2, ri, gi, bi, ai);
        line(consumer, entry, x1, y2, z2, x1, y2, z1, ri, gi, bi, ai);

        // Vertical edges
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
        consumer.vertex(entry, (float)x1, (float)y1, (float)z1)
                .color(r, g, b, a)
                .normal(entry, 0.0f, 1.0f, 0.0f)
                .lineWidth(width);
        consumer.vertex(entry, (float)x2, (float)y2, (float)z2)
                .color(r, g, b, a)
                .normal(entry, 0.0f, 1.0f, 0.0f)
                .lineWidth(width);
    }
}
