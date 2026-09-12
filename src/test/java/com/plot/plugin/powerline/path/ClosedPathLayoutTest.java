package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosedPathLayoutTest {

    @Test
    void closedPolylineProducesAtLeastThreeTowers() {
        PolylineShape triangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(20, 30)),
            true);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(triangle, IdentityCoordinateService.INSTANCE);

        assertTrue(footprint.isClosedLoop());
        assertTrue(footprint.getPathPoints().size() >= 3);
        assertTrue(ClosedPathGeometry.isValidTowerLoop(footprint.getPathPoints()));
    }

    @Test
    void circleUsesUniformRingWithoutDuplicateClosureTower() {
        CircleShape circle = new CircleShape(new Vec2d(50, 50), 30.0);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(circle, IdentityCoordinateService.INSTANCE);
        footprint.setMaxPoleSpacing(30.0);
        PowerLinePathLayout.layoutAndSync(footprint, IdentityCoordinateService.INSTANCE);

        assertTrue(footprint.getPathPoints().size() >= 3);
        for (int i = 0; i < footprint.getPathPoints().size(); i++) {
            for (int j = i + 1; j < footprint.getPathPoints().size(); j++) {
                assertTrue(
                    footprint.getPathPoints().get(i).distance(footprint.getPathPoints().get(j)) > 0.5,
                    "closure tower must not duplicate start tower");
            }
        }
    }

    @Test
    void collinearClosedPolylineIsRejected() {
        PolylineShape degenerate = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(40, 0)),
            true);
        assertThrows(
            ClosedLoopLayoutException.class,
            () -> PowerLinePathLayout.adopt(degenerate, IdentityCoordinateService.INSTANCE));
    }

    @Test
    void closedLoopGeneratesClosingSpan() {
        PolylineShape triangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(20, 30)),
            true);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(triangle, IdentityCoordinateService.INSTANCE);
        com.plot.plugin.powerline.style.PowerLineStyleEditor.selectPreset(
            footprint,
            com.plot.plugin.powerline.style.PowerLineStylePresetCatalog.classicWood());

        PowerLineGenerationResult result = createGenerator().generate(
            footprint,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertTrue(result.poleCount >= 3, "closed loop should place at least 3 poles");
        assertTrue(
            result.conductorSpans.size() >= 3,
            "closed loop should generate spans including the closing segment");
    }

    private static PowerLineGenerator createGenerator() {
        ICoordinateService coordinates = IdentityCoordinateService.INSTANCE;
        IBlockProjectionService projection = new IBlockProjectionService() {
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
        return new PowerLineGenerator(coordinates, projection);
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return false;
            }
        };
    }
}
