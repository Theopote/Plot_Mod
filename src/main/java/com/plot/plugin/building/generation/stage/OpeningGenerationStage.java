package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingGeometryUtils.WallSample;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGridAlignment;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver.ResolvedOpening;
import com.plot.plugin.building.generation.opening.OpeningVerticalLayout;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver.PlannedWindow;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.OpeningKind;
import com.plot.plugin.building.model.spec.OpeningSpec;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 门窗开洞：在墙体上镂空，覆盖先前写入的墙体记录。
 * <p>
 * 窗型阵列由 {@link WindowLayoutResolver} 沿外轮廓连续排布；显式开洞由 {@link OpeningSpec} 描述。
 * 默认 {@link FacadeEdgeScope#BASE_FOOTPRINT}：边索引相对基础轮廓，经方向继承映射到当前层。
 * inner offset 失败时仍沿外轮廓开洞，见 {@link com.plot.plugin.building.generation.massing.InnerOffsetDegradation}。
 */
public final class OpeningGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "opening";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        carvePatternWindows(context);
        carveExplicitOpenings(context);
    }

    private void carvePatternWindows(BuildingGenerationContext context) {
        BuildingDefinition definition = context.getDefinition();
        FacadeSpec facade = definition.facade();

        BuildingGenerationResult result = context.getResult();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();
        List<Vec2d> basePoints = massing.baseOuterPoints();
        FacadeEdgeScope scope = facade.edgeScope();
        BuildingCanvasScale canvasScale = context.getCanvasScale();
        String windowBlockId = BuildingGeometryUtils.resolveBlockId(facade.windowMaterial());

        for (int floor = 0; floor < massing.floors(); floor++) {
            ResolvedFloorPlate plate = context.resolvedFloorPlate(floor);
            List<Vec2d> outerPoints = plate.outerPoints();
            int floorSlabY = OpeningVerticalLayout.floorSlabY(
                baseElevation, floor, massing.floorHeight());

            List<PlannedWindow> windows = WindowLayoutResolver.layout(
                outerPoints,
                plate.outerPolygon(),
                plate.innerPolygon(),
                plate.outerCells(),
                facade,
                basePoints,
                scope,
                canvasScale,
                massing.floorHeight());

            for (PlannedWindow window : windows) {
                carveOpeningAtWallCells(
                    context,
                    canvasScale,
                    result,
                    outerPoints,
                    window.columnCenters(),
                    window.heightBlocks(),
                    OpeningVerticalLayout.windowStartY(floorSlabY, window.sillBlocks()),
                    envelope.wallThickness(),
                    windowBlockId,
                    projectionHandler
                );
            }
        }
    }

    private void carveExplicitOpenings(BuildingGenerationContext context) {
        BuildingDefinition definition = context.getDefinition();
        FacadeSpec facade = definition.facade();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        BuildingGenerationResult result = context.getResult();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();
        List<Vec2d> basePoints = massing.baseOuterPoints();
        FacadeEdgeScope scope = facade.edgeScope();
        BuildingCanvasScale canvasScale = context.getCanvasScale();
        String windowBlockId = BuildingGeometryUtils.resolveBlockId(facade.windowMaterial());

        for (OpeningSpec opening : facade.openings()) {
            if (opening.floor() < 0 || opening.floor() >= massing.floors()) {
                continue;
            }
            List<Vec2d> outerPoints = massing.plateForFloor(opening.floor()).outerPoints();
            int plateSegment = FacadeEdgeResolver.resolveSegmentIndex(
                scope, opening.wallSegmentIndex(), basePoints, outerPoints);
            ResolvedOpening resolved = OpeningPlacementResolver.resolve(
                opening, outerPoints, plateSegment, baseElevation, massing.floorHeight());
            if (resolved == null) {
                continue;
            }
            String fillBlockId = opening.kind() == OpeningKind.DOOR || opening.kind() == OpeningKind.ARCH
                ? "minecraft:air"
                : windowBlockId;
            carveOpening(
                context,
                canvasScale,
                result,
                resolved.centerPoint(),
                resolved.tangent(),
                resolved.inwardNormal(),
                resolved.width(),
                resolved.height(),
                resolved.startY(),
                envelope.wallThickness(),
                fillBlockId,
                projectionHandler
            );
        }
    }

    /**
     * 在墙体格网柱列上开洞（与 {@link com.plot.plugin.building.generation.stage.WallGenerationStage} 同列）。
     */
    static void carveOpeningAtWallCells(
            BuildingGenerationContext context,
            BuildingCanvasScale canvasScale,
            BuildingGenerationResult result,
            List<Vec2d> outerPoints,
            List<Vec2d> columnCenters,
            int height,
            int startY,
            int wallThickness,
            String fillBlockId,
            IBlockProjectionService projectionHandler) {
        if (height <= 0 || columnCenters == null || columnCenters.isEmpty()
                || outerPoints == null || outerPoints.size() < 3) {
            return;
        }

        double cellSize = BuildingGridAlignment.blockCellSizeCanvas(canvasScale, outerPoints);
        Set<BlockPos> carved = new LinkedHashSet<>();
        for (Vec2d center : columnCenters) {
            int segmentIndex = BuildingGeometryUtils.segmentIndexAtClosedDistance(
                outerPoints,
                com.plot.plugin.building.generation.opening.WallColumnRing.arcLengthAtPoint(
                    outerPoints, center));
            Vec2d inward = BuildingGeometryUtils.outwardNormal(outerPoints, segmentIndex).multiply(-1);
            for (int depth = 0; depth < wallThickness; depth++) {
                Vec2d point = depth == 0
                    ? center
                    : BuildingGridAlignment.snapToBlockCellCenter(
                        center.add(inward.multiply(depth * cellSize)), canvasScale, outerPoints);
                BlockPos column = context.canvasToColumn(point);
                for (int h = 0; h < height; h++) {
                    BlockPos pos = new BlockPos(column.getX(), startY + h, column.getZ());
                    if (carved.add(pos)) {
                        BuildingBlockWriter.recordBlock(result, pos, fillBlockId, projectionHandler);
                    }
                }
            }
        }
    }

    private void carveOpening(
            BuildingGenerationContext context,
            BuildingCanvasScale canvasScale,
            BuildingGenerationResult result,
            Vec2d centerPoint,
            Vec2d tangent,
            Vec2d inwardNormal,
            int width,
            int height,
            int startY,
            int wallThickness,
            String fillBlockId,
            IBlockProjectionService projectionHandler) {
        WallSample sample = new WallSample(0, centerPoint, tangent, inwardNormal);
        Set<BlockPos> carved = new LinkedHashSet<>();
        for (int w = 0; w < width; w++) {
            double lateralBlocks = w - (width - 1) / 2.0;
            double lateralCanvas = canvasScale.blocksToCanvas(lateralBlocks, centerPoint, tangent);
            Vec2d columnPoint = centerPoint.add(tangent.multiply(lateralCanvas));
            WallSample columnSample = new WallSample(0, columnPoint, tangent, inwardNormal);
            carveColumn(
                context, canvasScale, result, columnSample, height, startY, wallThickness, fillBlockId,
                projectionHandler, carved);
        }
    }

    private static void carveColumn(
            BuildingGenerationContext context,
            BuildingCanvasScale canvasScale,
            BuildingGenerationResult result,
            WallSample sample,
            int height,
            int startY,
            int wallThickness,
            String fillBlockId,
            IBlockProjectionService projectionHandler,
            Set<BlockPos> carved) {
        for (int depth = 0; depth < wallThickness; depth++) {
            double depthCanvas = canvasScale.blocksToCanvas(depth + 0.5, sample.point(), sample.inwardNormal());
            Vec2d point = sample.point().add(sample.inwardNormal().multiply(depthCanvas));
            BlockPos column = context.canvasToColumn(point);
            for (int h = 0; h < height; h++) {
                BlockPos pos = new BlockPos(column.getX(), startY + h, column.getZ());
                if (carved.add(pos)) {
                    BuildingBlockWriter.recordBlock(result, pos, fillBlockId, projectionHandler);
                }
            }
        }
    }
}
