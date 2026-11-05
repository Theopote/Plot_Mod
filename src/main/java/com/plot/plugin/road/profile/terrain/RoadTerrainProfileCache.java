package com.plot.plugin.road.profile.terrain;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按道路缓存 Level-1 地形纵断面；几何或地形版本变化即失效。 */
public final class RoadTerrainProfileCache {
    private final Map<String, RoadTerrainProfileData> byRoadId = new LinkedHashMap<>();

    public RoadTerrainProfileData get(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return null;
        }
        return byRoadId.get(roadId);
    }

    public boolean isFresh(RoadNetwork network, Road road, long terrainRevision) {
        RoadTerrainProfileData data = get(road != null ? road.getId() : null);
        if (data == null || network == null || road == null) {
            return false;
        }
        long geometry = RoadTerrainProfileSampler.geometryFingerprint(network, road);
        return data.matches(geometry, terrainRevision) && data.hasRenderableTerrain();
    }

    public boolean isStale(RoadNetwork network, Road road, long terrainRevision) {
        RoadTerrainProfileData data = get(road != null ? road.getId() : null);
        if (data == null || network == null || road == null) {
            return false;
        }
        long geometry = RoadTerrainProfileSampler.geometryFingerprint(network, road);
        return !data.matches(geometry, terrainRevision);
    }

    public RoadTerrainProfileData put(RoadTerrainProfileData data) {
        if (data == null) {
            return null;
        }
        byRoadId.put(data.roadId(), data);
        return data;
    }

    public RoadTerrainProfileData refresh(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config,
            long terrainRevision) {
        RoadTerrainProfileData sampled = RoadTerrainProfileSampler.sample(
            network, road, terrain, config, terrainRevision);
        if (sampled == null) {
            if (road != null) {
                byRoadId.remove(road.getId());
            }
            return null;
        }
        return put(sampled);
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
