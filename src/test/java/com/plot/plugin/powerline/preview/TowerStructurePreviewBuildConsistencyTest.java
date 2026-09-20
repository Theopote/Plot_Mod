package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TowerStructureGenerator;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecorationCatalog;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TowerStructurePreviewBuildConsistencyTest {

    private static final int GROUND_Y = 64;

    @Test
    void previewMatchesBuildForAsymmetricTower() {
        assertPreviewMatchesBuild(asymmetricTwoStationTower());
    }

    @Test
    void previewMatchesBuildForThickLegTower() {
        TowerStructureDesign structure = asymmetricTwoStationTower();
        structure.getLegProfile().setThickness(2);
        structure.getBraceProfile().setThickness(2);
        assertPreviewMatchesBuild(structure);
    }

    @Test
    void previewMatchesBuildForMonsterPylon() {
        assertPreviewMatchesBuild(TowerFamilyDesignPresets.monsterPylonSuspension().getTowerStructure());
    }

    @Test
    void poleVoxelizerMatchesBuildForDesignerTowerDesign() {
        PoleDesign design = TowerStructurePresets.taperedLatticePoleDesign("designer", "Designer");
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        String seed = footprint.getId();
        Map<String, String> uiPreview = voxelShape(PoleVoxelizer.voxelize(design, seed));
        Map<String, String> build = buildShape(design.getTowerStructure(), footprint);
        assertFalse(uiPreview.isEmpty(), "designer voxel preview should place blocks");
        assertEquals(build, uiPreview);
    }

    @Test
    void previewMatchesBuildWithDecorations() {
        TowerStructureDesign structure = asymmetricTwoStationTower();
        structure.addDecoration(TowerDecorationCatalog.beaconAtTop(8));
        structure.addDecoration(TowerDecorationCatalog.antennaAtTop(8));
        assertPreviewMatchesBuild(structure);
    }

    private static void assertPreviewMatchesBuild(TowerStructureDesign structure) {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        String seed = footprint.getId();
        Map<String, String> preview = previewShape(structure, seed);
        Map<String, String> build = buildShape(structure, footprint);
        assertFalse(preview.isEmpty(), "preview should place blocks");
        assertEquals(preview, build);
    }

    private static Map<String, String> voxelShape(PoleVoxelPreviewModel model) {
        Map<String, String> shape = new HashMap<>();
        for (PreviewVoxel voxel : model.voxels()) {
            shape.put(relativeKey(voxel.x(), voxel.y(), voxel.z()), voxel.blockId());
        }
        return shape;
    }

    private static Map<String, String> previewShape(TowerStructureDesign structure, String seed) {
        PreviewVoxelSink sink = new PreviewVoxelSink();
        TowerStructurePreviewVoxelPlacer.placePreview(structure, sink, seed);
        Map<String, String> shape = new HashMap<>();
        for (PreviewVoxel voxel : sink.snapshot()) {
            shape.put(relativeKey(voxel.x(), voxel.y(), voxel.z()), voxel.blockId());
        }
        return shape;
    }

    private static Map<String, String> buildShape(TowerStructureDesign structure, PowerLineFootprint footprint) {
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), GROUND_Y);
        TowerStructureGenerator.generate(
            structure,
            frame,
            footprint,
            result,
            identityCoordinates(),
            projection(),
            flatTerrain(GROUND_Y));

        Map<String, String> shape = new HashMap<>();
        for (BlockRecord record : result.placementRecords.values()) {
            BlockPos pos = record.pos;
            int lateral = pos.getZ();
            int vertical = pos.getY() - GROUND_Y;
            int longitudinal = pos.getX();
            shape.put(relativeKey(lateral, vertical, longitudinal), record.newBlockId);
        }
        return shape;
    }

    private static TowerStructureDesign asymmetricTwoStationTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 4, 2));
        structure.addStation(new TowerStation("s1", 8, 3, 1));
        TowerBay bay = new TowerBay("s0", "s1");
        bay.setFrontBackBracing(BracingPattern.X);
        bay.setSideBracing(BracingPattern.K);
        bay.setHorizontalRing(true);
        bay.setPlanDiagonalBracing(true);
        structure.addBay(bay);
        return structure;
    }

    private static String relativeKey(int lateral, int vertical, int longitudinal) {
        return lateral + "," + vertical + "," + longitudinal;
    }

    private static ICoordinateService identityCoordinates() {
        return com.plot.test.world.IdentityCoordinateService.INSTANCE;
    }

    private static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }
}
