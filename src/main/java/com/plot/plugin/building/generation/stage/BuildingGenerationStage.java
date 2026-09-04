package com.plot.plugin.building.generation.stage;

import com.plot.plugin.building.generation.BuildingGenerationContext;

/**
 * 建筑生成管线中的单个阶段。本阶段直接写入 {@link BuildingGenerationContext#getResult()}。
 */
public interface BuildingGenerationStage {
    /**
     * 执行本阶段生成逻辑。不得直接修改 Minecraft 世界。
     */
    void generate(BuildingGenerationContext context);

    /**
     * 阶段名称，用于测试与诊断。
     */
    String name();
}
