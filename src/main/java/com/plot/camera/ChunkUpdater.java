package com.plot.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 区块更新器：正交视距/投影变化后，强制 WorldRenderer 按新视锥重收集可见区块，
 * 并在需要时临时提高客户端渲染距离以覆盖正交视野。
 * <p>
 * 仅改投影矩阵时 Minecraft 不会自动 {@code applyFrustum}（需相机位移或
 * {@link WorldRenderer#scheduleTerrainUpdate()}），因此放大视图范围后外围会空白，
 * 直到中键拖动或切换相机。此外正交半宽常超过「视频设置 → 渲染距离」，
 * 超出部分本就没有 BuiltChunk，必须提升渲染距离才能显示。
 */
public final class ChunkUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ChunkUpdater");
    /** 客户端渲染距离上限（与原版选项一致） */
    private static final int MAX_CLIENT_VIEW_DISTANCE = 32;
    /** Plot 打开前保存的渲染距离；&lt;0 表示未改动 */
    private static int savedClientViewDistance = -1;

    private ChunkUpdater() {
    }

    /**
     * 强制按当前正交视野刷新地形可见性；必要时临时提高 MC 渲染距离。
     *
     * @param camera 正交相机
     * @return 额外调度邻接更新的区块数
     */
    public static int forceUpdateChunksInView(OrthographicCamera camera) {
        if (camera == null || !camera.isEnabled()) {
            LOGGER.warn("相机未启用，无法更新区块");
            return 0;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            LOGGER.warn("客户端、世界或玩家为空，无法更新区块");
            return 0;
        }

        WorldRenderer worldRenderer = client.worldRenderer;
        if (worldRenderer == null) {
            LOGGER.warn("WorldRenderer 为空，无法更新区块");
            return 0;
        }

        float halfWidth = camera.getViewDistance() * camera.getScale() / 2.0f;
        float aspect = 1.0f;
        if (client.getWindow() != null && client.getWindow().getFramebufferHeight() > 0) {
            aspect = (float) client.getWindow().getFramebufferWidth()
                / (float) client.getWindow().getFramebufferHeight();
        }
        float halfHeight = halfWidth / Math.max(0.1f, aspect);
        float halfExtent = Math.max(halfWidth, halfHeight);

        // 正交视野半宽超出当前渲染距离时，临时提高（关闭 Plot 时恢复）
        ensureClientRenderDistance(client, halfExtent);

        // 关键：下一帧用新正交投影重建遮挡图并 applyFrustum
        worldRenderer.scheduleTerrainUpdate();

        World world = client.world;
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        float panX = camera.getPosition().x;
        float panZ = camera.getPosition().y;
        double centerX = playerX + panX;
        double centerZ = playerZ + panZ;

        int minChunkX = ChunkSectionPos.getSectionCoord(centerX - halfWidth);
        int maxChunkX = ChunkSectionPos.getSectionCoord(centerX + halfWidth);
        int minChunkZ = ChunkSectionPos.getSectionCoord(centerZ - halfHeight);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(centerZ + halfHeight);

        int renderChunks = Math.max(2, client.options.getClampedViewDistance());
        int playerChunkX = ChunkSectionPos.getSectionCoord(playerX);
        int playerChunkZ = ChunkSectionPos.getSectionCoord(playerZ);
        minChunkX = Math.max(minChunkX, playerChunkX - renderChunks);
        maxChunkX = Math.min(maxChunkX, playerChunkX + renderChunks);
        minChunkZ = Math.max(minChunkZ, playerChunkZ - renderChunks);
        maxChunkZ = Math.min(maxChunkZ, playerChunkZ + renderChunks);

        LOGGER.info(
            "正在刷新正交视野区块: center=({}, {}), chunks X[{}..{}] Z[{}..{}], renderDist={}",
            String.format("%.1f", centerX),
            String.format("%.1f", centerZ),
            minChunkX, maxChunkX, minChunkZ, maxChunkZ,
            renderChunks);

        int scheduled = 0;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!world.getChunkManager().isChunkLoaded(x, z)) {
                    continue;
                }
                try {
                    worldRenderer.scheduleNeighborUpdates(new ChunkPos(x, z));
                    scheduled++;
                } catch (Exception e) {
                    LOGGER.debug("scheduleNeighborUpdates 失败 [{}, {}]: {}", x, z, e.getMessage());
                }
            }
        }

        LOGGER.info("正交视野刷新已调度: terrainUpdate + {} 邻接区块", scheduled);
        return scheduled;
    }

    /**
     * 若正交半宽超过当前客户端渲染距离，临时提高渲染距离（仅升高，关闭 Plot 时恢复）。
     */
    private static void ensureClientRenderDistance(MinecraftClient client, float halfExtentBlocks) {
        int neededChunks = (int) Math.ceil(halfExtentBlocks / 16.0) + 2;
        neededChunks = Math.min(MAX_CLIENT_VIEW_DISTANCE, Math.max(2, neededChunks));

        int current = client.options.getViewDistance().getValue();
        if (neededChunks <= current) {
            return;
        }

        if (savedClientViewDistance < 0) {
            savedClientViewDistance = current;
        }

        client.options.getViewDistance().setValue(neededChunks);
        LOGGER.info(
            "正交视野需要更大渲染距离: {} → {}（关闭 Plot 后恢复为 {}）",
            current, neededChunks, savedClientViewDistance);
    }

    /**
     * 恢复进入 Plot / 放大视图前的客户端渲染距离。
     */
    public static void restoreClientRenderDistance() {
        if (savedClientViewDistance < 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            savedClientViewDistance = -1;
            return;
        }
        int restore = savedClientViewDistance;
        savedClientViewDistance = -1;
        try {
            int now = client.options.getViewDistance().getValue();
            if (now != restore) {
                client.options.getViewDistance().setValue(restore);
                LOGGER.info("已恢复客户端渲染距离: {} → {}", now, restore);
            }
        } catch (Exception e) {
            LOGGER.warn("恢复客户端渲染距离失败: {}", e.getMessage());
        }
    }
}
