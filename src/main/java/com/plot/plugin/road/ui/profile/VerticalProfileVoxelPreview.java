package com.plot.plugin.road.ui.profile;

import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.RoadVoxelProfileCompileJob;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewCompileRequest;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewKey;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewState;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

/**
 * 纵断面编辑器「更新方块线」异步编译与 overlay 解析。
 */
public final class VerticalProfileVoxelPreview {

    private static final long VOXEL_COMPILE_TICK_BUDGET_NANOS = 8_000_000L;

    /** 供图表/交点解析注入，避免与壳层循环依赖。 */
    public interface Host {
        ProfileEditorWindowSession windowSession();

        ProfileEditSession editSession();

        RoadProfileChartData resolveChartData(RoadUiContext ctx, RoadNetwork network, Road road);

        List<RoadProfileIntersection> resolveIntersections(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            RoadProfileChartData chart,
            boolean allowCache);

        RoadGenerator gradeSeparationGenerator(
            RoadUiContext ctx, RoadSystemConfig config, long cacheKey);

        RoadElevationBounds resolveElevationBounds(RoadUiContext ctx);

        void onVoxelPreviewReady();
    }

    private final Host host;
    VoxelProfilePreviewCompileRequest pendingVoxelCompile;
    RoadVoxelProfileCompileJob activeVoxelCompile;
    ProfileEditorWindowSession voxelCompileOwner;
    VoxelProfilePreviewSlot voxelCompileSlot;
    long voxelCompileNetworkRevision;
    World voxelCompileWorld;

    public VerticalProfileVoxelPreview(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void clear() {
        cancel();
    }

    public void request(RoadUiContext ctx, Road road) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (windowSession == null || road == null) {
            return;
        }
        RoadNetwork editingNetwork = windowSession.editingNetwork();
        Road editingRoad = editingNetwork.getRoad(road.getId());
        if (editingRoad == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ProfileEditSession editSession = host.editSession();
        RoadNetwork compileNetwork = editSession.isolatedCompileNetwork(editingNetwork, editingRoad, config);
        Road compileRoad = compileNetwork.getRoad(road.getId());
        if (compileRoad == null) {
            return;
        }
        RoadVerticalAlignment effective = compileRoad.getVerticalAlignment();
        if (effective != null) {
            compileRoad.setVerticalAlignment(effective);
        }
        ProfileCompileOptions options = ProfileCompileOptions.defaults();
        RoadElevationBounds elevationBounds = host.resolveElevationBounds(ctx);
        RoadProfileChartData baseChart = host.resolveChartData(ctx, editingNetwork, editingRoad);
        List<RoadProfileIntersection> intersections = host.resolveIntersections(
            ctx, compileNetwork, compileRoad, config, baseChart, true);
        VoxelProfilePreviewKey key = VoxelProfilePreviewKey.of(
            compileNetwork,
            compileRoad,
            effective,
            intersections,
            com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints.empty(),
            elevationBounds,
            ctx.previewManager().getTerrainRevision(),
            config,
            options);
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(editingRoad.getId());
        cancel();
        slot.markComputing(key);
        long generatorCacheKey = System.identityHashCode(config) ^ editingRoad.getId().hashCode();
        pendingVoxelCompile = new VoxelProfilePreviewCompileRequest(
            editingRoad.getId(),
            compileNetwork,
            effective,
            key,
            options,
            elevationBounds,
            generatorCacheKey);
        voxelCompileOwner = windowSession;
        voxelCompileSlot = slot;
        voxelCompileNetworkRevision = ctx.networkManager().getNetworkRevision();
        voxelCompileWorld = RoadNetworkGenerator.getClientWorld();
    }

    public void cancel() {
        if (activeVoxelCompile != null) {
            activeVoxelCompile.cancel();
        }
        if (voxelCompileSlot != null && pendingVoxelCompile != null
                && Objects.equals(voxelCompileSlot.key(), pendingVoxelCompile.key())
                && voxelCompileSlot.state() == VoxelProfilePreviewState.COMPUTING) {
            voxelCompileSlot.markStale();
        }
        activeVoxelCompile = null;
        pendingVoxelCompile = null;
        voxelCompileOwner = null;
        voxelCompileSlot = null;
        voxelCompileWorld = null;
    }

    public void tick(RoadUiContext ctx) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (pendingVoxelCompile == null || windowSession == null || ctx == null) {
            return;
        }
        VoxelProfilePreviewCompileRequest request = pendingVoxelCompile;
        Road compileRoad = request.compileNetwork().getRoad(request.roadId());
        if (compileRoad == null) {
            cancel();
            return;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(request.roadId());
        if (voxelCompileOwner != windowSession || slot != voxelCompileSlot
                || slot.state() != VoxelProfilePreviewState.COMPUTING
                || !Objects.equals(slot.key(), request.key())
                || ctx.networkManager().getNetworkRevision() != voxelCompileNetworkRevision
                || ctx.previewManager().getTerrainRevision() != request.key().fingerprint().terrainRevision()
                || voxelCompileWorld != RoadNetworkGenerator.getClientWorld()
                || !Objects.equals(request.elevationBounds(), host.resolveElevationBounds(ctx))) {
            cancel();
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        try {
            if (activeVoxelCompile == null) {
                RoadGenerator generator = host.gradeSeparationGenerator(
                    ctx, config, request.generatorCacheKey());
                activeVoxelCompile = new RoadVoxelProfileCompileJob(
                    request.compileNetwork(), compileRoad, config, generator,
                    resolveTerrainSampler(generator),
                    ctx.previewManager().getLastNodeElevations(), request.options(),
                    request.elevationBounds(), request.alignmentOverride());
            }
            activeVoxelCompile.advance(VOXEL_COMPILE_TICK_BUDGET_NANOS);
            if (activeVoxelCompile.isDone()) {
                windowSession.storeVoxelPreview(request.roadId(), request.key(), activeVoxelCompile.result());
                host.onVoxelPreviewReady();
                cancel();
            }
        } catch (RuntimeException ex) {
            slot.markFailed(request.key(), ex.getMessage() != null ? ex.getMessage() : "compile error");
            cancel();
        }
    }

    public RoadProfileChartData resolveEffectiveChartData(Road road, RoadProfileChartData base) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (base == null || road == null || windowSession == null) {
            return base;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(road.getId());
        RoadProfileChartData overlay = slot.displayOverlay();
        if (overlay != null && overlay.hasBuildSamples()) {
            return overlay;
        }
        return base;
    }

    public boolean isVoxelBuildProfileStale(Road road) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (windowSession == null || road == null) {
            return false;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(road.getId());
        return slot.state() == VoxelProfilePreviewState.STALE
            || slot.state() == VoxelProfilePreviewState.NOT_COMPUTED
            || slot.state() == VoxelProfilePreviewState.FAILED
            || slot.state() == VoxelProfilePreviewState.COMPUTING;
    }

    public VoxelProfilePreviewSlot slot(Road road) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (windowSession == null || road == null) {
            return null;
        }
        return windowSession.voxelPreview(road.getId());
    }

    public boolean isComputing(Road road) {
        VoxelProfilePreviewSlot slot = slot(road);
        return slot != null && slot.state() == VoxelProfilePreviewState.COMPUTING;
    }

    public RoadVoxelProfileCompileJob activeJob() {
        return activeVoxelCompile;
    }

    public VoxelProfilePreviewCompileRequest pendingRequest() {
        return pendingVoxelCompile;
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }
}
