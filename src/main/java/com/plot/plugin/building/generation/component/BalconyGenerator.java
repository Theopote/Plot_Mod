package com.plot.plugin.building.generation.component;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.model.spec.BalconySpec;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * 阳台：外挑楼板 + 外围栏杆。
 */
public final class BalconyGenerator {
    private BalconyGenerator() {
    }

    public static void generate(BuildingGenerationContext context, BalconySpec spec) {
        if (spec == null) {
            return;
        }
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        if (spec.floor() >= massing.floors()) {
            return;
        }

        EnvelopeSpec envelope = definition.envelope();
        ResolvedFloorPlate plate = FloorPlateGeometryResolver.resolve(
            massing.plateForFloor(spec.floor()), envelope.wallThickness());
        List<Vec2d> outerPoints = plate.outerPoints();
        int segmentIndex = FacadeEdgeResolver.resolveSegmentIndex(
            definition.facade().edgeScope(),
            spec.wallSegmentIndex(),
            massing.baseOuterPoints(),
            outerPoints);

        int floorY = context.getBaseElevation() + spec.floor() * massing.floorHeight();
        BuildingGenerationResult result = context.getResult();
        String slabId = BuildingGeometryUtils.resolveBlockId(spec.resolvedSlabMaterial());
        String railingId = BuildingGeometryUtils.resolveBlockId(spec.resolvedRailingMaterial());

        Set<BlockPos> slabPositions = WallAttachmentPlacer.placeHorizontalSlab(
            result,
            outerPoints,
            segmentIndex,
            spec.positionRatio(),
            floorY,
            spec.width(),
            spec.depth(),
            slabId,
            context.getCoordinateService(),
            context.getProjectionService());

        WallAttachmentPlacer.placeRailingAround(
            result,
            slabPositions,
            floorY + 1,
            railingId,
            context.getProjectionService());
    }
}
