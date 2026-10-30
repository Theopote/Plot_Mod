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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 纵断面 / 预览用的道路走廊地形预取。
 * <p>
 * 按纵断面实际采样站与横断面偏移收集列；在集成服上分批加载区块并采样地面+裸露水面；
 * 结果缓存后非阻塞交给求解器。不调整客户端视距（显示与求解解耦）。
 */
public final class RoadCorridorTerrainPrefetch {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadTerrainPrefetch");

    private static final int MAX_COLUMNS = 24_000;
    private static final int CHUNKS_PER_SERVER_BATCH = 8;
    private static final int COLUMNS_PER_SERVER_BATCH = 256;

    private static final Object LOCK = new Object();
    private static CacheEntry cache;
    private static InflightJob inflight;

    private RoadCorridorTerrainPrefetch() {
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
        String key = fingerprint(network, clientWorld);
        PrefetchedGroundTerrainSampler.Builder snapshot = takeReadySnapshot(key);
        if (snapshot == null) {
            synchronized (LOCK) {
                ensureInflightLocked(
                    key, network, clientWorld, coordinates, config, unitsEstimator);
            }
            return clientSampler;
        }
        return snapshot.build(clientSampler, coordinates);
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

    /** 当前网络是否已有可用预取缓存。 */
    public static boolean isCacheReady(RoadNetwork network, World world) {
        if (network == null || world == null) {
            return false;
        }
        return takeReadySnapshot(fingerprint(network, world)) != null;
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
        PrefetchedGroundTerrainSampler.Builder ready;
        synchronized (LOCK) {
            if (inflight == null || !inflight.completed) {
                return false;
            }
            InflightJob done = inflight;
            inflight = null;
            if (done.builder == null || done.builder.isEmpty()) {
                return false;
            }
            cache = new CacheEntry(done.key, done.builder);
            ready = done.builder;
        }
        if (onReady != null) {
            onReady.run();
        }
        return ready != null;
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

    static List<BlockPos> collectProfileSampleColumns(
            RoadNetwork network,
            ICoordinateService coordinates,
            RoadSystemConfig config,
            RoadGeometrySampler.CanvasUnitsPerBlockEstimator unitsEstimator) {
        Set<Long> seen = new LinkedHashSet<>();
        List<BlockPos> columns = new ArrayList<>();
        if (network == null || config == null) {
            return columns;
        }
        RoadGeometrySampler.CanvasUnitsPerBlockEstimator estimator = unitsEstimator != null
            ? unitsEstimator
            : (path, segs) -> 1.0;
        double pathSampleDistance = Math.max(0.1, config.getPathSampleDistance());
        NodeGroundHeightResolver nodeResolver = new NodeGroundHeightResolver(config);

        for (RoadEdge edge : network.getEdges().values()) {
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
                    return columns;
                }
                if (i == segments.size() - 1) {
                    appendStationColumns(segment.end, tangent, halfWidth, coordinates, seen, columns);
                    if (columns.size() >= MAX_COLUMNS) {
                        return columns;
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
                break;
            }
        }
        return columns;
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

    private static PrefetchedGroundTerrainSampler.Builder takeReadySnapshot(String key) {
        synchronized (LOCK) {
            if (cache != null && key.equals(cache.key) && cache.builder != null && !cache.builder.isEmpty()) {
                return cache.builder;
            }
            if (inflight != null
                    && inflight.completed
                    && key.equals(inflight.key)
                    && inflight.builder != null
                    && !inflight.builder.isEmpty()) {
                cache = new CacheEntry(inflight.key, inflight.builder);
                PrefetchedGroundTerrainSampler.Builder builder = inflight.builder;
                inflight = null;
                return builder;
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

    static String fingerprint(RoadNetwork network, World world) {
        String worldKey = "";
        if (world != null && world.getRegistryKey() != null) {
            worldKey = world.getRegistryKey().getValue().toString();
        }
        long hash = 17L;
        hash = 31L * hash + worldKey.hashCode();
        if (network != null) {
            hash = 31L * hash + network.getEdges().size();
            hash = 31L * hash + network.getNodes().size();
            for (RoadEdge edge : network.getEdges().values()) {
                if (edge == null) {
                    continue;
                }
                hash = 31L * hash + edge.getId().hashCode();
                List<Vec2d> points = edge.getCenterlinePoints();
                if (points != null && !points.isEmpty()) {
                    Vec2d first = points.getFirst();
                    Vec2d last = points.getLast();
                    hash = 31L * hash + Double.hashCode(first.x);
                    hash = 31L * hash + Double.hashCode(first.y);
                    hash = 31L * hash + Double.hashCode(last.x);
                    hash = 31L * hash + Double.hashCode(last.y);
                    hash = 31L * hash + points.size();
                }
            }
        }
        return worldKey + "#" + Long.toHexString(hash);
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

        List<BlockPos> columns = collectProfileSampleColumns(
            network, coordinates, config, unitsEstimator);
        if (columns.isEmpty()) {
            return;
        }

        RegistryKey<World> worldKey = client.world.getRegistryKey();
        InflightJob job = new InflightJob(key, columns);
        inflight = job;
        server.execute(() -> runBatch(server, worldKey, job, 0, 0));
        LOGGER.debug("已启动走廊地形预取: key={}, columns={}", key, columns.size());
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

        List<Long> chunkKeys = job.chunkKeys;
        int chunksLoaded = 0;
        while (chunkIndex < chunkKeys.size() && chunksLoaded < CHUNKS_PER_SERVER_BATCH) {
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
        while (columnIndex < columns.size() && sampled < COLUMNS_PER_SERVER_BATCH) {
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
        job.readySignal.set(Boolean.TRUE);
        LOGGER.info(
            "道路走廊地形预取完成: columns={}, known={}",
            columns.size(),
            builder.knownCount());
    }

    private record CacheEntry(String key, PrefetchedGroundTerrainSampler.Builder builder) {
    }

    private static final class InflightJob {
        final String key;
        final List<BlockPos> columns;
        final List<Long> chunkKeys;
        final PrefetchedGroundTerrainSampler.Builder builder = new PrefetchedGroundTerrainSampler.Builder();
        final AtomicReference<Boolean> readySignal = new AtomicReference<>(Boolean.FALSE);
        volatile boolean cancelled;
        volatile boolean completed;

        InflightJob(String key, List<BlockPos> columns) {
            this.key = key;
            this.columns = List.copyOf(columns);
            LinkedHashSet<Long> chunks = new LinkedHashSet<>();
            for (BlockPos column : this.columns) {
                chunks.add(ChunkPos.toLong(column.getX() >> 4, column.getZ() >> 4));
            }
            this.chunkKeys = List.copyOf(chunks);
        }
    }
}
