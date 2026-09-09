package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;

import java.util.List;

/** {@link PoleDesign} → 局部体素预览模型。 */
public final class PoleVoxelizer {
    private PoleVoxelizer() {
    }

    public static PoleVoxelPreviewModel voxelize(PoleDesign design) {
        return voxelize(design, "powerline_preview");
    }

    public static PoleVoxelPreviewModel voxelize(PoleDesign design, String seedKey) {
        if (design == null) {
            return new PoleVoxelPreviewModel(List.of());
        }
        PreviewVoxelSink sink = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesign(design, sink, seedKey != null ? seedKey : design.getId());
        return PoleVoxelPreviewModel.fromSink(sink);
    }
}
