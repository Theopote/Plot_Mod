package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.block.BlockSpec;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            new Vec2d(1, 0),
            worldSink,
            "seed",
            PoleLayerVoxelPlacer.worldMapper(identityCoordinates()));

        Map<String, String> world = normalizeShape(worldSink, layerBaseY, originX, originZ);
        assertFalse(preview.isEmpty());
        assertEquals(preview.keySet(), world.keySet());
    }

    @Test
    void crossarmLightningRodUsesDirectionalFacing() {
        var design = PoleDesignCatalog.steampunkBrassTower();
        PreviewVoxelSink sink = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesignPreview(design, sink, "seed");

        long directionalRods = sink.snapshot().stream()
            .filter(voxel -> isDirectionalLightningRod(voxel.blockId()))
            .count();
        assertTrue(directionalRods >= 7, "rod crossarm should place facing lightning rods");

        long eastWest = sink.snapshot().stream()
            .filter(voxel -> hasLightningRodFacing(voxel.blockId(), "east", "west"))
            .count();
        assertTrue(eastWest >= 7, "rod crossarm should face along lateral axis");
    }

    @Test
    void capLanternAndVineUseBlockState() {
        PreviewVoxelSink japanese = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesignPreview(PoleDesignCatalog.japaneseStreetPole(), japanese, "seed");
        assertTrue(japanese.snapshot().stream().anyMatch(voxel ->
            "minecraft:lantern".equals(BlockSpec.parse(voxel.blockId()).blockId())
                && "false".equals(BlockSpec.parse(voxel.blockId()).property("hanging"))));

        PreviewVoxelSink rustic = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesignPreview(PoleDesignCatalog.rusticWoodPole(), rustic, "seed");
        assertTrue(rustic.snapshot().stream().anyMatch(voxel -> {
            BlockSpec spec = BlockSpec.parse(voxel.blockId());
            return "minecraft:vine".equals(spec.blockId())
                && "true".equals(spec.property("north"))
                && "false".equals(spec.property("up"));
        }));
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
        return com.plot.test.world.IdentityCoordinateService.INSTANCE;
    }

    private static boolean isDirectionalLightningRod(String blockId) {
        BlockSpec spec = BlockSpec.parse(blockId);
        if (!"minecraft:lightning_rod".equals(spec.blockId())) {
            return false;
        }
        String facing = spec.property("facing");
        return facing != null && !facing.isBlank();
    }

    private static boolean hasLightningRodFacing(String blockId, String... facings) {
        BlockSpec spec = BlockSpec.parse(blockId);
        if (!"minecraft:lightning_rod".equals(spec.blockId())) {
            return false;
        }
        String facing = spec.property("facing");
        if (facing == null) {
            return false;
        }
        for (String candidate : facings) {
            if (facing.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
