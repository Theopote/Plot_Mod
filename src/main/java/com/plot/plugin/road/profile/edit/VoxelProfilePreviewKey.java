package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.ProfileCompileInputsFingerprint;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.List;
import java.util.Objects;

/**
 * Fingerprint of voxel-preview inputs. Delegates to {@link ProfileCompileInputsFingerprint}
 * so geometry, intersections, terrain revision, and bounds invalidate the grey preview.
 */
public record VoxelProfilePreviewKey(ProfileCompileInputsFingerprint fingerprint) {

    public VoxelProfilePreviewKey {
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public String roadId() {
        return fingerprint.roadId();
    }

    public static VoxelProfilePreviewKey of(ProfileCompileInputsFingerprint fingerprint) {
        return new VoxelProfilePreviewKey(fingerprint);
    }

    public static VoxelProfilePreviewKey of(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment,
            List<RoadProfileIntersection> intersections,
            VoxelProfileConstraints constraints,
            RoadElevationBounds elevationBounds,
            long terrainRevision,
            RoadSystemConfig config,
            ProfileCompileOptions options) {
        return new VoxelProfilePreviewKey(ProfileCompileInputsFingerprint.of(
            network,
            road,
            alignment,
            intersections,
            constraints,
            elevationBounds,
            terrainRevision,
            config,
            options));
    }
}
