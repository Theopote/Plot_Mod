package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.MassingGeometryResolver;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingWorldScaleTest {

    private static final ICoordinateService FOUR_BLOCKS_PER_CANVAS_UNIT =
        SnapshotCoordinateService.uniformScale(4.0);

    private static final List<Vec2d> SQUARE = List.of(
        new Vec2d(0, 0),
        new Vec2d(20, 0),
        new Vec2d(20, 20),
        new Vec2d(0, 20)
    );

    @Test
    void wallThicknessIsScaleInvariantInWorldBlocks() {
        BuildingFootprint footprint = new BuildingFootprint(SQUARE, true);
        footprint.setWallThickness(2);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        BuildingGenerationResult result = new BuildingGenerationResult();

        var identity = MassingGeometryResolver.resolve(
            definition, result, BuildingCanvasScale.capture(IdentityCoordinateService.INSTANCE, SQUARE));
        var scaled = MassingGeometryResolver.resolve(
            definition, result, BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE));

        double identityCanvasInset = minCanvasX(identity.innerPoints());
        double scaledCanvasInset = minCanvasX(scaled.innerPoints());
        assertEquals(2.0, identityCanvasInset, 0.05);
        assertEquals(0.5, scaledCanvasInset, 0.05);

        double identityWorldInset = minWorldX(identity.innerPoints(), IdentityCoordinateService.INSTANCE);
        double scaledWorldInset = minWorldX(scaled.innerPoints(), FOUR_BLOCKS_PER_CANVAS_UNIT);
        assertEquals(2.0, identityWorldInset, 0.5);
        assertEquals(2.0, scaledWorldInset, 0.5);
    }

    @Test
    void blocksToCanvasScalesWithProjection() {
        BuildingCanvasScale identity = BuildingCanvasScale.capture(IdentityCoordinateService.INSTANCE, SQUARE);
        BuildingCanvasScale scaled = BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE);

        assertEquals(2.0, identity.uniformBlocksToCanvas(2, SQUARE), 1e-6);
        assertEquals(0.5, scaled.uniformBlocksToCanvas(2, SQUARE), 1e-6);
    }

    private static double minCanvasX(List<Vec2d> points) {
        return points.stream().mapToDouble(p -> p.x).min().orElse(0.0);
    }

    private static double minWorldX(List<Vec2d> points, ICoordinateService coordinates) {
        return points.stream()
            .mapToDouble(p -> coordinates.canvasToMinecraftWorld(p).x)
            .min()
            .orElse(0.0);
    }
}
