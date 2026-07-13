package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 路口标线：停止线、横断面延续、人行横道与转向箭头。
 * 默认全部按拓扑/横断面自动决策，用户无需逐项编辑。
 */
public final class RoadJunctionMarkingGenerator {
    private static final double STOP_LINE_INSET_RATIO = 0.85;
    private static final double CROSSWALK_INSET_RATIO = 0.72;
    private static final double ARROW_OUTSET_RATIO = 1.18;
    private static final int CROSSWALK_STRIPE_COUNT = 4;
    /** 视为直行的最小点积（约 ±60°） */
    private static final double STRAIGHT_DOT_THRESHOLD = 0.5;
    /** 视为左右转的最小 |叉积| */
    private static final double TURN_CROSS_THRESHOLD = 0.25;

    private final RoadGenerator generator;

    public RoadJunctionMarkingGenerator(RoadGenerator generator) {
        this.generator = generator;
    }

    public void generateMarkings(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            List<Vec2d> junctionPolygon,
            int junctionY) {
        if (blocks == null || node == null || network == null || edges == null || edges.isEmpty()) {
            return;
        }

        int junctionDegree = edges.size();
        Vec2d center = node.getPosition();
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        boolean anySidewalk = edges.stream().anyMatch(edge ->
            RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig()).includeSidewalk);

