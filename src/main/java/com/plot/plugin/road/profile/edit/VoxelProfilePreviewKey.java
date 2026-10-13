package com.plot.plugin.road.profile.edit;

import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.Objects;

/**
 * Fingerprint of voxel-preview inputs. Must not rely solely on live network revision because
 * window working-copy edits do not bump that revision.
 */
public record VoxelProfilePreviewKey(
        String roadId,
        int alignmentHash,
        RoadVerticalMode verticalMode,
        Double flatElevation,
        int compileAlgorithmVersion,
        int optionsHash) {

    public static VoxelProfilePreviewKey of(
            String roadId,
            RoadVerticalAlignment alignment,
            RoadVerticalMode mode,
            Double flatElevation,
            ProfileCompileOptions options) {
        ProfileCompileOptions effective = options != null ? options : ProfileCompileOptions.defaults();
        return new VoxelProfilePreviewKey(
            roadId != null ? roadId : "",
            contentHash(alignment),
            mode,
            flatElevation,
            effective.algorithmVersion(),
            Objects.hash(effective.minEffectiveRunBlocks(), effective.algorithmVersion()));
    }

    static int contentHash(RoadVerticalAlignment alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return 0;
        }
        int hash = 1;
        for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
            hash = 31 * hash + Objects.hash(
                pvi.getStation(),
                pvi.getElevation(),
                pvi.getCurveLength());
        }
        return hash;
    }
}
