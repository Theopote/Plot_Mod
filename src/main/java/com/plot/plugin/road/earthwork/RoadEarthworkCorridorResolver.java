package com.plot.plugin.road.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 从道路几何解析土方走廊轮廓与中心线。
 */
public final class RoadEarthworkCorridorResolver {

    private RoadEarthworkCorridorResolver() {
    }

    public static List<Vec2d> resolveOutline(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config,
            int extraMarginBlocks) {
        if (network == null || edge == null || config == null) {
            return List.of();
        }
        List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
        if (centerline.size() < 2) {
            return List.of();
        }
        double halfWidth = resolveCorridorHalfWidth(network, edge, config, extraMarginBlocks);
        if (halfWidth <= 0.0) {
            return List.of();
        }
        return buildCorridorPolygon(centerline, halfWidth);
    }

    public static List<Vec2d> resolveCenterline(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null) {
            return List.of();
        }
        return RoadPlanGeometry.resolveEdgeCenterline(network, edge);
    }

    public static double resolveCorridorHalfWidth(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config,
            int extraMarginBlocks) {
        ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, edge, config);
        double halfWidth = crossSection.carriagewayHalfWidth() + crossSection.outerBandWidth();
        if (crossSection.includeDrain) {
            halfWidth += 1.0;
        }
        return halfWidth + Math.max(0, extraMarginBlocks);
    }

    static List<Vec2d> buildCorridorPolygon(List<Vec2d> centerline, double halfWidth) {
        List<Vec2d> left = OffsetHandler.offsetPolyline(centerline, halfWidth);
        List<Vec2d> right = OffsetHandler.offsetPolyline(centerline, -halfWidth);
        if (left.size() < 2 || right.size() < 2) {
            return List.of();
        }
        List<Vec2d> polygon = new ArrayList<>(left.size() + right.size());
        polygon.addAll(left);
        for (int index = right.size() - 1; index >= 0; index--) {
            polygon.add(new Vec2d(right.get(index).x, right.get(index).y));
        }
        return polygon.size() >= 3 ? polygon : List.of();
    }
}
