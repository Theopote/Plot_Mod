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
 * 正交视距变大时，临时抬高 MC 渲染距离（最高原版 32 区块）以覆盖视野。
 * <p>
 * 只升不降；超过 32 会触发服务端 LevelPropagator 崩溃，故硬顶 32。
 * 滑动条连续拖动时防抖，避免每档都 reload / setViewDistance。
 */
public final class ChunkUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ChunkUpdater");

    private static final int VANILLA_OPTION_MAX = PlotRenderDistanceState.MAX_CHUNKS;
    public static final int MAX_PLOT_VIEW_DISTANCE_CHUNKS = PlotRenderDistanceState.MAX_CHUNKS;
    private static final int PADDING_CHUNKS = 4;
    /** 连续拖动时合并抬升，降低 reload 频率 */
    private static final long APPLY_DEBOUNCE_MS = 250L;

    private static int savedClientViewDistance = -1;
    private static int savedServerViewDistanceHint = -1;
    private static int savedIntegratedServerViewDistance = -1;
    private static int savedIntegratedSimulationDistance = -1;
    private static int lastAppliedChunks = -1;

    private static int pendingExpandTo = -1;
    private static float pendingCornerRadius = 0f;
    private static int pendingComputed = 0;
    private static int pendingFloor = 0;
    private static long pendingSinceMs = 0L;

    private ChunkUpdater() {
    }

    public static int getPlotRenderDistanceOverride() {
        return PlotRenderDistanceState.getOverrideChunks();
    }

    private static void setOverride(int chunks) {
        PlotRenderDistanceState.setOverrideChunks(chunks);
    }

    /**
     * 按正交视图全宽（方块）计算所需渲染距离并应用；不会低于玩家当前选项，不超过 32。
     */
    public static void applyForViewRangeBlocks(float viewRangeBlocks) {
        applyForViewRangeBlocks(viewRangeBlocks, false);
    }

    /**
     * @param immediate true 时跳过防抖（松手 / 打开 Plot 时）
     */
    public static void applyForViewRangeBlocks(float viewRangeBlocks, boolean immediate) {
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
        int floor = Math.max(savedClientViewDistance, clientOption);
        floor = Math.min(VANILLA_OPTION_MAX, floor);
        int needed = Math.min(VANILLA_OPTION_MAX, Math.max(computed, floor));

        int expandTo = needed;
        if (lastAppliedChunks > expandTo) {
            expandTo = lastAppliedChunks;
        }
        if (expandTo < floor) {
            expandTo = floor;
        }
        expandTo = Math.min(VANILLA_OPTION_MAX, expandTo);

        // 尚未真正抬高时先记下 override，供 getClampedViewDistance 使用；落地仍走防抖
        setOverride(expandTo);

        if (!immediate && expandTo > lastAppliedChunks && lastAppliedChunks >= 0) {
            pendingExpandTo = expandTo;
            pendingCornerRadius = cornerRadius;
            pendingComputed = computed;
            pendingFloor = floor;
            if (pendingSinceMs == 0L) {
                pendingSinceMs = System.currentTimeMillis();
            }
            return;
        }

        pendingExpandTo = -1;
        pendingSinceMs = 0L;
        applyNeededChunks(client, expandTo, cornerRadius, computed, floor);
    }

    /**
     * 在帧循环中调用：防抖到期后落地待定的视距抬升。
     */
    public static void flushPendingApplyIfDue() {
        if (pendingExpandTo < 0 || pendingSinceMs == 0L) {
            return;
        }
        if (System.currentTimeMillis() - pendingSinceMs < APPLY_DEBOUNCE_MS) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.options == null) {
            pendingExpandTo = -1;
            pendingSinceMs = 0L;
            return;
        }
        int expandTo = pendingExpandTo;
        float corner = pendingCornerRadius;
        int computed = pendingComputed;
        int floor = pendingFloor;
        pendingExpandTo = -1;
        pendingSinceMs = 0L;
        applyNeededChunks(client, expandTo, corner, computed, floor);
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
        applyForViewRangeBlocks(viewRange, true);

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

        int renderChunks = Math.max(2, Math.min(VANILLA_OPTION_MAX, client.options.getClampedViewDistance()));
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

        LOGGER.debug(
            "正交视野刷新: viewRange={}, clampedVD={}, scheduled={}",
            String.format("%.0f", viewRange),
            renderChunks,
            scheduled);
        return scheduled;
    }

    private static void applyNeededChunks(
        MinecraftClient client,
        int expandTo,
        float cornerRadiusBlocks,
        int computedFromView,
        int floorChunks
    ) {
        expandTo = Math.min(VANILLA_OPTION_MAX, Math.max(2, expandTo));

        var options = client.options;
        int clientOption = options.getViewDistance().getValue();
        int clampedBefore = options.getClampedViewDistance();

        boolean raised = expandTo > lastAppliedChunks
            && (lastAppliedChunks < 0 || expandTo > clampedBefore || expandTo > lastAppliedChunks);
        // 已应用过且目标未升高：不再 reload
        if (lastAppliedChunks >= 0 && expandTo <= lastAppliedChunks) {
            setOverride(lastAppliedChunks);
            return;
        }

        setOverride(expandTo);

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
            if (savedIntegratedServerViewDistance < 0) {
                savedIntegratedServerViewDistance = Math.min(serverVd, VANILLA_OPTION_MAX);
            }
            int safeVd = Math.min(VANILLA_OPTION_MAX, expandTo);
            if (safeVd > serverVd) {
                playerManager.setViewDistance(safeVd);
                LOGGER.info("集成服视距: {} → {}", serverVd, safeVd);
            }
            // 模拟距离不得超过 32，且不必跟满视距
            int sim = playerManager.getSimulationDistance();
            int minSim = Math.min(
                VANILLA_OPTION_MAX,
                Math.max(sim, Math.min(safeVd, Math.max(8, safeVd / 2))));
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

        if (raised && expandTo > clampedBefore) {
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
        } else {
            lastAppliedChunks = Math.max(lastAppliedChunks, expandTo);
        }
    }

    public static void restoreClientRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hadOverride = getPlotRenderDistanceOverride() > 0;
        int restoreTo = savedClientViewDistance;
        PlotRenderDistanceState.clear();
        lastAppliedChunks = -1;
        pendingExpandTo = -1;
        pendingSinceMs = 0L;

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
                    playerManager.setViewDistance(
                        Math.min(VANILLA_OPTION_MAX, savedIntegratedServerViewDistance));
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
