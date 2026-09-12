package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PluginProjectionContext;
import com.plot.api.world.WorldProjectionUnavailableException;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.placement.GenerationVoxelSink;
import com.plot.plugin.powerline.placement.PoleLayerVoxelPlacer;
import com.plot.plugin.powerline.placement.PolePlacementBase;
import com.plot.plugin.powerline.placement.PoleSiteDecorationClearance;
import com.plot.plugin.powerline.placement.PoleWaterFoundation;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionContext;
import com.plot.plugin.powerline.engineering.validation.ValidationLimits;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelopeResolver;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParametricLinePlacement;
import com.plot.plugin.powerline.design.parametric.TowerParametricSitePlacement;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.equipment.JumperWireGenerator;
import com.plot.plugin.powerline.equipment.LineEquipmentGenerator;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

        PluginProjectionContext projection;
        try {
            projection = PluginProjectionContext.capture(coordinateTransformer);
        } catch (WorldProjectionUnavailableException e) {
            return result;
        }

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(
            footprint, projection.coordinates());
        result.poleCount = sites.size();
        result.poleSites.addAll(sites.stream().map(PowerPoleSite::copy).toList());
        if (sites.isEmpty()) {
            return result;
        }

        PoleDesignAssignmentResolver assignmentResolver = new PoleDesignAssignmentResolver(
            designResolver,
            new TowerFamilyResolver());

        TowerLineBuildEnvelope lineEnvelope = TowerBuildEnvelopeResolver.fromPoleSites(sites, terrain);

        Set<String> emittedParametricLineWarnings = new LinkedHashSet<>();
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
                lineEnvelope,
                emittedParametricLineWarnings,
                result));
        }
        result.polePlacements.addAll(placements);

        for (int i = 0; i < placements.size(); i++) {
            TowerRole role = placements.get(i).role();
            if ((role == TowerRole.ANGLE || role == TowerRole.TERMINAL || role == TowerRole.DEAD_END)
                    && i > 0 && i < placements.size() - 1) {
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
                    coordinateTransformer,
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
            TowerLineBuildEnvelope lineEnvelope,
            Set<String> emittedParametricLineWarnings,
            PowerLineGenerationResult result) {
        Vec2d planPoint = site.getPlanPosition();
        PolePlacementBase placementBase = PolePlacementBase.resolve(planPoint, terrain);
        int buildBaseY = placementBase.buildBaseY();
        Vec2d tangent = computePoleTangentFromSites(sites, index);
        PoleFrame frame = PoleFrame.fromPole(planPoint, tangent, buildBaseY);

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
        TowerBuildEnvelope parametricEnvelope = footprint.isPerSiteParametricHeightEnabled()
            ? lineEnvelope.siteEnvelope(index)
            : lineEnvelope.constraintEnvelope();
        if (design != null && footprint.hasParametricTowerConfig()) {
            design = ParametricStyleTowerApplicator.apply(
                design,
                footprint.getParametricTowerConfig(),
                parametricEnvelope);
        }
        if (design != null) {
            if (footprint.isPerSiteParametricHeightEnabled()) {
                TowerParametricSitePlacement.PreparationResult prepared =
                    TowerParametricSitePlacement.prepareForSite(design, lineEnvelope, index);
                design = prepared.design();
                emitParametricSiteWarnings(prepared.warnings(), index, result, emittedParametricLineWarnings);
            } else {
                TowerParametricLinePlacement.PreparationResult prepared =
                    TowerParametricLinePlacement.prepare(design, lineEnvelope);
                design = prepared.design();
                emitParametricLineWarnings(prepared.warnings(), lineEnvelope, result, emittedParametricLineWarnings);
            }
        }
        if (design != null && !design.hasEnabledAttachments()) {
            design = design.copy();
            design.ensureDefaultConductorAttachments();
        }

        PoleSiteDecorationClearance.clearAroundPole(
            planPoint,
            placementBase,
            design,
            footprint.getPoleHeight(),
            tangent,
            terrain,
            result,
            projectionHandler,
            coordinateTransformer);
        fillWaterFoundationIfNeeded(planPoint, placementBase, footprint, result);

        int legacyWireHangY = buildBaseY + (int) Math.round(footprint.getPoleHeight());
        List<ResolvedAttachment> attachments = List.of();
        boolean usesAttachmentConductors = false;

        if (design != null) {
            boolean parametricBlocked = design.isParametricMode()
                && TowerParametricEditor.hasBlockingErrors(design, parametricEnvelope);
            if (parametricBlocked) {
                result.warnings.add("parametric.constraint_error");
            } else if (design.hasTowerStructure()) {
                legacyWireHangY = TowerStructureGenerator.generate(
                    design.getTowerStructure(),
                    frame,
                    footprint,
                    result,
                    coordinateTransformer,
                    projectionHandler,
                    terrain);
            } else {
                legacyWireHangY = applyPoleDesign(design, planPoint, placementBase, tangent, footprint, result);
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
            legacyWireHangY = buildBaseY + (int) Math.round(footprint.getPoleHeight());
            generateDefaultPole(planPoint, placementBase, legacyWireHangY, footprint, result);
            PoleDesign synthetic = new PoleDesign("_default_pole", "Default");
            synthetic.setAttachments(ConductorAttachmentPresets.singleConductor(footprint.getPoleHeight()));
            attachments = attachmentResolver.resolve(synthetic, frame);
            usesAttachmentConductors = !attachments.isEmpty();
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
            context.setIncomingSpan(PowerPoleLayoutUtils.worldSpanBlocks(sites.get(index - 1), site));
        }
        if (index < sites.size() - 1) {
            context.setOutgoingSpan(PowerPoleLayoutUtils.worldSpanBlocks(site, sites.get(index + 1)));
        }
        if (footprint.hasTowerFamily()) {
            context.setFamily(new TowerFamilyResolver().find(footprint.getTowerFamilyId()));
        }
        context.setRequiredGroundClearance(ValidationLimits.DEFAULT_MIN_GROUND_CLEARANCE);
        return context;
    }

    private void fillWaterFoundationIfNeeded(
            Vec2d planPoint,
            PolePlacementBase placementBase,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        PoleWaterFoundation.fillBelowBuildBase(
            planPoint,
            placementBase,
            footprint.getPoleMaterial(),
            footprint.getId(),
            result,
            projectionHandler,
            coordinateTransformer);
    }

    private void generateDefaultPole(
            Vec2d planPoint,
            PolePlacementBase placementBase,
            int poleTopY,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
        MaterialMix poleMaterial = footprint.getPoleMaterial();
        for (int y = placementBase.poleLayerStartY(); y <= poleTopY; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(poleMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId);
        }
    }

    int applyPoleDesign(
            PoleDesign design,
            Vec2d planPoint,
            PolePlacementBase placementBase,
            Vec2d tangent,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        int buildBaseY = placementBase.buildBaseY();
        int wireHangY = design.wireHangHeightFromGround(buildBaseY);
        Vec2d direction = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : new Vec2d(1, 0);
        Vec2d normal = WorldCoordinateUtils.leftNormal(direction);
        PoleLayerVoxelPlacer.placeDesign(
            design,
            planPoint,
            placementBase.poleLayerStartY(),
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
        Vec2d inNorm = incoming.lengthSquared() > 1e-12 ? incoming.normalize() : incoming;
        Vec2d outNorm = outgoing.lengthSquared() > 1e-12 ? outgoing.normalize() : outgoing;
        return inNorm.add(outNorm);
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

    private static void emitParametricLineWarnings(
            List<String> warnings,
            TowerLineBuildEnvelope lineEnvelope,
            PowerLineGenerationResult result,
            Set<String> emittedParametricLineWarnings) {
        for (String warning : warnings) {
            if (!emittedParametricLineWarnings.add(warning)) {
                continue;
            }
            if (warning.startsWith("parametric.height_clamped_for_line:")) {
                int maxHeight = Integer.parseInt(warning.substring(warning.indexOf(':') + 1));
                result.warnings.add(PowerLineGenerationI18n.parametricHeightClampedForLine(
                    maxHeight,
                    lineEnvelope.limitingSiteIndex() + 1));
            } else if ("parametric.world_height_exceeded_on_line".equals(warning)) {
                result.warnings.add(PowerLineGenerationI18n.parametricWorldHeightExceededOnLine());
            }
        }
    }

    private static void emitParametricSiteWarnings(
            List<String> warnings,
            int siteIndex,
            PowerLineGenerationResult result,
            Set<String> emittedParametricLineWarnings) {
        for (String warning : warnings) {
            String dedupeKey = warning + "@" + siteIndex;
            if (!emittedParametricLineWarnings.add(dedupeKey)) {
                continue;
            }
            if (warning.startsWith("parametric.height_clamped_for_site:")) {
                int maxHeight = Integer.parseInt(warning.substring(warning.indexOf(':') + 1));
                result.warnings.add(PowerLineGenerationI18n.parametricHeightClampedForSite(
                    maxHeight,
                    siteIndex + 1));
            } else if ("parametric.world_height_exceeded_on_site".equals(warning)) {
                result.warnings.add(PowerLineGenerationI18n.parametricWorldHeightExceededOnSite(siteIndex + 1));
            }
        }
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
