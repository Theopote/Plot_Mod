package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.JunctionPreviewGeometry;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 从路网合成交叉点叠加层条目。 */
public final class RoadJunctionOverlayController {
    private RoadJunctionOverlayController() {
    }

    public static List<RoadJunctionOverlayEntry> snapshot(
            RoadNetwork network,
            RoadNetworkBuilder builder,
            String selectedCrossingId,
            String selectedNodeId) {
        return snapshot(network, builder, null, selectedCrossingId, selectedNodeId, false);
    }

    public static List<RoadJunctionOverlayEntry> snapshot(
            RoadNetwork network,
            RoadNetworkBuilder builder,
            RoadSystemConfig config,
            String selectedCrossingId,
            String selectedNodeId,
            boolean showFilletPreview) {
        return snapshot(
            network, builder, config, null, selectedCrossingId, selectedNodeId, showFilletPreview);
    }

    /**
     * @param coordinates 主画布投影；非空时倒圆角半宽/半径与道路走廊叠加层同尺度
     */
    public static List<RoadJunctionOverlayEntry> snapshot(
            RoadNetwork network,
            RoadNetworkBuilder builder,
            RoadSystemConfig config,
            ICoordinateService coordinates,
            String selectedCrossingId,
            String selectedNodeId,
            boolean showFilletPreview) {
        if (network == null || builder == null) {
            return List.of();
        }
        List<Vec2d> sharpOutline = List.of();
        List<List<Vec2d>> filletArcs = List.of();
        if (showFilletPreview && config != null) {
            JunctionGeometryResult geometry = JunctionPreviewGeometry.resolve(
                network, config, selectedNodeId, selectedCrossingId, coordinates);
            if (geometry.hasFillPolygon() || !geometry.fillets().isEmpty()) {
                sharpOutline = geometry.sharpPolygon();
                filletArcs = JunctionFilletArcSampler.sampleArcs(geometry);
            }
        }

        List<RoadJunctionOverlayEntry> entries = new ArrayList<>();
        Set<String> coveredPositions = new HashSet<>();

        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing == null || crossing.position() == null) {
                continue;
            }
            boolean selected = crossing.id().equals(selectedCrossingId);
            entries.add(new RoadJunctionOverlayEntry(
                IntersectionOverlaySource.CROSSING,
                crossing.id(),
                crossing.position(),
                resolveCrossingKind(crossing, selected),
                selected,
                selected ? sharpOutline : List.of(),
                selected ? filletArcs : List.of()));
            coveredPositions.add(positionKey(crossing.position()));
        }

        for (RoadStackCrossing stack : network.getStackCrossings().values()) {
            if (stack == null || stack.position() == null) {
                continue;
            }
            boolean selected = stack.id().equals(selectedCrossingId);
            entries.add(new RoadJunctionOverlayEntry(
                IntersectionOverlaySource.STACK_CROSSING,
                stack.id(),
                stack.position(),
                resolveStackKind(stack, selected),
                selected));
            coveredPositions.add(positionKey(stack.position()));
        }

        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 2) {
                continue;
            }
            if (!node.isJunction() && !RoadGraphQueries.isSimpleCrossing(node, network)) {
                continue;
            }
            if (node.getPosition() != null
                    && coveredPositions.contains(positionKey(node.getPosition()))) {
                continue;
            }
            boolean selected = node.getId().equals(selectedNodeId)
                && (selectedCrossingId == null || selectedCrossingId.isBlank());
            entries.add(new RoadJunctionOverlayEntry(
                IntersectionOverlaySource.LEGACY_NODE,
                node.getId(),
                node.getPosition(),
                resolveLegacyKind(node, network, builder, selected),
                selected,
                selected ? sharpOutline : List.of(),
                selected ? filletArcs : List.of()));
        }
        return entries;
    }

    private static RoadJunctionOverlayKind resolveStackKind(RoadStackCrossing stack, boolean selected) {
        if (selected) {
            return RoadJunctionOverlayKind.SELECTED;
        }
        if (stack.isGradeSeparated()) {
            return RoadJunctionOverlayKind.GRADE_SEPARATED;
        }
        return RoadJunctionOverlayKind.AT_GRADE;
    }

    private static RoadJunctionOverlayKind resolveCrossingKind(RoadCrossing crossing, boolean selected) {
        if (selected) {
            return RoadJunctionOverlayKind.SELECTED;
        }
        if (crossing.type() == CrossingType.GRADE_SEPARATED) {
            return RoadJunctionOverlayKind.GRADE_SEPARATED;
        }
        return RoadJunctionOverlayKind.AT_GRADE;
    }

    private static RoadJunctionOverlayKind resolveLegacyKind(
            RoadNode node,
            RoadNetwork network,
            RoadNetworkBuilder builder,
            boolean selected) {
        if (selected) {
            return RoadJunctionOverlayKind.SELECTED;
        }
        if (node.isGradeSeparated()) {
            return RoadJunctionOverlayKind.GRADE_SEPARATED;
        }
        RoadNetworkBuilder.JunctionType type = builder.classify(node);
        if (type == RoadNetworkBuilder.JunctionType.COMPLEX) {
            return RoadJunctionOverlayKind.COMPLEX;
        }
        return RoadJunctionOverlayKind.AT_GRADE;
    }

    private static String positionKey(Vec2d position) {
        return Math.round(position.x * 100.0) + "," + Math.round(position.y * 100.0);
    }

    /**
     * 画布点击命中测试：返回距点击位置最近的交叉点。
     */
    public static IntersectionHit hitTest(
            List<RoadJunctionOverlayEntry> entries,
            double worldX,
            double worldY,
            double worldRadius) {
        if (entries == null || entries.isEmpty() || worldRadius <= 0.0) {
            return null;
        }
        IntersectionHit closest = null;
        double closestDistance = worldRadius;
        for (RoadJunctionOverlayEntry entry : entries) {
            if (entry == null || entry.position() == null) {
                continue;
            }
            double distance = Math.hypot(
                worldX - entry.position().x,
                worldY - entry.position().y);
            if (distance <= closestDistance) {
                closestDistance = distance;
                closest = new IntersectionHit(entry.source(), entry.sourceId());
            }
        }
        return closest;
    }
}
