package com.plot.plugin.road.manager;

import com.plot.api.world.GhostBlockOwners;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.core.context.PluginContext;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.validation.RoadElevationBoundsResolver;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.RoadPlacementVisibility;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.profile.terrain.RoadTerrainProfileCache;
import com.plot.plugin.road.profile.terrain.RoadTerrainProfileData;
import com.plot.plugin.road.profile.terrain.RoadTerrainProfileSampler;
import com.plot.plugin.road.profile.terrain.RoadTerrainProfileSamplingJob;
import com.plot.plugin.road.profile.terrain.TerrainProfileKey;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.terrain.RoadCorridorTerrainPrefetch;
import com.plot.plugin.road.terrain.RoadWorldTerrainProbe;
import com.plot.utils.PlotI18n;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 道路预览、虚影投影与世界落地。
 */
public final class RoadPreviewManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadPreview");
    /** 每帧上传的幽灵方块数上限；再受 {@link #GHOST_UPLOAD_BUDGET_NANOS} 约束。 */
    static final int GHOST_BLOCKS_PER_TICK = 2_500;
    /** 幽灵上传每帧时间预算，避免投影阶段把 FPS 打到 1。 */
    static final long GHOST_UPLOAD_BUDGET_NANOS = 6_000_000L;

    private final RoadProjectStatus status;
    private final PluginContext host;
    private RoadNetworkGenerator networkGenerator;
    private RoadGenerationResult lastGenerationResult;
    private RoadNetwork previewNetwork;
    private List<Map.Entry<net.minecraft.util.math.BlockPos, String>> pendingGhostUpload;
    private int pendingGhostUploadIndex;
    private int pendingGhostUploadTotal;
    private boolean pendingGhostClear;
    private RoadNetwork lastProfileNetwork;
    private Map<String, RoadGenerationResult> lastEdgeResults = Collections.emptyMap();
    private Map<String, Integer> lastNodeElevations = Collections.emptyMap();
    private Map<String, RoadProfileChartData> lastRoadProfiles = Collections.emptyMap();
    private final RoadTerrainProfileCache terrainProfileCache = new RoadTerrainProfileCache();
    private RoadTerrainProfileSamplingJob terrainProfileJob;
    /** 概览合成图表缓存：避免每帧 withTerrainGround 重算。 */
    private final Map<String, OverviewChartCacheEntry> overviewChartCache = new LinkedHashMap<>();
    /** 已自动请求过的 TerrainProfileKey 编码；同键不重复自动读，失败也不每帧重试。 */
    private final Set<String> autoTerrainRequestTokens = new HashSet<>();
    private RoadNetworkGenerator.NetworkGenerationResult lastNetworkGenerationResult;
    private long terrainRevision = 0L;
    private boolean previewNeedsRecalc = false;
    private RoadNetworkPreviewJob previewJob;
    private long lastPreviewTerrainFingerprint = 0L;
    /** 道路几何指纹（节点/中心线）；仅几何变化时不冲刷世界地形世代。 */
    private long lastRoadGeometryFingerprint = 0L;
    /** 沿走廊中心线站的地表指纹；中段改地形也会变。 */
    private long lastWorldTerrainSampleFingerprint = 0L;
    private String previewWorldKey = "";
    /** 地形预取完成后需用服务端快照重刷的路网（轻量纵断面）。 */
    private RoadNetwork pendingTerrainPrefetchRefresh;
    /** 完整预览在缓存未就绪时启动；预取完成后提示重新计算。与轻量采样独立。 */
    private boolean pendingFullPreviewTerrainRefresh;
    private boolean suppressTerrainPrefetchReschedule;
    /** 路网存在未提交编辑事务时为 true（例如统一 Y 拖动中）。 */
    private BooleanSupplier pendingNetworkEditCheck = () -> false;
    /** 发布地形任务结果时读取当前 live 路网（几何/投影必须重新校验）。 */
    private Supplier<RoadNetwork> liveNetworkLookup = () -> null;
    /** 测试可覆盖世界键；生产默认识别当前客户端世界。 */
    private Supplier<String> worldKeyLookup = RoadPreviewManager::detectCurrentWorldKey;

    public RoadPreviewManager(RoadProjectStatus status, PluginContext host) {
        this.status = status;
        this.host = Objects.requireNonNull(host, "host");
    }

    public void setNetworkGenerator(RoadNetworkGenerator networkGenerator) {
        this.networkGenerator = networkGenerator;
    }

    public void setPendingNetworkEditCheck(BooleanSupplier pendingNetworkEditCheck) {
        this.pendingNetworkEditCheck =
            pendingNetworkEditCheck != null ? pendingNetworkEditCheck : () -> false;
    }

    public void setLiveNetworkLookup(Supplier<RoadNetwork> liveNetworkLookup) {
        this.liveNetworkLookup = liveNetworkLookup != null ? liveNetworkLookup : () -> null;
    }

    public void setWorldKeyLookup(Supplier<String> worldKeyLookup) {
        this.worldKeyLookup = worldKeyLookup != null
            ? worldKeyLookup
            : RoadPreviewManager::detectCurrentWorldKey;
    }

    private void bumpOverviewChartCache() {
        overviewChartCache.clear();
    }

    private boolean hasPendingNetworkEdit() {
        return pendingNetworkEditCheck.getAsBoolean();
    }

    public Map<String, Integer> getLastNodeElevations() {
        return lastNodeElevations;
    }

    /** 地形/预览上下文变更计数，供 UI 缓存失效。 */
    public long getTerrainRevision() {
        return terrainRevision;
    }

    /** 是否曾有过预览且因路网/参数变更而失效，供 UI 切换「重新计算」文案。 */
    public boolean needsPreviewRecalc() {
        return previewNeedsRecalc;
    }

    /** 是否仍保留沿路径的地形纵断面采样（纵断面编辑会话用）。 */
    public boolean hasProfileSampling() {
        return !lastEdgeResults.isEmpty();
    }

    private void bumpTerrainRevision() {
        terrainRevision++;
        bumpOverviewChartCache();
    }

    /**
     * 分离道路几何变化与世界地表变化：
     * <ul>
     *   <li>仅移动节点 / 改折线 → 更新几何指纹；预取缓存键本就会变，不 bump 世界世代</li>
     *   <li>走廊中段地表 Y 变化 → {@link RoadCorridorTerrainPrefetch#notifyWorldTerrainChanged()}</li>
     * </ul>
     */
    private void syncWorldTerrainPrefetchEpoch(RoadNetwork network) {
        if (network == null || networkGenerator == null) {
            return;
        }
        long geometry = RoadWorldTerrainProbe.geometryFingerprint(network);
        long surface = worldSurfaceFingerprint(network);
        boolean geometryChanged = lastRoadGeometryFingerprint != 0L
            && geometry != lastRoadGeometryFingerprint;
        boolean surfaceChanged = lastWorldTerrainSampleFingerprint != 0L
            && surface != lastWorldTerrainSampleFingerprint
            && surface != 0L;
        if (surfaceChanged) {
            RoadCorridorTerrainPrefetch.notifyWorldTerrainChanged();
            LOGGER.debug("检测到走廊地表变更（含中段），走廊预取缓存已失效");
        } else if (geometryChanged) {
            LOGGER.debug("道路几何已变，预取将按新几何指纹重建（不冲刷世界地形世代）");
        }
        lastRoadGeometryFingerprint = geometry;
        if (surface != 0L) {
            lastWorldTerrainSampleFingerprint = surface;
        }
    }

    /**
     * 用户主动重算纵断面 / 预览时强制丢弃预取缓存，兜底刷子中段改地却未被探针命中的情况。
     */
    public void forceWorldTerrainResample() {
        RoadCorridorTerrainPrefetch.notifyWorldTerrainChanged();
        lastWorldTerrainSampleFingerprint = 0L;
        bumpTerrainRevision();
        LOGGER.debug("已强制失效走廊地形预取（用户重算）");
    }

    /**
     * 全量预览是否需要冲刷走廊预取：缓存未就绪，或已有地表探针且指纹已变。
     * 首次尚无指纹但缓存已就绪时信任预取（避免预取刚完成又被冲掉）。
     */
    private boolean shouldForceTerrainResampleForPreview(RoadNetwork network, World world) {
        if (network == null || world == null || networkGenerator == null) {
            return true;
        }
        if (!RoadCorridorTerrainPrefetch.isCacheReady(
                network, world, networkGenerator.getConfig(), networkGenerator.coordinateTransformer())) {
            return true;
        }
        long surface = worldSurfaceFingerprint(network);
        if (surface == 0L) {
            return true;
        }
        if (lastWorldTerrainSampleFingerprint == 0L) {
            return false;
        }
        return surface != lastWorldTerrainSampleFingerprint;
    }

    private void invalidateManualProfilesIfTerrainChanged(RoadNetwork network) {
        if (network == null || networkGenerator == null) {
            return;
        }
        syncWorldTerrainPrefetchEpoch(network);
        long fingerprint = combinedTerrainFingerprint(network);
        if (lastPreviewTerrainFingerprint != 0L && fingerprint != lastPreviewTerrainFingerprint) {
            LOGGER.debug("检测到地形/几何变更，预览将重新采样");
        }
    }

    private long combinedTerrainFingerprint(RoadNetwork network) {
        long geometry = RoadWorldTerrainProbe.geometryFingerprint(network);
        long surface = worldSurfaceFingerprint(network);
        return 31L * geometry + surface;
    }

    private long worldSurfaceFingerprint(RoadNetwork network) {
        if (network == null || networkGenerator == null) {
            return 0L;
        }
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            return 0L;
        }
        return RoadWorldTerrainProbe.surfaceFingerprint(
            network,
            networkGenerator.createTerrainSampler(world),
            networkGenerator.getConfig());
    }

    public RoadGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public Map<String, RoadGenerationResult> getLastEdgeResults() {
        return lastEdgeResults;
    }

    public RoadNetwork getLastProfileNetwork() {
        return lastProfileNetwork;
    }

    public RoadProfileChartData getRoadProfileChart(String roadId) {
        if (roadId == null || lastRoadProfiles.isEmpty()) {
            return null;
        }
        return lastRoadProfiles.get(roadId);
    }

    /**
     * 概览图表：
     * <ul>
     *   <li>Level-1 fresh → 当前地形（可叠加过期设计参考）</li>
     *   <li>完整图表与当前路网/世界一致且未标记过期 → 允许显示</li>
     *   <li>否则不回退过期 Level-1 或过期完整图</li>
     * </ul>
     * 合成结果按版本缓存，避免 ImGui 每帧重复 withTerrainGround。
     */
    public RoadProfileChartData getOverviewChart(RoadNetwork network, String roadId) {
        if (network == null || roadId == null || roadId.isBlank()) {
            return null;
        }
        Road road = network.getRoad(roadId);
        RoadTerrainProfileData terrain = getTerrainProfile(network, roadId);
        TerrainProfileKey liveKey = road != null ? currentTerrainKey(network, road) : null;
        boolean terrainFresh = liveKey != null
            && terrain != null
            && terrain.hasRenderableTerrain()
            && terrain.matches(liveKey);
        RoadProfileChartData full = getRoadProfileChart(roadId);
        boolean fullCurrent = isFullProfileChartCurrent(full);

        OverviewChartCacheEntry cached = overviewChartCache.get(roadId);
        long cacheToken = overviewCacheToken(liveKey, terrain, full, terrainFresh, fullCurrent);
        if (cached != null && cached.token() == cacheToken) {
            return cached.chart();
        }

        RoadProfileChartData chart = null;
        if (terrainFresh) {
            if (full != null && full.hasProfileData()) {
                chart = full.withTerrainGround(terrain);
            } else {
                chart = RoadProfileChartData.fromTerrain(terrain);
            }
        } else if (fullCurrent) {
            chart = full;
        }

        overviewChartCache.put(roadId, new OverviewChartCacheEntry(cacheToken, chart));
        return chart;
    }

    /**
     * 首次进入纵断面且当前道路无有效 Level-1 缓存时，自动排队一次轻量读取。
     * 同键只请求一次；路网拖动中不触发；失败不每帧重试。
     */
    public void maybeAutoRefreshTerrainProfile(RoadNetwork network, Road road) {
        if (network == null || road == null || hasPendingNetworkEdit()) {
            return;
        }
        if (isTerrainProfileJobRunning()) {
            return;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return;
        }
        TerrainProfileKey key = currentTerrainKey(network, road);
        if (key == null || terrainProfileCache.isFresh(key)) {
            return;
        }
        String token = encodeTerrainKey(key);
        if (!autoTerrainRequestTokens.add(token)) {
            return;
        }
        refreshTerrainProfile(network, road.getId(), false);
    }

    private boolean isFullProfileChartCurrent(RoadProfileChartData full) {
        return full != null
            && full.canRenderChart()
            && !previewNeedsRecalc
            && previewWorldMatchesCurrent();
    }

    private static long overviewCacheToken(
            TerrainProfileKey liveKey,
            RoadTerrainProfileData terrain,
            RoadProfileChartData full,
            boolean terrainFresh,
            boolean fullCurrent) {
        long hash = 17L;
        hash = 31L * hash + (liveKey != null ? liveKey.hashCode() : 0);
        hash = 31L * hash + (terrain != null ? System.identityHashCode(terrain) : 0);
        hash = 31L * hash + (full != null ? System.identityHashCode(full) : 0);
        hash = 31L * hash + (terrainFresh ? 1 : 0);
        hash = 31L * hash + (fullCurrent ? 1 : 0);
        return hash;
    }

    private static String encodeTerrainKey(TerrainProfileKey key) {
        return key.worldId() + '|'
            + key.roadId() + '|'
            + key.geometryFingerprint() + '|'
            + key.transformFingerprint() + '|'
            + key.terrainEpoch() + '|'
            + Double.doubleToLongBits(key.sampleSpacing());
    }

    private record OverviewChartCacheEntry(long token, RoadProfileChartData chart) {
    }

    /** 当前地形采样任务是否属于指定道路。 */
    public boolean isTerrainProfileJobForRoad(String roadId) {
        return terrainProfileJob != null
            && terrainProfileJob.isRunning()
            && roadId != null
            && roadId.equals(terrainProfileJob.key().roadId());
    }

    public RoadTerrainProfileData getTerrainProfile(RoadNetwork network, String roadId) {
        return terrainProfileCache.get(roadId);
    }

    public boolean isTerrainProfileFresh(RoadNetwork network, Road road) {
        TerrainProfileKey key = currentTerrainKey(network, road);
        return key != null && terrainProfileCache.isFresh(key);
    }

    public boolean isTerrainProfileStale(RoadNetwork network, Road road) {
        TerrainProfileKey key = currentTerrainKey(network, road);
        return key != null && terrainProfileCache.isStale(key);
    }

    public boolean isTerrainProfileJobRunning() {
        return terrainProfileJob != null && terrainProfileJob.isRunning();
    }

    public RoadTerrainProfileSamplingJob terrainProfileJob() {
        return terrainProfileJob;
    }

    public ICoordinateService hostCoordinates() {
        return host.coordinates();
    }

    /**
     * Level-1：排队分帧读取当前道路中心线地形。不改设计权威、不启动正式建造预览。
     * 自动场景可命中缓存；用户点击「更新地形」应传 {@code force=true} 强制重采。
     */
    public boolean refreshTerrainProfile(RoadNetwork network, String roadId) {
        return refreshTerrainProfile(network, roadId, false);
    }

    public boolean refreshTerrainProfile(RoadNetwork network, String roadId, boolean force) {
        if (network == null || roadId == null || roadId.isBlank()) {
            return false;
        }
        Road road = network.getRoad(roadId);
        if (road == null || !RoadStationing.isStationable(network, road)) {
            status.warning(PlotI18n.tr("plugin.road.terrain_profile.unavailable"));
            return false;
        }
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            status.error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return false;
        }
        var config = networkGenerator != null ? networkGenerator.getConfig() : null;
        TerrainProfileKey key = RoadTerrainProfileSampler.buildKey(
            currentWorldKey(), network, road, host.coordinates(), config);
        if (!force && terrainProfileCache.isFresh(key)) {
            status.success(PlotI18n.tr("plugin.road.terrain_profile.ready"));
            return true;
        }
        cancelTerrainProfileJobSilently();
        if (force) {
            terrainProfileCache.invalidate(roadId);
        }
        double spacing = RoadTerrainProfileSampler.resolveSpacing(config);
        var stations = RoadTerrainProfileSampler.buildStations(network, road, spacing);
        var points = RoadTerrainProfileSampler.resolvePoints(network, road, stations);
        double totalStation = RoadStationing.canonicalLength(network, road);
        // 发布前会用 live 路网重建 key；此处 key 仅标识任务起点身份。
        terrainProfileJob = new RoadTerrainProfileSamplingJob(
            this, key, totalStation, stations, points);
        status.progress(PlotI18n.tr("plugin.road.terrain_profile.sampling"));
        return true;
    }

    public void tickTerrainProfileJob() {
        if (terrainProfileJob != null && terrainProfileJob.isRunning()) {
            terrainProfileJob.tick();
        }
    }

    public void cancelTerrainProfileJob() {
        if (terrainProfileJob == null) {
            return;
        }
        terrainProfileJob.cancel();
        if (terrainProfileJob.isRunning()) {
            terrainProfileJob.tick();
        } else {
            terrainProfileJob = null;
        }
        status.info(PlotI18n.tr("plugin.road.terrain_profile.cancelled"));
    }

    public void cancelTerrainProfileJobSilently() {
        if (terrainProfileJob == null) {
            return;
        }
        terrainProfileJob.cancel();
        if (terrainProfileJob.isRunning()) {
            terrainProfileJob.tick();
        }
        terrainProfileJob = null;
    }

    public void updateTerrainProfileJobProgress(RoadTerrainProfileSamplingJob job) {
        if (job == null || terrainProfileJob != job) {
            return;
        }
        status.progress(PlotI18n.tr(
            "plugin.road.terrain_profile.sampling_progress",
            job.processedWorkUnits(),
            job.totalWorkUnits()));
    }

    public void completeTerrainProfileJob(RoadTerrainProfileSamplingJob job, RoadTerrainProfileData data) {
        if (terrainProfileJob != job) {
            return;
        }
        terrainProfileJob = null;
        if (data == null || !data.hasAnyValidSample()) {
            status.warning(PlotI18n.tr("plugin.road.terrain_profile.read_failed"));
            return;
        }
        // 必须用当前 live 路网/投影/世界重建 key，禁止复制任务旧几何指纹。
        TerrainProfileKey expected = currentLiveTerrainKey(data.roadId());
        if (expected == null || !data.matches(expected)) {
            status.warning(PlotI18n.tr("plugin.road.terrain_profile.rejected_stale"));
            return;
        }
        terrainProfileCache.put(data);
        bumpOverviewChartCache();
        if (!data.hasDrawableSegment()) {
            status.warning(PlotI18n.tr("plugin.road.terrain_profile.sparse"));
        } else if (!data.fullyLoaded()) {
            status.warning(PlotI18n.tr("plugin.road.terrain_profile.partial"));
        } else {
            status.success(PlotI18n.tr("plugin.road.terrain_profile.ready"));
        }
    }

    public void failTerrainProfileJob(RoadTerrainProfileSamplingJob job) {
        if (terrainProfileJob == job) {
            terrainProfileJob = null;
        }
        status.error(PlotI18n.tr("plugin.road.terrain_profile.read_failed"));
    }

    public void finishCancelledTerrainProfileJob(RoadTerrainProfileSamplingJob job) {
        if (terrainProfileJob == job) {
            terrainProfileJob = null;
        }
    }

    private TerrainProfileKey currentTerrainKey(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return null;
        }
        var config = networkGenerator != null ? networkGenerator.getConfig() : null;
        return RoadTerrainProfileSampler.buildKey(
            currentWorldKey(), network, road, host.coordinates(), config);
    }

    /** 从 live 路网重新计算当前道路的 TerrainProfileKey（任务发布验收用）。 */
    private TerrainProfileKey currentLiveTerrainKey(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return null;
        }
        RoadNetwork network = liveNetworkLookup.get();
        if (network == null) {
            return null;
        }
        Road road = network.getRoad(roadId);
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return null;
        }
        return currentTerrainKey(network, road);
    }

    public RoadNetworkGenerator.NetworkGenerationResult getLastNetworkGenerationResult() {
        return lastNetworkGenerationResult;
    }

    public RoadGenerationResult getLastEdgeResult(String edgeId) {
        if (edgeId == null || lastEdgeResults.isEmpty()) {
            return null;
        }
        return lastEdgeResults.get(edgeId);
    }

    public boolean isPreviewJobRunning() {
        return previewJob != null && previewJob.isRunning();
    }

    public RoadNetworkPreviewJob previewJob() {
        return previewJob;
    }

    /**
     * 启动分帧路网预览（UI 入口）。完成时可选自动投影虚影。
     *
     * @return 是否成功启动 job
     */
    public boolean startNetworkPreview(RoadNetwork network) {
        return startNetworkPreview(network, true);
    }

    /**
     * 轻量纵断面采样：仅更新 {@link #lastEdgeResults} 与节点标高，不生成建造预览或虚影。
     */
    public boolean calculateProfileSamplingOnly(RoadNetwork network) {
        if (network.getEdges().isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.no_edges"));
            return false;
        }
        if (hasPendingNetworkEdit()) {
            status.warning(PlotI18n.tr("plugin.road.preview_blocked_pending_edit"));
            return false;
        }

        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            LOGGER.warn("世界或生成器未就绪");
            status.error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return false;
        }

        cancelPreviewJobSilently();
        // 用户主动采样：强制丢弃预取，兜底中段改地；预取完成回调路径不强制。
        if (!suppressTerrainPrefetchReschedule) {
            forceWorldTerrainResample();
        } else {
            syncWorldTerrainPrefetchEpoch(network);
        }
        try {
            RoadNetworkGenerator.ProfileSamplingResult sampling =
                networkGenerator.calculateProfileSampling(network, world);
            if (sampling.isEmpty()) {
                status.warning(PlotI18n.tr("plugin.road.profile_sampling_empty"));
                return false;
            }
            applyProfileSamplingResult(network, sampling);
            if (!suppressTerrainPrefetchReschedule) {
                var prefetchStatus = RoadCorridorTerrainPrefetch.cacheStatus(
                    network, world, networkGenerator.getConfig(), networkGenerator.coordinateTransformer());
                if (!prefetchStatus.isUsable()) {
                    pendingTerrainPrefetchRefresh = network;
                    if (RoadCorridorTerrainPrefetch.isPrefetchPending()) {
                        status.info(PlotI18n.tr("plugin.road.terrain_prefetch_pending"));
                    }
                } else if (prefetchStatus.coverage()
                        == RoadCorridorTerrainPrefetch.Coverage.PARTIAL) {
                    status.warning(partialPrefetchMessage(
                        "plugin.road.terrain_prefetch_partial", prefetchStatus));
                }
            }
            return true;
        } catch (RuntimeException e) {
            status.error(PlotI18n.tr("plugin.road.profile_sampling_failed"));
            LOGGER.error("纵断面采样失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 每帧轮询走廊地形预取。轻量纵断面与完整预览待刷新相互独立，不会互相清除。
     */
    public void tickTerrainPrefetch() {
        RoadCorridorTerrainPrefetch.pollCompleted(null);
        if (isPreviewJobRunning() || hasPendingNetworkEdit()) {
            return;
        }
        World world = RoadNetworkGenerator.getClientWorld();
        RoadNetwork profileNetwork = pendingTerrainPrefetchRefresh;
        if (profileNetwork != null
                && world != null
                && RoadCorridorTerrainPrefetch.isCacheReady(
                    profileNetwork,
                    world,
                    networkGenerator != null ? networkGenerator.getConfig() : null,
                    networkGenerator != null ? networkGenerator.coordinateTransformer() : null)) {
            pendingTerrainPrefetchRefresh = null;
            suppressTerrainPrefetchReschedule = true;
            try {
                if (calculateProfileSamplingOnly(profileNetwork)) {
                    var coverage = RoadCorridorTerrainPrefetch.cacheStatus(
                        profileNetwork,
                        world,
                        networkGenerator.getConfig(),
                        networkGenerator.coordinateTransformer());
                    if (coverage.coverage() == RoadCorridorTerrainPrefetch.Coverage.PARTIAL) {
                        status.warning(partialPrefetchMessage(
                            "plugin.road.terrain_prefetch_partial_applied", coverage));
                    } else {
                        status.info(PlotI18n.tr("plugin.road.terrain_prefetch_applied"));
                    }
                }
            } finally {
                suppressTerrainPrefetchReschedule = false;
            }
            // 不清除 pendingFullPreviewTerrainRefresh：两种刷新状态独立
        }
        if (pendingFullPreviewTerrainRefresh
                && world != null
                && previewNetwork != null
                && RoadCorridorTerrainPrefetch.isCacheReady(
                    previewNetwork,
                    world,
                    networkGenerator != null ? networkGenerator.getConfig() : null,
                    networkGenerator != null ? networkGenerator.coordinateTransformer() : null)) {
            pendingFullPreviewTerrainRefresh = false;
            previewNeedsRecalc = true;
            bumpTerrainRevision();
            var coverage = RoadCorridorTerrainPrefetch.cacheStatus(
                previewNetwork,
                world,
                networkGenerator != null ? networkGenerator.getConfig() : null,
                networkGenerator != null ? networkGenerator.coordinateTransformer() : null);
            if (coverage.coverage() == RoadCorridorTerrainPrefetch.Coverage.PARTIAL) {
                status.warning(partialPrefetchMessage(
                    "plugin.road.terrain_prefetch_partial_preview_stale", coverage));
            } else {
                status.info(PlotI18n.tr("plugin.road.terrain_prefetch_preview_stale"));
            }
        }
    }

    private static String partialPrefetchMessage(
            String key,
            RoadCorridorTerrainPrefetch.PrefetchStatus status) {
        return PlotI18n.tr(
            key,
            status.columnCount(),
            status.targetColumnCount(),
            status.uncoveredColumnCount());
    }

    public boolean startNetworkPreview(RoadNetwork network, boolean autoProjectGhosts) {
        if (network.getEdges().isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.no_edges"));
            return false;
        }
        if (hasPendingNetworkEdit()) {
            status.warning(PlotI18n.tr("plugin.road.preview_blocked_pending_edit"));
            return false;
        }

        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            LOGGER.warn("世界或生成器未就绪");
            status.error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return false;
        }

        // 预取已就绪且走廊地表指纹未变时复用缓存，避免「预取刚完成又被强制冲刷」导致边生成冷采样卡顿。
        // 中段改地仍依赖 notifyWorldTerrainChanged / 地表探针；无法确认时继续强制重采样。
        if (shouldForceTerrainResampleForPreview(network, world)) {
            forceWorldTerrainResample();
        } else {
            syncWorldTerrainPrefetchEpoch(network);
            LOGGER.debug("走廊地形预取仍有效，跳过强制重采样");
        }
        invalidateManualProfilesIfTerrainChanged(network);

        RoadElevationBounds elevationBounds = validationElevationBounds();
        RoadNetworkValidationReport preflight =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network, elevationBounds);
        if (preflight.blocksBuild()) {
            invalidatePreview();
            status.error(PlotI18n.tr("plugin.road.preview_blocked_validation"));
            return false;
        }

        cancelPreviewJobSilently();
        previewJob = new RoadNetworkPreviewJob(network, autoProjectGhosts, networkGenerator, this);
        if (!RoadCorridorTerrainPrefetch.isCacheReady(
                network, world, networkGenerator.getConfig(), networkGenerator.coordinateTransformer())) {
            pendingFullPreviewTerrainRefresh = true;
        }
        updatePreviewJobProgress(previewJob);
        return true;
    }

    public void tickPreviewJob() {
        if (previewJob != null && previewJob.isRunning()) {
            previewJob.tick();
        }
    }

    public void cancelPreviewJob() {
        if (previewJob == null) {
            return;
        }
        previewJob.cancel();
        cancelPendingGhostProjection();
        if (previewJob.isRunning()) {
            previewJob.tick();
        } else {
            previewJob = null;
        }
        status.info(PlotI18n.tr("plugin.road.preview_cancelled"));
    }

    void updatePreviewJobProgress(RoadNetworkPreviewJob job) {
        if (job == null) {
            return;
        }
        String message = PlotI18n.tr(job.phaseTranslationKey());
        if (job.phase() == RoadNetworkPreviewJob.Phase.PROJECTING && pendingGhostUploadTotal > 0) {
            message += " — " + PlotI18n.tr(
                "plugin.road.preview_ghost_uploading",
                pendingGhostUploadIndex,
                pendingGhostUploadTotal);
        } else if (job.totalWorkUnits() > 0) {
            message += " — " + PlotI18n.tr(
                "plugin.road.preview_progress_detail",
                job.processedWorkUnits(),
                job.totalWorkUnits());
        }
        status.progress(message);
    }

    void completePreviewJob(
            RoadNetworkPreviewJob job,
            RoadNetworkGenerator.PreviewResult previewResult,
            boolean autoProjectGhosts) {
        if (previewJob != job) {
            return;
        }
        if (hasPendingNetworkEdit()) {
            // 拖动等未提交编辑期间完成的旧作业不得标为当前可建造预览。
            previewJob = null;
            cancelPendingGhostProjection();
            lastGenerationResult = null;
            previewNetwork = null;
            lastNetworkGenerationResult = null;
            previewNeedsRecalc = true;
            clearGhostBlocksSafely();
            job.markFailed();
            status.warning(PlotI18n.tr("plugin.road.preview_blocked_pending_edit"));
            return;
        }
        // Keep job alive while ghost batches upload so progress UI stays visible.
        applyPreviewResult(job.sourceNetwork(), previewResult, autoProjectGhosts, true);
        if (!autoProjectGhosts || !hasPendingGhostProjection()) {
            previewJob = null;
        }
    }

    void finishPreviewJobProjection(RoadNetworkPreviewJob job) {
        if (previewJob == job) {
            previewJob = null;
        }
        applyPreviewReadyStatus();
    }

    void failPreviewJob(RoadNetworkPreviewJob job) {
        failPreviewJob(job, null);
    }

    void failPreviewJob(RoadNetworkPreviewJob job, RuntimeException error) {
        cancelPendingGhostProjection();
        if (previewJob == job) {
            previewJob = null;
        }
        lastGenerationResult = null;
        previewNetwork = null;
        clearProfileSamplingState();
        lastNetworkGenerationResult = null;
        status.error(PlotI18n.tr("plugin.road.generate_preview_failed"));
        if (error != null) {
            LOGGER.error("计算路网预览失败: {}", error.getMessage(), error);
        }
    }

    void finishCancelledPreviewJob(RoadNetworkPreviewJob job) {
        cancelPendingGhostProjection();
        if (previewJob == job) {
            previewJob = null;
        }
    }

    /** 静默取消预览 job（切 Tab / 失效预览时使用，不写入状态栏）。 */
    public void cancelPreviewJobSilently() {
        if (previewJob == null && pendingGhostUpload == null) {
            return;
        }
        if (previewJob != null) {
            previewJob.cancel();
            if (previewJob.isRunning()) {
                previewJob.tick();
            } else {
                previewJob = null;
            }
        }
        cancelPendingGhostProjection();
        previewJob = null;
    }

    /**
     * 同步计算路网预览（基准 / 测试 / 无 tick 循环时使用）。
     */
    public boolean calculateNetworkPreview(RoadNetwork network) {
        return calculateNetworkPreview(network, true);
    }

    public boolean calculateNetworkPreview(RoadNetwork network, boolean autoProjectGhosts) {
        if (network.getEdges().isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.no_edges"));
            return false;
        }
        if (hasPendingNetworkEdit()) {
            status.warning(PlotI18n.tr("plugin.road.preview_blocked_pending_edit"));
            return false;
        }

        invalidateManualProfilesIfTerrainChanged(network);

        RoadElevationBounds elevationBounds = validationElevationBounds();
        RoadNetworkValidationReport preflight =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network, elevationBounds);
        if (preflight.blocksBuild()) {
            invalidatePreview();
            status.error(PlotI18n.tr("plugin.road.preview_blocked_validation"));
            return false;
        }

        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            LOGGER.warn("世界或生成器未就绪");
            status.error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return false;
        }

        try {
            RoadNetworkGenerator.PreviewResult previewResult = networkGenerator.generatePreview(network, world);
            applyPreviewResult(network, previewResult, autoProjectGhosts);
            return lastGenerationResult != null && !lastGenerationResult.placementRecords.isEmpty();
        } catch (RuntimeException e) {
            lastGenerationResult = null;
            previewNetwork = null;
            clearProfileSamplingState();
            lastNetworkGenerationResult = null;
            status.error(PlotI18n.tr("plugin.road.generate_preview_failed"));
            LOGGER.error("计算路网预览失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private void applyProfileSamplingResult(
            RoadNetwork network,
            RoadNetworkGenerator.ProfileSamplingResult sampling) {
        previewNetwork = network;
        applyProfileSamplingState(sampling);
        bumpTerrainRevision();
        status.success(PlotI18n.tr(
            "plugin.road.profile_sampling_ready",
            sampling.edgeResults().size()));
    }

    private void applyPreviewResult(
            RoadNetwork network,
            RoadNetworkGenerator.PreviewResult previewResult,
            boolean autoProjectGhosts) {
        applyPreviewResult(network, previewResult, autoProjectGhosts, false);
    }

    private void applyPreviewResult(
            RoadNetwork network,
            RoadNetworkGenerator.PreviewResult previewResult,
            boolean autoProjectGhosts,
            boolean incrementalGhosts) {
        lastGenerationResult = previewResult.aggregate();
        previewNetwork = network;
        applyProfileSamplingState(previewResult);
        lastNetworkGenerationResult = previewResult.networkResult();
        lastPreviewTerrainFingerprint = combinedTerrainFingerprint(network);
        previewWorldKey = currentWorldKey();
        bumpTerrainRevision();

        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.generate_empty_result"));
            LOGGER.warn("路网预览未产生可投影方块");
            return;
        }

        if (lastGenerationResult.droppedSolidCount > 0) {
            LOGGER.error(
                "路网预览不完整：因实体上限丢弃 {} 个图元",
                lastGenerationResult.droppedSolidCount);
        }

        LOGGER.info("路网预览: 挖{} 填{} 路灯{} 丢弃{}",
            lastGenerationResult.cutVolume, lastGenerationResult.fillVolume,
            lastGenerationResult.streetlightCount,
            lastGenerationResult.droppedSolidCount);
        // Explicit full preview is authoritative. startNetworkPreview force-resamples
        // terrain and may arm pendingFullPreviewTerrainRefresh; if prefetch finishes
        // afterwards, do not immediately flip this successful preview back to stale.
        previewNeedsRecalc = false;
        pendingFullPreviewTerrainRefresh = false;
        if (!autoProjectGhosts) {
            applyPreviewReadyStatus();
            return;
        }
        if (incrementalGhosts) {
            beginGhostProjection(lastGenerationResult);
            if (!hasPendingGhostProjection()) {
                applyPreviewReadyStatus();
            } else {
                status.progress(PlotI18n.tr(
                    "plugin.road.preview_ghost_uploading",
                    0,
                    pendingGhostUploadTotal));
            }
            return;
        }
        projectRoadPreview();
        applyPreviewReadyStatus();
    }

    boolean hasPendingGhostProjection() {
        return pendingGhostUpload != null;
    }

    int ghostProjectionProcessed() {
        return pendingGhostUploadIndex;
    }

    int ghostProjectionTotal() {
        return pendingGhostUploadTotal;
    }

    /**
     * @return true if more ghost batches remain
     */
    boolean tickGhostProjection() {
        if (pendingGhostUpload == null) {
            return false;
        }
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            cancelPendingGhostProjection();
            return false;
        }
        if (pendingGhostClear) {
            ghostBlockManager.clearGhostBlocks(GhostBlockOwners.ROAD);
            pendingGhostClear = false;
            // 清完后本帧让出，避免与大批量上传叠在同一帧。
            return true;
        }
        long started = System.nanoTime();
        int uploaded = 0;
        while (pendingGhostUploadIndex < pendingGhostUpload.size()
                && uploaded < GHOST_BLOCKS_PER_TICK
                && (uploaded == 0 || System.nanoTime() - started < GHOST_UPLOAD_BUDGET_NANOS)) {
            int end = Math.min(
                pendingGhostUploadIndex + Math.min(512, GHOST_BLOCKS_PER_TICK - uploaded),
                pendingGhostUpload.size());
            Map<net.minecraft.util.math.BlockPos, String> batch =
                new LinkedHashMap<>(end - pendingGhostUploadIndex);
            for (int i = pendingGhostUploadIndex; i < end; i++) {
                Map.Entry<net.minecraft.util.math.BlockPos, String> entry = pendingGhostUpload.get(i);
                batch.put(entry.getKey(), entry.getValue());
            }
            ghostBlockManager.addGhostBlocks(GhostBlockOwners.ROAD, batch);
            uploaded += end - pendingGhostUploadIndex;
            pendingGhostUploadIndex = end;
        }
        if (pendingGhostUploadIndex >= pendingGhostUpload.size()) {
            pendingGhostUpload = null;
            pendingGhostUploadIndex = 0;
            pendingGhostUploadTotal = 0;
            return false;
        }
        return true;
    }

    private void beginGhostProjection(RoadGenerationResult result) {
        cancelPendingGhostProjection();
        if (result == null || result.placementRecords.isEmpty()) {
            clearGhostBlocksSafely();
            return;
        }
        List<Map.Entry<net.minecraft.util.math.BlockPos, String>> entries =
            new ArrayList<>(result.placementRecords.size());
        for (BlockRecord record : result.placementRecords.values()) {
            if (record != null && record.pos != null && record.newBlockId != null) {
                entries.add(Map.entry(record.pos, record.newBlockId));
            }
        }
        if (entries.isEmpty()) {
            clearGhostBlocksSafely();
            return;
        }
        pendingGhostUpload = entries;
        pendingGhostUploadIndex = 0;
        pendingGhostUploadTotal = entries.size();
        pendingGhostClear = true;
    }

    private void cancelPendingGhostProjection() {
        pendingGhostUpload = null;
        pendingGhostUploadIndex = 0;
        pendingGhostUploadTotal = 0;
        pendingGhostClear = false;
    }

    private void applyPreviewReadyStatus() {
        StringBuilder message = new StringBuilder(PlotI18n.tr("plugin.road.generate_preview_ready"));
        RoadStatus.Severity severity = RoadStatus.Severity.SUCCESS;
        if (lastNetworkGenerationResult != null && lastNetworkGenerationResult.hasPartialFailure()) {
            message.append(" — ").append(PlotI18n.tr(
                "plugin.road.generate_partial_failure",
                lastNetworkGenerationResult.successEdgeCount(),
                lastNetworkGenerationResult.totalEdgeCount(),
                lastNetworkGenerationResult.getFailedEdgeIds().size(),
                lastNetworkGenerationResult.getSkippedJunctionIds().size()));
            List<String> details = lastNetworkGenerationResult.partialFailureDetails();
            if (!details.isEmpty()) {
                LOGGER.warn("路网预览部分失败明细: {}", String.join(" | ", details));
                String first = details.getFirst();
                if (first.length() > 160) {
                    first = first.substring(0, 157) + "...";
                }
                message.append(" — ").append(first);
            }
            severity = RoadStatus.Severity.WARNING;
        }
        if (lastGenerationResult != null && lastGenerationResult.droppedSolidCount > 0) {
            message.append(" — ").append(PlotI18n.tr(
                "plugin.road.generate_dropped_solids",
                lastGenerationResult.droppedSolidCount));
            severity = RoadStatus.Severity.WARNING;
        }
        RoadPlacementVisibility.Analysis visibility = analyzeRoadVisibility();
        if (visibility != null && visibility.requiresWarning()) {
            message.append(" — ").append(RoadPlacementVisibility.formatWarningMessage(visibility));
            severity = RoadStatus.Severity.WARNING;
        }
        status.set(severity, message.toString());
    }

    public RoadPlacementVisibility.Analysis analyzeRoadVisibility() {
        if (lastGenerationResult == null) {
            return null;
        }
        return RoadPlacementVisibility.analyze(lastGenerationResult, host.coordinates());
    }

    public String formatVisibilityWarning() {
        return RoadPlacementVisibility.formatWarningMessage(analyzeRoadVisibility());
    }

    public void projectRoadPreview() {
        if (lastGenerationResult == null) {
            return;
        }
        // Manual/sync projection replaces immediately; incremental job uses beginGhostProjection.
        cancelPendingGhostProjection();
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            return;
        }

        java.util.LinkedHashMap<net.minecraft.util.math.BlockPos, String> ghosts =
            new java.util.LinkedHashMap<>(lastGenerationResult.placementRecords.size());
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            ghosts.put(record.pos, record.newBlockId);
        }
        ghostBlockManager.replaceGhostBlocks(GhostBlockOwners.ROAD, ghosts);
    }

    public void clearPreview() {
        cancelPreviewJobSilently();
        cancelPendingGhostProjection();
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearGhostBlocks(GhostBlockOwners.ROAD);
        }
        clearProfileSamplingState();
        lastNetworkGenerationResult = null;
        lastGenerationResult = null;
        previewNetwork = null;
        previewNeedsRecalc = false;
        // 正式建造预览清除后仍可保留有效 Level-1 地形缓存。
    }

    /** 世界切换、插件卸载或路网重载时失效 Level-1 地形缓存。 */
    public void clearTerrainProfileCache() {
        cancelTerrainProfileJobSilently();
        terrainProfileCache.clear();
        autoTerrainRequestTokens.clear();
        bumpOverviewChartCache();
    }

    /**
     * 检查建造预览是否有效（含可投影方块）。
     */
    public boolean hasValidPreview() {
        return lastGenerationResult != null && !lastGenerationResult.placementRecords.isEmpty();
    }

    /** 当前预览可用于投影/建造：有效、未过期、世界一致、不在计算中、无未提交路网编辑。 */
    public boolean isBuildPreviewCurrent() {
        return hasValidPreview()
            && !previewNeedsRecalc
            && !isPreviewJobRunning()
            && previewWorldMatchesCurrent()
            && !hasPendingNetworkEdit();
    }

    private boolean previewWorldMatchesCurrent() {
        return previewWorldKey.isEmpty() || previewWorldKey.equals(currentWorldKey());
    }

    private String currentWorldKey() {
        String key = worldKeyLookup.get();
        return key != null ? key : "";
    }

    private static String detectCurrentWorldKey() {
        net.minecraft.world.World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            return "";
        }
        return world.getRegistryKey().getValue().toString();
    }

    /**
     * 按路网变更类型失效预览：纵断面编辑仅标记建造预览过期，保留地形采样。
     */
    public void handleNetworkChanged(RoadChangeKind kind) {
        if (kind != null && kind.preservesProfileSampling()) {
            markBuildPreviewStalePreservingProfile();
            return;
        }
        invalidatePreview();
    }

    /**
     * 使建造预览过期，但保留 {@link #lastEdgeResults} 供纵断面编辑器继续使用。
     */
    public void markBuildPreviewStalePreservingProfile() {
        cancelPreviewJobSilently();
        boolean hadBuildPreview = lastGenerationResult != null;
        lastGenerationResult = null;
        lastNetworkGenerationResult = null;
        previewNetwork = null;
        clearGhostBlocksSafely();
        if (hadBuildPreview || hasProfileSampling()) {
            previewNeedsRecalc = true;
            bumpOverviewChartCache();
            LOGGER.debug("纵断面/交叉口编辑：建造预览已标记过期，保留地形纵断面采样");
        }
    }

    /**
     * 使预览完全失效（在网络结构/地形相关变更时调用）。
     * 同时清除虚影，避免界面上仍显示与当前路网不一致的投影。
     */
    public void invalidatePreview() {
        cancelPreviewJobSilently();
        boolean hadPreview = lastGenerationResult != null || !lastEdgeResults.isEmpty();
        clearProfileSamplingState();
        lastNetworkGenerationResult = null;
        lastGenerationResult = null;
        previewNetwork = null;
        clearGhostBlocksSafely();
        lastPreviewTerrainFingerprint = 0L;
        previewWorldKey = "";
        pendingTerrainPrefetchRefresh = null;
        pendingFullPreviewTerrainRefresh = false;
        if (hadPreview) {
            previewNeedsRecalc = true;
            LOGGER.debug("网络已变更，预览结果与虚影已失效");
            status.info(PlotI18n.tr("plugin.road.preview_invalidated"));
        }
        bumpTerrainRevision();
    }

    public void buildRoadInWorld() {
        if (hasPendingNetworkEdit()) {
            status.error(PlotI18n.tr("plugin.road.build_blocked_pending_edit"));
            return;
        }
        if (!isBuildPreviewCurrent()) {
            status.error(PlotI18n.tr("plugin.road.build_blocked_stale_preview"));
            return;
        }
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.build_no_blocks"));
            return;
        }
        if (lastNetworkGenerationResult != null && lastNetworkGenerationResult.hasPartialFailure()) {
            status.error(PlotI18n.tr("plugin.road.build_blocked_partial_generation"));
            return;
        }
        if (previewNetwork != null
                && RoadNetworkEngineeringValidator.analyzePreGeneration(
                    previewNetwork, validationElevationBounds()).blocksBuild()) {
            invalidatePreview();
            status.error(PlotI18n.tr("plugin.road.build_blocked_validation"));
            return;
        }

        com.plot.api.world.PlacementReadiness readiness =
            host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            status.error(readiness.message());
            return;
        }

        if (host.placement().isBusy()) {
            status.warning(PlotI18n.tr("plugin.road.build_in_progress_wait"));
            return;
        }
        if (!previewWorldMatchesCurrent()) {
            invalidatePreview();
            status.error(PlotI18n.tr("plugin.road.build_blocked_world_changed"));
            return;
        }

        RoadPlacementVisibility.Analysis visibility = analyzeRoadVisibility();
        if (visibility != null && visibility.requiresWarning()) {
            LOGGER.warn("道路落地范围超出当前摄像机视野: {}", visibility.status());
        }

        List<BlockRecord> records =
            new ArrayList<>(lastGenerationResult.placementRecords.values());
        GenerateRoadCommand command = new GenerateRoadCommand(records, host.projection(), host.placement());
        status.progress(PlotI18n.tr("plugin.road.build_in_progress", records.size()));
        command.executeScheduled(() -> {
            GenerateRoadCommand.ExecutionResult result = command.getLastExecutionResult();
            if (result != null && result.cancelled()) {
                if (command.hasAppliedRecords()) {
                    host.commands().pushExecuted(command);
                    status.warning(PlotI18n.tr(
                        "plugin.road.build_cancelled_undoable",
                        command.getAppliedRecordCount(),
                        result.total()));
                } else {
                    status.warning(PlotI18n.tr("plugin.road.build_cancelled", result.success(), result.total()));
                }
                clearPreview();
                return;
            }
            if (command.hasAppliedRecords()) {
                host.commands().pushExecuted(command);
                RoadCorridorTerrainPrefetch.notifyWorldTerrainChanged();
            }
            applyBuildResultStatus(result);
            clearPreview();
        });
    }

    private void applyBuildResultStatus(GenerateRoadCommand.ExecutionResult result) {
        if (result == null || result.total() == 0) {
            status.warning(PlotI18n.tr("plugin.road.build_no_blocks"));
            return;
        }
        if (result.cancelled()) {
            if (result.success() > 0) {
                status.warning(PlotI18n.tr(
                    "plugin.road.build_cancelled_undoable", result.success(), result.total()));
            } else {
                status.warning(PlotI18n.tr("plugin.road.build_cancelled", result.success(), result.total()));
            }
            return;
        }
        if (result.isFullSuccess()) {
            status.success(PlotI18n.tr("plugin.road.build_success", result.success()));
            return;
        }
        if (result.isTotalFailure()) {
            status.error(PlotI18n.tr("plugin.road.build_failed", result.total()));
            return;
        }
        status.warning(PlotI18n.tr(
            "plugin.road.build_partial_undoable",
            result.success(),
            result.total(),
            result.failed()));
    }

    private void applyProfileSamplingState(RoadNetworkGenerator.ProfileSamplingResult sampling) {
        lastProfileNetwork = sampling.profileNetwork();
        lastEdgeResults = new LinkedHashMap<>(sampling.edgeResults());
        lastNodeElevations = new LinkedHashMap<>(sampling.nodeElevations());
        lastRoadProfiles = new LinkedHashMap<>(sampling.roadProfiles());
        bumpOverviewChartCache();
    }

    private void applyProfileSamplingState(RoadNetworkGenerator.PreviewResult previewResult) {
        lastProfileNetwork = previewResult.profileNetwork();
        lastEdgeResults = new LinkedHashMap<>(previewResult.edgeResults());
        lastNodeElevations = new LinkedHashMap<>(previewResult.nodeElevations());
        lastRoadProfiles = new LinkedHashMap<>(previewResult.roadProfiles());
        bumpOverviewChartCache();
    }

    private RoadElevationBounds validationElevationBounds() {
        return RoadElevationBoundsResolver.resolveClientWorld(host.coordinates());
    }

    private void clearProfileSamplingState() {
        lastProfileNetwork = null;
        lastEdgeResults = Collections.emptyMap();
        lastNodeElevations = Collections.emptyMap();
        lastRoadProfiles = Collections.emptyMap();
        bumpOverviewChartCache();
        // Level-1 地形缓存按几何指纹惰性失效，不随建造预览清空而抹掉。
    }

    private void clearGhostBlocksSafely() {
        try {
            com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
            if (ghostBlockManager != null) {
                ghostBlockManager.clearGhostBlocks(GhostBlockOwners.ROAD);
            }
        } catch (IllegalStateException ignored) {
            // 逻辑侧 PluginContext 无世界服务时跳过虚影清理
        }
    }
}
