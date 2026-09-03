package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.material.EarthMaterialClass;
import com.plot.core.material.MaterialConversionModel;
import com.plot.core.model.Shape;
import com.plot.core.plugin.PluginManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.BuildingPlugin;
import com.plot.plugin.RoadSystemPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.*;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.design.RoadCorridorBaker;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.geometry.ZoneBoundaryRetainingEdgeAdapter;
import com.plot.plugin.earthwork.grading.ZoneOverlapAnalyzer;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


/** 土方编辑 Tab：区域几何、分区与坡面设置。 */
public final class EarthworkEditPanel {
    private final EarthworkUiContext ctx;

    public EarthworkEditPanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        EarthworkUiWidgets.renderRegionSelector(ctx);
                ImGui.separator();
                renderGlobalGridSettings();
                ImGui.separator();
                renderCompositionSettings();
                ImGui.separator();

                GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
                if (region == null) {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.select_region_hint"));
                    return;
                }

                if (!region.getId().equals(ctx.regionNameEditingRegionId())) {
                    ctx.regionNameBuffer().set(region.getName());
                    ctx.setRegionNameEditingRegionId(region.getId());
                }
                if (ImGui.inputText(PlotI18n.tr("plugin.earthwork.region_name"), ctx.regionNameBuffer())) {
                    region.setName(ctx.regionNameBuffer().get());
                }
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }

                renderSelectedZoneOverlapWarnings(region.getId());
                renderRegionGeometrySettings(region);

                renderSurfaceModeSettings(region);
                renderZoneTypeSettings(region);
                renderZoneEdgeSettings(region);

                if (ctx.config().getWorkMode().showsLearningMetrics()) {
                    renderMaterialPropertiesSettings(region);
                }

                int[] previewGridSize = {region.getPreviewGridSize()};
                boolean previewGridChanged = ImGui.sliderInt("##preview_grid_size", previewGridSize, 1, 20,
                    PlotI18n.tr("plugin.earthwork.preview_grid_size", previewGridSize[0]));
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                if (previewGridChanged) {
                    region.setPreviewGridSize(previewGridSize[0]);
                    ctx.invalidatePreview();
                }
                UIUtils.renderEngineeringTooltip("hint.plot.earthwork.preview_grid_size");

                EarthworkUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.earthwork.cut_material"), region.getCutExposeMaterial(),
                    blockId -> {
                        ctx.projectHistory().push(ctx.project());
                        region.setCutExposeMaterial(blockId);
                        ctx.invalidatePreview();
                    });
                EarthworkUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.earthwork.fill_material"), region.getFillMaterial(),
                    blockId -> {
                        ctx.projectHistory().push(ctx.project());
                        region.setFillMaterial(blockId);
                        ctx.invalidatePreview();
                    });
    }

    private void renderSurfaceModeSettings(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone != null && !zone.getType().isSupportedInMvp()) {
            renderPhaseCZoneSettings(zone);
            return;
        }
        GradingSurfaceMode[] modes = GradingSurfaceMode.values();
        String[] modeLabels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            modeLabels[i] = modes[i].label();
        }
        ImInt modeIndex = new ImInt(region.getSurfaceMode().ordinal());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.surface_mode"), modeIndex, modeLabels)) {
            int selected = modeIndex.get();
            if (selected >= 0 && selected < modes.length && modes[selected] != region.getSurfaceMode()) {
                ctx.projectHistory().push(ctx.project());
                region.setSurfaceMode(modes[selected]);
                if (zone != null) {
                    zone.getDesignSurface().setKind(DesignSurfaceKind.fromSurfaceMode(modes[selected]));
                    zone.syncDesignSurfaceToRegion();
                }
                initializeSurfaceDefaults(region, modes[selected]);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.surface_mode");

        switch (region.getSurfaceMode()) {
            case LEVEL_PAD -> renderFlatSurfaceSettings(region);
            case SINGLE_SLOPE_PLANE -> renderFixedSlopeSettings(region);
            case THREE_POINT_PLANE -> renderThreePointSurfaceSettings(region);
            case BEST_FIT_PLANE, DRAINAGE_SURFACE -> renderFitSlopeSettings(region);
            case MATCH_EXISTING -> renderMatchExistingSettings(region);
            case MULTI_PLANE -> renderMultiPlaneSettings(region);
        }
    }

    private void renderMatchExistingSettings(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone == null) {
            return;
        }
        DesignSurface surface = zone.getDesignSurface();
        int[] offset = {surface.getVerticalOffset()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.vertical_offset"), offset, -64, 128,
            PlotI18n.tr("plugin.earthwork.vertical_offset_value", offset[0]))) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            surface.setVerticalOffset(offset[0]);
            zone.syncDesignSurfaceToRegion();
            ctx.invalidatePreview();
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.match_existing_hint"));
    }

    private void renderMultiPlaneSettings(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone == null) {
            return;
        }
        int facetCount = zone.getDesignSurface().getFacets().size();
        ImGui.text(PlotI18n.tr("plugin.earthwork.multi_plane_facet_count", facetCount));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.multi_plane_hint"));
    }

    private void renderZoneTypeSettings(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone == null) {
            return;
        }
        GradingZoneType[] types = {
            GradingZoneType.FLAT,
            GradingZoneType.SLOPED,
            GradingZoneType.BUILDING_PAD,
            GradingZoneType.EXCAVATION_PIT,
            GradingZoneType.TERRAIN_FIT,
            GradingZoneType.ROAD_CORRIDOR,
            GradingZoneType.LANDSCAPE
        };
        String[] labels = new String[types.length];
        int selectedIndex = 0;
        for (int i = 0; i < types.length; i++) {
            labels[i] = PlotI18n.tr("plugin.earthwork.zone_type." + types[i].name().toLowerCase());
            if (types[i] == zone.getType()) {
                selectedIndex = i;
            }
        }
        ImInt typeIndex = new ImInt(selectedIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.zone_type"), typeIndex, labels)) {
            int picked = typeIndex.get();
            if (picked >= 0 && picked < types.length && types[picked] != zone.getType()) {
                ctx.projectHistory().push(ctx.project());
                zone.setType(types[picked]);
                ctx.invalidatePreview();
            }
        }

        renderVerticalAdjustmentPolicy(zone);

        int[] priority = {zone.getPriority()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.zone_priority"), priority, 0, 200)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.setPriority(priority[0]);
            ctx.invalidatePreview();
        }
    }

    private void renderVerticalAdjustmentPolicy(GradingZone zone) {
        VerticalAdjustmentPolicy policy = zone.getVerticalAdjustmentPolicy();
        VerticalAdjustmentPolicy.Mode[] modes = VerticalAdjustmentPolicy.Mode.values();
        String[] labels = new String[modes.length];
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.earthwork.vertical_adjustment." + modes[i].name().toLowerCase());
            if (modes[i] == policy.getMode()) {
                selectedIndex = i;
            }
        }
        ImInt modeIndex = new ImInt(selectedIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.vertical_adjustment_policy"), modeIndex, labels)) {
            int picked = modeIndex.get();
            if (picked >= 0 && picked < modes.length && modes[picked] != policy.getMode()) {
                ctx.projectHistory().push(ctx.project());
                VerticalAdjustmentPolicy next = policy.copy();
                applyVerticalAdjustmentModeDefaults(next, modes[picked], zone);
                zone.setVerticalAdjustmentPolicy(next);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.vertical_adjustment_policy");
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.earthwork.vertical_adjustment_hint." + policy.getMode().name().toLowerCase()));

        if (!policy.allowsVerticalAdjustment()) {
            return;
        }

        int[] minOffset = {policy.getMinOffset()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.vertical_adjustment_min"), minOffset, -32, 32)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            VerticalAdjustmentPolicy next = zone.getVerticalAdjustmentPolicy().copy();
            next.setMinOffset(minOffset[0]);
            zone.setVerticalAdjustmentPolicy(next);
            ctx.invalidatePreview();
        }

        int[] maxOffset = {policy.getMaxOffset()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.vertical_adjustment_max"), maxOffset, -32, 32)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            VerticalAdjustmentPolicy next = zone.getVerticalAdjustmentPolicy().copy();
            next.setMaxOffset(maxOffset[0]);
            zone.setVerticalAdjustmentPolicy(next);
            ctx.invalidatePreview();
        }

        float[] weight = {policy.getWeight()};
        boolean weightChanged = ImGui.sliderFloat("##vertical_adjustment_weight", weight, 0.0f, 2.0f,
            PlotI18n.tr("plugin.earthwork.vertical_adjustment_weight", weight[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (weightChanged) {
            VerticalAdjustmentPolicy next = zone.getVerticalAdjustmentPolicy().copy();
            next.setWeight(weight[0]);
            zone.setVerticalAdjustmentPolicy(next);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.vertical_adjustment_weight");
    }

    private static void applyVerticalAdjustmentModeDefaults(
            VerticalAdjustmentPolicy next,
            VerticalAdjustmentPolicy.Mode mode,
            GradingZone zone) {
        next.setMode(mode);
        if (mode == VerticalAdjustmentPolicy.Mode.LOCKED || mode == VerticalAdjustmentPolicy.Mode.DERIVED) {
            next.setMinOffset(0);
            next.setMaxOffset(0);
            next.setWeight(VerticalAdjustmentPolicy.DEFAULT_WEIGHT);
            return;
        }
        if (next.getMinOffset() != 0 || next.getMaxOffset() != 0) {
            return;
        }
        if (mode == VerticalAdjustmentPolicy.Mode.BOUNDED) {
            next.setMinOffset(-VerticalAdjustmentPolicy.ROAD_BOUNDED_RANGE);
            next.setMaxOffset(VerticalAdjustmentPolicy.ROAD_BOUNDED_RANGE);
            next.setWeight(VerticalAdjustmentPolicy.DEFAULT_WEIGHT);
            return;
        }
        VerticalAdjustmentPolicy seed = VerticalAdjustmentPolicy.defaultFor(
            zone.getType(),
            true,
            zone.getDesignSurface().getKind());
        if (seed.allowsVerticalAdjustment() && (seed.getMinOffset() != 0 || seed.getMaxOffset() != 0)) {
            next.setMinOffset(seed.getMinOffset());
            next.setMaxOffset(seed.getMaxOffset());
            next.setWeight(seed.getWeight());
            return;
        }
        next.setMinOffset(-VerticalAdjustmentPolicy.LANDSCAPE_RANGE);
        next.setMaxOffset(VerticalAdjustmentPolicy.LANDSCAPE_RANGE);
        next.setWeight(VerticalAdjustmentPolicy.LANDSCAPE_WEIGHT);
    }

    private void renderPhaseCZoneSettings(GradingZone zone) {
        if (zone.getType() == GradingZoneType.BUILDING_PAD) {
            renderBuildingPadSettings(zone);
        } else if (zone.getType() == GradingZoneType.EXCAVATION_PIT) {
            renderExcavationPitSettings(zone);
        } else if (zone.getType() == GradingZoneType.TERRAIN_FIT
            || zone.getType() == GradingZoneType.LANDSCAPE) {
            if (zone.getDesignSurface().hasBakedElevation()) {
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.baked_samples",
                    zone.getDesignSurface().getBakedElevationGrid().sampleCount()));
            } else {
                renderFitSlopeSettings(zone.getRegion());
            }
        } else if (zone.getType() == GradingZoneType.ROAD_CORRIDOR) {
            renderRoadCorridorSettings(zone);
        }
    }

    private void renderZoneEdgeSettings(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone == null) {
            return;
        }
        ZoneEdgeSettings settings = zone.getEdgeSettings();
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.earthwork.edge_settings_header"));
        ctx.showEdgeTreatmentOverlayRef().set(ctx.config().isShowEdgeTreatmentOverlay());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.edge_show_canvas_overlay"), ctx.showEdgeTreatmentOverlayRef())) {
            ctx.config().setShowEdgeTreatmentOverlay(ctx.showEdgeTreatmentOverlayRef().get());
            ctx.config().save();
        }
        renderEdgeTreatmentLegend();

        EdgeTreatment[] treatments = EdgeTreatment.values();
        String[] treatmentLabels = new String[treatments.length];
        int defaultIndex = settings.getDefaultTreatment().ordinal();
        for (int i = 0; i < treatments.length; i++) {
            treatmentLabels[i] = PlotI18n.tr(treatments[i].i18nKey());
            if (treatments[i] == settings.getDefaultTreatment()) {
                defaultIndex = i;
            }
        }
        ImInt treatmentIndex = new ImInt(defaultIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.edge_default_treatment"), treatmentIndex, treatmentLabels)) {
            int picked = treatmentIndex.get();
            if (picked >= 0 && picked < treatments.length) {
                ctx.projectHistory().push(ctx.project());
                settings.setDefaultTreatment(treatments[picked]);
                ctx.invalidatePreview();
            }
        }

        int[] cutPitch = {settings.getCutSlopePitchRatio()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_cut_slope_pitch"), cutPitch, 1, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            settings.setCutSlopePitchRatio(cutPitch[0]);
            ctx.invalidatePreview();
        }

        int[] fillNumerator = {settings.getFillSlopePitchNumerator()};
        int[] fillDenominator = {settings.getFillSlopePitchDenominator()};
        boolean fillChanged = ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_fill_slope_run"), fillNumerator, 1, 16);
        fillChanged |= ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_fill_slope_rise"), fillDenominator, 1, 16);
        if (fillChanged) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            settings.setFillSlopePitchNumerator(fillNumerator[0]);
            settings.setFillSlopePitchDenominator(fillDenominator[0]);
            ctx.invalidatePreview();
        }
        ImGui.textDisabled(PlotI18n.tr(
            "plugin.earthwork.edge_fill_slope_ratio",
            fillNumerator[0],
            fillDenominator[0]));

        int[] maxReach = {settings.getMaximumReachBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_max_reach"), maxReach, 0, 32)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            settings.setMaximumReachBlocks(maxReach[0]);
            ctx.invalidatePreview();
        }

        int[] benchWidth = {settings.getBenchWidthBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_bench_width"), benchWidth, 0, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            settings.setBenchWidthBlocks(benchWidth[0]);
            ctx.invalidatePreview();
        }

        if (settings.getDefaultTreatment() == EdgeTreatment.RETAINING_WALL
            || hasRetainingWallEdgeOverride(settings)) {
            ImBoolean useZoneFill = new ImBoolean(settings.isUseLinkedZoneFillMaterial());
            if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.edge_use_zone_fill_material"), useZoneFill)) {
                ctx.projectHistory().push(ctx.project());
                settings.setUseLinkedZoneFillMaterial(useZoneFill.get());
                ctx.invalidatePreview();
            }
            if (!settings.isUseLinkedZoneFillMaterial()) {
                EarthworkUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.earthwork.edge_wall_material"), settings.getWallMaterial(),
                    blockId -> {
                        ctx.projectHistory().push(ctx.project());
                        settings.setWallMaterial(blockId);
                        ctx.invalidatePreview();
                    });
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.edge_sync_retaining_edges"), 0, 0)) {
                ctx.projectHistory().push(ctx.project());
                EarthworkSite site = ctx.project().getActiveSite();
                int synced = ZoneBoundaryRetainingEdgeAdapter.syncZoneToSite(site, zone);
                ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.edge_sync_retaining_edges_done", synced));
                ctx.invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.earthwork.edge_sync_retaining_edges");
        }

        renderBoundaryEdgeOverrides(region, zone, settings, treatments, treatmentLabels);
    }

    private static boolean hasRetainingWallEdgeOverride(ZoneEdgeSettings settings) {
        for (BoundaryEdgeOverride override : settings.getEdgeOverrides()) {
            if (override != null && override.getTreatment() == EdgeTreatment.RETAINING_WALL) {
                return true;
            }
        }
        return false;
    }

    private void renderEdgeTreatmentLegend() {
        EdgeTreatment[] treatments = EdgeTreatment.values();
        for (int i = 0; i < treatments.length; i++) {
            EdgeTreatment treatment = treatments[i];
            if (i > 0) {
                ImGui.sameLine();
            }
            ImGui.textColored(
                EarthworkEdgeTreatmentColors.colorFor(treatment),
                "■ " + PlotI18n.tr(treatment.i18nKey()));
        }
    }

    private void renderBoundaryEdgeOverrides(
            GradingRegion region,
            GradingZone zone,
            ZoneEdgeSettings settings,
            EdgeTreatment[] treatments,
            String[] treatmentLabels) {
        List<Vec2d> points = region.getOuterPoints();
        if (points.size() < 3) {
            return;
        }
        if (!ImGui.treeNode(PlotI18n.tr("plugin.earthwork.edge_per_edge_overrides"))) {
            return;
        }
        int edgeCount = points.size();
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            EdgeTreatment current = settings.resolveTreatment(edgeIndex);
            ImInt edgeTreatmentIndex = new ImInt(current.ordinal());
            String label = PlotI18n.tr("plugin.earthwork.edge_item", edgeIndex + 1);
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo(label, edgeTreatmentIndex, treatmentLabels)) {
                int picked = edgeTreatmentIndex.get();
                if (picked >= 0 && picked < treatments.length) {
                    ctx.projectHistory().push(ctx.project());
                    setEdgeOverride(settings, edgeIndex, treatments[picked], settings.getDefaultTreatment());
                    ctx.invalidatePreview();
                }
            }
        }
        ImGui.treePop();
    }

    private static void setEdgeOverride(
            ZoneEdgeSettings settings,
            int edgeIndex,
            EdgeTreatment treatment,
            EdgeTreatment defaultTreatment) {
        List<BoundaryEdgeOverride> overrides = new ArrayList<>(settings.getEdgeOverrides());
        overrides.removeIf(item -> item != null && item.getEdgeIndex() == edgeIndex);
        if (treatment != defaultTreatment) {
            overrides.add(new BoundaryEdgeOverride(edgeIndex, treatment));
        }
        settings.setEdgeOverrides(overrides);
    }

    private void renderSelectedZoneOverlapWarnings(String zoneId) {
        if (zoneId == null || zoneId.isBlank() || ctx.project().getRegionCount() < 2) {
            return;
        }
        List<ZoneOverlapAnalyzer.ZoneOverlap> overlaps =
            ZoneOverlapAnalyzer.findOverlapsInvolving(ctx.project().getActiveSite(), zoneId);
        if (overlaps.isEmpty()) {
            return;
        }
        ImGui.textColored(
            PluginUiColors.WARNING_OVERLAP,
            PlotI18n.tr("plugin.earthwork.zone_overlap_selected_header"));
        for (ZoneOverlapAnalyzer.ZoneOverlap overlap : overlaps) {
            String otherName = overlap.zoneIdA().equals(zoneId) ? overlap.zoneNameB() : overlap.zoneNameA();
            ImGui.bulletText(PlotI18n.tr(
                "plugin.earthwork.zone_overlap_selected_item",
                otherName,
                overlap.winnerZoneName(),
                overlap.overlapCells()));
        }
        ImGui.spacing();
    }

    private void renderBalanceScopeSettings(CompositionPolicy policy) {
        BalanceScope[] scopes = {
            BalanceScope.SITE,
            BalanceScope.ZONE,
            BalanceScope.PROJECT
        };
        String[] labels = {
            PlotI18n.tr("plugin.earthwork.balance_scope.site"),
            PlotI18n.tr("plugin.earthwork.balance_scope.zone"),
            PlotI18n.tr("plugin.earthwork.balance_scope.project")
        };
        int selected = 0;
        BalanceScope current = policy.getBalanceScopeEnum();
        for (int i = 0; i < scopes.length; i++) {
            if (scopes[i] == current) {
                selected = i;
                break;
            }
        }
        ImInt scopeIndex = new ImInt(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.balance_scope_label"), scopeIndex, labels)) {
            int picked = scopeIndex.get();
            if (picked >= 0 && picked < scopes.length) {
                ctx.projectHistory().push(ctx.project());
                policy.setBalanceScope(scopes[picked]);
                ctx.invalidatePreview();
            }
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.balance_scope_hint"));
        ImGui.spacing();
    }

    private void renderOverlapResolutionSettings(CompositionPolicy policy) {
        if (ctx.project().getRegionCount() < 2) {
            return;
        }
        String[] modes = {
            CompositionPolicy.OVERLAP_HIGHEST_PRIORITY_WINS,
            CompositionPolicy.OVERLAP_LARGEST_ZONE_WINS
        };
        String[] labels = {
            PlotI18n.tr("plugin.earthwork.overlap_resolution.highest_priority_wins"),
            PlotI18n.tr("plugin.earthwork.overlap_resolution.largest_zone_wins")
        };
        int selected = CompositionPolicy.OVERLAP_LARGEST_ZONE_WINS.equals(policy.getOverlapResolution()) ? 1 : 0;
        ImInt modeIndex = new ImInt(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.overlap_resolution_label"), modeIndex, labels)) {
            int picked = modeIndex.get();
            if (picked >= 0 && picked < modes.length) {
                ctx.projectHistory().push(ctx.project());
                policy.setOverlapResolution(modes[picked]);
                ctx.invalidatePreview();
            }
        }
        ImGui.spacing();
    }

    private void renderBalanceMethodSettings(CompositionPolicy policy) {
        if (!policy.getBalanceScopeEnum().allowsSiteVerticalOptimization()
            || ctx.project().getRegionCount() < 2) {
            return;
        }
        OptimizationMode[] modes = {
            OptimizationMode.NONE,
            OptimizationMode.UNIFORM_VERTICAL_SHIFT,
            OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION
        };
        String[] labels = {
            PlotI18n.tr("plugin.earthwork.optimization_mode.none"),
            PlotI18n.tr("plugin.earthwork.optimization_mode.uniform_vertical_shift"),
            PlotI18n.tr("plugin.earthwork.optimization_mode.constrained_zone")
        };
        int selected = 0;
        OptimizationMode current = policy.getOptimizationModeEnum();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                selected = i;
                break;
            }
        }
        ImInt methodIndex = new ImInt(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.optimization_mode_label"), methodIndex, labels)) {
            int picked = methodIndex.get();
            if (picked >= 0 && picked < modes.length) {
                ctx.projectHistory().push(ctx.project());
                policy.setOptimizationMode(modes[picked]);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.optimization_mode");
        ImGui.spacing();
    }

    private void renderCompositionSettings() {
        EarthworkSite site = ctx.project().getActiveSite();
        CompositionPolicy policy = site.getCompositionPolicy();

        ImGui.text(PlotI18n.tr("plugin.earthwork.composition_settings"));
        renderBalanceScopeSettings(policy);
        renderBalanceMethodSettings(policy);
        renderOverlapResolutionSettings(policy);
        int[] blendWidth = {policy.getBlendWidthBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.blend_width_blocks"), blendWidth, 0, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            policy.setBlendWidthBlocks(blendWidth[0]);
            ctx.invalidatePreview();
        }

        ImGui.spacing();
        renderExclusionZoneSettings(site);
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.earthwork.breaklines_header"));
        List<GradingZone> zones = new ArrayList<>(site.getGradingZones().values());
        if (zones.size() < 2) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.breaklines_need_two_zones"));
        } else {
            for (Breakline breakline : site.getBreaklines()) {
                renderBreaklineRow(site, breakline, zones);
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.add_breakline"), 0, 0)) {
                ctx.projectHistory().push(ctx.project());
                Breakline breakline = new Breakline(UUID.randomUUID().toString());
                breakline.setName(PlotI18n.tr("plugin.earthwork.breakline_default_name", site.getBreaklines().size() + 1));
                breakline.setPoints(List.of(new Vec2d(5, 0), new Vec2d(5, 10)));
                breakline.setLeftZoneId(zones.get(0).getId());
                breakline.setRightZoneId(zones.get(1).getId());
                site.addBreakline(breakline);
                ctx.invalidatePreview();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.breakline_from_selection"), 0, 0)) {
                List<Vec2d> points = extractBreaklinePointsFromSelection();
                if (points.size() >= 2) {
                    ctx.projectHistory().push(ctx.project());
                    Breakline breakline = new Breakline(UUID.randomUUID().toString());
                    breakline.setName(PlotI18n.tr("plugin.earthwork.breakline_default_name", site.getBreaklines().size() + 1));
                    breakline.setPoints(points);
                    breakline.setLeftZoneId(zones.get(0).getId());
                    breakline.setRightZoneId(zones.get(1).getId());
                    site.addBreakline(breakline);
                    ctx.invalidatePreview();
                }
            }
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.edge_sync_all_retaining_edges"), 0, 0)) {
            ctx.projectHistory().push(ctx.project());
            int synced = ZoneBoundaryRetainingEdgeAdapter.syncAllZonesToSite(site);
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.edge_sync_retaining_edges_done", synced));
            ctx.invalidatePreview();
        }
        renderRetainingEdgeSettings(site);
    }

    private void renderRetainingEdgeSettings(EarthworkSite site) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.retaining_edges_header"));
        for (RetainingEdge edge : site.getRetainingEdges()) {
            ImGui.pushID(edge.getId());
            ImGui.text(edge.getName().isBlank() ? edge.getId() : edge.getName());
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
                ctx.projectHistory().push(ctx.project());
                site.removeRetainingEdge(edge.getId());
                ctx.invalidatePreview();
                ImGui.popID();
                continue;
            }
            int[] topElevation = {edge.getTopElevation()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.retaining_top_elevation"), topElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                edge.setTopElevation(topElevation[0]);
                ctx.invalidatePreview();
            }
            int[] bottomElevation = {edge.getBottomElevation()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.retaining_bottom_elevation"), bottomElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                edge.setBottomElevation(bottomElevation[0]);
                ctx.invalidatePreview();
            }
            List<GradingZone> zones = new ArrayList<>(site.getGradingZones().values());
            if (!zones.isEmpty()) {
                String[] zoneLabels = zones.stream().map(GradingZone::getName).toArray(String[]::new);
                String[] zoneIds = zones.stream().map(GradingZone::getId).toArray(String[]::new);
                ImInt linkedIndex = new ImInt(indexOfZone(zoneIds, edge.getLinkedZoneId()));
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                if (ImGui.combo(PlotI18n.tr("plugin.earthwork.retaining_linked_zone"), linkedIndex, zoneLabels)) {
                    ctx.projectHistory().push(ctx.project());
                    edge.setLinkedZoneId(zoneIds[linkedIndex.get()]);
                    ctx.invalidatePreview();
                }
            }
            ImBoolean useZoneFill = new ImBoolean(edge.isUseLinkedZoneFillMaterial());
            if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.retaining_use_zone_fill_material"), useZoneFill)) {
                ctx.projectHistory().push(ctx.project());
                edge.setUseLinkedZoneFillMaterial(useZoneFill.get());
                ctx.invalidatePreview();
            }
            if (!edge.isUseLinkedZoneFillMaterial()) {
                EarthworkUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.earthwork.retaining_wall_material"), edge.getWallMaterial(),
                    blockId -> {
                        ctx.projectHistory().push(ctx.project());
                        edge.setWallMaterial(blockId);
                        ctx.invalidatePreview();
                    });
            }
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.add_retaining_edge"), 0, 0)) {
            List<Vec2d> points = extractBreaklinePointsFromSelection();
            if (points.size() < 2) {
                points = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
            }
            ctx.projectHistory().push(ctx.project());
            RetainingEdge edge = new RetainingEdge(UUID.randomUUID().toString());
            edge.setName(PlotI18n.tr("plugin.earthwork.retaining_edge_default_name", site.getRetainingEdges().size() + 1));
            edge.setPolyline(points);
            edge.setTopElevation(72);
            edge.setBottomElevation(64);
            site.addRetainingEdge(edge);
            ctx.invalidatePreview();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.retaining_edge_from_selection"), 0, 0)) {
            List<Vec2d> points = extractBreaklinePointsFromSelection();
            if (points.size() >= 2) {
                ctx.projectHistory().push(ctx.project());
                RetainingEdge edge = new RetainingEdge(UUID.randomUUID().toString());
                edge.setName(PlotI18n.tr("plugin.earthwork.retaining_edge_default_name", site.getRetainingEdges().size() + 1));
                edge.setPolyline(points);
                edge.setTopElevation(72);
                edge.setBottomElevation(64);
                site.addRetainingEdge(edge);
                ctx.invalidatePreview();
            }
        }
    }

    private void renderRegionGeometrySettings(GradingRegion region) {
        RegionGeometry geometry = region.getGeometry();
        ImGui.text(PlotI18n.tr("plugin.earthwork.geometry_header"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.earthwork.geometry_outer_summary",
            geometry.outerRing().size(),
            geometry.area()));
        if (geometry.hasHoles()) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.geometry_hole_count", geometry.holes().size()));
        }
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.geometry_add_hole_from_selection"), 0, 0)) {
            addHoleToRegion(region);
        }
        ImGui.sameLine();
        boolean clearDisabled = !geometry.hasHoles();
        if (clearDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.geometry_clear_holes"), 0, 0)) {
            ctx.projectHistory().push(ctx.project());
            region.setHoles(List.of());
            ctx.invalidatePreview();
        }
        if (clearDisabled) {
            ImGui.endDisabled();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.geometry_holes");

        if (!geometry.hasHoles()) {
            return;
        }
        ImGui.beginChild("earthwork_region_holes", 0, 72, true);
        List<List<Vec2d>> holes = region.getHoles();
        for (int i = 0; i < holes.size(); i++) {
            ImGui.pushID("hole_" + i);
            List<Vec2d> hole = holes.get(i);
            double holeArea = Math.abs(GradingRegion.signedArea(hole));
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.geometry_hole_item",
                i + 1,
                hole.size(),
                holeArea));
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
                ctx.projectHistory().push(ctx.project());
                List<List<Vec2d>> updated = new ArrayList<>(holes);
                updated.remove(i);
                region.setHoles(updated);
                ctx.invalidatePreview();
                ImGui.popID();
                break;
            }
            ImGui.popID();
        }
        ImGui.endChild();
        ImGui.spacing();
    }

    private void renderExclusionZoneSettings(EarthworkSite site) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.exclusions_header"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.exclusions_hint"));
        List<ExclusionZone> exclusions = site.getExclusionZones();
        if (exclusions.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.exclusions_empty"));
        } else {
            for (ExclusionZone exclusion : exclusions) {
                renderExclusionZoneRow(site, exclusion);
            }
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.exclusion_add_from_selection"), 0, 0)) {
            addExclusionFromSelection(site);
        }
    }

    private void renderExclusionZoneRow(EarthworkSite site, ExclusionZone exclusion) {
        ImGui.pushID(exclusion.getId());
        ImGui.text(exclusion.getName().isBlank() ? exclusion.getId() : exclusion.getName());
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
            ctx.projectHistory().push(ctx.project());
            site.removeExclusionZone(exclusion.getId());
            ctx.invalidatePreview();
            ImGui.popID();
            return;
        }

        String[] modes = {
            ExclusionZone.MODE_PRESERVE_EXISTING,
            ExclusionZone.MODE_NO_TOUCH
        };
        String[] modeLabels = {
            PlotI18n.tr("plugin.earthwork.exclusion_mode.preserve_existing"),
            PlotI18n.tr("plugin.earthwork.exclusion_mode.no_touch")
        };
        int modeIndex = ExclusionZone.MODE_NO_TOUCH.equals(exclusion.getMode()) ? 1 : 0;
        ImInt selectedMode = new ImInt(modeIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.48f);
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.exclusion_mode_label"), selectedMode, modeLabels)) {
            int picked = selectedMode.get();
            if (picked >= 0 && picked < modes.length) {
                ctx.projectHistory().push(ctx.project());
                exclusion.setMode(modes[picked]);
                ctx.invalidatePreview();
            }
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.geometry_add_hole_from_selection"), 0, 0)) {
            addHoleToExclusion(exclusion);
        }

        RegionGeometry geometry = exclusion.getGeometry();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.earthwork.exclusion_geometry_summary",
            geometry.outerRing().size(),
            geometry.holes().size(),
            geometry.area()));
        ImGui.popID();
    }

    private void addHoleToRegion(GradingRegion region) {
        List<Vec2d> hole = extractRegionOutlineFromSelection();
        if (hole.size() < 3) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_no_valid_selection"));
            return;
        }
        RegionGeometry geometry = region.getGeometry();
        Vec2d centroid = PolygonRegionUtils.computeCentroid(hole);
        if (!geometry.contains(centroid)) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_hole_outside_outer"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        List<List<Vec2d>> holes = new ArrayList<>(region.getHoles());
        holes.add(hole);
        region.setHoles(holes);
        ctx.invalidatePreview();
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_hole_added"));
    }

    private void addHoleToExclusion(ExclusionZone exclusion) {
        List<Vec2d> hole = extractRegionOutlineFromSelection();
        if (hole.size() < 3) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_no_valid_selection"));
            return;
        }
        RegionGeometry geometry = exclusion.getGeometry();
        if (geometry.isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.exclusion_needs_outer_ring"));
            return;
        }
        Vec2d centroid = PolygonRegionUtils.computeCentroid(hole);
        if (!geometry.contains(centroid)) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_hole_outside_outer"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        List<List<Vec2d>> holes = new ArrayList<>(exclusion.getHoles());
        holes.add(hole);
        exclusion.setHoles(holes);
        ctx.invalidatePreview();
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_hole_added"));
    }

    private void addExclusionFromSelection(EarthworkSite site) {
        List<Vec2d> outer = extractRegionOutlineFromSelection();
        if (outer.size() < 3) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.geometry_no_valid_selection"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        ExclusionZone exclusion = new ExclusionZone(UUID.randomUUID().toString(), outer);
        exclusion.setName(PlotI18n.tr(
            "plugin.earthwork.exclusion_default_name",
            site.getExclusionZones().size() + 1));
        site.addExclusionZone(exclusion);
        ctx.invalidatePreview();
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.exclusion_added"));
    }

    private List<Vec2d> extractRegionOutlineFromSelection() {
        for (Shape shape : ctx.host().appState().getSelectedShapes()) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() >= 3) {
                return points;
            }
        }
        return List.of();
    }

    private List<Vec2d> extractBreaklinePointsFromSelection() {
        List<Vec2d> points = new ArrayList<>();
        for (Shape shape : ctx.host().appState().getSelectedShapes()) {
            if (shape instanceof LineShape || shape instanceof PolylineShape || shape instanceof FreeDrawPath) {
                List<Vec2d> shapePoints = shape.getPoints();
                if (shapePoints != null && shapePoints.size() >= 2) {
                    for (Vec2d point : shapePoints) {
                        if (point != null) {
                            points.add(new Vec2d(point.x, point.y));
                        }
                    }
                    return points;
                }
            }
        }
        return points;
    }

    private void renderRoadCorridorSettings(GradingZone zone) {
        List<RoadEarthworkSurfaceSampler.EdgeRef> edges = EarthworkUiLookups.listAvailableRoadEdges();
        if (edges.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.no_road_edges_available"));
            return;
        }
        String[] labels = edges.stream().map(RoadEarthworkSurfaceSampler.EdgeRef::label).toArray(String[]::new);
        String[] ids = edges.stream().map(RoadEarthworkSurfaceSampler.EdgeRef::id).toArray(String[]::new);
        int currentIndex = indexOfZone(ids, zone.getRoadEdgeRef());
        ImInt edgeIndex = new ImInt(currentIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.road_edge_ref"), edgeIndex, labels)) {
            int picked = edgeIndex.get();
            if (picked >= 0 && picked < ids.length) {
                ctx.projectHistory().push(ctx.project());
                zone.setRoadEdgeRef(ids[picked]);
                ctx.invalidatePreview();
            }
        }
        if (zone.getDesignSurface().hasBakedElevation()) {
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.baked_samples",
                zone.getDesignSurface().getBakedElevationGrid().sampleCount()));
        }
        int[] corridorMargin = {zone.getDesignSurface().getWorkingMarginBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.corridor_margin_blocks"), corridorMargin, 0, 8)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.getDesignSurface().setWorkingMarginBlocks(corridorMargin[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.import_corridor_outline"), 0, 0)) {
            importRoadCorridorOutline(zone);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.bake_road_elevations"), 0, 0)) {
            bakeRoadCorridorElevations(zone);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.import_road_centerline_breakline"), 0, 0)) {
            importRoadCenterlineBreakline(zone);
        }
    }

    private void importRoadCorridorOutline(GradingZone zone) {
        if (zone == null || zone.getRoadEdgeRef().isBlank()) {
            return;
        }
        com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("road_system");
        if (!(plugin instanceof RoadSystemPlugin roadPlugin)) {
            return;
        }
        List<Vec2d> outline = roadPlugin.resolveEarthworkCorridorOutline(
            zone.getRoadEdgeRef(),
            zone.getDesignSurface().getWorkingMarginBlocks());
        if (outline.size() < 3) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.import_corridor_failed"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        zone.setOuterPoints(outline);
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.import_corridor_success"));
        ctx.invalidatePreview();
    }

    private void importRoadCenterlineBreakline(GradingZone zone) {
        if (zone == null || zone.getRoadEdgeRef().isBlank()) {
            return;
        }
        EarthworkSite site = ctx.project().getActiveSite();
        List<GradingZone> zones = new ArrayList<>(site.getGradingZones().values());
        if (zones.size() < 2) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.breaklines_need_two_zones"));
            return;
        }
        com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("road_system");
        if (!(plugin instanceof RoadSystemPlugin roadPlugin)) {
            return;
        }
        List<Vec2d> centerline = roadPlugin.resolveEarthworkRoadCenterline(zone.getRoadEdgeRef());
        if (centerline.size() < 2) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.import_corridor_failed"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        Breakline breakline = new Breakline(UUID.randomUUID().toString());
        breakline.setName(PlotI18n.tr("plugin.earthwork.road_centerline_breakline_name", zone.getName()));
        breakline.setPoints(centerline);
        breakline.setRole(Breakline.ROLE_NO_BLENDING);
        breakline.setLeftZoneId(zones.get(0).getId());
        breakline.setRightZoneId(zones.get(1).getId());
        site.addBreakline(breakline);
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.import_road_centerline_success"));
        ctx.invalidatePreview();
    }

    private void bakeRoadCorridorElevations(GradingZone zone) {
        if (zone == null || zone.getRoadEdgeRef().isBlank()) {
            return;
        }
        World world = EarthworkUiWidgets.getClientWorld();
        if (world == null) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.generate_world_unavailable"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        EarthworkSite site = ctx.project().getActiveSite();
        TerrainSnapshot terrain = ctx.terrainSnapshotCache().captureFreshSite(site, world, ctx.host().coordinates());
        int bakedCount = RoadCorridorBaker.bake(zone, terrain, EarthworkUiLookups.createRoadSurfaceLookup());
        if (bakedCount <= 0) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.bake_road_failed"));
        } else {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.bake_road_success", bakedCount));
        }
        ctx.invalidatePreview();
    }

    private void renderBreaklineRow(EarthworkSite site, Breakline breakline, List<GradingZone> zones) {
        ImGui.pushID(breakline.getId());
        ImGui.text(breakline.getName());
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
            ctx.projectHistory().push(ctx.project());
            site.removeBreakline(breakline.getId());
            ctx.invalidatePreview();
            ImGui.popID();
            return;
        }

        String[] zoneLabels = zones.stream().map(GradingZone::getName).toArray(String[]::new);
        String[] zoneIds = zones.stream().map(GradingZone::getId).toArray(String[]::new);

        ImInt leftIndex = new ImInt(indexOfZone(zoneIds, breakline.getLeftZoneId()));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.48f);
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.breakline_left_zone"), leftIndex, zoneLabels)) {
            ctx.projectHistory().push(ctx.project());
            breakline.setLeftZoneId(zoneIds[leftIndex.get()]);
            ctx.invalidatePreview();
        }
        ImGui.sameLine();
        ImInt rightIndex = new ImInt(indexOfZone(zoneIds, breakline.getRightZoneId()));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.breakline_right_zone"), rightIndex, zoneLabels)) {
            ctx.projectHistory().push(ctx.project());
            breakline.setRightZoneId(zoneIds[rightIndex.get()]);
            ctx.invalidatePreview();
        }
        ImGui.popID();
    }

    private static int indexOfZone(String[] zoneIds, String zoneId) {
        for (int i = 0; i < zoneIds.length; i++) {
            if (zoneIds[i].equals(zoneId)) {
                return i;
            }
        }
        return 0;
    }

    private void renderBuildingPadSettings(GradingZone zone) {
        List<BuildingFootprint> buildings = EarthworkUiLookups.listAvailableBuildings();
        if (!buildings.isEmpty()) {
            String[] labels = buildings.stream().map(BuildingFootprint::getName).toArray(String[]::new);
            String[] ids = buildings.stream().map(BuildingFootprint::getId).toArray(String[]::new);
            int currentIndex = 0;
            String currentRef = zone.getBuildingFootprintRef();
            for (int i = 0; i < ids.length; i++) {
                if (ids[i].equals(currentRef)) {
                    currentIndex = i;
                    break;
                }
            }
            ImInt buildingIndex = new ImInt(currentIndex);
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo(PlotI18n.tr("plugin.earthwork.building_footprint_ref"), buildingIndex, labels)) {
                int picked = buildingIndex.get();
                if (picked >= 0 && picked < ids.length) {
                    ctx.projectHistory().push(ctx.project());
                    zone.setBuildingFootprintRef(ids[picked]);
                    zone.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
                    ctx.invalidatePreview();
                }
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.import_building_outline"), 0, 0)) {
                BuildingFootprint footprint = buildings.get(buildingIndex.get());
                if (footprint != null) {
                    ctx.projectHistory().push(ctx.project());
                    zone.setOuterPoints(footprint.getOuterPoints());
                    zone.setBuildingFootprintRef(footprint.getId());
                    ctx.invalidatePreview();
                }
            }
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.no_buildings_available"));
        }

        boolean useBuildingElevation = zone.getDesignSurface().getElevationSource()
            == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION;
        ImBoolean useBuildingRef = new ImBoolean(useBuildingElevation);
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.use_building_base_elevation"), useBuildingRef)) {
            ctx.projectHistory().push(ctx.project());
            zone.getDesignSurface().setElevationSource(useBuildingRef.get()
                ? DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION
                : DesignSurfaceElevationSource.MANUAL);
            ctx.invalidatePreview();
        }

        if (!useBuildingRef.get()) {
            Integer manual = zone.getDesignSurface().getElevation();
            int initial = manual != null ? manual : 64;
            int[] elevation = {initial};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pad_elevation"), elevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                zone.getDesignSurface().setElevation(elevation[0]);
                ctx.invalidatePreview();
            }
        }
    }

    private void renderExcavationPitSettings(GradingZone zone) {
        List<BuildingFootprint> buildings = EarthworkUiLookups.listAvailableBuildings();
        if (!buildings.isEmpty()) {
            String[] labels = buildings.stream().map(BuildingFootprint::getName).toArray(String[]::new);
            String[] ids = buildings.stream().map(BuildingFootprint::getId).toArray(String[]::new);
            int currentIndex = 0;
            String currentRef = zone.getBuildingFootprintRef();
            for (int i = 0; i < ids.length; i++) {
                if (ids[i].equals(currentRef)) {
                    currentIndex = i;
                    break;
                }
            }
            ImInt buildingIndex = new ImInt(currentIndex);
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo(PlotI18n.tr("plugin.earthwork.building_footprint_ref"), buildingIndex, labels)) {
                int picked = buildingIndex.get();
                if (picked >= 0 && picked < ids.length) {
                    ctx.projectHistory().push(ctx.project());
                    zone.setBuildingFootprintRef(ids[picked]);
                    zone.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
                    ctx.invalidatePreview();
                }
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.import_building_outline"), 0, 0)) {
                BuildingFootprint footprint = buildings.get(buildingIndex.get());
                if (footprint != null) {
                    ctx.projectHistory().push(ctx.project());
                    zone.setOuterPoints(footprint.getOuterPoints());
                    zone.setBuildingFootprintRef(footprint.getId());
                    zone.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
                    ctx.invalidatePreview();
                }
            }
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.no_buildings_available"));
        }

        boolean useBuildingBottom = zone.getDesignSurface().getElevationSource()
            == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION;
        ImBoolean useBuildingRef = new ImBoolean(useBuildingBottom);
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.use_building_pit_bottom"), useBuildingRef)) {
            ctx.projectHistory().push(ctx.project());
            zone.getDesignSurface().setElevationSource(useBuildingRef.get()
                ? DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION
                : DesignSurfaceElevationSource.MANUAL);
            ctx.invalidatePreview();
        }

        if (useBuildingRef.get()) {
            DesignSurface surface = zone.getDesignSurface();
            int[] basementFloorDepth = {surface.getBasementFloorDepth()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.basement_floor_depth"), basementFloorDepth, 0, 32)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                surface.setBasementFloorDepth(basementFloorDepth[0]);
                ctx.invalidatePreview();
            }
            int[] foundationDepth = {surface.getFoundationDepth()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.foundation_depth"), foundationDepth, 0, 16)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                surface.setFoundationDepth(foundationDepth[0]);
                ctx.invalidatePreview();
            }
            int[] pitWorkingAllowance = {surface.getPitWorkingAllowance()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_working_allowance"), pitWorkingAllowance, 0, 8)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                surface.setPitWorkingAllowance(pitWorkingAllowance[0]);
                ctx.invalidatePreview();
            }
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.earthwork.pit_excavation_depth_hint",
                surface.getExcavationPit().totalExcavationDepth()));
        } else {
            Integer bottom = zone.getDesignSurface().getBottomElevation();
            int[] bottomElevation = {bottom != null ? bottom : 60};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_bottom_elevation"), bottomElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                zone.getDesignSurface().setBottomElevation(bottomElevation[0]);
                ctx.invalidatePreview();
            }
        }

        int[] workingMargin = {zone.getDesignSurface().getWorkingMarginBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_working_margin"), workingMargin, 0, 8)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.getDesignSurface().setWorkingMarginBlocks(workingMargin[0]);
            ctx.invalidatePreview();
        }

        int[] slopePitch = {zone.getDesignSurface().getSlopePitchRatio()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_slope_pitch"), slopePitch, 1, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.getDesignSurface().setSlopePitchRatio(slopePitch[0]);
            ctx.invalidatePreview();
        }
    }

    private void renderMaterialPropertiesSettings(GradingRegion region) {
        MaterialConversionModel siteModel = ctx.project().getActiveSite().getMaterialModel();
        if (region.usesSiteMaterialDefault()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.earthwork.material_inherit_site"));
        }
        MaterialConversionModel display = region.resolveMaterialModel(siteModel);
        EarthworkUiWidgets.renderMaterialConversionSliders(ctx, display, updated -> {
            region.setMaterialProperties(updated);
            ctx.invalidatePreview();
        });

        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone != null) {
            renderEarthMaterialClassSettings(zone);
        }
    }

    private void renderEarthMaterialClassSettings(GradingZone zone) {
        EarthMaterialClass[] classes = EarthMaterialClass.values();
        String[] labels = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            labels[i] = PlotI18n.tr(classes[i].i18nKey());
        }

        int cutSelected = indexOfMaterialClass(classes, zone.getCutMaterialClass());
        ImInt cutIndex = new ImInt(cutSelected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.cut_material_class"), cutIndex, labels)) {
            int picked = cutIndex.get();
            if (picked >= 0 && picked < classes.length) {
                ctx.projectHistory().push(ctx.project());
                zone.setCutMaterialClass(classes[picked]);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.cut_material_class");

        int fillSelected = indexOfMaterialClass(classes, zone.getFillMaterialClass());
        ImInt fillIndex = new ImInt(fillSelected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.fill_material_class"), fillIndex, labels)) {
            int picked = fillIndex.get();
            if (picked >= 0 && picked < classes.length) {
                ctx.projectHistory().push(ctx.project());
                zone.setFillMaterialClass(classes[picked]);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fill_material_class");
    }

    private static int indexOfMaterialClass(EarthMaterialClass[] classes, EarthMaterialClass value) {
        EarthMaterialClass target = value != null ? value : EarthMaterialClass.UNKNOWN;
        for (int i = 0; i < classes.length; i++) {
            if (classes[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private void renderFlatSurfaceSettings(GradingRegion region) {
        ctx.autoBalanceRef().set(region.isAutoBalance());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), ctx.autoBalanceRef())) {
            ctx.projectHistory().push(ctx.project());
            region.setAutoBalance(ctx.autoBalanceRef().get());
            GradingZone zone = ctx.project().getZone(region.getId());
            if (zone != null) {
                zone.syncVerticalPolicyWithAutoBalance();
            }
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.auto_balance");

        if (!region.isAutoBalance()) {
            int initial = region.getManualTargetElevation() != null ? region.getManualTargetElevation() : 64;
            int[] elevation = {initial};
            boolean elevationChanged = ImGui.sliderInt("##target_elevation", elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.target_elevation", elevation[0]));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (elevationChanged) {
                region.setManualTargetElevation(elevation[0]);
                ctx.invalidatePreview();
            }
        } else {
            ImGui.beginDisabled();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.manual_elevation_disabled"));
            ImGui.endDisabled();
        }
    }

    private void renderFixedSlopeSettings(GradingRegion region) {
        float[] direction = {(float) region.getSlopeDirectionDegrees()};
        boolean directionChanged = ImGui.sliderFloat("##slope_direction", direction, 0.0f, 359.0f,
            PlotI18n.tr("plugin.earthwork.slope_direction", String.format("%.0f", direction[0])));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (directionChanged) {
            region.setSlopeDirectionDegrees(direction[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_direction");

        int[] pitch = {region.getSlopePitchRatio()};
        boolean pitchChanged = ImGui.sliderInt("##slope_pitch", pitch, 1, 32,
            PlotI18n.tr("plugin.earthwork.slope_pitch", pitch[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (pitchChanged) {
            region.setSlopePitchRatio(pitch[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_pitch");

        int anchorInitial = region.getSlopeAnchorElevation() != null ? region.getSlopeAnchorElevation() : 64;
        int[] anchorElevation = {anchorInitial};
        boolean anchorChanged = ImGui.sliderInt("##slope_anchor_elevation", anchorElevation, -64, 320,
            PlotI18n.tr("plugin.earthwork.slope_anchor_elevation", anchorElevation[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (anchorChanged) {
            region.setSlopeAnchorElevation(anchorElevation[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_anchor_elevation");

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.slope_reset_anchor"))) {
            ctx.projectHistory().push(ctx.project());
            region.setSlopeAnchorCanvas(EarthworkGeometryUtils.computeCentroid(region.getOuterPoints()));
            initializeSurfaceDefaults(region, GradingSurfaceMode.SINGLE_SLOPE_PLANE);
            ctx.invalidatePreview();
        }
    }

    private void renderThreePointSurfaceSettings(GradingRegion region) {
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.three_point_reset"))) {
            ctx.projectHistory().push(ctx.project());
            initializeSurfaceDefaults(region, GradingSurfaceMode.THREE_POINT_PLANE);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.three_point_reset");

        var bounds = EarthworkGeometryUtils.computeBounds(region.getOuterPoints());
        for (int i = 0; i < 3; i++) {
            ImGui.separator();
            ImGui.text(PlotI18n.tr("plugin.earthwork.three_point_label", i + 1));

            float[] canvasX = {(float) region.getThreePointCanvasX(i)};
            boolean xChanged = ImGui.sliderFloat("##three_point_x_" + i, canvasX,
                (float) bounds.minX(), (float) bounds.maxX(),
                PlotI18n.tr("plugin.earthwork.three_point_canvas_x", String.format("%.1f", canvasX[0])));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (xChanged) {
                region.setThreePointCanvasX(i, canvasX[0]);
                ctx.invalidatePreview();
            }

            float[] canvasZ = {(float) region.getThreePointCanvasY(i)};
            boolean zChanged = ImGui.sliderFloat("##three_point_z_" + i, canvasZ,
                (float) bounds.minZ(), (float) bounds.maxZ(),
                PlotI18n.tr("plugin.earthwork.three_point_canvas_z", String.format("%.1f", canvasZ[0])));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (zChanged) {
                region.setThreePointCanvasY(i, canvasZ[0]);
                ctx.invalidatePreview();
            }

            int[] elevation = {region.getThreePointElevation(i)};
            boolean yChanged = ImGui.sliderInt("##three_point_y_" + i, elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.three_point_elevation", elevation[0]));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (yChanged) {
                region.setThreePointElevation(i, elevation[0]);
                ctx.invalidatePreview();
            }

            boolean pickingThisPoint = ctx.threePointPickSession().isActive()
                && ctx.threePointPickSession().getControlPointIndex() == i;
            if (pickingThisPoint) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.STATUS_INFO);
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.three_point_pick") + "##pick_" + i)) {
                startThreePointPick(region, i);
            }
            if (pickingThisPoint) {
                ImGui.popStyleColor();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.earthwork.three_point_pick");
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.three_point");
    }

    private void renderFitSlopeSettings(GradingRegion region) {
        ImBoolean balanceRef = new ImBoolean(region.isFitSlopeBalanceCutFill());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.fit_slope_balance"), balanceRef)) {
            ctx.projectHistory().push(ctx.project());
            region.setFitSlopeBalanceCutFill(balanceRef.get());
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fit_slope_balance");
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.fit_slope_hint"));
    }

    private void initializeSurfaceDefaults(GradingRegion region, GradingSurfaceMode mode) {
        com.plot.api.world.ICoordinateService transformer = ctx.host().coordinates();
        World world = EarthworkUiWidgets.getClientWorld();
        if (world == null || transformer == null) {
            return;
        }
        TerrainSnapshot terrain = ctx.terrainSnapshotCache().getOrCapture(region, world, transformer);
        if (terrain.isEmpty()) {
            return;
        }

        if (mode == GradingSurfaceMode.THREE_POINT_PLANE) {
            GradingSurfaceResolver.initializeThreePointDefaults(
                region, terrain.centers(), terrain.groundHeights(), transformer);
        } else if (mode == GradingSurfaceMode.SINGLE_SLOPE_PLANE) {
            GradingSurfaceResolver.initializeFixedSlopeDefaults(
                region, terrain.centers(), terrain.groundHeights(), transformer);
        }
    }

    private void renderGlobalGridSettings() {
        ImGui.text(PlotI18n.tr("plugin.earthwork.grid_settings"));
        ctx.showGridRef().set(ctx.config().isShowGrid());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.show_grid"), ctx.showGridRef())) {
            ctx.config().setShowGrid(ctx.showGridRef().get());
            ctx.config().save();
        }
    }

    private void startThreePointPick(GradingRegion region, int controlPointIndex) {
        if (region == null || controlPointIndex < 0 || controlPointIndex > 2) {
            return;
        }
        if (EarthworkUiWidgets.getClientWorld() == null) {
            ctx.setProjectStatus(PlotI18n.tr("status.plot.earthwork.three_point_pick_world_unavailable"));
            return;
        }
        ctx.pickSession().cancel();
        ctx.threePointPickSession().begin(controlPointIndex);
        ctx.setProjectStatus(PlotI18n.tr("status.plot.earthwork.three_point_pick_active", controlPointIndex + 1));
    }

    public void tickThreePointPickSession() {
        GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
        if (region == null) {
            ctx.threePointPickSession().cancel();
            return;
        }

        EarthworkThreePointPickSession.Outcome outcome =
            ctx.threePointPickSession().tick(ctx.host().appState(), region.getOuterPoints(), ctx.host().coordinates());
        switch (outcome.getResult()) {
            case PICKED -> {
                EarthworkThreePointPickSession.PickResult pick = outcome.getPick();
                if (pick != null) {
                    ctx.projectHistory().push(ctx.project());
                    region.setThreePointControl(
                        outcome.getControlPointIndex(),
                        pick.canvasPoint(),
                        pick.elevation());
                    ctx.invalidatePreview();
                    ctx.setProjectStatus(PlotI18n.tr(
                        "status.plot.earthwork.three_point_pick_success",
                        outcome.getControlPointIndex() + 1));
                }
            }
            case OUTSIDE_REGION -> ctx.setProjectStatus(
                PlotI18n.tr("status.plot.earthwork.three_point_pick_outside_region"));
            case WORLD_UNAVAILABLE -> ctx.setProjectStatus(
                PlotI18n.tr("status.plot.earthwork.three_point_pick_world_unavailable"));
            case CANCELLED -> ctx.setProjectStatus(
                PlotI18n.tr("status.plot.earthwork.three_point_pick_cancelled"));
            default -> ctx.setProjectStatus(PlotI18n.tr(
                "status.plot.earthwork.three_point_pick_active",
                outcome.getControlPointIndex() + 1));
        }
    }
}
