package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.placement.PoleLayerVoxelPlacer;

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
        String seed = seedKey != null ? seedKey : design.getId();
        if (design.hasTowerStructure()) {
            TowerStructurePreviewVoxelPlacer.placePreview(design.getTowerStructure(), sink, seed);
        } else {
            PoleLayerVoxelPlacer.placeDesignPreview(design, sink, seed);
        }
        return PoleVoxelPreviewModel.fromSink(sink);
    }
}
