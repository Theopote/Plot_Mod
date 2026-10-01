package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.RoadStationing;

import java.util.LinkedHashMap;
import java.util.Map;

/** 在 profile 采样网络上预组装 road-level 纵断面图数据。 */
public final class RoadProfileSamplingAssembler {

    private RoadProfileSamplingAssembler() {
    }

    public static Map<String, RoadProfileChartData> assembleAll(
            RoadNetwork profileNetwork,
            Map<String, RoadGenerationResult> edgeResults,
            RoadSystemConfig config) {
        Map<String, RoadProfileChartData> roadProfiles = new LinkedHashMap<>();
        if (profileNetwork == null || edgeResults == null || config == null || edgeResults.isEmpty()) {
            return roadProfiles;
        }
        for (Road road : profileNetwork.getRoads().values()) {
            if (!RoadStationing.isStationable(profileNetwork, road)) {
                continue;
            }
            RoadProfileChartAssembler.assemble(profileNetwork, road, config, edgeResults)
                .ifPresent(chart -> roadProfiles.put(road.getId(), chart));
        }
        return roadProfiles;
    }
}
