package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.block.BlockSpec;
import com.plot.core.material.MaterialMix;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.preview.PoleVoxelPreviewModel;
import com.plot.plugin.powerline.preview.PoleVoxelizer;
import com.plot.plugin.powerline.preview.PreviewVoxel;
import com.plot.plugin.powerline.preview.PreviewVoxelSink;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    void rotatedCrossarmRemainsSixConnected() {
        PoleDesign design = new PoleDesign("rotated_crossarm", "Rotated crossarm");
        design.addLayer(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            1,
            MaterialMix.single("minecraft:oak_fence")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        crossarm.setCrossarmLength(7);
        design.addLayer(crossarm);

        PreviewVoxelSink sink = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesign(
            design,
            new Vec2d(0, 0),
            0,
            new Vec2d(1, 1),
            sink,
            "rotated",
            PoleLayerVoxelPlacer.previewMapper());

        Set<String> crossarmBlocks = new HashSet<>();
        for (PreviewVoxel voxel : sink.snapshot()) {
            if (voxel.y() == 1 && "minecraft:iron_bars".equals(BlockSpec.parse(voxel.blockId()).blockId())) {
                crossarmBlocks.add(voxel.x() + "," + voxel.z());
            }
        }
        assertTrue(crossarmBlocks.size() >= 7);
        for (String key : crossarmBlocks) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int z = Integer.parseInt(parts[1]);
            boolean connected = crossarmBlocks.contains((x + 1) + "," + z)
                || crossarmBlocks.contains((x - 1) + "," + z)
                || crossarmBlocks.contains(x + "," + (z + 1))
                || crossarmBlocks.contains(x + "," + (z - 1));
            assertTrue(connected, "isolated rotated crossarm block at " + key);
        }
    }

    @Test
    void crossarmSlabsUseBottomType() {
        PreviewVoxelSink sink = new PreviewVoxelSink();
        PoleLayerVoxelPlacer.placeDesignPreview(PoleDesignCatalog.japaneseStreetPole(), sink, "seed");
        assertTrue(sink.snapshot().stream().anyMatch(voxel -> {
            BlockSpec spec = BlockSpec.parse(voxel.blockId());
            return "minecraft:dark_oak_slab".equals(spec.blockId())
                && "bottom".equals(spec.property("type"));
        }));
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
