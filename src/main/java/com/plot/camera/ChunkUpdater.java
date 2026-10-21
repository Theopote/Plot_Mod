package com.plot.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 区块更新器：正交视距/投影变化后，强制 WorldRenderer 按新视锥重收集可见区块，
 * 并在需要时临时提高客户端与集成服渲染距离以覆盖正交视野（含矩形四角）。
 * <p>
 * 原版 {@code getClampedViewDistance()} = min(客户端选项, 服务端下发距离)。
 * 只改客户端选项时，圆形可见区仍被服务端距离卡住，看起来像「最大圆固定」。
 */
public final class ChunkUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ChunkUpdater");
    /** 原版渲染距离选项上限（高内存 32 / 低内存 16） */
    private static final int MAX_CLIENT_VIEW_DISTANCE = 32;
    private static final int RENDER_DISTANCE_PADDING_CHUNKS = 3;

    private static int savedClientViewDistance = -1;
    private static int savedServerViewDistanceHint = -1;
    private static int savedIntegratedServerViewDistance = -1;
    private static int savedIntegratedSimulationDistance = -1;

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
        // 矩形视锥四角到中心的距离；圆形渲染区必须盖住四角，否则边角空白
        float cornerRadius = (float) Math.hypot(halfWidth, halfHeight);

        ensureRenderDistanceCovers(client, cornerRadius);

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
            "正在刷新正交视野区块: center=({}, {}), chunks X[{}..{}] Z[{}..{}], clampedRenderDist={}",
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
     * 让客户端钳制渲染距离与集成服视距足以覆盖正交矩形四角。
     */
    private static void ensureRenderDistanceCovers(MinecraftClient client, float cornerRadiusBlocks) {
        int neededChunks = (int) Math.ceil(cornerRadiusBlocks / 16.0) + RENDER_DISTANCE_PADDING_CHUNKS;
        neededChunks = Math.min(MAX_CLIENT_VIEW_DISTANCE, Math.max(2, neededChunks));

        var options = client.options;
        int clientOption = options.getViewDistance().getValue();
        int clampedBefore = options.getClampedViewDistance();

        if (neededChunks > clientOption) {
            if (savedClientViewDistance < 0) {
                savedClientViewDistance = clientOption;
            }
            options.getViewDistance().setValue(neededChunks);
        }

        // 抬高客户端缓存的「服务端视距」，否则 getClampedViewDistance 仍被旧值卡住
        if (savedServerViewDistanceHint < 0) {
            // 无法直接读私有字段；用当前钳制结果反推：clamped = min(client, server)
            // 若 server>0 且限制了 client，则 clamped < clientOption
            savedServerViewDistanceHint = clampedBefore;
        }
        // setServerViewDistance(0) 表示不钳制；我们设为至少 needed，避免 min() 压低
        options.setServerViewDistance(Math.max(neededChunks, clampedBefore));

        IntegratedServer server = client.getServer();
        if (server != null) {
            var playerManager = server.getPlayerManager();
            int serverVd = playerManager.getViewDistance();
            if (neededChunks > serverVd) {
                if (savedIntegratedServerViewDistance < 0) {
                    savedIntegratedServerViewDistance = serverVd;
                }
                playerManager.setViewDistance(neededChunks);
                LOGGER.info("集成服视距: {} → {}", serverVd, neededChunks);
            }
            int sim = playerManager.getSimulationDistance();
            // 模拟距离不必跟满视距（省 CPU），但过小会导致外围实体/方块更新异常；至少抬到视距的一半
            int minSim = Math.max(sim, Math.min(neededChunks, Math.max(8, neededChunks / 2)));
            if (minSim > sim) {
                if (savedIntegratedSimulationDistance < 0) {
                    savedIntegratedSimulationDistance = sim;
                }
                playerManager.setSimulationDistance(minSim);
            }
        }

        int clampedAfter = options.getClampedViewDistance();
        if (clampedAfter != clampedBefore || neededChunks > clientOption) {
            LOGGER.info(
                "正交视野渲染距离: clientOption {}→{}, clamped {}→{}, needed≈{}（关闭 Plot 后恢复）",
                clientOption, options.getViewDistance().getValue(),
                clampedBefore, clampedAfter, neededChunks);
        }
    }

    /**
     * 恢复进入 Plot / 放大视图前的客户端与集成服渲染距离。
     */
    public static void restoreClientRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            savedClientViewDistance = -1;
            savedServerViewDistanceHint = -1;
            savedIntegratedServerViewDistance = -1;
            savedIntegratedSimulationDistance = -1;
            return;
        }

        try {
            IntegratedServer server = client.getServer();
            if (server != null) {
                var playerManager = server.getPlayerManager();
                if (savedIntegratedServerViewDistance >= 0) {
                    playerManager.setViewDistance(savedIntegratedServerViewDistance);
                    LOGGER.info("已恢复集成服视距: {}", savedIntegratedServerViewDistance);
                }
                if (savedIntegratedSimulationDistance >= 0) {
                    playerManager.setSimulationDistance(savedIntegratedSimulationDistance);
                }
            }

            if (savedClientViewDistance >= 0) {
                int now = client.options.getViewDistance().getValue();
                if (now != savedClientViewDistance) {
                    client.options.getViewDistance().setValue(savedClientViewDistance);
                    LOGGER.info("已恢复客户端渲染距离: {} → {}", now, savedClientViewDistance);
                }
            }

            if (savedServerViewDistanceHint >= 0) {
                client.options.setServerViewDistance(savedServerViewDistanceHint);
            }
        } catch (Exception e) {
            LOGGER.warn("恢复渲染距离失败: {}", e.getMessage());
        } finally {
            savedClientViewDistance = -1;
            savedServerViewDistanceHint = -1;
            savedIntegratedServerViewDistance = -1;
            savedIntegratedSimulationDistance = -1;
        }
    }
}
