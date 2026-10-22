package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.EditedProfileGeometry;
import com.plot.plugin.road.vertical.ProfileSource;

import java.util.List;

/**
 * 道路模型测试辅助：为几何 Edge 绑定工程 Road。
 */
public final class RoadTestFixtures {
    private RoadTestFixtures() {
    }

    public static RoadEdge geometryEdge(
            RoadNetwork network,
            String id,
            String startNodeId,
            String endNodeId,
            List<Vec2d> centerline,
            Integer width) {
        Road road = network.createRoad();
        if (width != null) {
            road.setWidth(width);
        }
        return new RoadEdge(id, startNodeId, endNodeId, centerline, road.getId(), null);
    }

    public static RoadEdge geometryEdge(
            RoadNetwork network,
            String id,
            String startNodeId,
            String endNodeId,
            List<Vec2d> centerline) {
        return geometryEdge(network, id, startNodeId, endNodeId, centerline, null);
    }

    /** 绑定编辑纵断面几何签名，使生成管线认可 EDITED 来源。 */
    public static void confirmEditedProfileGeometry(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return;
        }
        road.setProfileSource(ProfileSource.EDITED);
        road.setEditedGeometrySignature(EditedProfileGeometry.signature(network, road));
    }
}
