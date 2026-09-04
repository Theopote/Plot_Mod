package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.DoorOpeningSpec;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 门窗开洞：在墙体上镂空，覆盖先前写入的墙体记录。
 * 每层使用对应 FloorPlate 的外轮廓采样窗洞位置。
 */
public final class OpeningGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "opening";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        carveWindows(context);
        carveDoors(context);
    }

    private void carveWindows(BuildingGenerationContext context) {
        BuildingDefinition definition = context.getDefinition();
        WindowPatternSpec windows = definition.facade().defaultWindowPattern();
        if (!windows.enabled()) {
            return;
        }

        BuildingGenerationResult result = context.getResult();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        for (int floor = 0; floor < massing.floors(); floor++) {
            List<Vec2d> outerPoints = massing.plateForFloor(floor).outerPoints();
            List<BuildingGeometryUtils.WallSample> samples = BuildingGeometryUtils.sampleAlongWallSegments(
                outerPoints, windows.spacing());
            int floorBaseY = baseElevation + floor * massing.floorHeight();
            int sill = windows.sillHeight();
            int maxWindowHeight = Math.max(1, massing.floorHeight() - sill - 1);
            int windowHeight = Math.min(windows.height(), maxWindowHeight);
            for (BuildingGeometryUtils.WallSample sample : samples) {
                carveOpening(
                    context,
                    result,
                    sample.point(),
                    sample.tangent(),
                    sample.inwardNormal(),
                    windows.width(),
                    windowHeight,
                    floorBaseY + sill,
                    envelope.wallThickness(),
                    projectionHandler
                );
            }
        }
    }

    private void carveDoors(BuildingGenerationContext context) {
        BuildingDefinition definition = context.getDefinition();
        FacadeSpec facade = definition.facade();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        BuildingGenerationResult result = context.getResult();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        for (DoorOpeningSpec door : facade.doors()) {
            if (door.floor() < 0 || door.floor() >= massing.floors()) {
                continue;
            }
            List<Vec2d> outerPoints = massing.plateForFloor(door.floor()).outerPoints();
            int segmentCount = outerPoints.size();
            int segmentIndex = Math.floorMod(door.wallSegmentIndex(), segmentCount);
            Vec2d point = BuildingGeometryUtils.pointOnWallSegment(
                outerPoints, segmentIndex, door.positionRatio());
            if (point == null) {
                continue;
            }
            Vec2d start = outerPoints.get(segmentIndex);
            Vec2d end = outerPoints.get((segmentIndex + 1) % segmentCount);
            Vec2d tangent = end.subtract(start).normalize();
            Vec2d inwardNormal = BuildingGeometryUtils.leftNormal(tangent);
            if (BuildingFootprint.signedArea(outerPoints) >= 0) {
                inwardNormal = inwardNormal.multiply(-1);
            }
            int floorBaseY = baseElevation + door.floor() * massing.floorHeight();
            int maxDoorHeight = Math.max(1, massing.floorHeight() - 1);
            int doorHeight = Math.min(Math.max(1, door.height()), maxDoorHeight);
            carveOpening(
                context,
                result,
                point,
                tangent,
                inwardNormal,
                door.width(),
                doorHeight,
                floorBaseY,
                envelope.wallThickness(),
                projectionHandler
            );
        }
    }

    private void carveOpening(
            BuildingGenerationContext context,
            BuildingGenerationResult result,
            Vec2d centerPoint,
            Vec2d tangent,
            Vec2d inwardNormal,
            int width,
            int height,
            int startY,
            int wallThickness,
            IBlockProjectionService projectionHandler) {
        Set<BlockPos> carved = new LinkedHashSet<>();
        for (int w = 0; w < width; w++) {
            double lateral = w - (width - 1) / 2.0;
            for (int depth = 0; depth < wallThickness; depth++) {
                Vec2d sample = centerPoint
                    .add(tangent.multiply(lateral))
                    .add(inwardNormal.multiply(depth + 0.5));
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(
                    sample, context.getCoordinateService());
                for (int h = 0; h < height; h++) {
                    BlockPos pos = new BlockPos(column.getX(), startY + h, column.getZ());
                    if (carved.add(pos)) {
                        BuildingBlockWriter.recordBlock(result, pos, "minecraft:air", projectionHandler);
                    }
                }
            }
        }
    }
}
