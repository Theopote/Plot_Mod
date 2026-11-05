package com.plot.plugin.road.profile.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按道路缓存 Level-1 地形纵断面；统一用 {@link TerrainProfileKey} 判定命中。 */
public final class RoadTerrainProfileCache {
    private final Map<String, RoadTerrainProfileData> byRoadId = new LinkedHashMap<>();

    public RoadTerrainProfileData get(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return null;
        }
        return byRoadId.get(roadId);
    }

    public boolean isFresh(TerrainProfileKey expected) {
        if (expected == null) {
            return false;
        }
        RoadTerrainProfileData data = get(expected.roadId());
        return data != null
            && data.hasRenderableTerrain()
            && data.matches(expected);
    }

    public boolean isStale(TerrainProfileKey expected) {
        if (expected == null) {
            return false;
        }
        RoadTerrainProfileData data = get(expected.roadId());
        return data != null && !data.matches(expected);
    }

    public RoadTerrainProfileData put(RoadTerrainProfileData data) {
        if (data == null) {
            return null;
        }
        byRoadId.put(data.roadId(), data);
        return data;
    }

    public void invalidate(String roadId) {
        if (roadId != null) {
            byRoadId.remove(roadId);
        }
    }

    public void clear() {
        byRoadId.clear();
    }
}
