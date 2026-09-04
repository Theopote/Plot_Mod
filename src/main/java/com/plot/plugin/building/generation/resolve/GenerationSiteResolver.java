package com.plot.plugin.building.generation.resolve;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.site.BuildingSiteElevationResolver;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形采样 + 土方垫层 + 基面标高（场地解析，不含体量几何）。
 */
public final class GenerationSiteResolver {
    private GenerationSiteResolver() {
    }

    public record ResolvedSiteElevation(
            int baseElevation,
            Integer earthworkPadElevation,
            boolean usedEarthworkPad) {
    }

    /**
     * 生产路径：采样足迹格点地形并解析垫层。
     */
    public static ResolvedSiteElevation resolve(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            World world,
            ICoordinateService coordinateService,
            BuildingGenerationResult result) {
        List<Integer> groundHeights = new ArrayList<>();
        if (massing != null && massing.valid()) {
            for (GridCell cell : massing.footprintCells()) {
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateService);
                groundHeights.add(sampleTopHeight(world, column));
            }
        }

        Integer earthworkPadElevation = null;
        if (footprint != null) {
            earthworkPadElevation = BuildingSiteElevationResolver.resolveEarthworkPadElevation(footprint);
        } else if (definition != null) {
            List<Vec2d> outer = massing != null ? massing.outerPoints() : definition.footprint().outerPoints();
            earthworkPadElevation = BuildingSiteElevationResolver.resolveEarthworkPadElevation(
                definition.footprint().id(), outer);
        }

        Integer manual = definition != null ? definition.foundation().manualBaseElevation() : null;
        int baseElevation = BuildingFoundationUtils.computeBaseElevation(
            groundHeights, manual, earthworkPadElevation);
        boolean usedPad = earthworkPadElevation != null && manual == null;
        if (usedPad && result != null) {
            result.warnings.add("plugin.building.warn.using_earthwork_pad_elevation");
        }
        return new ResolvedSiteElevation(baseElevation, earthworkPadElevation, usedPad);
    }

    /**
     * 测试路径：无 World，仅用手动标高。
     */
    public static ResolvedSiteElevation resolveForTesting(BuildingDefinition definition) {
        Integer manual = definition != null ? definition.foundation().manualBaseElevation() : null;
        int baseElevation = BuildingFoundationUtils.computeBaseElevation(List.of(), manual);
        return new ResolvedSiteElevation(baseElevation, null, false);
    }

    public static int sampleTopHeight(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
        }
        return EngineeringTerrainService.of(world).sampleGroundSurface(pos.getX(), pos.getZ());
    }
}
