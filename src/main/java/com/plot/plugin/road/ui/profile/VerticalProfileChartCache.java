package com.plot.plugin.road.ui.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileRenderCache;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;

import java.util.List;

/**
 * 纵断面编辑器图表数据缓存：overview 合成图 fallback + 静态渲染层缓存。
 */
public final class VerticalProfileChartCache {

    private RoadProfileChartData cachedChartData;
    private String cachedChartRoadId = "";
    private long cachedChartNetworkRevision = Long.MIN_VALUE;
    private long cachedChartTerrainRevision = Long.MIN_VALUE;
    private double cachedChartTotalStation = Double.NaN;
    private ProfileRenderCache profileRenderCache;

    public void clear() {
        cachedChartData = null;
        cachedChartRoadId = "";
        cachedChartNetworkRevision = Long.MIN_VALUE;
        cachedChartTerrainRevision = Long.MIN_VALUE;
        cachedChartTotalStation = Double.NaN;
        profileRenderCache = null;
    }

    public void invalidateRenderCache() {
        profileRenderCache = null;
    }

    public RoadProfileChartData resolveChartData(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            ProfileEditorWindowSession windowSession) {
        if (road == null) {
            return null;
        }
        long networkRevision = windowSession != null
            ? windowSession.openRevision()
            : ctx.networkManager().getNetworkRevision();
        long terrainRevision = ctx.previewManager().getTerrainRevision();
        double roadLength = network != null
            ? RoadStationing.canonicalLength(network, road)
            : Double.NaN;
        RoadProfileChartData chart = ctx.previewManager().getOverviewChart(network, road.getId());
        if (chart != null && chart.canRenderChart()) {
            rememberChartFallback(chart, road.getId(), networkRevision, terrainRevision);
            return chart;
        }
        if (isChartFallbackValid(road.getId(), networkRevision, terrainRevision, roadLength)) {
            return cachedChartData;
        }
        return null;
    }

    public ProfileRenderCache resolveRenderCache(
            RoadUiContext ctx,
            Road road,
            String editorRoadId,
            RoadProfileChartData chartData,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadProfileIntersection> intersections,
            VoxelProfilePreviewSlot voxelSlot) {
        long cacheKey = ProfileRenderCache.computeKey(
            road.getId(),
            ctx.networkManager().getNetworkRevision(),
            ctx.previewManager().getTerrainRevision(),
            chartData,
            voxelSlot != null ? voxelSlot.state().ordinal() : -1,
            voxelSlot != null && voxelSlot.key() != null ? voxelSlot.key().hashCode() : 0);
        if (profileRenderCache != null
                && profileRenderCache.cacheKey() == cacheKey
                && road.getId().equals(editorRoadId)) {
            return profileRenderCache;
        }
        profileRenderCache = ProfileRenderCache.build(
            cacheKey, chartData, intersections, flatOverlay);
        return profileRenderCache;
    }

    private void rememberChartFallback(
            RoadProfileChartData chart,
            String roadId,
            long networkRevision,
            long terrainRevision) {
        cachedChartData = chart;
        cachedChartRoadId = roadId != null ? roadId : "";
        cachedChartNetworkRevision = networkRevision;
        cachedChartTerrainRevision = terrainRevision;
        cachedChartTotalStation = chart != null ? chart.totalStation() : Double.NaN;
    }

    /** Package-visible for unit tests: chart fallback must match road + revisions + length. */
    public static boolean matchesChartFallbackIdentity(
            String roadId,
            String cachedRoadId,
            long networkRevision,
            long cachedNetworkRevision,
            long terrainRevision,
            long cachedTerrainRevision,
            double roadLength,
            double cachedTotalStation) {
        if (roadId == null || !roadId.equals(cachedRoadId)) {
            return false;
        }
        if (networkRevision != cachedNetworkRevision || terrainRevision != cachedTerrainRevision) {
            return false;
        }
        if (!Double.isFinite(roadLength) || !Double.isFinite(cachedTotalStation)) {
            return false;
        }
        return Math.abs(roadLength - cachedTotalStation) <= 1e-3;
    }

    private boolean isChartFallbackValid(
            String roadId,
            long networkRevision,
            long terrainRevision,
            double roadLength) {
        return cachedChartData != null
            && cachedChartData.hasProfileData()
            && matchesChartFallbackIdentity(
                roadId,
                cachedChartRoadId,
                networkRevision,
                cachedChartNetworkRevision,
                terrainRevision,
                cachedChartTerrainRevision,
                roadLength,
                cachedChartTotalStation);
    }
}
