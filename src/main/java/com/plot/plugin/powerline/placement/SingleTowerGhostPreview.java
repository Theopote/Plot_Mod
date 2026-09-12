package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.PluginProjectionContext;
import com.plot.core.context.PluginContext;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TowerStructureGenerator;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelopeResolver;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricLinePlacement;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.placement.GenerationVoxelSink;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;

/** 在指定画布点生成单塔 Ghost 方块预览。 */
public final class SingleTowerGhostPreview {
    private SingleTowerGhostPreview() {
    }

    public static PowerLineGenerationResult generate(
            PluginContext host,
            PoleDesign design,
            PowerLineFootprint styleSource,
            Vec2d planPoint,
            int rotationQuadrant,
            TerrainSampler terrain) {
        if (host == null || design == null || styleSource == null || planPoint == null || terrain == null) {
            return null;
        }
        if (PluginProjectionContext.tryCapture(host.coordinates()).isEmpty()) {
            return null;
        }

        ICoordinateService coordinates = host.coordinates();
        IBlockProjectionService projection = host.projection();
        PowerLineGenerationResult result = new PowerLineGenerationResult(styleSource);
        PolePlacementBase placementBase = PolePlacementBase.resolve(planPoint, terrain);
        Vec2d tangent = SingleTowerOrientation.tangentForQuadrant(rotationQuadrant);
        PoleFrame frame = PoleFrame.fromPole(planPoint, tangent, placementBase.buildBaseY());

        PoleDesign resolved = design;
        if (styleSource.hasParametricTowerConfig()) {
            TowerBuildEnvelope siteEnvelope = TowerBuildEnvelopeResolver.forSite(planPoint, terrain);
            TowerLineBuildEnvelope lineEnvelope = TowerLineBuildEnvelope.fromSiteEnvelopes(
                java.util.List.of(siteEnvelope));
            resolved = ParametricStyleTowerApplicator.apply(
                design,
                styleSource.getParametricTowerConfig(),
                lineEnvelope.constraintEnvelope());
            TowerParametricLinePlacement.PreparationResult prepared =
                TowerParametricLinePlacement.prepare(resolved, lineEnvelope);
            resolved = prepared.design();
        }

        if (resolved.hasTowerStructure()) {
            TowerStructureGenerator.generate(
                resolved.getTowerStructure(),
                frame,
                styleSource,
                result,
                coordinates,
                projection,
                terrain);
        } else {
            GenerationVoxelSink sink = new GenerationVoxelSink(result, projection);
            PoleLayerVoxelPlacer.placeDesign(
                resolved,
                planPoint,
                placementBase.poleLayerStartY(),
                WorldCoordinateUtils.leftNormal(tangent),
                sink,
                styleSource.getId(),
                PoleLayerVoxelPlacer.worldMapper(coordinates));
        }

        result.poleCount = 1;
        return result.blockCount() > 0 ? result : null;
    }
}
