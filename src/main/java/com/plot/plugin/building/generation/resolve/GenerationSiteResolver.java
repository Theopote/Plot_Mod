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
 * <p>
 * 标高 ownership 三分开，禁止混成同一个数再回写：
 * <ul>
 *   <li>{@code requestedBaseElevation} — 建筑想要的手动 ±0</li>
 *   <li>{@code resolvedPadElevation} — 土方侧已解析垫层（EARTHWORK_OWNED）</li>
 *   <li>{@code actualFoundationElevation} — 生成实际使用的地基标高</li>
 * </ul>
 * 优先级：manual &gt; earthwork pad &gt; terrain。
 */
public final class GenerationSiteResolver {
    private GenerationSiteResolver() {
    }

    public record ResolvedSiteElevation(
            Integer requestedBaseElevation,
            Integer resolvedPadElevation,
            Integer terrainSampledElevation,
            int actualFoundationElevation,
            FoundationElevationSource source) {

        /** @deprecated 使用 {@link #actualFoundationElevation()} */
        @Deprecated
        public int baseElevation() {
            return actualFoundationElevation;
        }

        /** @deprecated 使用 {@link #resolvedPadElevation()} */
        @Deprecated
        public Integer earthworkPadElevation() {
            return resolvedPadElevation;
        }

        /** @deprecated 使用 {@code source == EARTHWORK_PAD} */
        @Deprecated
        public boolean usedEarthworkPad() {
            return source == FoundationElevationSource.EARTHWORK_PAD;
        }
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

        Integer requested = definition != null ? definition.foundation().manualBaseElevation() : null;

        Integer resolvedPad = null;
        if (footprint != null) {
            resolvedPad = BuildingSiteElevationResolver.resolveEarthworkPadElevation(footprint);
        } else if (definition != null) {
            List<Vec2d> outer = massing != null ? massing.outerPoints() : definition.footprint().outerPoints();
            resolvedPad = BuildingSiteElevationResolver.resolveEarthworkPadElevation(
                definition.footprint().id(), outer);
        }

        Integer terrain = groundHeights.isEmpty()
            ? null
            : BuildingFoundationUtils.computeBaseElevation(groundHeights, null, null);

        int actual = BuildingFoundationUtils.computeBaseElevation(groundHeights, requested, resolvedPad);
        FoundationElevationSource source;
        if (requested != null) {
            source = FoundationElevationSource.MANUAL;
        } else if (resolvedPad != null) {
            source = FoundationElevationSource.EARTHWORK_PAD;
            if (result != null) {
                result.warnings.add("plugin.building.warn.using_earthwork_pad_elevation");
            }
        } else {
            source = FoundationElevationSource.TERRAIN;
        }

        return new ResolvedSiteElevation(requested, resolvedPad, terrain, actual, source);
    }

    /**
     * 测试路径：无 World；仅手动 / 默认地形众数缺省。
     */
    public static ResolvedSiteElevation resolveForTesting(BuildingDefinition definition) {
        Integer requested = definition != null ? definition.foundation().manualBaseElevation() : null;
        int actual = BuildingFoundationUtils.computeBaseElevation(List.of(), requested);
        FoundationElevationSource source = requested != null
            ? FoundationElevationSource.MANUAL
            : FoundationElevationSource.TERRAIN;
        Integer terrain = requested == null ? actual : null;
        return new ResolvedSiteElevation(requested, null, terrain, actual, source);
    }

    public static int sampleTopHeight(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
        }
        return EngineeringTerrainService.of(world).sampleGroundSurface(pos.getX(), pos.getZ());
    }
}
