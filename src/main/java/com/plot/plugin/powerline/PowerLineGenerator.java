package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.placement.GenerationVoxelSink;
import com.plot.plugin.powerline.placement.PoleLayerVoxelPlacer;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionContext;
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.equipment.JumperWireGenerator;
import com.plot.plugin.powerline.equipment.LineEquipmentGenerator;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 电力线路生成器：立杆 + 多挂点导线。
 */
public class PowerLineGenerator {
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;
    private final PowerLineAttachmentResolver attachmentResolver;

    public PowerLineGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.coordinateTransformer = java.util.Objects.requireNonNull(
            coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = java.util.Objects.requireNonNull(
            projectionHandler, "projectionHandler");
        this.attachmentResolver = new PowerLineAttachmentResolver(coordinateTransformer);
    }

    public PowerLineGenerationResult generate(
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PoleDesignResolver designResolver) {
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        if (footprint == null || terrain == null) {
            return result;
        }

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(footprint);
        result.poleCount = sites.size();
        result.poleSites.addAll(sites.stream().map(PowerPoleSite::copy).toList());
        if (sites.isEmpty()) {
            return result;
        }

        PoleDesignAssignmentResolver assignmentResolver = new PoleDesignAssignmentResolver(
            designResolver,
            new TowerFamilyResolver());

        List<PolePlacement> placements = new ArrayList<>(sites.size());
        for (int i = 0; i < sites.size(); i++) {
            placements.add(buildPolePlacement(
                sites.get(i),
                sites,
                i,
                footprint,
                assignmentResolver,
                designResolver,
                terrain,
                result));
        }
        result.polePlacements.addAll(placements);

        for (int i = 0; i < placements.size(); i++) {
            if (placements.get(i).role() == TowerRole.ANGLE && i > 0 && i < placements.size() - 1) {
                Vec2d incoming = sites.get(i).getPlanPosition()
                    .subtract(sites.get(i - 1).getPlanPosition());
                Vec2d outgoing = sites.get(i + 1).getPlanPosition()
                    .subtract(sites.get(i).getPlanPosition());
                JumperWireGenerator.generateForAngleTower(
                    placements.get(i),
                    incoming,
                    outgoing,
                    footprint,
                    result,
                    projectionHandler);
            }
        }

        for (int span = 0; span < placements.size() - 1; span++) {
            ConductorSpanGenerator.generateBetween(
                placements.get(span),
                placements.get(span + 1),
                span,
                span + 1,
                sites.get(span).getId(),
                sites.get(span + 1).getId(),
                footprint,
                terrain,
                result,
                coordinateTransformer,
                projectionHandler);
        }
        return result;
    }

    private PolePlacement buildPolePlacement(
            PowerPoleSite site,
            List<PowerPoleSite> sites,
            int index,
            PowerLineFootprint footprint,
            PoleDesignAssignmentResolver assignmentResolver,
            PoleDesignResolver designResolver,
            TerrainSampler terrain,
            PowerLineGenerationResult result) {
        Vec2d planPoint = site.getPlanPosition();
        int groundY = terrain.sampleSurfaceY(planPoint);
        Vec2d tangent = computePoleTangentFromSites(sites, index);
        PoleFrame frame = PoleFrame.fromPole(planPoint, tangent, groundY);

        TowerSelectionContext selectionContext = buildSelectionContext(
            site,
            sites,
            index,
            footprint,
            designResolver);
        PoleDesignAssignmentResolver.AssignmentResult assignment =
            assignmentResolver.resolve(site, footprint, selectionContext);
        result.warnings.addAll(assignment.warnings());
        PoleDesign design = assignment.design();

        int legacyWireHangY;
        List<ResolvedAttachment> attachments = List.of();
        boolean usesAttachmentConductors = false;

        if (design != null) {
            if (design.hasTowerStructure()) {
                legacyWireHangY = TowerStructureGenerator.generate(
                    design.getTowerStructure(),
                    frame,
                    footprint,
                    result,
                    coordinateTransformer,
                    projectionHandler,
                    terrain);
            } else {
                legacyWireHangY = applyPoleDesign(design, planPoint, groundY, tangent, footprint, result);
            }
            attachments = attachmentResolver.resolve(design, frame);
            usesAttachmentConductors = design.hasEnabledAttachments();
            for (TowerValidationIssue issue : TowerStructureValidator.validate(design)) {
                if (issue.severity() != com.plot.plugin.powerline.design.structure.TowerValidationSeverity.INFO) {
                    result.warnings.add(issue.localizedMessage());
                }
            }
            for (ResolvedAttachment attachment : attachments) {
                LineEquipmentGenerator.place(attachment, frame, footprint, result, projectionHandler);
            }
        } else {
            legacyWireHangY = groundY + (int) Math.round(footprint.getPoleHeight());
            generateDefaultPole(planPoint, groundY, legacyWireHangY, footprint, result);
        }

        result.recordRole(site.getRole());

        return new PolePlacement(
            planPoint,
            frame,
            design,
            attachments,
            legacyWireHangY,
            usesAttachmentConductors,
            site.getRole(),
            assignment.resolvedDesignId(),
            site.getStationing());
    }

