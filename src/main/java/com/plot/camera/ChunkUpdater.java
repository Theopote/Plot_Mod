package com.plot.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 正交视距变大时，临时抬高 MC 渲染距离以覆盖视野四角。
 * <p>
 * 原则：只升不降。开 Plot / 小视距时不得把玩家原有渲染距离压小，
 * 否则会出现「比原来更小的矩形空白区」。
 */
public final class ChunkUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ChunkUpdater");

    private static final int VANILLA_OPTION_MAX = 32;
    /** 覆盖视图宽度 1200 的四角所需约 57，取 64 */
    public static final int MAX_PLOT_VIEW_DISTANCE_CHUNKS = 64;
    private static final int PADDING_CHUNKS = 4;

    private static int savedClientViewDistance = -1;
    private static int savedServerViewDistanceHint = -1;
    private static int savedIntegratedServerViewDistance = -1;
    private static int savedIntegratedSimulationDistance = -1;
    /** 仅表示「希望抬到的下限」；mixin 用 max(原版, override) */
    private static volatile int plotRenderDistanceOverride = -1;
    private static int lastAppliedChunks = -1;

    private ChunkUpdater() {
    }

    public static int getPlotRenderDistanceOverride() {
        return plotRenderDistanceOverride;
    }

    /**
     * 按正交视图全宽（方块）计算所需渲染距离并应用；不会低于玩家当前选项。
     */
    public static void applyForViewRangeBlocks(float viewRangeBlocks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.options == null) {
            return;
        }

        float aspect = 1.0f;
        if (client.getWindow() != null && client.getWindow().getFramebufferHeight() > 0) {
            aspect = (float) client.getWindow().getFramebufferWidth()
                / (float) client.getWindow().getFramebufferHeight();
        }
        float halfWidth = Math.max(1.0f, viewRangeBlocks) * 0.5f;
        float halfHeight = halfWidth / Math.max(0.1f, aspect);
        float cornerRadius = (float) Math.hypot(halfWidth, halfHeight);
        int computed = (int) Math.ceil(cornerRadius / 16.0) + PADDING_CHUNKS;
        computed = Math.min(MAX_PLOT_VIEW_DISTANCE_CHUNKS, Math.max(2, computed));

        int clientOption = client.options.getViewDistance().getValue();
        if (savedClientViewDistance < 0) {
            savedClientViewDistance = clientOption;
        }
        // 地板：玩家原选项与当前选项，绝不能为了「小视距」去缩小世界
        int floor = Math.max(savedClientViewDistance, clientOption);
        int needed = Math.max(computed, floor);

        applyNeededChunks(client, needed, cornerRadius, computed, floor);
    }

    public static int forceUpdateChunksInView(OrthographicCamera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            LOGGER.warn("客户端、世界或玩家为空，无法更新区块");
            return 0;
        }

        float viewRange = camera != null
            ? camera.getViewDistance() * Math.max(0.1f, camera.getScale())
            : CameraManager.getInstance().getViewDistance();
        applyForViewRangeBlocks(viewRange);

        WorldRenderer worldRenderer = client.worldRenderer;
        if (worldRenderer == null || camera == null) {
            return 0;
        }
        worldRenderer.scheduleTerrainUpdate();

        float halfWidth = viewRange * 0.5f;
        float aspect = 1.0f;
        if (client.getWindow() != null && client.getWindow().getFramebufferHeight() > 0) {
            aspect = (float) client.getWindow().getFramebufferWidth()
                / (float) client.getWindow().getFramebufferHeight();
        }
        float halfHeight = halfWidth / Math.max(0.1f, aspect);

        World world = client.world;
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        float panX = camera.getPosition().x;
        float panZ = camera.getPosition().y;
        double centerX = playerX + panX;
        double centerZ = playerZ + panZ;

        int renderChunks = Math.max(2, client.options.getClampedViewDistance());
        int playerChunkX = ChunkSectionPos.getSectionCoord(playerX);
        int playerChunkZ = ChunkSectionPos.getSectionCoord(playerZ);
        int minChunkX = Math.max(ChunkSectionPos.getSectionCoord(centerX - halfWidth), playerChunkX - renderChunks);
        int maxChunkX = Math.min(ChunkSectionPos.getSectionCoord(centerX + halfWidth), playerChunkX + renderChunks);
        int minChunkZ = Math.max(ChunkSectionPos.getSectionCoord(centerZ - halfHeight), playerChunkZ - renderChunks);
        int maxChunkZ = Math.min(ChunkSectionPos.getSectionCoord(centerZ + halfHeight), playerChunkZ + renderChunks);

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

        LOGGER.info(
            "正交视野刷新: viewRange={}, clampedVD={}, chunks X[{}..{}] Z[{}..{}], scheduled={}",
            String.format("%.0f", viewRange),
            renderChunks,
            minChunkX, maxChunkX, minChunkZ, maxChunkZ,
            scheduled);
        return scheduled;
    }

    private static void applyNeededChunks(
        MinecraftClient client,
        int neededChunks,
        float cornerRadiusBlocks,
        int computedFromView,
        int floorChunks
    ) {
        var options = client.options;
        int clientOption = options.getViewDistance().getValue();
        int clampedBefore = options.getClampedViewDistance();

        // Plot 打开期间只扩大：缩小视图范围时保持已抬高的 RD，关闭 Plot 再恢复
        int expandTo = neededChunks;
        if (lastAppliedChunks > expandTo) {
            expandTo = lastAppliedChunks;
        }
        if (expandTo < floorChunks) {
            expandTo = floorChunks;
        }

        boolean raised = expandTo > clampedBefore && (lastAppliedChunks < 0 || expandTo > lastAppliedChunks);
        plotRenderDistanceOverride = expandTo;

        int optionTarget = Math.min(VANILLA_OPTION_MAX, expandTo);
        if (optionTarget > clientOption) {
            try {
                options.getViewDistance().setValue(optionTarget);
            } catch (Exception e) {
                LOGGER.warn("写入客户端渲染距离选项失败: {}", e.getMessage());
            }
        }

        if (savedServerViewDistanceHint < 0) {
            savedServerViewDistanceHint = Math.max(1, Math.min(clampedBefore, clientOption));
        }
        options.setServerViewDistance(Math.max(expandTo, 1));

        IntegratedServer server = client.getServer();
        if (server != null) {
            var playerManager = server.getPlayerManager();
            int serverVd = playerManager.getViewDistance();
            if (expandTo > serverVd) {
                if (savedIntegratedServerViewDistance < 0) {
                    savedIntegratedServerViewDistance = serverVd;
                }
                playerManager.setViewDistance(expandTo);
                LOGGER.info("集成服视距: {} → {}", serverVd, expandTo);
            }
            int sim = playerManager.getSimulationDistance();
            int minSim = Math.max(sim, Math.min(expandTo, Math.max(8, expandTo / 2)));
            if (minSim > sim) {
                if (savedIntegratedSimulationDistance < 0) {
                    savedIntegratedSimulationDistance = sim;
                }
                playerManager.setSimulationDistance(minSim);
            }
        }

        if (raised || lastAppliedChunks < 0) {
            try {
                if (client.world != null && client.world.getChunkManager() instanceof ClientChunkManager ccm) {
                    ccm.updateLoadDistance(expandTo);
                }
            } catch (Exception e) {
                LOGGER.warn("更新 ClientChunkManager 负载距离失败: {}", e.getMessage());
            }
        }

        if (raised) {
            WorldRenderer worldRenderer = client.worldRenderer;
            if (worldRenderer != null) {
                try {
                    worldRenderer.reload();
                    LOGGER.info(
                        "Plot 渲染距离已抬高: {}→{} chunks (viewCalc={}, floor={}), corner≈{}, option={}/{}",
                        clampedBefore,
                        expandTo,
                        computedFromView,
                        floorChunks,
                        String.format("%.0f", cornerRadiusBlocks),
                        clientOption,
                        options.getViewDistance().getValue());
                } catch (Exception e) {
                    LOGGER.error("WorldRenderer.reload 失败: {}", e.getMessage(), e);
                }
            }
            lastAppliedChunks = expandTo;
        } else if (lastAppliedChunks < 0) {
            lastAppliedChunks = expandTo;
            LOGGER.info(
                "Plot 保持原渲染距离 {} chunks（viewCalc={} ≤ floor={}，不缩小）",
                expandTo, computedFromView, floorChunks);
        }
    }

    public static void restoreClientRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hadOverride = plotRenderDistanceOverride > 0;
        int restoreTo = savedClientViewDistance;
        plotRenderDistanceOverride = -1;
        lastAppliedChunks = -1;

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

            if (restoreTo >= 0) {
                int now = client.options.getViewDistance().getValue();
                if (now != restoreTo) {
                    client.options.getViewDistance().setValue(restoreTo);
                    LOGGER.info("已恢复客户端渲染距离: {} → {}", now, restoreTo);
                }
            }

            if (savedServerViewDistanceHint >= 0) {
                client.options.setServerViewDistance(savedServerViewDistanceHint);
            }

            if (client.world != null
                && client.world.getChunkManager() instanceof ClientChunkManager ccm
                && restoreTo >= 0) {
                ccm.updateLoadDistance(restoreTo);
            }

            if (hadOverride && client.worldRenderer != null) {
                client.worldRenderer.reload();
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
