package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.pipeline.profile.NodeGroundHeightResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 纵断面 / 预览用的道路走廊地形预取。
 * <p>
 * 按纵断面实际采样站与横断面偏移收集列；在集成服上分批加载区块并采样地面+裸露水面；
 * 结果按几何+地形世代缓存。不调整客户端视距。
 */
public final class RoadCorridorTerrainPrefetch {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadTerrainPrefetch");

    static final int MAX_COLUMNS = 24_000;
    private static final int CHUNKS_PER_SERVER_BATCH = 8;
    private static final int COLUMNS_PER_SERVER_BATCH = 256;
    /** 单批服务端预算，避免大型路网卡死服务端 tick。 */
    private static final long SERVER_BATCH_BUDGET_NS = TimeUnit.MILLISECONDS.toNanos(4);

    private static final Object LOCK = new Object();
    private static final AtomicLong WORLD_TERRAIN_EPOCH = new AtomicLong(1L);

    private static CacheEntry cache;
    private static InflightJob inflight;

    public enum Coverage {
        /** 全部目标列已纳入快照。 */
        COMPLETE,
        /** 因列上限等原因只覆盖部分路网。 */
        PARTIAL,
        /** 无可用列。 */
        EMPTY
    }

    public record PrefetchStatus(Coverage coverage, int columnCount, boolean truncated) {
        public static PrefetchStatus empty() {
            return new PrefetchStatus(Coverage.EMPTY, 0, false);
        }

        public boolean isUsable() {
            return coverage == Coverage.COMPLETE || coverage == Coverage.PARTIAL;
        }
    }

    private RoadCorridorTerrainPrefetch() {
    }

    /** 世界方块 / 推土等地形变更时调用，使全部预取缓存失效。 */
    public static void notifyWorldTerrainChanged() {
        WORLD_TERRAIN_EPOCH.incrementAndGet();
        synchronized (LOCK) {
            cache = null;
            if (inflight != null) {
                inflight.cancelled = true;
            }
            inflight = null;
        }
        LOGGER.debug("走廊地形预取已因世界地形变更失效 (epoch={})", WORLD_TERRAIN_EPOCH.get());
    }

    /** 当前世界地形世代（纳入缓存指纹）。 */
    public static long worldTerrainEpoch() {
        return WORLD_TERRAIN_EPOCH.get();
    }

    /**
     * 非阻塞解析采样器：缓存命中则包装返回；否则启动后台预取并立即返回客户端采样器。
     */
    public static TerrainSampler resolve(
            RoadNetwork network,
            World clientWorld,
            ICoordinateService coordinates,
            RoadSystemConfig config,
            RoadGeometrySampler.CanvasUnitsPerBlockEstimator unitsEstimator,
            TerrainSampler clientSampler) {
        if (network == null || clientWorld == null || clientSampler == null) {
            return clientSampler;
        }
        if (config == null || unitsEstimator == null || coordinates == null) {
            return clientSampler;
        }
        String key = fingerprint(network, clientWorld, config, coordinates);
        CacheEntry hit = takeReadyEntry(key);
        if (hit == null) {
            synchronized (LOCK) {
                ensureInflightLocked(
                    key, network, clientWorld, coordinates, config, unitsEstimator);
            }
            return clientSampler;
        }
        return hit.builder.build(clientSampler, coordinates);
    }

    /**
     * 兼容旧入口：缺少 config/estimator 时无法按纵断面列预取，直接返回客户端采样器。
     */
    public static TerrainSampler prepareForNetwork(
            RoadNetwork network,
            World clientWorld,
            ICoordinateService coordinates,
            TerrainSampler clientSampler) {
        return clientSampler;
    }

    /** 当前网络是否已有可用预取缓存（含部分覆盖）。 */
    public static boolean isCacheReady(RoadNetwork network, World world) {
        return isCacheReady(network, world, null, null);
    }

    public static boolean isCacheReady(
            RoadNetwork network,
            World world,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        PrefetchStatus status = cacheStatus(network, world, config, coordinates);
        return status.isUsable();
    }

    public static PrefetchStatus cacheStatus(
            RoadNetwork network,
            World world,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        if (network == null || world == null) {
            return PrefetchStatus.empty();
        }
        String key = fingerprint(network, world, config, coordinates);
        CacheEntry hit = takeReadyEntry(key);
        if (hit == null) {
            return PrefetchStatus.empty();
        }
        return hit.status;
    }

