package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.block.BlockSpec;
import com.plot.plugin.road.solid.RoadSolidLayer;

import java.util.Locale;
import java.util.Set;

/**
 * 路面 / 桥面下方「有效承重」判定：区分已确认承重、已确认无效、材质未知。
 * <p>
 * 生成管线应写入 {@link RoadSolidLayer}；无层信息时不靠黑名单启发式默认通过。
 */
public final class RoadPavementSupportMaterials {
    public enum SupportVerdict {
        /** 结构层 + 非装饰材料，可作路基/桥面板。 */
        CONFIRMED,
        /** 空气、流体、装饰或非承重层。 */
        INVALID,
        /** 无结构层且材质不在排除表，无法确认承重。 */
        UNKNOWN
    }

    private static final Set<String> FLUID_IDS = Set.of(
        "minecraft:water",
        "minecraft:lava",
        "minecraft:bubble_column",
        "minecraft:powder_snow");

    private static final Set<String> NON_BEARING_IDS = Set.of(
        "minecraft:air",
        "minecraft:cave_air",
        "minecraft:void_air",
        "minecraft:fire",
        "minecraft:soul_fire",
        "minecraft:torch",
        "minecraft:wall_torch",
        "minecraft:soul_torch",
        "minecraft:soul_wall_torch",
        "minecraft:redstone_torch",
        "minecraft:redstone_wall_torch",
        "minecraft:lantern",
        "minecraft:soul_lantern",
        "minecraft:lever",
        "minecraft:tripwire",
        "minecraft:tripwire_hook",
        "minecraft:rail",
        "minecraft:powered_rail",
        "minecraft:detector_rail",
        "minecraft:activator_rail",
        "minecraft:ladder",
        "minecraft:vine",
        "minecraft:glow_lichen",
        "minecraft:scaffolding",
        "minecraft:snow",
        "minecraft:moss_carpet",
        "minecraft:carpet");

    private RoadPavementSupportMaterials() {
    }

    public static String canonicalBlockId(String materialOrSpec) {
        return BlockSpec.parse(materialOrSpec).blockId().toLowerCase(Locale.ROOT);
    }

    public static boolean isAirId(String materialOrSpec) {
        String id = canonicalBlockId(materialOrSpec);
        return "minecraft:air".equals(id)
            || "minecraft:cave_air".equals(id)
            || "minecraft:void_air".equals(id);
    }

    public static boolean isFluidId(String materialOrSpec) {
        String id = canonicalBlockId(materialOrSpec);
        return FLUID_IDS.contains(id) || id.endsWith("_bucket");
    }

    /** 明确不能作为路基 / 桥面板承重的材料（含空气、流体、火把等）。 */
    public static boolean isNonLoadBearingId(String materialOrSpec) {
        if (materialOrSpec == null || materialOrSpec.isBlank()) {
            return true;
        }
        String id = canonicalBlockId(materialOrSpec);
        if (isAirId(id) || isFluidId(id) || NON_BEARING_IDS.contains(id)) {
            return true;
        }
        return id.contains("torch")
            || id.contains("button")
            || id.contains("pressure_plate")
            || id.contains("sign")
            || id.contains("banner")
            || id.contains("candle")
            || id.contains("flower")
            || id.contains("sapling")
            || id.contains("carpet")
            || id.endsWith("_rail");
    }

    /** 生成管线写入的承重结构层（路基、桥面、挡墙、衬砌等）。 */
    public static boolean isLoadBearingLayer(RoadSolidLayer layer) {
        if (layer == null) {
            return false;
        }
        return layer == RoadSolidLayer.SUBGRADE
            || layer == RoadSolidLayer.BRIDGE
            || layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.RETAINING_WALL
            || layer == RoadSolidLayer.TUNNEL;
    }

    /**
     * 三态分类：优先依赖生成结构层；无层时不默认承重（UNKNOWN → 不计入支撑）。
     */
    public static SupportVerdict classifyGeneratedSupport(RoadSolidLayer layer, String materialOrSpec) {
        if (isNonLoadBearingId(materialOrSpec)) {
            return SupportVerdict.INVALID;
        }
        if (layer == null) {
            return SupportVerdict.UNKNOWN;
        }
        return isLoadBearingLayer(layer) ? SupportVerdict.CONFIRMED : SupportVerdict.INVALID;
    }

    /** 仅 {@link SupportVerdict#CONFIRMED} 可作为生成侧有效支撑。 */
    public static boolean isGeneratedStructuralSupport(RoadSolidLayer layer, String materialOrSpec) {
        return classifyGeneratedSupport(layer, materialOrSpec) == SupportVerdict.CONFIRMED;
    }
}
