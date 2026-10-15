package com.plot.plugin.road.profile.edit;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.Objects;

/**
 * Deferred voxel compile payload: captured on the UI click frame, executed on the next editor tick
 * so ImGui does not block on {@link com.plot.plugin.road.pipeline.profile.RoadVoxelProfileCompiler}.
 */
public final class VoxelProfilePreviewCompileRequest {

    private final String roadId;
    private final RoadNetwork compileNetwork;
    private final RoadVerticalAlignment alignmentOverride;
    private final VoxelProfilePreviewKey key;
    private final ProfileCompileOptions options;
    private final RoadElevationBounds elevationBounds;
    private final long generatorCacheKey;

    public VoxelProfilePreviewCompileRequest(
            String roadId,
            RoadNetwork compileNetwork,
            RoadVerticalAlignment alignmentOverride,
            VoxelProfilePreviewKey key,
            ProfileCompileOptions options,
            RoadElevationBounds elevationBounds,
            long generatorCacheKey) {
        this.roadId = Objects.requireNonNull(roadId, "roadId");
        this.compileNetwork = Objects.requireNonNull(compileNetwork, "compileNetwork");
        this.alignmentOverride = alignmentOverride;
        this.key = Objects.requireNonNull(key, "key");
        this.options = options != null ? options : ProfileCompileOptions.defaults();
        this.elevationBounds = elevationBounds;
        this.generatorCacheKey = generatorCacheKey;
    }

    public String roadId() {
        return roadId;
    }

    public RoadNetwork compileNetwork() {
        return compileNetwork;
    }

    public RoadVerticalAlignment alignmentOverride() {
        return alignmentOverride;
    }

    public VoxelProfilePreviewKey key() {
        return key;
    }

    public ProfileCompileOptions options() {
        return options;
    }

    public RoadElevationBounds elevationBounds() {
        return elevationBounds;
    }

    public long generatorCacheKey() {
        return generatorCacheKey;
    }
}
