package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JunctionCornerRadiusTest {

    @Test
    void crossingNullDoesNotOverrideNodeExplicitRadius() {
        RoadSystemConfig config = new RoadSystemConfig("corner-auth");
        config.setDefaultCornerRadius(9.0f);
        RoadNode node = new RoadNetwork().createNode(new Vec2d(0, 0));
        node.setCornerRadius(4.0);
        RoadCrossing unset = RoadCrossing.atGrade("a", 0, "b", 0, new Vec2d(0, 0));

        assertEquals(4.0, JunctionCornerRadius.resolve(node, unset, config), 1e-9);
        assertEquals(0.0, unset.effectiveCornerRadius(config.getDefaultCornerRadius()), 1e-9);
    }

    @Test
    void explicitCrossingZeroClosesFilletEvenIfNodeHasRadius() {
        RoadSystemConfig config = new RoadSystemConfig("corner-off");
        config.setDefaultCornerRadius(9.0f);
        RoadNode node = new RoadNetwork().createNode(new Vec2d(0, 0));
        node.setCornerRadius(4.0);
        RoadCrossing closed = RoadCrossing.atGrade("a", 0, "b", 0, new Vec2d(0, 0))
            .withCornerRadius(0.0);

        assertEquals(0.0, JunctionCornerRadius.resolve(node, closed, config), 1e-9);
    }

    @Test
    void defaultConfigRadiusIsNotInheritedWhenUnset() {
        RoadSystemConfig config = new RoadSystemConfig("corner-default-off");
        config.setDefaultCornerRadius(9.0f);
        RoadNode node = new RoadNetwork().createNode(new Vec2d(0, 0));

        assertEquals(0.0, JunctionCornerRadius.resolve(node, (RoadCrossing) null, config), 1e-9);
        assertEquals(0.0, JunctionCornerRadius.resolve(
            node, RoadCrossing.atGrade("a", 0, "b", 0, new Vec2d(0, 0)), config), 1e-9);
    }

    @Test
    void applyToJunctionNodePreservesNodeWhenCrossingUnset() {
        RoadNode junction = new RoadNetwork().createNode(new Vec2d(0, 0));
        junction.setCornerRadius(5.0);
        JunctionCornerRadius.applyToJunctionNode(
            junction, RoadCrossing.atGrade("a", 0, "b", 0, new Vec2d(0, 0)));
        assertEquals(5.0, junction.getCornerRadius(), 1e-9);

        JunctionCornerRadius.applyToJunctionNode(
            junction,
            new RoadCrossing("gs", "a", 0, "b", 0, new Vec2d(0, 0),
                CrossingType.GRADE_SEPARATED, "a", 8.0, null, null));
        assertEquals(0.0, junction.getCornerRadius(), 1e-9);
    }
}