        List<ApproachGeometry> approaches = buildApproachGeometries(node, edges);
        for (ApproachGeometry approach : approaches) {
            RoadEdge edge = approach.edge();
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, edge, generator.getConfig());
            Vec2d direction = approach.outward();
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }

            // 标线延续：仅当该边本身有中线/分道线时自动延续进路口
            boolean continueMarkings = node.getContinuedMarkings().resolve(
                RoadMarkingPasses.hasAnyMarkings(crossSection));
            if (continueMarkings) {
                generateContinuedMarkings(
                    blocks, edge, node.getId(), junctionPolygon, junctionY, junctionRadius, crossSection);
            }

            // 斑马线：有人行道的路口自动铺；无横断面配置时也可由 AUTO 关闭
            boolean wantCrosswalk = node.getCrosswalks().resolve(
                crossSection.includeSidewalk || (junctionDegree >= 3 && anySidewalk));
            if (wantCrosswalk && (crossSection.includeSidewalk || anySidewalk)) {
                generateCrosswalk(blocks, center, direction, junctionRadius, junctionY, crossSection);
            }

            // 转向箭头：三岔及以上路口按出口拓扑自动生成，不依赖用户开标线
            boolean wantArrows = node.getTurnArrows().resolve(junctionDegree >= 3);
            if (wantArrows) {
                TurnOptions turns = resolveTurnOptions(approach, approaches);
                generateTurnArrows(blocks, center, direction, junctionRadius, junctionY, turns);
            }
        }
    }

    private static List<ApproachGeometry> buildApproachGeometries(RoadNode node, List<RoadEdge> edges) {
        List<ApproachGeometry> approaches = new ArrayList<>(edges.size());
        for (RoadEdge edge : edges) {
            Vec2d outward = RoadJunctionGeometry.computeApproachDirection(edge, node.getId());
            if (outward.lengthSquared() < 1e-12) {
                continue;
            }
            approaches.add(new ApproachGeometry(edge, outward.normalize()));
        }
        return approaches;
    }

    /**
     * 根据当前进口与其它出口的相对方位，推断直行/左转/右转是否可用。
     */
    static TurnOptions resolveTurnOptions(ApproachGeometry approach, List<ApproachGeometry> allApproaches) {
        if (approach == null || allApproaches == null || allApproaches.isEmpty()) {
            return TurnOptions.straightOnly();
        }
        // 车辆沿 -outward 驶入路口，再沿其它边的 outward 驶出
        Vec2d travelIn = approach.outward().multiply(-1.0);
        boolean straight = false;
        boolean left = false;
        boolean right = false;
        for (ApproachGeometry other : allApproaches) {
            if (other.edge().getId().equals(approach.edge().getId())) {
                continue;
            }
            Vec2d exit = other.outward();
            double dot = travelIn.x * exit.x + travelIn.y * exit.y;
            double cross = travelIn.x * exit.y - travelIn.y * exit.x;
            if (dot >= STRAIGHT_DOT_THRESHOLD) {
                straight = true;
            } else if (cross >= TURN_CROSS_THRESHOLD) {
                left = true;
            } else if (cross <= -TURN_CROSS_THRESHOLD) {
                right = true;
            } else if (dot > 0) {
                // 锐角斜出：偏向直行
                straight = true;
            } else if (cross > 0) {
                left = true;
            } else {
                right = true;
            }
        }
        if (!straight && !left && !right) {
            return TurnOptions.straightOnly();
        }
        return new TurnOptions(straight, left, right);
    }

    private void generateContinuedMarkings(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadEdge edge,
            String nodeId,
            List<Vec2d> junctionPolygon,
            int junctionY,
            double junctionRadius,
            ResolvedCrossSection crossSection) {
        List<Vec2d> outward = RoadJunctionGeometry.extractApproachCenterline(
            edge, nodeId, junctionRadius * 1.35);
        if (outward.size() < 2) {
            return;
        }

        List<Vec2d> inward = new ArrayList<>(outward);
        Collections.reverse(inward);
        List<RoadMarkingPasses.Pass> passes = RoadMarkingPasses.fromCrossSection(crossSection);

        for (RoadMarkingPasses.Pass pass : passes) {
            List<Vec2d> offsetLine = OffsetHandler.offsetPolyline(inward, pass.offset());
            for (int i = 0; i < offsetLine.size(); i++) {
                if (!pass.solid() && i % 2 != 0) {
                    continue;
                }
                Vec2d point = offsetLine.get(i);
                if (junctionPolygon != null
                    && junctionPolygon.size() >= 3
                    && !RoadGeometryUtils.pointInPolygon(point, junctionPolygon)) {
                    continue;
                }
                addMarking(blocks, point, junctionY);
            }
        }
    }

    private void generateCrosswalk(
            RoadJunctionGenerator.JunctionBlocks blocks,
            Vec2d center,
            Vec2d direction,
            double junctionRadius,
            int junctionY,
            ResolvedCrossSection crossSection) {
        Vec2d unit = direction.normalize();
        Vec2d perpendicular = unit.perpendicular();
        double halfWidth = crossSection.carriagewayWidth / 2.0;
        double sidewalkBand = crossSection.includeSidewalk ? crossSection.sidewalkWidth : 0;
        double totalHalf = halfWidth + sidewalkBand;

        Vec2d crosswalkCenter = center.add(unit.multiply(junctionRadius * CROSSWALK_INSET_RATIO));
        for (int stripe = 0; stripe < CROSSWALK_STRIPE_COUNT; stripe++) {
            double along = (stripe - (CROSSWALK_STRIPE_COUNT - 1) / 2.0) * 0.85;
            Vec2d stripeOrigin = crosswalkCenter.add(unit.multiply(along));
            for (int lateral = (int) Math.floor(-totalHalf); lateral <= (int) Math.ceil(totalHalf); lateral++) {
                addMarking(blocks, stripeOrigin.add(perpendicular.multiply(lateral)), junctionY);
            }
        }
    }

    private void generateTurnArrows(
            RoadJunctionGenerator.JunctionBlocks blocks,
            Vec2d center,
            Vec2d direction,
            double junctionRadius,
            int junctionY,
            TurnOptions turns) {
        Vec2d unit = direction.normalize();
        Vec2d perpendicular = unit.perpendicular();
        Vec2d arrowBase = center.add(unit.multiply(junctionRadius * ARROW_OUTSET_RATIO));

        List<TurnKind> kinds = new ArrayList<>(3);
        if (turns.straight()) {
            kinds.add(TurnKind.STRAIGHT);
        }
        if (turns.left()) {
            kinds.add(TurnKind.LEFT);
        }
        if (turns.right()) {
            kinds.add(TurnKind.RIGHT);
        }
        if (kinds.isEmpty()) {
            kinds.add(TurnKind.STRAIGHT);
        }

        // 多箭头时沿横向排开，贴近真实车道箭头布置
        double spacing = 1.4;
        double startOffset = -((kinds.size() - 1) * spacing) / 2.0;
        for (int i = 0; i < kinds.size(); i++) {
            Vec2d origin = arrowBase.add(perpendicular.multiply(startOffset + i * spacing));
            placeArrowGlyph(blocks, origin, unit, perpendicular, junctionY, kinds.get(i));
        }
    }

    private void placeArrowGlyph(
            RoadJunctionGenerator.JunctionBlocks blocks,
            Vec2d base,
            Vec2d outward,
            Vec2d perpendicular,
            int junctionY,
            TurnKind kind) {
        // 箭头指向路口（-outward）
        Vec2d into = outward.multiply(-1.0);
        switch (kind) {
            case STRAIGHT -> {
                Vec2d tip = base.add(into.multiply(2.0));
                Vec2d wingL = base.add(into.multiply(0.8)).add(perpendicular.multiply(0.6));
                Vec2d wingR = base.add(into.multiply(0.8)).subtract(perpendicular.multiply(0.6));
                Vec2d tail = base.subtract(into.multiply(1.2));
                addMarking(blocks, tip, junctionY);
                addMarking(blocks, wingL, junctionY);
                addMarking(blocks, wingR, junctionY);
                addMarking(blocks, tail, junctionY);
                addMarking(blocks, base, junctionY);
            }
            case LEFT -> {
                // L 形：沿进口前进后向左
                Vec2d mid = base.add(into.multiply(1.0));
                Vec2d tip = mid.add(perpendicular.multiply(1.4));
                Vec2d wingA = tip.subtract(into.multiply(0.5)).subtract(perpendicular.multiply(0.3));
                Vec2d wingB = tip.add(into.multiply(0.5)).subtract(perpendicular.multiply(0.3));
                Vec2d tail = base.subtract(into.multiply(1.0));
                addMarking(blocks, base, junctionY);
                addMarking(blocks, mid, junctionY);
                addMarking(blocks, tip, junctionY);
                addMarking(blocks, wingA, junctionY);
                addMarking(blocks, wingB, junctionY);
                addMarking(blocks, tail, junctionY);
            }
            case RIGHT -> {
                Vec2d mid = base.add(into.multiply(1.0));
                Vec2d tip = mid.subtract(perpendicular.multiply(1.4));
                Vec2d wingA = tip.subtract(into.multiply(0.5)).add(perpendicular.multiply(0.3));
                Vec2d wingB = tip.add(into.multiply(0.5)).add(perpendicular.multiply(0.3));
                Vec2d tail = base.subtract(into.multiply(1.0));
                addMarking(blocks, base, junctionY);
                addMarking(blocks, mid, junctionY);
                addMarking(blocks, tip, junctionY);
                addMarking(blocks, wingA, junctionY);
                addMarking(blocks, wingB, junctionY);
                addMarking(blocks, tail, junctionY);
            }
        }
    }

    void generateStopLines(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY) {
        // 默认三岔及以上自动铺停止线；不再依赖道路是否开启中线标线
        if (!node.getStopLines().resolve(edges != null && edges.size() >= 3)) {
            return;
        }
        Vec2d center = node.getPosition();
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        for (RoadEdge edge : edges) {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, edge, generator.getConfig());

            Vec2d direction = RoadJunctionGeometry.computeApproachDirection(edge, node.getId());
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }

            Vec2d unit = direction.normalize();
            Vec2d perpendicular = unit.perpendicular();
            double halfWidth = Math.max(1.0, crossSection.carriagewayWidth / 2.0);
            Vec2d stopCenter = center.add(unit.multiply(junctionRadius * STOP_LINE_INSET_RATIO));

            for (int offset = -(int) Math.ceil(halfWidth); offset <= (int) Math.ceil(halfWidth); offset++) {
                addMarking(blocks, stopCenter.add(perpendicular.multiply(offset)), junctionY);
            }
        }
    }

    private void addMarking(RoadJunctionGenerator.JunctionBlocks blocks, Vec2d point, int junctionY) {
        blocks.getSolids().add(point, junctionY, RoadSolidLayer.MARKING);
    }

    record ApproachGeometry(RoadEdge edge, Vec2d outward) {
    }

    record TurnOptions(boolean straight, boolean left, boolean right) {
        static TurnOptions straightOnly() {
            return new TurnOptions(true, false, false);
        }
    }

    private enum TurnKind {
        STRAIGHT,
        LEFT,
        RIGHT
    }
}
