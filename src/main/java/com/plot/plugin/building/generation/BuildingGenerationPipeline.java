package com.plot.plugin.building.generation;

import com.plot.plugin.building.generation.stage.AccessoryGenerationStage;
import com.plot.plugin.building.generation.stage.BuildingGenerationStage;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.FoundationGenerationStage;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 建筑生成管线。默认顺序与旧 BuildingGenerator 一致：
 * Foundation → Wall → Floor → Roof → Accessory → Opening。
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
     * 默认管线：地基 → 墙体 → 楼板 → 屋顶 → 构件 → 开洞。
     */
    public static BuildingGenerationPipeline createDefault() {
        return new BuildingGenerationPipeline(List.of(
            new FoundationGenerationStage(),
            new WallGenerationStage(),
            new FloorGenerationStage(),
            new RoofGenerationStage(),
            new AccessoryGenerationStage(),
            new OpeningGenerationStage()
        ));
    }

    public BuildingGenerationResult generate(BuildingGenerationContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.isValid()) {
            return context.getResult();
        }
        for (BuildingGenerationStage stage : stages) {
            stage.generate(context);
        }
        BuildingGenerationResult result = context.getResult();
        result.blockCount = result.placementRecords.size();
        return result;
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
