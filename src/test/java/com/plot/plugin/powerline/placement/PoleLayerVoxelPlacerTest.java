package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.preview.PoleVoxelPreviewModel;
import com.plot.plugin.powerline.preview.PoleVoxelizer;
import com.plot.plugin.powerline.preview.PreviewVoxel;
import com.plot.plugin.powerline.preview.PreviewVoxelSink;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PoleLayerVoxelPlacerTest {

    @Test
    void previewAndIdentityWorldMapperProduceSameRelativeShape() {
        var design = PoleDesignCatalog.japaneseStreetPole();
        PreviewVoxelSink previewSink = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesignPreview(design, previewSink, "seed");
        Map<String, String> preview = normalizeShape(previewSink, 0, 0, 0);

        PreviewVoxelSink worldSink = new PreviewVoxelSink();
        Vec2d planPoint = new Vec2d(12.4, -3.6);
        int layerBaseY = 65;
        int originX = (int) Math.round(planPoint.x);
        int originZ = (int) Math.round(planPoint.y);
        PoleLayerVoxelPlacer.placeDesign(
            design,
            planPoint,
            layerBaseY,
            new Vec2d(0, 1),
            worldSink,
            "seed",
            PoleLayerVoxelPlacer.worldMapper(identityCoordinates()));

        Map<String, String> world = normalizeShape(worldSink, layerBaseY, originX, originZ);
        assertFalse(preview.isEmpty());
        assertEquals(preview.keySet(), world.keySet());
    }

    @Test
    void voxelizerUsesSamePlacerAsExplicitPreview() {
        var design = PoleDesignCatalog.simpleWoodPole();
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design, "seed");
        PreviewVoxelSink sink = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesignPreview(design, sink, "seed");
        assertEquals(sink.snapshot().size(), model.voxels().size());
    }

    private static Map<String, String> normalizeShape(PreviewVoxelSink sink, int yOrigin, int originX, int originZ) {
        Map<String, String> normalized = new HashMap<>();
        for (PreviewVoxel voxel : sink.snapshot()) {
            normalized.put(
                relativeKey(voxel.x() - originX, voxel.y() - yOrigin, voxel.z() - originZ),
                "");
        }
        return normalized;
    }

    private static String relativeKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static ICoordinateService identityCoordinates() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public com.plot.api.world.WorldViewBounds getMinecraftWorldViewBounds() {
                return new com.plot.api.world.WorldViewBounds(0, 100, 0, 100);
            }
        };
    }
}
