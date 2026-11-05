package com.plot.plugin.road.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.manager.RoadPreviewManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Level-1 地形采样分帧任务：主线程每 tick 在样本数与纳秒预算内处理，可取消；
 * 完成时由 Manager 用 live 路网重建 key 验收后再发布。
 */
public final class RoadTerrainProfileSamplingJob {

    public enum Phase {
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    /** 单帧最多处理的采样列数（硬上限）。 */
    public static final int SAMPLES_PER_TICK = RoadTerrainProfileSampler.SAMPLES_PER_TICK;
    /** 单帧时间预算（纳秒），避免复杂世界读取拖垮 ImGui。 */
    public static final long TICK_BUDGET_NS = 2_000_000L;

    private final RoadPreviewManager manager;
    private final TerrainProfileKey key;
    private final double totalStation;
    private final List<Double> stations;
    private final List<Vec2d> points;
    private final List<Double> terrainY;
    private final List<Double> waterY;
    private final List<Boolean> loaded;

    private Phase phase = Phase.RUNNING;
    private int nextIndex;
    private volatile boolean cancelled;
    private long lastTickNanos;
    private long maxTickNanos;

    public RoadTerrainProfileSamplingJob(
            RoadPreviewManager manager,
            TerrainProfileKey key,
            double totalStation,
            List<Double> stations,
            List<Vec2d> points) {
        this.manager = manager;
        this.key = key;
        this.totalStation = totalStation;
        this.stations = List.copyOf(stations);
        // 允许 null 占位（桩号无法解析），List.copyOf 会拒绝 null。
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        if (this.stations.size() != this.points.size()) {
            throw new IllegalArgumentException("stations/points size mismatch");
        }
        int n = this.stations.size();
        this.terrainY = new ArrayList<>(n);
        this.waterY = new ArrayList<>(n);
        this.loaded = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            this.terrainY.add(Double.NaN);
            this.waterY.add(Double.NaN);
            this.loaded.add(false);
        }
    }

    public TerrainProfileKey key() {
        return key;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isRunning() {
        return phase == Phase.RUNNING;
    }

    public int processedWorkUnits() {
        return nextIndex;
    }

    public int totalWorkUnits() {
        return stations.size();
    }

    public long lastTickNanos() {
        return lastTickNanos;
    }

    public long maxTickNanos() {
        return maxTickNanos;
    }

    public void cancel() {
        cancelled = true;
    }

    public void tick() {
        if (phase != Phase.RUNNING) {
            return;
        }
        long tickStart = System.nanoTime();
        if (cancelled) {
            phase = Phase.CANCELLED;
            manager.finishCancelledTerrainProfileJob(this);
            return;
        }
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            phase = Phase.FAILED;
            manager.failTerrainProfileJob(this);
            return;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, manager.hostCoordinates());
        int sampleBudget = SAMPLES_PER_TICK;
        long deadline = tickStart + TICK_BUDGET_NS;
        while (sampleBudget-- > 0 && nextIndex < stations.size()) {
            if (cancelled) {
                phase = Phase.CANCELLED;
                manager.finishCancelledTerrainProfileJob(this);
                recordTick(tickStart);
                return;
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            RoadTerrainProfileSampler.SampleColumn column =
                RoadTerrainProfileSampler.sampleColumn(terrain, points.get(nextIndex));
            terrainY.set(nextIndex, column.terrainY());
            waterY.set(nextIndex, column.waterY());
            loaded.set(nextIndex, column.loaded());
            nextIndex++;
        }
        recordTick(tickStart);
        manager.updateTerrainProfileJobProgress(this);
        if (nextIndex >= stations.size()) {
            RoadTerrainProfileData data = new RoadTerrainProfileData(
                key,
                totalStation,
                stations,
                terrainY,
                waterY,
                loaded);
            phase = Phase.COMPLETED;
            manager.completeTerrainProfileJob(this, data);
        }
    }

    private void recordTick(long tickStart) {
        lastTickNanos = System.nanoTime() - tickStart;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
    }
}
