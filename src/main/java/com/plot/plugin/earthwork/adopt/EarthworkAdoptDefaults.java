package com.plot.plugin.earthwork.adopt;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.material.MaterialConversionModel;
import com.plot.core.terrain.EngineeringTerrainSampler;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.material.EarthworkMaterialScope;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 将 {@link EarthworkConfig} 中的<strong>认领默认值</strong>应用到新建 {@link GradingRegion}。
 * <p>
 * 手动目标标高从现状地形采样，不使用 config 中遗留的 {@code targetElevation}（避免默认 Y=0）。
 */
public final class EarthworkAdoptDefaults {

    private EarthworkAdoptDefaults() {
    }

    public static void applyToNewRegion(
            GradingRegion region,
            EarthworkConfig config,
            EarthworkSite site,
            World world,
            ICoordinateService transformer) {
        if (region == null || config == null) {
            return;
        }
        if (site != null && EarthworkMaterialScope.usesDefaultMaterialModel(site)) {
            site.setMaterialModel(config.getDefaultMaterialProperties());
        }
        region.setAutoBalance(config.isAdoptDefaultAutoBalance());
        region.setMaterialProperties(MaterialConversionModel.DEFAULT);
        region.setPreviewGridSize(config.getPreviewGridSize());
        if (!region.isAutoBalance()) {
            region.setManualTargetElevation(
                resolveInitialManualElevation(region.getOuterPoints(), world, transformer));
        }
    }

    /** @deprecated 请传入 {@link EarthworkSite}，以便首块认领时初始化场地材料模型。 */
    @Deprecated
    public static void applyToNewRegion(
            GradingRegion region,
            EarthworkConfig config,
            World world,
            ICoordinateService transformer) {
        applyToNewRegion(region, config, null, world, transformer);
    }

    /**
     * 认领/切换手动模式时的初始设计标高：优先采样区域代表点现状地面。
     */
    public static int resolveInitialManualElevation(
            List<Vec2d> outline,
            World world,
            ICoordinateService transformer) {
        if (outline == null || outline.size() < 3) {
            return EngineeringTerrainSampler.defaultGroundElevation();
        }
        if (world == null || transformer == null) {
            return EngineeringTerrainSampler.defaultGroundElevation();
        }
        Vec2d centroid = polygonCentroid(outline);
        BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(centroid, transformer);
        return EngineeringTerrainSampler.sampleGroundSurface(world, column.getX(), column.getZ());
    }

    private static Vec2d polygonCentroid(List<Vec2d> outline) {
        double x = 0;
        double z = 0;
        int count = 0;
        for (Vec2d point : outline) {
            if (point == null) {
                continue;
            }
            x += point.x;
            z += point.y;
            count++;
        }
        if (count == 0) {
            return new Vec2d(0, 0);
        }
        return new Vec2d(x / count, z / count);
    }
}
