package com.plot.plugin.road.model.persistence;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkFormatException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 道路网络持久化边界（Facade）：稳定对外 API，内部仍委托 {@link RoadNetwork} JSON 实现。
 * <p>
 * 后续 format v2 / DTO 迁移时，调用方无需改动。
 */
public final class RoadNetworkPersistence {
    private RoadNetworkPersistence() {
    }

    public static String serialize(RoadNetwork network) {
        return network.toJson();
    }

    public static RoadNetwork deserialize(String json) throws RoadNetworkFormatException {
        return RoadNetwork.fromJson(json);
    }

    public static RoadNetwork load(Path path) throws IOException {
        return RoadNetwork.loadFrom(path);
    }

    public static void save(RoadNetwork network, Path path) throws IOException {
        network.saveTo(path);
    }

    public static RoadNetwork snapshot(RoadNetwork network) {
        return network.snapshot();
    }
}
