package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginJobProgressUi;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 片区预览分帧 job：每帧在客户端主线程处理若干栋，避免 UI 长时间阻塞。
 * <p>
 * World 采样必须在主线程，故不使用后台线程；由 {@link BuildingUIManager} 每帧 {@link #tick()}。
 */
public final class DistrictPreviewJob {
    /** 每帧处理的建筑数（较小值可减少单帧卡顿）。 */
    public static final int BUILDINGS_PER_TICK = 1;

    private enum Phase {
        GENERATING,
        FINALIZING
    }

    private final List<BuildingFootprint> buildings;
    private final boolean autoProjectGhosts;
    private final boolean buildConfirmOnComplete;
    private final BuildingActions actions;

    private final DistrictGenerationResult district = new DistrictGenerationResult();
    private int nextIndex;
    private int activeBuildingIndex;
    private Phase phase = Phase.GENERATING;
    private volatile boolean running = true;
    private volatile boolean cancelled;

    public DistrictPreviewJob(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete,
            BuildingActions actions) {
        this.buildings = buildings == null ? List.of() : List.copyOf(buildings);
        this.autoProjectGhosts = autoProjectGhosts;
        this.buildConfirmOnComplete = buildConfirmOnComplete;
        this.actions = actions;
    }

    public void tick() {
        if (!running || cancelled || buildings.isEmpty()) {
            return;
        }

        if (phase == Phase.FINALIZING) {
            actions.completeDistrictPreviewJob(this, district, autoProjectGhosts, buildConfirmOnComplete);
            running = false;
            activeBuildingIndex = 0;
            return;
        }

        World world = actions.getClientWorld();
        BuildingGenerator generator = actions.buildingGenerator();
        if (world == null || generator == null) {
            actions.failDistrictPreviewJob(this);
            return;
        }

        int end = Math.min(nextIndex + BUILDINGS_PER_TICK, buildings.size());
        boolean frameOnly = actions.isFrameOnlyGenerate();
        DistrictMassingGenerator.BuildingGenerateFn generateFn =
            footprint -> generator.generate(footprint, world, frameOnly);
        for (int i = nextIndex; i < end; i++) {
            activeBuildingIndex = i + 1;
            actions.updateDistrictPreviewJobProgress(this);
            DistrictMassingGenerator.processOne(buildings.get(i), generateFn, district);
        }
        nextIndex = end;
        activeBuildingIndex = 0;
        actions.updateDistrictPreviewJobProgress(this);

        if (nextIndex >= buildings.size()) {
            DistrictMassingGenerator.finalizeResult(district);
            phase = Phase.FINALIZING;
            actions.updateDistrictPreviewJobProgress(this);
        }
    }

    public void cancel() {
        cancelled = true;
        running = false;
        activeBuildingIndex = 0;
    }

    public boolean isRunning() {
        return running && !cancelled;
    }

    /** 已完成栋数。 */
    public int processedCount() {
        return nextIndex;
    }

    /** 进度条展示用：含当前正在检查的栋（若有）。 */
    public int displayProcessedCount() {
        if (phase == Phase.FINALIZING) {
            return buildings.size();
        }
        if (activeBuildingIndex > 0) {
            return activeBuildingIndex;
        }
        return nextIndex;
    }

    public int totalCount() {
        return buildings.size();
    }

    public float progressFraction() {
        return PluginJobProgressUi.fraction(displayProcessedCount(), totalCount());
    }

    public int progressPercent() {
        return PluginJobProgressUi.percent(displayProcessedCount(), totalCount());
    }

    /** 防御性拷贝供测试断言。 */
    List<BuildingFootprint> buildings() {
        return new ArrayList<>(buildings);
    }
}