    /** 是否有尚未完成的预取任务。 */
    public static boolean isPrefetchPending() {
        synchronized (LOCK) {
            return inflight != null && !inflight.completed;
        }
    }

    /**
     * 客户端每帧轮询：若预取刚完成则调用 {@code onReady}（调用方应已在客户端线程）。
     *
     * @return 是否刚完成一次可用预取
     */
    public static boolean pollCompleted(Runnable onReady) {
        CacheEntry promoted;
        synchronized (LOCK) {
            if (inflight == null || !inflight.completed) {
                return false;
            }
            InflightJob done = inflight;
            inflight = null;
            if (done.builder == null || done.builder.isEmpty()) {
                return false;
            }
            PrefetchStatus status = new PrefetchStatus(
                done.truncated ? Coverage.PARTIAL : Coverage.COMPLETE,
                done.builder.knownCount(),
                done.truncated);
            cache = new CacheEntry(done.key, done.builder, status);
            promoted = cache;
        }
        if (onReady != null) {
            onReady.run();
        }
        return promoted != null && promoted.status.isUsable();
    }

    /** 测试 / 关闭时清空缓存与进行中任务。 */
    public static void clearForTests() {
        synchronized (LOCK) {
            cache = null;
            if (inflight != null) {
                inflight.cancelled = true;
            }
            inflight = null;
        }
    }

