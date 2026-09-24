package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingFootprintValidator;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginJobProgressUi;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 片区预览分帧 job：每帧推进若干管线阶段，避免 UI 长时间阻塞。
 * <p>
 * World 采样必须在主线程，故不使用后台线程；由 {@link BuildingUIManager} 每帧 {@link #tick()}。
 */
public final class DistrictPreviewJob {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/DistrictPreviewJob");

    /** 每帧推进的管线阶段数。 */
    public static final int STAGES_PER_TICK = 1;

    private enum Phase {
        GENERATING,
        FINALIZING
    }

    private final List<BuildingFootprint> buildings;
    private final boolean autoProjectGhosts;
    private final boolean buildConfirmOnComplete;
    private final boolean frameOnly;
    private final BuildingActions actions;
    private final int stageCount;

    private final DistrictGenerationResult district = new DistrictGenerationResult();
    private int buildingIndex;
    private int stageIndex;
    private BuildingGenerationContext activeContext;
    private BuildingGenerationPipeline activePipeline;
    private Phase phase = Phase.GENERATING;
    private volatile boolean running = true;
    private volatile boolean cancelled;

    public DistrictPreviewJob(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete,
            BuildingActions actions) {
        this.buildings = buildings == null
            ? List.of()
            : DistrictMassingGenerator.sortedBuildingsForGeneration(buildings);
        this.autoProjectGhosts = autoProjectGhosts;
        this.buildConfirmOnComplete = buildConfirmOnComplete;
        this.frameOnly = actions.isFrameOnlyGenerate();
        this.actions = actions;
        BuildingGenerator generator = actions.buildingGenerator();
        this.stageCount = generator != null
            ? generator.pipelineFor(frameOnly).stageCount()
            : 1;
    }

    public void tick() {
        if (!running || cancelled || buildings.isEmpty()) {
            return;
        }

        if (phase == Phase.FINALIZING) {
            actions.completeDistrictPreviewJob(this, district, autoProjectGhosts, buildConfirmOnComplete);
            running = false;
            return;
        }

        World world = actions.getClientWorld();
        BuildingGenerator generator = actions.buildingGenerator();
        if (world == null || generator == null) {
            actions.failDistrictPreviewJob(this);
            return;
        }

        int stagesRun = 0;
        while (stagesRun < STAGES_PER_TICK && phase == Phase.GENERATING) {
            if (activeContext == null) {
                if (buildingIndex >= buildings.size()) {
                    DistrictMassingGenerator.finalizeResult(district);
                    phase = Phase.FINALIZING;
                    break;
                }
                if (!beginBuilding(generator, world, buildings.get(buildingIndex))) {
                    buildingIndex++;
                    continue;
                }
            }

            try {
                activePipeline.runStage(activeContext, stageIndex);
                stageIndex++;
                stagesRun++;

                if (stageIndex >= stageCount) {
                    finishCurrentBuilding();
                }
            } catch (Exception e) {
                BuildingFootprint building = buildings.get(buildingIndex);
                LOGGER.warn(
                    "District preview skipped building {} ({}): {}",
                    building.getId(),
                    building.getName(),
                    e.getMessage());
                DistrictMassingGenerator.recordSkipped(
                    building,
                    DistrictGenerationResult.SkipReason.ERROR,
                    e.getMessage(),
                    district);
                activeContext = null;
                activePipeline = null;
                buildingIndex++;
                stageIndex = 0;
            }
        }

        actions.updateDistrictPreviewJobProgress(this);
    }

    private boolean beginBuilding(BuildingGenerator generator, World world, BuildingFootprint building) {
        BuildingFootprintValidator.Result validation =
            BuildingFootprintValidator.validate(building.getOuterPoints());
        if (!validation.valid()) {
            DistrictMassingGenerator.recordSkipped(
                building,
                DistrictGenerationResult.SkipReason.INVALID,
                validation.reason() != null ? validation.reason().name() : null,
                district);
            return false;
        }

        activePipeline = generator.pipelineFor(frameOnly);
        activeContext = generator.beginGenerate(building, world);
        stageIndex = 0;
        if (!activeContext.isValid()) {
            DistrictMassingGenerator.recordGenerationOutcome(
                building, activeContext.getResult(), district);
            activeContext = null;
            activePipeline = null;
            return false;
        }
        return true;
    }

    private void finishCurrentBuilding() {
        BuildingFootprint building = buildings.get(buildingIndex);
        activePipeline.finalizeGeneration(activeContext);
        DistrictMassingGenerator.recordGenerationOutcome(
            building, activeContext.getResult(), district);

        activeContext = null;
        activePipeline = null;
        buildingIndex++;
        stageIndex = 0;
    }

    public void cancel() {
        cancelled = true;
        running = false;
        activeContext = null;
        activePipeline = null;
    }

    public boolean isRunning() {
        return running && !cancelled;
    }

    public int progressTotalSteps() {
        return Math.max(1, buildings.size() * Math.max(1, stageCount));
    }

    public int progressCompletedSteps() {
        if (phase == Phase.FINALIZING) {
            return progressTotalSteps();
        }
        return Math.min(progressTotalSteps(), buildingIndex * stageCount + stageIndex);
    }

    public int totalBuildingCount() {
        return buildings.size();
    }

    public int completedBuildingCount() {
        return Math.min(buildings.size(), buildingIndex);
    }

    /** 当前正在检查的建筑（1-based）；汇总阶段返回总数。 */
    public int displayBuildingNumber() {
        if (phase == Phase.FINALIZING) {
            return buildings.size();
        }
        return Math.min(buildings.size(), buildingIndex + 1);
    }

    public String currentBuildingLabel() {
        if (buildings.isEmpty()) {
            return "";
        }
        int index = phase == Phase.FINALIZING
            ? buildings.size() - 1
            : Math.min(buildingIndex, buildings.size() - 1);
        BuildingFootprint building = buildings.get(index);
        String name = building.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return building.getId();
    }

    public float progressFraction() {
        return PluginJobProgressUi.fraction(progressCompletedSteps(), progressTotalSteps());
    }

    public int progressPercent() {
        return PluginJobProgressUi.percent(progressCompletedSteps(), progressTotalSteps());
    }

    /** 防御性拷贝供测试断言。 */
    List<BuildingFootprint> buildings() {
        return new ArrayList<>(buildings);
    }
}
