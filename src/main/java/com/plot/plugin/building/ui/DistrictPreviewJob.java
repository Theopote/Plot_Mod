package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginJobProgressUi;
import imgui.ImGui;
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
    public static final int BUILDINGS_PER_TICK = 2;

    private final List<BuildingFootprint> buildings;
    private final boolean autoProjectGhosts;
    private final boolean buildConfirmOnComplete;
    private final BuildingActions actions;

    private final DistrictGenerationResult district = new DistrictGenerationResult();
    private final int createdFrame;
    private int nextIndex;
    private volatile boolean running = true;
    private volatile boolean cancelled;
    private int completedFrame = -1;

    public DistrictPreviewJob(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete,
            BuildingActions actions) {
        this.buildings = buildings == null ? List.of() : List.copyOf(buildings);
        this.autoProjectGhosts = autoProjectGhosts;
        this.buildConfirmOnComplete = buildConfirmOnComplete;
        this.actions = actions;
        this.createdFrame = ImGui.getFrameCount();
    }

    public boolean shouldDeferTick() {
        return ImGui.getFrameCount() <= createdFrame;
    }

    public boolean isVisibleInUi() {
        return isRunning() || completedFrame >= 0;
    }

    public boolean shouldDismissFromState() {
        return completedFrame >= 0 && ImGui.getFrameCount() > completedFrame;
    }

    public void tick() {
        if (!running || cancelled || buildings.isEmpty() || shouldDeferTick()) {
            return;
        }
        World world = actions.getClientWorld();
        BuildingGenerator generator = actions.buildingGenerator();
        if (world == null || generator == null) {
            actions.failDistrictPreviewJob(this);
            return;
        }

        int end = Math.min(nextIndex + BUILDINGS_PER_TICK, buildings.size());
        DistrictMassingGenerator.BuildingGenerateFn generateFn =
            footprint -> generator.generate(footprint, world);
        for (int i = nextIndex; i < end; i++) {
            DistrictMassingGenerator.processOne(buildings.get(i), generateFn, district);
        }
        nextIndex = end;

        if (nextIndex >= buildings.size()) {
            DistrictMassingGenerator.finalizeResult(district);
            running = false;
            completedFrame = ImGui.getFrameCount();
            actions.completeDistrictPreviewJob(this, district, autoProjectGhosts, buildConfirmOnComplete);
        }
    }

    public void cancel() {
        cancelled = true;
        running = false;
    }

    public boolean isRunning() {
        return running && !cancelled;
    }

    public int processedCount() {
        return nextIndex;
    }

    public int totalCount() {
        return buildings.size();
    }

    public float progressFraction() {
        return PluginJobProgressUi.fraction(processedCount(), totalCount());
    }

    public int progressPercent() {
        return PluginJobProgressUi.percent(processedCount(), totalCount());
    }

    /** 防御性拷贝供测试断言。 */
    List<BuildingFootprint> buildings() {
        return new ArrayList<>(buildings);
    }
}
