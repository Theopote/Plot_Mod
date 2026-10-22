package com.plot.plugin.road.profile.edit;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.ProfileSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEditorNetworkApplierTest {

    @Test
    void detectsProfileSourceChangeWithoutPviChange() {
        RoadNetwork baseline = roadNetwork();
        RoadNetwork working = baseline.snapshot();
        working.getRoad("r1").setProfileSource(ProfileSource.EDITED);

        assertTrue(ProfileEditorNetworkApplier.hasVerticalStateChanges(baseline, working));
    }

    @Test
    void detectsManualElevationOriginChangeWithSameValue() {
        RoadNetwork baseline = roadNetwork();
        RoadNetwork working = baseline.snapshot();
        RoadNode node = working.getNodes().values().iterator().next();
        node.setManualElevation(64.0, ManualElevationOrigin.USER);

        assertTrue(ProfileEditorNetworkApplier.hasVerticalStateChanges(baseline, working));
    }

    @Test
    void unchangedBaselineIsNotDirty() {
        RoadNetwork baseline = roadNetwork();
        RoadNetwork working = baseline.snapshot();
        assertFalse(ProfileEditorNetworkApplier.hasVerticalStateChanges(baseline, working));
    }

    private static RoadNetwork roadNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        b.setManualElevation(64.0, ManualElevationOrigin.PROFILE_PROPAGATED);
        network.createEdge(
            a.getId(),
            b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());
        return network;
    }
}
