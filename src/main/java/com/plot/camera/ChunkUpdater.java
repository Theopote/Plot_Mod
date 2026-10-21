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
 * 区块更新器：正交视距变化后，临时突破原版 32 区块渲染上限，并重建 WorldRenderer 区块存储。
 * <p>
 * 原版选项 {@code options.renderDistance} 硬顶 16/32，{@code setValue} 无法写入更大值。
 * 因此用 {@link #plotRenderDistanceOverride} + GameOptionsMixin 让
 * {@code getClampedViewDistance()} 直接返回 Plot 需要的区块数。
 */
public final class ChunkUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ChunkUpdater");

    /** 原版选项可写入的上限（高内存 32） */
    private static final int VANILLA_OPTION_MAX = 32;
    /**
     * Plot 临时渲染距离上限（区块）。
     * 覆盖视图宽度 1200：半宽 600，四角半径 ≈849 → ceil(849/16)+padding ≈ 57，取 64。
     */
    public static final int MAX_PLOT_VIEW_DISTANCE_CHUNKS = 64;
    private static final int PADDING_CHUNKS = 4;

    private static int savedClientViewDistance = -1;
    private static int savedServerViewDistanceHint = -1;
    private static int savedIntegratedServerViewDistance = -1;
    private static int savedIntegratedSimulationDistance = -1;
    /** &gt;0 时 mixin 直接返回该值；-1 表示未启用 */
    private static volatile int plotRenderDistanceOverride = -1;
    private static int lastAppliedChunks = -1;

    private ChunkUpdater() {
    }

    public static int getPlotRenderDistanceOverride() {
        return plotRenderDistanceOverride;
    }

    /**
     * 按正交「视图范围」（全宽，方块）计算并应用所需 MC 渲染距离。
     * 不依赖 OrthographicCamera.enabled，供滑动条/设置直接调用。
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
        int needed = (int) Math.ceil(cornerRadius / 16.0) + PADDING_CHUNKS;
        needed = Math.min(MAX_PLOT_VIEW_DISTANCE_CHUNKS, Math.max(2, needed));

        applyNeededChunks(client, needed, cornerRadius);
    }

    /**
     * 强制按当前正交相机刷新；同时确保渲染距离已按视距抬高。
     */
    public static int forceUpdateChunksInView(OrthographicCamera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            LOGGER.warn("客户端、世界或玩家为空，无法更新区块");
            return 0;
        }

        float viewRange = camera != null ? camera.getViewDistance() * Math.max(0.1f, camera.getScale())
            : CameraManager.getInstance().getViewDistance();
        applyForViewRangeBlocks(viewRange);

        WorldRenderer worldRenderer = client.worldRenderer;
        if (worldRenderer == null) {
            return 0;
        }
        worldRenderer.scheduleTerrainUpdate();

        if (camera == null) {
            return 0;
        }

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

    private static void applyNeededChunks(MinecraftClient client, int neededChunks, float cornerRadiusBlocks) {
        var options = client.options;
        int clientOption = options.getViewDistance().getValue();
        int clampedBefore = options.getClampedViewDistance();
        boolean changed = lastAppliedChunks != neededChunks || plotRenderDistanceOverride != neededChunks;

        // 先写 override，再改选项 / reload，保证 getClampedViewDistance 立刻返回新值
        plotRenderDistanceOverride = neededChunks;

        int optionTarget = Math.min(VANILLA_OPTION_MAX, neededChunks);
        if (optionTarget > clientOption) {
            if (savedClientViewDistance < 0) {
                savedClientViewDistance = clientOption;
            }
            try {
                options.getViewDistance().setValue(optionTarget);
            } catch (Exception e) {
                LOGGER.warn("写入客户端渲染距离选项失败: {}", e.getMessage());
            }
        }

        if (savedServerViewDistanceHint < 0) {
            savedServerViewDistanceHint = Math.max(1, clampedBefore);
        }
        options.setServerViewDistance(Math.max(neededChunks, 1));

        IntegratedServer server = client.getServer();
        if (server != null) {
            var playerManager = server.getPlayerManager();
            int serverVd = playerManager.getViewDistance();
            if (neededChunks != serverVd) {
                if (savedIntegratedServerViewDistance < 0) {
                    savedIntegratedServerViewDistance = serverVd;
                }
                playerManager.setViewDistance(neededChunks);
                LOGGER.info("集成服视距: {} → {}", serverVd, neededChunks);
            }
            int sim = playerManager.getSimulationDistance();
            int minSim = Math.max(sim, Math.min(neededChunks, Math.max(8, neededChunks / 2)));
            if (minSim > sim) {
                if (savedIntegratedSimulationDistance < 0) {
                    savedIntegratedSimulationDistance = sim;
                }
                playerManager.setSimulationDistance(minSim);
            }
        } else {
            LOGGER.debug("非集成服：仅抬高客户端渲染距离，外围区块仍受服务器视距限制");
        }

        // 客户端区块缓存半径（不依赖数据包时序）
        try {
            if (client.world != null && client.world.getChunkManager() instanceof ClientChunkManager ccm) {
                ccm.updateLoadDistance(neededChunks);
            }
        } catch (Exception e) {
            LOGGER.warn("更新 ClientChunkManager 负载距离失败: {}", e.getMessage());
        }

        int clampedAfter = options.getClampedViewDistance();
        if (changed || clampedAfter < neededChunks) {
            WorldRenderer worldRenderer = client.worldRenderer;
            if (worldRenderer != null) {
                try {
                    worldRenderer.reload();
                    lastAppliedChunks = neededChunks;
                    LOGGER.info(
                        "Plot 渲染距离已应用: needed={} chunks (corner≈{} blocks), "
                            + "option={}/{}, getClampedViewDistance={}→{}, override={}",
                        neededChunks,
                        String.format("%.0f", cornerRadiusBlocks),
                        clientOption,
                        options.getViewDistance().getValue(),
                        clampedBefore,
                        clampedAfter,
                        plotRenderDistanceOverride);
                } catch (Exception e) {
                    LOGGER.error("WorldRenderer.reload 失败（超大视距可能导致内存不足）: {}", e.getMessage(), e);
                }
            }
        }
    }

    public static void restoreClientRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hadOverride = plotRenderDistanceOverride > 0;
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

            if (client.world != null
                && client.world.getChunkManager() instanceof ClientChunkManager ccm
                && savedClientViewDistance >= 0) {
                ccm.updateLoadDistance(savedClientViewDistance);
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
