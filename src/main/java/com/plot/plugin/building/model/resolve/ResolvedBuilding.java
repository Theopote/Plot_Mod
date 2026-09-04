package com.plot.plugin.building.model.resolve;

/**
 * Stabilization 目标层（占位约定，尚未全面落地）：
 * <pre>
 * BuildingFootprint (legacy DTO)
 *         ↓
 * BuildingDefinition (用户想要什么)
 *         ↓
 * ResolvedBuilding (系统最终怎么生成)
 *         ↓
 * GenerationPipeline
 * </pre>
 * 例如：requested roof=HIP → resolved roof=FLAT（geometry invalid）；
 * requested elevation=AUTO → actualFoundationElevation=72（source=EARTHWORK_PAD）；
 * Facade SOUTH → 各层 plate 上的 resolved edges。
 * <p>
 * 当前生成链已有 {@link com.plot.plugin.building.generation.resolve.ResolvedBuildingDefinition}
 * （massing/site/material）；本类型表示完整「决策解析」方向，稳定化阶段勿提前堆构件。
 */
public final class ResolvedBuilding {
    private ResolvedBuilding() {
    }
}
