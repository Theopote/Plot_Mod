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
 * 路口标线：停止线以外的横断面延续、人行横道与转向箭头。
 */
public final class RoadJunctionMarkingGenerator {
    private static final double STOP_LINE_INSET_RATIO = 0.85;
    private static final double CROSSWALK_INSET_RATIO = 0.72;
    private static final double ARROW_OUTSET_RATIO = 1.18;
    private static final int CROSSWALK_STRIPE_COUNT = 4;

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

        for (RoadEdge edge : edges) {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, edge, generator.getConfig());
            if (!RoadMarkingPasses.hasAnyMarkings(crossSection)) {
                continue;
            }

            Vec2d direction = RoadJunctionGeometry.computeApproachDirection(edge, node.getId());
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }

            generateContinuedMarkings(
                blocks, edge, node.getId(), junctionPolygon, junctionY, junctionRadius, crossSection,
                node.getContinuedMarkings().resolve(true));
            if (node.getCrosswalks().resolve(crossSection.includeSidewalk)) {
                generateCrosswalk(blocks, center, direction, junctionRadius, junctionY, crossSection);
            }
            if (node.getTurnArrows().resolve(crossSection.laneCount >= 1 && junctionDegree >= 3)) {
                generateTurnArrow(blocks, center, direction, junctionRadius, junctionY);
            }
        }
    }

    private void generateContinuedMarkings(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadEdge edge,
            String nodeId,
            List<Vec2d> junctionPolygon,
            int junctionY,
            double junctionRadius,
            ResolvedCrossSection crossSection,
            boolean enabled) {
        if (!enabled) {
            return;
        }
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

    private void generateTurnArrow(
            RoadJunctionGenerator.JunctionBlocks blocks,
            Vec2d center,
            Vec2d direction,
            double junctionRadius,
            int junctionY) {
        Vec2d unit = direction.normalize();
        Vec2d perpendicular = unit.perpendicular();
        Vec2d arrowBase = center.add(unit.multiply(junctionRadius * ARROW_OUTSET_RATIO));
        Vec2d arrowTip = arrowBase.add(unit.multiply(-2.0));
        Vec2d wingLeft = arrowBase.add(perpendicular.multiply(0.6));
        Vec2d wingRight = arrowBase.subtract(perpendicular.multiply(0.6));
        Vec2d tail = arrowBase.add(unit.multiply(1.5));

        addMarking(blocks, arrowTip, junctionY);
        addMarking(blocks, wingLeft, junctionY);
        addMarking(blocks, wingRight, junctionY);
        addMarking(blocks, tail, junctionY);
    }

    void generateStopLines(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY) {
        if (!node.getStopLines().resolve(true)) {
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
            if (!RoadMarkingPasses.hasAnyMarkings(crossSection)) {
                continue;
            }

            Vec2d direction = RoadJunctionGeometry.computeApproachDirection(edge, node.getId());
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }

            Vec2d unit = direction.normalize();
            Vec2d perpendicular = unit.perpendicular();
            double halfWidth = crossSection.carriagewayWidth / 2.0;
            Vec2d stopCenter = center.add(unit.multiply(junctionRadius * STOP_LINE_INSET_RATIO));

            for (int offset = -(int) Math.ceil(halfWidth); offset <= (int) Math.ceil(halfWidth); offset++) {
                addMarking(blocks, stopCenter.add(perpendicular.multiply(offset)), junctionY);
            }
        }
    }

    private void addMarking(RoadJunctionGenerator.JunctionBlocks blocks, Vec2d point, int junctionY) {
        blocks.getSolids().add(point, junctionY, RoadSolidLayer.MARKING);
    }
}