    static ColumnCollectResult collectProfileSampleColumns(
            RoadNetwork network,
            ICoordinateService coordinates,
            RoadSystemConfig config,
            RoadGeometrySampler.CanvasUnitsPerBlockEstimator unitsEstimator) {
        Set<Long> seen = new LinkedHashSet<>();
        List<BlockPos> columns = new ArrayList<>();
        if (network == null || config == null) {
            return ColumnCollectResult.empty();
        }
        RoadGeometrySampler.CanvasUnitsPerBlockEstimator estimator = unitsEstimator != null
            ? unitsEstimator
            : (path, segs) -> 1.0;
        double pathSampleDistance = Math.max(0.1, config.getPathSampleDistance());
        NodeGroundHeightResolver nodeResolver = new NodeGroundHeightResolver(config);
        boolean truncated = false;

        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        edges.sort(Comparator.comparing(e -> e != null && e.getId() != null ? e.getId() : ""));
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            List<Vec2d> pathPoints = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
            if (pathPoints.size() < 2) {
                continue;
            }
            List<PathSegment> segments = RoadGeometrySampler.sample(
                pathPoints, pathSampleDistance, estimator);
            if (segments.isEmpty()) {
                continue;
            }
            double halfWidth = RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.getEffectiveWidth(network, edge, config));
            for (int i = 0; i < segments.size(); i++) {
                PathSegment segment = segments.get(i);
                Vec2d tangent = segment.end.subtract(segment.start);
                appendStationColumns(segment.start, tangent, halfWidth, coordinates, seen, columns);
                if (columns.size() >= MAX_COLUMNS) {
                    truncated = true;
                    return new ColumnCollectResult(columns, true);
                }
                if (i == segments.size() - 1) {
                    appendStationColumns(segment.end, tangent, halfWidth, coordinates, seen, columns);
                    if (columns.size() >= MAX_COLUMNS) {
                        truncated = true;
                        return new ColumnCollectResult(columns, true);
                    }
                }
            }
        }

        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getPosition() == null) {
                continue;
            }
            Vec2d tangent = nodeResolver.nodeTangent(node, network);
            double halfWidth = nodeResolver.nodeHalfWidth(node, network);
            appendStationColumns(node.getPosition(), tangent, halfWidth, coordinates, seen, columns);
            if (columns.size() >= MAX_COLUMNS) {
                truncated = true;
                break;
            }
        }
        return new ColumnCollectResult(columns, truncated);
    }

    /** 与 {@link TerrainSampler} 横断面偏移一致：中心 + ±halfWidth。 */
    static List<Integer> crossSectionSampleOffsets(double halfWidth) {
        if (halfWidth <= 0) {
            return List.of(0);
        }
        LinkedHashSet<Integer> offsets = new LinkedHashSet<>();
        offsets.add(0);
        offsets.add((int) Math.round(-halfWidth));
        offsets.add((int) Math.round(halfWidth));
        return List.copyOf(offsets);
    }

    /**
     * 缓存身份：完整中心线几何、有效半宽、采样间距、坐标变换探针、世界地形世代。
     */
    static String fingerprint(
            RoadNetwork network,
            World world,
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        String worldKey = "";
        if (world != null && world.getRegistryKey() != null) {
            worldKey = world.getRegistryKey().getValue().toString();
        }
        long hash = 17L;
        hash = 31L * hash + worldKey.hashCode();
        hash = 31L * hash + WORLD_TERRAIN_EPOCH.get();
        if (config != null) {
            hash = 31L * hash + Double.hashCode(config.getPathSampleDistance());
            hash = 31L * hash + config.getRoadWidth();
        }
        hash = 31L * hash + coordinateProbeHash(coordinates);
        if (network != null) {
            hash = 31L * hash + network.getEdges().size();
            hash = 31L * hash + network.getNodes().size();
            List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
            edges.sort(Comparator.comparing(e -> e != null && e.getId() != null ? e.getId() : ""));
            for (RoadEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                hash = 31L * hash + edge.getId().hashCode();
                if (config != null) {
                    hash = 31L * hash + RoadModelUtils.getEffectiveWidth(network, edge, config);
                }
                hash = mixCenterline(hash, RoadPlanGeometry.resolveEdgeCenterline(network, edge));
                hash = mixCenterline(hash, edge.getCenterlinePoints());
            }
            List<RoadNode> nodes = new ArrayList<>(network.getNodes().values());
            nodes.sort(Comparator.comparing(n -> n != null && n.getId() != null ? n.getId() : ""));
            for (RoadNode node : nodes) {
                if (node == null || node.getPosition() == null) {
                    continue;
                }
                hash = 31L * hash + node.getId().hashCode();
                hash = 31L * hash + Double.hashCode(node.getPosition().x);
                hash = 31L * hash + Double.hashCode(node.getPosition().y);
            }
        }
        return worldKey + "#" + Long.toHexString(hash);
    }

    /** 兼容测试：无 config/坐标时仍可算几何指纹。 */
    static String fingerprint(RoadNetwork network, World world) {
        return fingerprint(network, world, null, null);
    }

    private static long mixCenterline(long hash, List<Vec2d> points) {
        if (points == null) {
            return hash;
        }
        hash = 31L * hash + points.size();
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            hash = 31L * hash + Double.hashCode(point.x);
            hash = 31L * hash + Double.hashCode(point.y);
        }
        return hash;
    }

    private static long coordinateProbeHash(ICoordinateService coordinates) {
        if (coordinates == null) {
            return 0L;
        }
        long hash = 17L;
        for (Vec2d probe : List.of(new Vec2d(0, 0), new Vec2d(100, 0), new Vec2d(0, 100))) {
            try {
                Vec2d world = coordinates.canvasToMinecraftWorld(probe);
                if (world != null) {
                    hash = 31L * hash + Double.hashCode(world.x);
                    hash = 31L * hash + Double.hashCode(world.y);
                }
            } catch (RuntimeException ignored) {
                hash = 31L * hash + 1L;
            }
        }
        return hash;
    }

    private static CacheEntry takeReadyEntry(String key) {
        synchronized (LOCK) {
            if (cache != null && key.equals(cache.key) && cache.builder != null && !cache.builder.isEmpty()) {
                return cache;
            }
            if (inflight != null
                    && inflight.completed
                    && key.equals(inflight.key)
                    && inflight.builder != null
                    && !inflight.builder.isEmpty()) {
                PrefetchStatus status = new PrefetchStatus(
                    inflight.truncated ? Coverage.PARTIAL : Coverage.COMPLETE,
                    inflight.builder.knownCount(),
                    inflight.truncated);
                cache = new CacheEntry(inflight.key, inflight.builder, status);
                CacheEntry entry = cache;
                inflight = null;
                return entry;
            }
            return null;
        }
    }

    private static void appendStationColumns(
            Vec2d center,
            Vec2d tangent,
            double halfWidth,
            ICoordinateService coordinates,
            Set<Long> seen,
            List<BlockPos> columns) {
        if (center == null) {
            return;
        }
        Vec2d normal = leftUnit(tangent);
        for (int offset : crossSectionSampleOffsets(halfWidth)) {
            Vec2d plan = offset == 0 ? center : center.add(normal.multiply(offset));
            BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(plan, coordinates);
            long key = PrefetchedGroundTerrainSampler.packColumn(column.getX(), column.getZ());
            if (seen.add(key)) {
                columns.add(column);
            }
        }
    }

    private static Vec2d leftUnit(Vec2d direction) {
        if (direction == null || direction.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = direction.normalize();
        return new Vec2d(-unit.y, unit.x);
    }

    private static void ensureInflightLocked(
            String key,
            RoadNetwork network,
            World clientWorld,
            ICoordinateService coordinates,
            RoadSystemConfig config,
            RoadGeometrySampler.CanvasUnitsPerBlockEstimator unitsEstimator) {
        if (inflight != null && !inflight.completed && key.equals(inflight.key)) {
            return;
        }
        if (inflight != null && !inflight.completed) {
            inflight.cancelled = true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client != null ? client.getServer() : null;
        if (server == null || client.world == null) {
            LOGGER.debug("无集成服，跳过走廊地形预取");
            return;
        }

        ColumnCollectResult collected = collectProfileSampleColumns(
            network, coordinates, config, unitsEstimator);
        if (collected.columns().isEmpty()) {
            return;
        }

        RegistryKey<World> worldKey = client.world.getRegistryKey();
        InflightJob job = new InflightJob(key, collected.columns(), collected.truncated());
        inflight = job;
        server.execute(() -> runBatch(server, worldKey, job, 0, 0));
        LOGGER.debug(
            "已启动走廊地形预取: key={}, columns={}, truncated={}",
            key,
            collected.columns().size(),
            collected.truncated());
    }

    private static void runBatch(
            MinecraftServer server,
            RegistryKey<World> worldKey,
            InflightJob job,
            int chunkIndex,
            int columnIndex) {
        if (job.cancelled) {
            job.completed = true;
            return;
        }
        ServerWorld serverWorld = server.getWorld(worldKey);
        if (serverWorld == null) {
            job.completed = true;
            return;
        }

        long deadline = System.nanoTime() + SERVER_BATCH_BUDGET_NS;
        List<Long> chunkKeys = job.chunkKeys;
        int chunksLoaded = 0;
        while (chunkIndex < chunkKeys.size()
                && chunksLoaded < CHUNKS_PER_SERVER_BATCH
                && System.nanoTime() < deadline) {
            long chunkKey = chunkKeys.get(chunkIndex++);
            serverWorld.getChunk(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
            chunksLoaded++;
        }
        if (chunkIndex < chunkKeys.size()) {
            int nextChunk = chunkIndex;
            int nextColumn = columnIndex;
            server.execute(() -> runBatch(server, worldKey, job, nextChunk, nextColumn));
            return;
        }

        EngineeringTerrainService terrain = EngineeringTerrainService.of(serverWorld);
        PrefetchedGroundTerrainSampler.Builder builder = job.builder;
        int sampled = 0;
        List<BlockPos> columns = job.columns;
        while (columnIndex < columns.size()
                && sampled < COLUMNS_PER_SERVER_BATCH
                && System.nanoTime() < deadline) {
            if (job.cancelled) {
                job.completed = true;
                return;
            }
            BlockPos column = columns.get(columnIndex++);
            OptionalInt ground = terrain.findGroundSurface(column.getX(), column.getZ());
            OptionalInt water = terrain.findExposedWaterSurface(column.getX(), column.getZ());
            builder.putColumn(column.getX(), column.getZ(), ground, water);
            sampled++;
        }
        if (columnIndex < columns.size()) {
            int nextColumn = columnIndex;
            server.execute(() -> runBatch(server, worldKey, job, chunkKeys.size(), nextColumn));
            return;
        }

        job.completed = true;
        LOGGER.info(
            "道路走廊地形预取完成: columns={}, known={}, truncated={}",
            columns.size(),
            builder.knownCount(),
            job.truncated);
    }

    record ColumnCollectResult(List<BlockPos> columns, boolean truncated) {
        static ColumnCollectResult empty() {
            return new ColumnCollectResult(List.of(), false);
        }
    }

    private record CacheEntry(
            String key,
            PrefetchedGroundTerrainSampler.Builder builder,
            PrefetchStatus status) {
    }

    private static final class InflightJob {
        final String key;
        final List<BlockPos> columns;
        final List<Long> chunkKeys;
        final boolean truncated;
        final PrefetchedGroundTerrainSampler.Builder builder = new PrefetchedGroundTerrainSampler.Builder();
        volatile boolean cancelled;
        volatile boolean completed;

        InflightJob(String key, List<BlockPos> columns, boolean truncated) {
            this.key = key;
            this.columns = List.copyOf(columns);
            this.truncated = truncated;
            LinkedHashSet<Long> chunks = new LinkedHashSet<>();
            for (BlockPos column : this.columns) {
                chunks.add(ChunkPos.toLong(column.getX() >> 4, column.getZ() >> 4));
            }
            this.chunkKeys = List.copyOf(chunks);
        }
    }
}