    private static TowerSelectionContext buildSelectionContext(
            PowerPoleSite site,
            List<PowerPoleSite> sites,
            int index,
            PowerLineFootprint footprint,
            PoleDesignResolver designResolver) {
        TowerSelectionContext context = new TowerSelectionContext();
        context.setSite(site);
        context.setDeflectionAngle(site.getDeflectionAngle());
        if (index > 0) {
            context.setIncomingSpan(site.getPlanPosition().distance(sites.get(index - 1).getPlanPosition()));
        }
        if (index < sites.size() - 1) {
            context.setOutgoingSpan(site.getPlanPosition().distance(sites.get(index + 1).getPlanPosition()));
        }
        if (footprint.hasTowerFamily()) {
            context.setFamily(new TowerFamilyResolver().find(footprint.getTowerFamilyId()));
        }
        context.setProfile(new EngineeringRuleProfileResolver().find(footprint.effectiveEngineeringProfileId()));
        context.setRequiredGroundClearance(context.getProfile().getClearance().getMinimumGroundClearance());
        return context;
    }

    private void generateDefaultPole(
            Vec2d planPoint,
            int groundY,
            int poleTopY,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
        MaterialMix poleMaterial = footprint.getPoleMaterial();
        for (int y = groundY + 1; y <= poleTopY; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(poleMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId);
        }
    }

    int applyPoleDesign(
            PoleDesign design,
            Vec2d planPoint,
            int groundY,
            Vec2d tangent,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        int wireHangY = design.wireHangHeightFromGround(groundY);
        Vec2d direction = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : new Vec2d(1, 0);
        Vec2d normal = RoadGeometryUtils.leftNormal(direction);
        PoleLayerVoxelPlacer.placeDesign(
            design,
            planPoint,
            groundY + 1,
            normal,
            new GenerationVoxelSink(result, projectionHandler),
            footprint.getId(),
            PoleLayerVoxelPlacer.worldMapper(coordinateTransformer));
        return wireHangY;
    }

    static int computeWireHangHeight(int groundY, PoleDesign design) {
        return design.wireHangHeightFromGround(groundY);
    }

    static Vec2d computePoleTangent(List<Vec2d> poles, int index) {
        if (poles == null || poles.size() < 2) {
            return new Vec2d(1, 0);
        }
        if (index <= 0) {
            return poles.get(1).subtract(poles.get(0));
        }
        if (index >= poles.size() - 1) {
            return poles.get(index).subtract(poles.get(index - 1));
        }
        Vec2d incoming = poles.get(index).subtract(poles.get(index - 1));
        Vec2d outgoing = poles.get(index + 1).subtract(poles.get(index));
        return incoming.add(outgoing);
    }

    static Vec2d computePoleTangentFromSites(List<PowerPoleSite> sites, int index) {
        if (sites == null || sites.size() < 2) {
            return new Vec2d(1, 0);
        }
        List<Vec2d> positions = new ArrayList<>(sites.size());
        for (PowerPoleSite site : sites) {
            positions.add(site.getPlanPosition());
        }
        return computePoleTangent(positions, index);
    }

    private void recordBlock(PowerLineGenerationResult result, BlockPos pos, String newBlockId) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projectionHandler.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }
}
