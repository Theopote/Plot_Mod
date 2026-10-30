package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator;
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
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS)
        );

        boolean anySidewalk = edges.stream().anyMatch(edge ->
            RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig()).includeSidewalk);

        List<ApproachGeometry> approaches = buildApproachGeometries(node, edges, network);
        List<ApproachGeometry> railApproaches = new ArrayList<>();
        for (ApproachGeometry approach : approaches) {
            RoadEdge edge = approach.edge();
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, edge, generator.getConfig());
            Vec2d direction = approach.outward();
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }

            boolean railApproach = crossSection.centerLineStyle == CenterLineStyle.RAIL;
            if (railApproach) {
                railApproaches.add(approach);
                // 铁路延续与公路「延续标线」开关解耦：关标线不得掐断进路口铁轨
                generateContinuedRailApproach(
                    blocks, edge, node.getId(), junctionPolygon, junctionY, junctionRadius, crossSection, network);
                continue;
            }

            // 公路标线延续：仅当该边本身有中线/分道线时按路口开关决定
            boolean continueMarkings = node.getContinuedMarkings().resolve(
                RoadMarkingPasses.hasAnyMarkings(crossSection));
            if (continueMarkings) {
                generateContinuedMarkings(
                    blocks, edge, node.getId(), junctionPolygon, junctionY, junctionRadius, crossSection, network);
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
        placeRailwayTurnoutMarkers(
            blocks, node, network, center, junctionY, junctionRadius, railApproaches);
    }

    /**
     * 立交上层铁路：在桥面标高贯通轨枕+铁轨，避免被下层广场切断轨面高程。
     */
    public void generateElevatedRailwayContinuation(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> elevatedEdges,
            int elevatedDeckY) {
        if (blocks == null || node == null || network == null
                || elevatedEdges == null || elevatedEdges.isEmpty()) {
            return;
        }
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            elevatedEdges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()) / 2.0,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS));
        Vec2d center = node.getPosition();
        List<ApproachGeometry> railApproaches = new ArrayList<>();
        for (ApproachGeometry approach : buildApproachGeometries(node, elevatedEdges, network)) {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, approach.edge(), generator.getConfig());
            if (crossSection.centerLineStyle != CenterLineStyle.RAIL) {
                continue;
            }
            railApproaches.add(approach);
            List<Vec2d> inward = extractInwardApproach(
                approach, node.getId(), network, junctionRadius);
            if (inward.size() < 2) {
                continue;
            }
            placeElevatedRailStrip(blocks, inward, elevatedDeckY, crossSection);
        }
        if (railApproaches.size() < 2) {
            return;
        }
        List<Vec2d> outwards = new ArrayList<>(railApproaches.size());
        for (ApproachGeometry approach : railApproaches) {
            outwards.add(approach.outward());
        }
        var through = RoadRailwayTurnout.resolveThroughPairIndices(outwards);
        if (through.isEmpty()) {
            // 无对向时仍保证岔心有轨，衔接两侧进口端点
            blocks.getSolids().add(
                center,
                elevatedDeckY + 1,
                RoadSolidLayer.MARKING,
                "minecraft:rail",
                RoadRailwayTurnout.PRIORITY_THROUGH_CONNECT);
            return;
        }
        int[] pair = through.get();
        List<Vec2d> inwardA = extractInwardApproach(
            railApproaches.get(pair[0]), node.getId(), network, junctionRadius);
        List<Vec2d> inwardB = extractInwardApproach(
            railApproaches.get(pair[1]), node.getId(), network, junctionRadius);
        List<Vec2d> throughPath = RoadRailwayTurnout.densifyThroughPath(inwardA, inwardB, center, 0.5);
        ResolvedCrossSection railCs = RoadModelUtils.resolveCrossSection(
            network, railApproaches.get(pair[0]).edge(), generator.getConfig());
        String railId = RoadMarkingGenerator.resolveRailBlockId(railCs.markingMaterial);
        for (Vec2d point : throughPath) {
            blocks.getSolids().add(
                point,
                elevatedDeckY,
                RoadSolidLayer.MARKING,
                RoadMarkingGenerator.RAIL_SLEEPER_MATERIAL,
                RoadRailwayTurnout.PRIORITY_APPROACH_RAIL);
            blocks.getSolids().add(
                point,
                elevatedDeckY + 1,
                RoadSolidLayer.MARKING,
                railId,
                RoadRailwayTurnout.PRIORITY_THROUGH_CONNECT);
        }
    }

    private void placeElevatedRailStrip(
            RoadJunctionGenerator.JunctionBlocks blocks,
            List<Vec2d> inward,
            int elevatedDeckY,
            ResolvedCrossSection crossSection) {
        String railId = RoadMarkingGenerator.resolveRailBlockId(crossSection.markingMaterial);
        for (Vec2d point : RoadRailwayTurnout.densifyPolyline(inward, 0.5)) {
            blocks.getSolids().add(
                point,
                elevatedDeckY,
                RoadSolidLayer.MARKING,
                RoadMarkingGenerator.RAIL_SLEEPER_MATERIAL,
                RoadRailwayTurnout.PRIORITY_APPROACH_RAIL);
            blocks.getSolids().add(
                point,
                elevatedDeckY + 1,
                RoadSolidLayer.MARKING,
                railId,
                RoadRailwayTurnout.PRIORITY_APPROACH_RAIL);
        }
    }

    /**
     * 三路及以上铁路进口：直股连通骨架（普通轨）+ 装饰动力轨 + 岔心外侧探测轨。
     * 无足够对向进口时不强制选直股，仅保留各进口延续轨。
     */
    private void placeRailwayTurnoutMarkers(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            Vec2d center,
            int junctionY,
            double junctionRadius,
            List<ApproachGeometry> railApproaches) {
        if (railApproaches.size() < 3) {
            return;
        }

        List<Vec2d> outwards = new ArrayList<>(railApproaches.size());
        for (ApproachGeometry approach : railApproaches) {
            outwards.add(approach.outward());
        }
        var through = RoadRailwayTurnout.resolveThroughPairIndices(outwards);
        if (through.isEmpty()) {
            return;
        }
        int[] pair = through.get();
        ApproachGeometry armA = railApproaches.get(pair[0]);
        ApproachGeometry armB = railApproaches.get(pair[1]);
        List<Vec2d> inwardA = extractInwardApproach(armA, node.getId(), network, junctionRadius);
        List<Vec2d> inwardB = extractInwardApproach(armB, node.getId(), network, junctionRadius);
        List<Vec2d> throughPath = RoadRailwayTurnout.densifyThroughPath(inwardA, inwardB, center, 0.5);

        // 1) 直股连通骨架：普通铁轨贯穿岔心，优先级高于进口延续轨与装饰动力轨。
        for (Vec2d point : throughPath) {
            if (!RoadRailwayTurnout.inThroughPowerBand(point, center, junctionRadius)
                    && point.distanceSquared(center) > 1e-6) {
                continue;
            }
            blocks.getSolids().add(
                point,
                junctionY + 1,
                RoadSolidLayer.MARKING,
                "minecraft:rail",
                RoadRailwayTurnout.PRIORITY_THROUGH_CONNECT);
        }

        // 2) 装饰动力轨：离开岔心的直股段可见标记；岔心保留普通轨供支线汇合。
        for (Vec2d point : throughPath) {
            if (!RoadRailwayTurnout.inPoweredDecorBand(point, center, junctionRadius)) {
                continue;
            }
            blocks.getSolids().add(
                point,
                junctionY + 1,
                RoadSolidLayer.MARKING,
                "minecraft:powered_rail",
                RoadRailwayTurnout.PRIORITY_POWERED_DECOR);
        }

        // 3) 探测轨标记在岔心外侧一格，避免占住汇合点破坏支线接入。
        RoadRailwayTurnout.detectorMarkPosition(center, armA.outward(), armB.outward())
            .ifPresent(mark -> blocks.getSolids().add(
                mark,
                junctionY + 1,
                RoadSolidLayer.MARKING,
                "minecraft:detector_rail",
                RoadRailwayTurnout.PRIORITY_DETECTOR_MARK));
    }

    private static List<Vec2d> extractInwardApproach(
            ApproachGeometry approach,
            String nodeId,
            RoadNetwork network,
            double junctionRadius) {
        List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(network, approach.edge());
        List<Vec2d> outward = RoadJunctionGeometry.extractApproachCenterline(
            centerline, approach.edge(), nodeId, junctionRadius * 1.35);
        if (outward.size() < 2) {
            return List.of();
        }
        List<Vec2d> inward = new ArrayList<>(outward);
        Collections.reverse(inward);
        return inward;
    }

    private static List<ApproachGeometry> buildApproachGeometries(
            RoadNode node,
            List<RoadEdge> edges,
            RoadNetwork network) {
        var centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
        List<ApproachGeometry> approaches = new ArrayList<>(edges.size());
        for (RoadEdge edge : edges) {
            List<Vec2d> centerline = centerlineResolver.apply(edge);
            Vec2d outward = RoadJunctionGeometry.computeApproachDirection(centerline, edge, node.getId());
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

    private void generateContinuedRailApproach(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadEdge edge,
            String nodeId,
            List<Vec2d> junctionPolygon,
            int junctionY,
            double junctionRadius,
            ResolvedCrossSection crossSection,
            RoadNetwork network) {
        List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
        List<Vec2d> outward = RoadJunctionGeometry.extractApproachCenterline(
            centerline, edge, nodeId, junctionRadius * 1.35);
        if (outward.size() < 2) {
            return;
        }
        List<Vec2d> inward = new ArrayList<>(outward);
        Collections.reverse(inward);
        generateContinuedRails(blocks, inward, junctionPolygon, junctionY, crossSection);
    }

    private void generateContinuedMarkings(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadEdge edge,
            String nodeId,
            List<Vec2d> junctionPolygon,
            int junctionY,
            double junctionRadius,
            ResolvedCrossSection crossSection,
            RoadNetwork network) {
        if (crossSection.centerLineStyle == CenterLineStyle.RAIL) {
            // 铁路走 generateContinuedRailApproach，避免被公路标线开关误伤
            generateContinuedRailApproach(
                blocks, edge, nodeId, junctionPolygon, junctionY, junctionRadius, crossSection, network);
            return;
        }
        List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
        List<Vec2d> outward = RoadJunctionGeometry.extractApproachCenterline(
            centerline, edge, nodeId, junctionRadius * 1.35);
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

    /**
     * 铁路进口：路口广场内延续轨枕 + 铁轨（多路汇合处形成可连通的道岔状轨网）。
     */
    private void generateContinuedRails(
            RoadJunctionGenerator.JunctionBlocks blocks,
            List<Vec2d> inward,
            List<Vec2d> junctionPolygon,
            int junctionY,
            ResolvedCrossSection crossSection) {
        String railId = RoadMarkingGenerator.resolveRailBlockId(crossSection.markingMaterial);
        String sleeperId = RoadMarkingGenerator.RAIL_SLEEPER_MATERIAL;
        // 铁轨沿整段进口采样铺设（含广场外缘），保证与路段轨衔接；
        // 轨枕仅落在广场多边形内，避免广场外道床被木板覆盖。
        for (int i = 0; i < inward.size(); i++) {
            Vec2d point = inward.get(i);
            boolean inPlaza = junctionPolygon == null
                || junctionPolygon.size() < 3
                || RoadGeometryUtils.pointInPolygon(point, junctionPolygon);
            Vec2d tangent = sampleTangent(inward, i);
            Vec2d lateral = tangent.perpendicular();
            if (lateral.lengthSquared() > 1e-12) {
                lateral = lateral.normalize();
            } else {
                lateral = new Vec2d(1, 0);
            }
            if (inPlaza) {
                for (int lat = -RoadMarkingGenerator.RAIL_SLEEPER_HALF_WIDTH;
                     lat <= RoadMarkingGenerator.RAIL_SLEEPER_HALF_WIDTH;
                     lat++) {
                    blocks.getSolids().add(
                        point.add(lateral.multiply(lat)),
                        junctionY,
                        RoadSolidLayer.MARKING,
                        sleeperId);
                }
            }
            blocks.getSolids().add(
                point,
                junctionY + 1,
                RoadSolidLayer.MARKING,
                railId,
                RoadRailwayTurnout.PRIORITY_APPROACH_RAIL);
        }
    }

    private static Vec2d sampleTangent(List<Vec2d> polyline, int index) {
        if (polyline == null || polyline.isEmpty()) {
            return new Vec2d(1, 0);
        }
        if (polyline.size() == 1) {
            return new Vec2d(1, 0);
        }
        if (index <= 0) {
            return polyline.get(1).subtract(polyline.get(0));
        }
        if (index >= polyline.size() - 1) {
            return polyline.getLast().subtract(polyline.get(polyline.size() - 2));
        }
        return polyline.get(index + 1).subtract(polyline.get(index - 1));
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
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS)
        );

        var centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);
        for (RoadEdge edge : edges) {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, edge, generator.getConfig());
            if (crossSection.centerLineStyle == CenterLineStyle.RAIL) {
                continue;
            }

            List<Vec2d> centerline = centerlineResolver.apply(edge);
            Vec2d direction = RoadJunctionGeometry.computeApproachDirection(centerline, edge, node.getId());
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
