package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver.ResolvedOpening;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 门窗开洞：在墙体上镂空，覆盖先前写入的墙体记录。
 * <p>
 * 窗型阵列由 {@link FacadeSpec#windowPatternForSegment} 控制；显式开洞由 {@link OpeningSpec} 描述。
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

        for (int floor = 0; floor < massing.floors(); floor++) {
            List<Vec2d> outerPoints = massing.plateForFloor(floor).outerPoints();
            int segmentCount = outerPoints.size();
            int floorBaseY = baseElevation + floor * massing.floorHeight();

            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
                int patternIndex = FacadeEdgeResolver.patternSourceIndex(
                    scope, segmentIndex, basePoints, outerPoints);
                int patternCount = scope == FacadeEdgeScope.FLOOR_LOCAL
                    ? segmentCount
                    : basePoints.size();
                WindowPatternSpec windows = facade.windowPatternForSegment(patternIndex, patternCount);
                if (!windows.enabled()) {
                    continue;
                }
                int sill = windows.sillHeight();
                int maxWindowHeight = Math.max(1, massing.floorHeight() - sill - 1);
                int windowHeight = Math.min(windows.height(), maxWindowHeight);
                List<BuildingGeometryUtils.WallSample> samples = BuildingGeometryUtils.sampleAlongWallSegment(
                    outerPoints, segmentIndex, windows.spacing());
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
            carveOpening(
                context,
                result,
                resolved.centerPoint(),
                resolved.tangent(),
                resolved.inwardNormal(),
                resolved.width(),
                resolved.height(),
                resolved.startY(),
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
                BlockPos column = context.canvasToColumn(sample);
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
