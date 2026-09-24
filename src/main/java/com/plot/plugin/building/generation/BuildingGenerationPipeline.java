package com.plot.plugin.building.generation;

import com.plot.plugin.building.generation.stage.AccessoryGenerationStage;
import com.plot.plugin.building.generation.stage.BuildingGenerationStage;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.FoundationGenerationStage;
import com.plot.plugin.building.generation.stage.FrameGenerationStage;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.generation.stage.SitePreparationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 建筑生成管线。默认顺序：
 * SitePreparation → Foundation → Wall → Floor → Roof → Accessory → Opening。
 * <p>
 * Accessory 阶段仅含冻结集（女儿墙 / 雨篷 / 阳台），见 {@link com.plot.plugin.building.model.spec.AccessoryKind}。
 */
public final class BuildingGenerationPipeline {
    private final List<BuildingGenerationStage> stages;

    public BuildingGenerationPipeline(List<BuildingGenerationStage> stages) {
        Objects.requireNonNull(stages, "stages");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        this.stages = List.copyOf(stages);
    }

    /**
     * 默认管线：场地清理 → 地基 → 墙体 → 楼板 → 屋顶 → 构件 → 开洞。
     */
    public static BuildingGenerationPipeline createDefault() {
        return new BuildingGenerationPipeline(List.of(
            new SitePreparationStage(),
            new FoundationGenerationStage(),
            new WallGenerationStage(),
            new FloorGenerationStage(),
            new RoofGenerationStage(),
            new AccessoryGenerationStage(),
            new OpeningGenerationStage()
        ));
    }

    /**
     * 框架管线：场地清理 → 地基找平 → 结构线（地基平面、转角柱、外圈梁、顶轮廓）。
     */
    public static BuildingGenerationPipeline createFrameOnly() {
        return new BuildingGenerationPipeline(List.of(
            new SitePreparationStage(),
            new FoundationGenerationStage(),
            new FrameGenerationStage()
        ));
    }

    public BuildingGenerationResult generate(BuildingGenerationContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.isValid()) {
            return context.getResult();
        }
        for (int i = 0; i < stages.size(); i++) {
            runStage(context, i);
        }
        finalizeGeneration(context);
        return context.getResult();
    }

    public int stageCount() {
        return stages.size();
    }

    public void runStage(BuildingGenerationContext context, int stageIndex) {
        Objects.requireNonNull(context, "context");
        if (!context.isValid() || stageIndex < 0 || stageIndex >= stages.size()) {
            return;
        }
        stages.get(stageIndex).generate(context);
    }

    public void finalizeGeneration(BuildingGenerationContext context) {
        Objects.requireNonNull(context, "context");
        BuildingGenerationResult result = context.getResult();
        result.footprintWorldColumns = collectFootprintWorldColumns(context);
        result.blockCount = result.placementRecords.size();
    }

    private static Set<Long> collectFootprintWorldColumns(BuildingGenerationContext context) {
        Set<Long> columns = new LinkedHashSet<>();
        for (BuildingGenerationContext.GridCell cell : context.getFootprintCells()) {
            var column = context.canvasToColumn(cell.center());
            columns.add(packWorldColumn(column.getX(), column.getZ()));
        }
        return columns;
    }

    public static long packWorldColumn(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public List<BuildingGenerationStage> getStages() {
        return stages;
    }

    public List<String> getStageNames() {
        List<String> names = new ArrayList<>(stages.size());
        for (BuildingGenerationStage stage : stages) {
            names.add(stage.name());
        }
        return Collections.unmodifiableList(names);
    }
}
