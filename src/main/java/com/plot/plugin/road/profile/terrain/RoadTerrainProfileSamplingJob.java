package com.plot.plugin.road.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.manager.RoadPreviewManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Level-1 地形采样分帧任务：主线程每 tick 处理有限列，可取消；完成时校验 key 再发布。
 */
public final class RoadTerrainProfileSamplingJob {

    public enum Phase {
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

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
        this.points = List.copyOf(points);
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

    public void cancel() {
        cancelled = true;
    }

    public void tick() {
        if (phase != Phase.RUNNING) {
            return;
        }
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
        int budget = RoadTerrainProfileSampler.SAMPLES_PER_TICK;
        while (budget-- > 0 && nextIndex < stations.size()) {
            if (cancelled) {
                phase = Phase.CANCELLED;
                manager.finishCancelledTerrainProfileJob(this);
                return;
            }
            RoadTerrainProfileSampler.SampleColumn column =
                RoadTerrainProfileSampler.sampleColumn(terrain, points.get(nextIndex));
            terrainY.set(nextIndex, column.terrainY());
            waterY.set(nextIndex, column.waterY());
            loaded.set(nextIndex, column.loaded());
            nextIndex++;
        }
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
}
