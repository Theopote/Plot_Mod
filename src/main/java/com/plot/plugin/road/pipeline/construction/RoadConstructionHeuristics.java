package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.terrain.RoadTerrainStyle;

/**
 * Internal construction heuristics keyed by {@link RoadTerrainStyle}.
 * Product UI exposes presets only; numeric thresholds and complexity weights stay here
 * unless the user sets an explicit bridge-threshold override on config/road.
 */
public final class RoadConstructionHeuristics {

    public static final double MIN_CONSIDERATION_HEIGHT = 2.0;
    /** 软评分桥隧最短保留长度（格）；硬阈值桥隧不受此限制。 */
    public static final double MIN_STRUCTURE_RUN = 1.0;
    /**
     * 两段隧道之间的开敞路基若短于此长度（世界格），并入隧道，避免短暂开敞放坡掏山。
     * 仅合并隧道↔隧道间隙，不影响桥或路侧开敞。
     */
    public static final double MAX_TUNNEL_GAP_MERGE_BLOCKS = 5.0;

    public static final double CUT_WEIGHT = 1.0;
    public static final double FILL_WEIGHT = 1.0;
    public static final double BRIDGE_PREFERENCE = 15.0;
    public static final double BRIDGE_PREFERENCE_PER_LENGTH = 0.8;
    public static final double TUNNEL_PREFERENCE = 25.0;
    public static final double TUNNEL_PREFERENCE_PER_LENGTH = 1.5;

    public static final double ENVIRONMENT_SAMPLE_SPACING_METERS = 2.0;

    private RoadConstructionHeuristics() {
    }

    /** 地形风格默认架桥净高阈值（无用户覆盖时）。 */
    public static int bridgeThreshold(RoadTerrainStyle style) {
        return switch (style != null ? style : RoadTerrainStyle.BALANCED) {
            case FOLLOW -> 6;
            case BALANCED -> RoadParameterLimits.DEFAULT_BRIDGE_THRESHOLD;
            case SMOOTH -> 2;
        };
    }

    public static int tunnelThreshold(RoadTerrainStyle style) {
        return switch (style != null ? style : RoadTerrainStyle.BALANCED) {
            case FOLLOW -> 8;
            case BALANCED -> 4;
            case SMOOTH -> 3;
        };
    }

    /**
     * 配置级解析：显式 {@link RoadSystemConfig#getBridgeThreshold()} 优先，否则用配置地形风格表。
     */
    public static int bridgeThreshold(RoadSystemConfig config) {
        if (config != null && config.getBridgeThreshold() != null) {
            return RoadParameterLimits.clampBridgeThreshold(config.getBridgeThreshold());
        }
        return bridgeThreshold(config != null ? config.getTerrainStyle() : null);
    }

    public static int tunnelThreshold(RoadSystemConfig config) {
        return tunnelThreshold(config != null ? config.getTerrainStyle() : null);
    }

    /**
     * 单路解析：道路覆盖 → 配置覆盖 → 该路有效地形风格表。
     */
    public static int bridgeThreshold(Road road, RoadSystemConfig config) {
        if (road != null && road.getBridgeThreshold() != null) {
            return RoadParameterLimits.clampBridgeThreshold(road.getBridgeThreshold());
        }
        if (config != null && config.getBridgeThreshold() != null) {
            return RoadParameterLimits.clampBridgeThreshold(config.getBridgeThreshold());
        }
        return bridgeThreshold(RoadTerrainStyle.effective(road, config));
    }

    /**
     * 分类/路基用：在给定有效地形风格下，配置覆盖优先于风格表。
     * 道路级覆盖应在调用前解析为 {@code bridgeThresholdOverride}。
     */
    public static int bridgeThreshold(
            RoadTerrainStyle style,
            RoadSystemConfig config,
            Integer bridgeThresholdOverride) {
        if (bridgeThresholdOverride != null) {
            return RoadParameterLimits.clampBridgeThreshold(bridgeThresholdOverride);
        }
        if (config != null && config.getBridgeThreshold() != null) {
            return RoadParameterLimits.clampBridgeThreshold(config.getBridgeThreshold());
        }
        return bridgeThreshold(style != null
            ? style
            : (config != null ? config.getTerrainStyle() : null));
    }

    public static int tunnelThreshold(Road road, RoadSystemConfig config) {
        return tunnelThreshold(RoadTerrainStyle.effective(road, config));
    }

    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            RoadTerrainStyle style) {
        RoadTerrainStyle effective = style != null ? style : RoadTerrainStyle.BALANCED;
        return scoreConfig(bridgeThreshold(effective), tunnelThreshold(effective));
    }

    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            RoadSystemConfig config) {
        RoadTerrainStyle style = config != null ? config.getTerrainStyle() : null;
        return scoreConfig(bridgeThreshold(config), tunnelThreshold(style));
    }

    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            Road road,
            RoadSystemConfig config) {
        return scoreConfig(
            bridgeThreshold(road, config),
            tunnelThreshold(road, config));
    }

    /**
     * 流水线分类：权重跟风格走；架桥阈值用已解析值（含用户覆盖）。
     */
    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            RoadTerrainStyle style,
            RoadSystemConfig config,
            int resolvedBridgeThreshold) {
        RoadTerrainStyle effective = style != null
            ? style
            : (config != null ? config.getTerrainStyle() : RoadTerrainStyle.BALANCED);
        if (effective == null) {
            effective = RoadTerrainStyle.BALANCED;
        }
        return scoreConfig(
            RoadParameterLimits.clampBridgeThreshold(resolvedBridgeThreshold),
            tunnelThreshold(effective));
    }

    private static RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig(
            int bridgeThreshold,
            int tunnelThreshold) {
        return new RoadConstructionEvaluator.RoadConstructionScoreConfig(
            FILL_WEIGHT,
            BRIDGE_PREFERENCE,
            BRIDGE_PREFERENCE_PER_LENGTH,
            CUT_WEIGHT,
            TUNNEL_PREFERENCE,
            TUNNEL_PREFERENCE_PER_LENGTH,
            MIN_CONSIDERATION_HEIGHT,
            bridgeThreshold,
            tunnelThreshold);
    }
}
