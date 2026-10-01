package com.plot.plugin.road.profile;

import com.plot.plugin.road.model.Road;

import java.util.List;

/** 纵断面编辑器内可测试的 Road 导航逻辑。 */
public final class RoadProfileRoadNavigator {

    private RoadProfileRoadNavigator() {
    }

    public static int indexOf(List<Road> roads, String roadId) {
        if (roads == null || roadId == null || roadId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < roads.size(); i++) {
            if (roadId.equals(roads.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    public static String normalizeRoadId(List<Road> roads, String roadId) {
        if (roads == null || roads.isEmpty()) {
            return null;
        }
        if (roadId != null && !roadId.isBlank() && indexOf(roads, roadId) >= 0) {
            return roadId;
        }
        return roads.getFirst().getId();
    }

    public static String nextRoadId(List<Road> roads, String currentRoadId) {
        if (roads == null || roads.isEmpty()) {
            return null;
        }
        int index = indexOf(roads, currentRoadId);
        if (index < 0) {
            return roads.getFirst().getId();
        }
        return roads.get((index + 1) % roads.size()).getId();
    }

    public static String previousRoadId(List<Road> roads, String currentRoadId) {
        if (roads == null || roads.isEmpty()) {
            return null;
        }
        int index = indexOf(roads, currentRoadId);
        if (index < 0) {
            return roads.getFirst().getId();
        }
        int target = (index - 1 + roads.size()) % roads.size();
        return roads.get(target).getId();
    }
}
