package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.camera.ChunkUpdater;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 纵断面 / 预览前强制加载道路走廊区块，并从集成服世界采样工程地面，
 * 使画布尚未刷出的地形仍能得到正确高程。
 */
public final class RoadCorridorTerrainPrefetch {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadTerrainPrefetch");

    /** 沿中心线采样步长（方块）。 */
    private static final int PATH_STRIDE_BLOCKS = 4;
    /** 横断面半宽缓冲（方块），覆盖常见路幅采样偏移。 */
    private static final int CROSS_SECTION_PAD_BLOCKS = 8;
    private static final long SERVER_SAMPLE_TIMEOUT_MS = 5_000L;
    private static final int MAX_COLUMNS = 24_000;

    private RoadCorridorTerrainPrefetch() {
    }

    /**
     * 抬高视距、在集成服上强制加载走廊区块并缓存工程地面；无集成服时仅抬视距并返回原采样器。
     */
    public static TerrainSampler prepareForNetwork(
            RoadNetwork network,
            World clientWorld,
            ICoordinateService coordinates,
            TerrainSampler clientSampler) {
        if (network == null || clientWorld == null || clientSampler == null) {
            return clientSampler;
        }

        List<BlockPos> columns = collectCorridorColumns(network, coordinates);
        if (columns.isEmpty()) {
            return clientSampler;
        }

        expandViewDistanceForColumns(columns);

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client != null ? client.getServer() : null;
        if (server == null || client.world == null) {
            LOGGER.debug("无集成服，仅抬升视距以请求客户端刷出走廊地形 ({} 列)", columns.size());
            return clientSampler;
        }

        RegistryKey<World> worldKey = client.world.getRegistryKey();
        PrefetchedGroundTerrainSampler.Builder builder = sampleOnServer(server, worldKey, columns);
        if (builder == null || builder.isEmpty()) {
            LOGGER.debug("走廊地形服务端采样为空，回退客户端采样器");
            return clientSampler;
        }

        PrefetchedGroundTerrainSampler prefetched = builder.build(clientSampler, coordinates);
        LOGGER.info(
            "道路走廊地形预取: columns={}, ground={}, empty={}",
            columns.size(),
            prefetched.cachedColumnCount(),
            prefetched.emptyColumnCount());
        return prefetched;
    }

    static List<BlockPos> collectCorridorColumns(RoadNetwork network, ICoordinateService coordinates) {
        Set<Long> seen = new LinkedHashSet<>();
        List<BlockPos> columns = new ArrayList<>();
        if (network == null) {
            return columns;
        }
        for (RoadEdge edge : network.getEdges().values()) {
            List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
            if (centerline.size() < 2) {
                continue;
            }
            appendDensifiedColumns(centerline, coordinates, seen, columns);
            if (columns.size() >= MAX_COLUMNS) {
                break;
            }
        }
        return columns;
    }

    private static void appendDensifiedColumns(
            List<Vec2d> centerline,
            ICoordinateService coordinates,
            Set<Long> seen,
            List<BlockPos> columns) {
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d a = centerline.get(i);
            Vec2d b = centerline.get(i + 1);
            BlockPos start = WorldCoordinateUtils.canvasToBlockXZ(a, coordinates);
            BlockPos end = WorldCoordinateUtils.canvasToBlockXZ(b, coordinates);
            int dx = end.getX() - start.getX();
            int dz = end.getZ() - start.getZ();
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / (double) PATH_STRIDE_BLOCKS));
            Vec2d tangent = b.subtract(a);
            Vec2d normal = leftUnit(tangent);
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                int x = start.getX() + (int) Math.round(dx * t);
                int z = start.getZ() + (int) Math.round(dz * t);
                addColumnWithPads(x, z, normal, seen, columns);
                if (columns.size() >= MAX_COLUMNS) {
                    return;
                }
            }
        }
        BlockPos last = WorldCoordinateUtils.canvasToBlockXZ(centerline.getLast(), coordinates);
        addColumnWithPads(last.getX(), last.getZ(), new Vec2d(0, 1), seen, columns);
    }

    private static void addColumnWithPads(
            int centerX,
            int centerZ,
            Vec2d normal,
            Set<Long> seen,
            List<BlockPos> columns) {
        int nx = (int) Math.round(normal.x);
        int nz = (int) Math.round(normal.y);
        if (nx == 0 && nz == 0) {
            nx = 0;
            nz = 1;
        }
        int[] offsets = {0, -CROSS_SECTION_PAD_BLOCKS, CROSS_SECTION_PAD_BLOCKS};
        for (int offset : offsets) {
            int x = centerX + nx * offset;
            int z = centerZ + nz * offset;
            long key = PrefetchedGroundTerrainSampler.packColumn(x, z);
            if (seen.add(key)) {
                columns.add(new BlockPos(x, 0, z));
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

    private static void expandViewDistanceForColumns(List<BlockPos> columns) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || columns.isEmpty()) {
            return;
        }
        double px = client.player.getX();
        double pz = client.player.getZ();
        double maxDist = 0.0;
        for (BlockPos column : columns) {
            maxDist = Math.max(maxDist, Math.hypot(column.getX() - px, column.getZ() - pz));
        }
        // applyForViewRangeBlocks 以全宽计；覆盖玩家到最远走廊点需约 2×半径
        float viewRange = (float) Math.min(1024.0, maxDist * 2.0 + 64.0);
        ChunkUpdater.applyForViewRangeBlocks(viewRange, true);
    }

    private static PrefetchedGroundTerrainSampler.Builder sampleOnServer(
            MinecraftServer server,
            RegistryKey<World> worldKey,
            List<BlockPos> columns) {
        if (server.isOnThread()) {
            return sampleColumns(server, worldKey, columns);
        }
        CompletableFuture<PrefetchedGroundTerrainSampler.Builder> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(sampleColumns(server, worldKey, columns));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(SERVER_SAMPLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warn("走廊地形服务端预取超时或失败: {}", e.getMessage());
            return null;
        }
    }

    private static PrefetchedGroundTerrainSampler.Builder sampleColumns(
            MinecraftServer server,
            RegistryKey<World> worldKey,
            List<BlockPos> columns) {
        ServerWorld serverWorld = server.getWorld(worldKey);
        if (serverWorld == null) {
            return null;
        }
        Set<Long> chunkKeys = new LinkedHashSet<>();
        for (BlockPos column : columns) {
            chunkKeys.add(ChunkPos.toLong(column.getX() >> 4, column.getZ() >> 4));
        }
        for (long chunkKey : chunkKeys) {
            int cx = ChunkPos.getPackedX(chunkKey);
            int cz = ChunkPos.getPackedZ(chunkKey);
            serverWorld.getChunk(cx, cz);
        }

        EngineeringTerrainService terrain = EngineeringTerrainService.of(serverWorld);
        PrefetchedGroundTerrainSampler.Builder builder = new PrefetchedGroundTerrainSampler.Builder();
        for (BlockPos column : columns) {
            OptionalInt ground = terrain.findGroundSurface(column.getX(), column.getZ());
            if (ground.isPresent()) {
                builder.putGround(column.getX(), column.getZ(), ground.getAsInt());
            } else {
                builder.putEmpty(column.getX(), column.getZ());
            }
        }
        return builder;
    }
}
