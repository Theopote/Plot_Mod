package com.plot.plugin.earthwork.pipeline;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import com.plot.plugin.earthwork.volume.EarthworkVolumeCalculator;

/**
 * 组装场地级与单分区 legacy 土方管线（2.0 推荐入口）。
 */
public final class EarthworkPipelines {
    public record Bundle(
            SiteEarthworkPipeline site,
            LegacyRegionPipeline legacy) {
    }

    private EarthworkPipelines() {
    }

    public static Bundle create(ICoordinateService coordinateService) {
        return create(coordinateService, null);
    }

    public static Bundle create(
            ICoordinateService coordinateService,
            EarthworkVoxelizer.BlockSampler blockSampler) {
        // coordinateService 在单元测试中可为 null（预置 TerrainSnapshot）
        EarthworkVoxelizer voxelizer = new EarthworkVoxelizer(blockSampler);
        EarthworkVolumeCalculator volumeCalculator = new EarthworkVolumeCalculator(voxelizer);
        LegacyRegionPipeline legacy = new LegacyRegionPipeline(coordinateService, volumeCalculator);
        SiteEarthworkPipeline site = new SiteEarthworkPipeline(
            new DefaultSiteEarthworkOperations(coordinateService, volumeCalculator, legacy));
        return new Bundle(site, legacy);
    }
}
