package com.plot.plugin.earthwork.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.core.plugin.PluginManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.BuildingPlugin;
import com.plot.plugin.RoadSystemPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
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

/** 土方 ImGui 界面编排。 */
public final class EarthworkUIManager {
    private final EarthworkUiContext ctx;

    public EarthworkUIManager(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        if (ctx.config() == null) {
            return;
        }

        if (ctx.pickSession().isActive()) {
            handlePickSessionTick();
        }
        if (ctx.threePointPickSession().isActive()) {
            handleThreePointPickSessionTick();
        }

        renderToolbar();
        renderActivePlacementControls();

        if (ImGui.beginTabBar("##earthwork_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.overview"))) {
                renderOverviewTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.adopt"))) {
                renderAdoptTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.edit"))) {
                renderEditTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.generate"))) {
                renderGenerateTab();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    public void renderDeferredModals() {
        renderDeleteConfirmPopup();
        renderBuildConfirmPopup();
    }

    private void renderToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        boolean undoDisabled = !ctx.projectHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.undo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().undo(ctx.project()));
            syncSelectedRegionAfterHistory();
            ctx.setRegionNameEditingRegionId("");
            clearPreview();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !ctx.projectHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.redo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().redo(ctx.project()));
            syncSelectedRegionAfterHistory();
            ctx.setRegionNameEditingRegionId("");
            clearPreview();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, ctx.projectStatus());
        }
        ImGui.separator();
    }

    private void renderActivePlacementControls() {
        com.plot.api.world.IBlockPlacementService scheduler = ctx.host().placement();
        if (!scheduler.isBusy()) {
            return;
        }

        com.plot.api.world.IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            ImGui.textColored(PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.earthwork.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.earthwork.build_in_progress_hint"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
        ImGui.separator();
    }

    private void renderOverviewTab() {
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_stats",
            ctx.project().getRegionCount(),
            String.format("%.1f", ctx.project().getTotalArea())));
        renderSiteOverlapWarnings();
        EarthworkSite site = ctx.project().getActiveSite();
        if (!site.getExclusionZones().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.earthwork.exclusions_count",
                site.getExclusionZones().size()));
        }

        if (ctx.project().getRegionCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.no_regions"));
            return;
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##earthwork_region_sort", ctx.regionSortMode().label())) {
            for (EarthworkRegionListHelper.SortMode mode : EarthworkRegionListHelper.SortMode.values()) {
                boolean selected = mode == ctx.regionSortMode();
                if (ImGui.selectable(mode.label(), selected)) {
                    ctx.setRegionSortMode(mode);
                }
            }
            ImGui.endCombo();
        }

        ImGui.beginChild("earthwork_overview_list", 0, 220, true);
        for (GradingRegion region : EarthworkRegionListHelper.sorted(ctx.project(), ctx.regionSortMode())) {
            ImGui.pushID(region.getId());
            boolean selected = region.getId().equals(ctx.selectedRegionId());
            if (ImGui.selectable(region.getName() + "##row", selected)) {
                ctx.setSelectedRegionId(region.getId());
            }

            ImGui.sameLine();
            String stats = region.getLastVolumeReport().hasGeometricVolume()
                ? PlotI18n.tr("plugin.earthwork.overview_stats",
                    region.getLastVolumeReport().geometricCutVolume(),
                    region.getLastVolumeReport().geometricFillVolume(),
                    region.getLastResolvedElevation())
                : PlotI18n.tr("plugin.earthwork.overview_no_stats");
            String areaText = String.format("%.1f", region.computeArea());
            if (!region.getHoles().isEmpty()) {
                areaText += PlotI18n.tr("plugin.earthwork.overview_holes_suffix", region.getHoles().size());
            }
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.earthwork.overview_item",
                areaText,
                stats));

            if (ImGui.button(PlotI18n.tr("plugin.earthwork.locate"), 60, 0)) {
                locateRegion(region);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.delete"), 60, 0)) {
                ctx.setPendingDeleteRegionId(region.getId());
                ctx.setDeleteConfirmPending(true);
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderAdoptTab() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.adopt_hint"));
        ImGui.spacing();

        if (ctx.pickSession().isActive()) {
            int count = ctx.pickSession().getAccumulatedCount();
            if (count > 0) {
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.earthwork.regions_selected"),
                    count));
            }
        } else {
            updateSelectedRegions();
        }

        if (!ctx.selectedRegions().isEmpty()) {
            ImGui.text(String.format(
                PlotI18n.tr("plugin.earthwork.regions_selected"),
                ctx.selectedRegions().size()));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.draw_region_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.pick_region"), 0, 0)) {
            startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = ctx.selectedRegions().isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.adopt_region"), 0, 0)) {
            adoptSelectedRegions();
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderEditTab() {
        renderRegionSelector();
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

        renderMaterialPropertiesSettings(region);

        int[] previewGridSize = {region.getPreviewGridSize()};
        boolean previewGridChanged = ImGui.sliderInt("##preview_grid_size", previewGridSize, 1, 20,
            PlotI18n.tr("plugin.earthwork.preview_grid_size", previewGridSize[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (previewGridChanged) {
            region.setPreviewGridSize(previewGridSize[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.preview_grid_size");

        renderMaterialButton(PlotI18n.tr("plugin.earthwork.cut_material"), region.getCutExposeMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                region.setCutExposeMaterial(blockId);
                invalidatePreview();
            });
        renderMaterialButton(PlotI18n.tr("plugin.earthwork.fill_material"), region.getFillMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                region.setFillMaterial(blockId);
                invalidatePreview();
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
                invalidatePreview();
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
            invalidatePreview();
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
                invalidatePreview();
            }
        }

        int[] priority = {zone.getPriority()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.zone_priority"), priority, 0, 200)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.setPriority(priority[0]);
            invalidatePreview();
        }
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
                invalidatePreview();
            }
        }

        int[] cutPitch = {settings.getCutSlopePitchRatio()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_cut_slope_pitch"), cutPitch, 1, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            settings.setCutSlopePitchRatio(cutPitch[0]);
            invalidatePreview();
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
            invalidatePreview();
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
            invalidatePreview();
        }

        int[] benchWidth = {settings.getBenchWidthBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.edge_bench_width"), benchWidth, 0, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            settings.setBenchWidthBlocks(benchWidth[0]);
            invalidatePreview();
        }

        if (settings.getDefaultTreatment() == EdgeTreatment.RETAINING_WALL
            || hasRetainingWallEdgeOverride(settings)) {
            ImBoolean useZoneFill = new ImBoolean(settings.isUseLinkedZoneFillMaterial());
            if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.edge_use_zone_fill_material"), useZoneFill)) {
                ctx.projectHistory().push(ctx.project());
                settings.setUseLinkedZoneFillMaterial(useZoneFill.get());
                invalidatePreview();
            }
            if (!settings.isUseLinkedZoneFillMaterial()) {
                renderMaterialButton(PlotI18n.tr("plugin.earthwork.edge_wall_material"), settings.getWallMaterial(),
                    blockId -> {
                        ctx.projectHistory().push(ctx.project());
                        settings.setWallMaterial(blockId);
                        invalidatePreview();
                    });
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.edge_sync_retaining_edges"), 0, 0)) {
                ctx.projectHistory().push(ctx.project());
                EarthworkSite site = ctx.project().getActiveSite();
                int synced = ZoneBoundaryRetainingEdgeAdapter.syncZoneToSite(site, zone);
                ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.edge_sync_retaining_edges_done", synced));
                invalidatePreview();
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
                    invalidatePreview();
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

    private void renderSiteOverlapWarnings() {
        if (ctx.project().getRegionCount() < 2) {
            return;
        }
        List<ZoneOverlapAnalyzer.ZoneOverlap> overlaps =
            ZoneOverlapAnalyzer.findOverlaps(ctx.project().getActiveSite());
        if (overlaps.isEmpty()) {
            return;
        }
        ImGui.textColored(
            PluginUiColors.WARNING_OVERLAP,
            PlotI18n.tr("plugin.earthwork.zone_overlap_header", overlaps.size()));
        float childHeight = Math.min(140f, overlaps.size() * 22f + 12f);
        ImGui.beginChild("earthwork_site_overlap_warnings", 0, childHeight, true);
        for (ZoneOverlapAnalyzer.ZoneOverlap overlap : overlaps) {
            ImGui.bulletText(PlotI18n.tr(
                "plugin.earthwork.zone_overlap_item",
                overlap.zoneNameA(),
                overlap.zoneNameB(),
                overlap.winnerZoneName(),
                overlap.overlapCells()));
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.earthwork.zone_overlap_policy",
                PlotI18n.tr("plugin.earthwork.overlap_resolution." + ctx.project().getActiveSite()
                    .getCompositionPolicy().getOverlapResolution().toLowerCase())));
        ImGui.endChild();
        ImGui.spacing();
    }

    private void renderProjectBalanceReport(EarthworkProjectReport report) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_balance_header"));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_total_cut", report.totalCut()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_total_fill", report.totalFill()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_net_volume", report.netVolume()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.reusable_cut_volume", report.reusableCut()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.export_volume", report.exportRequired()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.import_volume", report.importRequired()));
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.earthwork.project_balance_scope",
                PlotI18n.tr("plugin.earthwork.balance_scope." + report.balanceScope().toLowerCase())));
        if (report.siteWideVerticalOffset() != 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.site_wide_vertical_offset",
                report.siteWideVerticalOffset()));
        }
        if (report.hasZoneVerticalOffsets()) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.zone_vertical_offsets_header"));
            for (Map.Entry<String, Integer> entry : report.zoneVerticalOffsets().entrySet()) {
                GradingRegion zoneRegion = ctx.project().getRegion(entry.getKey());
                String zoneName = zoneRegion != null ? zoneRegion.getName() : entry.getKey();
                ImGui.bulletText(PlotI18n.tr(
                    "plugin.earthwork.zone_vertical_offset_item",
                    zoneName,
                    entry.getValue()));
            }
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.earthwork.zone_volume_header"));
        for (Map.Entry<String, EarthworkVolumeReport> entry : report.byZone().entrySet()) {
            GradingRegion zoneRegion = ctx.project().getRegion(entry.getKey());
            String zoneName = zoneRegion != null ? zoneRegion.getName() : entry.getKey();
            EarthworkVolumeReport zoneVolumes = entry.getValue();
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.zone_volume_item",
                zoneName,
                zoneVolumes.geometricCutVolume(),
                zoneVolumes.geometricFillVolume()));
        }

        if (!report.allocationMatrix().isEmpty()) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.earthwork.allocation_matrix_header"));
            for (EarthworkAllocationMatrix.Transfer transfer : report.allocationMatrix().transfers()) {
                ImGui.bulletText(PlotI18n.tr(
                    "plugin.earthwork.allocation_transfer",
                    resolveAllocationEndpoint(transfer.sourceZoneId(), true),
                    resolveAllocationEndpoint(transfer.destinationZoneId(), false),
                    transfer.volume()));
            }
        }
    }

    private String resolveAllocationEndpoint(String zoneId, boolean source) {
        if (EarthworkAllocationMatrix.EXPORT.equals(zoneId)) {
            return PlotI18n.tr("plugin.earthwork.allocation_export");
        }
        if (EarthworkAllocationMatrix.IMPORT.equals(zoneId)) {
            return PlotI18n.tr("plugin.earthwork.allocation_import");
        }
        GradingRegion region = ctx.project().getRegion(zoneId);
        if (region != null && region.getName() != null && !region.getName().isBlank()) {
            return region.getName();
        }
        return zoneId != null ? zoneId : "";
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
        String[] scopes = {
            CompositionPolicy.BALANCE_SCOPE_SITE_WIDE,
            CompositionPolicy.BALANCE_SCOPE_PER_ZONE
        };
        String[] labels = {
            PlotI18n.tr("plugin.earthwork.balance_scope.site_wide"),
            PlotI18n.tr("plugin.earthwork.balance_scope.per_zone")
        };
        int selected = CompositionPolicy.BALANCE_SCOPE_PER_ZONE.equals(policy.getBalanceScope()) ? 1 : 0;
        ImInt scopeIndex = new ImInt(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.balance_scope_label"), scopeIndex, labels)) {
            int picked = scopeIndex.get();
            if (picked >= 0 && picked < scopes.length) {
                ctx.projectHistory().push(ctx.project());
                policy.setBalanceScope(scopes[picked]);
                invalidatePreview();
            }
        }
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
                invalidatePreview();
            }
        }
        ImGui.spacing();
    }

    private void renderBalanceMethodSettings(CompositionPolicy policy) {
        if (!policy.isSiteWideBalance() || ctx.project().getRegionCount() < 2) {
            return;
        }
        String[] methods = {
            CompositionPolicy.BALANCE_METHOD_ZONE_ALLOCATION,
            CompositionPolicy.BALANCE_METHOD_UNIFORM
        };
        String[] labels = {
            PlotI18n.tr("plugin.earthwork.balance_method.zone_allocation"),
            PlotI18n.tr("plugin.earthwork.balance_method.uniform_offset")
        };
        int selected = policy.isZoneAllocationBalance() ? 0 : 1;
        ImInt methodIndex = new ImInt(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.balance_method_label"), methodIndex, labels)) {
            int picked = methodIndex.get();
            if (picked >= 0 && picked < methods.length) {
                ctx.projectHistory().push(ctx.project());
                policy.setBalanceMethod(methods[picked]);
                invalidatePreview();
            }
        }
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
            invalidatePreview();
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
                invalidatePreview();
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
                    invalidatePreview();
                }
            }
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.edge_sync_all_retaining_edges"), 0, 0)) {
            ctx.projectHistory().push(ctx.project());
            int synced = ZoneBoundaryRetainingEdgeAdapter.syncAllZonesToSite(site);
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.edge_sync_retaining_edges_done", synced));
            invalidatePreview();
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
                invalidatePreview();
                ImGui.popID();
                continue;
            }
            int[] topElevation = {edge.getTopElevation()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.retaining_top_elevation"), topElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                edge.setTopElevation(topElevation[0]);
                invalidatePreview();
            }
            int[] bottomElevation = {edge.getBottomElevation()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.retaining_bottom_elevation"), bottomElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                edge.setBottomElevation(bottomElevation[0]);
                invalidatePreview();
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
                    invalidatePreview();
                }
            }
            ImBoolean useZoneFill = new ImBoolean(edge.isUseLinkedZoneFillMaterial());
            if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.retaining_use_zone_fill_material"), useZoneFill)) {
                ctx.projectHistory().push(ctx.project());
                edge.setUseLinkedZoneFillMaterial(useZoneFill.get());
                invalidatePreview();
            }
            if (!edge.isUseLinkedZoneFillMaterial()) {
                renderMaterialButton(PlotI18n.tr("plugin.earthwork.retaining_wall_material"), edge.getWallMaterial(),
                    blockId -> {
                        ctx.projectHistory().push(ctx.project());
                        edge.setWallMaterial(blockId);
                        invalidatePreview();
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
            invalidatePreview();
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
                invalidatePreview();
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
            invalidatePreview();
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
                invalidatePreview();
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
            invalidatePreview();
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
                invalidatePreview();
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
        invalidatePreview();
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
        invalidatePreview();
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
        invalidatePreview();
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
        List<RoadEarthworkSurfaceSampler.EdgeRef> edges = listAvailableRoadEdges();
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
                invalidatePreview();
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
            invalidatePreview();
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
        invalidatePreview();
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
        invalidatePreview();
    }

    private void bakeRoadCorridorElevations(GradingZone zone) {
        if (zone == null || zone.getRoadEdgeRef().isBlank()) {
            return;
        }
        World world = getClientWorld();
        if (world == null) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.generate_world_unavailable"));
            return;
        }
        ctx.projectHistory().push(ctx.project());
        EarthworkSite site = ctx.project().getActiveSite();
        TerrainSnapshot terrain = ctx.terrainSnapshotCache().captureFreshSite(site, world, ctx.host().coordinates());
        int bakedCount = RoadCorridorBaker.bake(zone, terrain, createRoadSurfaceLookup());
        if (bakedCount <= 0) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.bake_road_failed"));
        } else {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.bake_road_success", bakedCount));
        }
        invalidatePreview();
    }

    private void renderBreaklineRow(EarthworkSite site, Breakline breakline, List<GradingZone> zones) {
        ImGui.pushID(breakline.getId());
        ImGui.text(breakline.getName());
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
            ctx.projectHistory().push(ctx.project());
            site.removeBreakline(breakline.getId());
            invalidatePreview();
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
            invalidatePreview();
        }
        ImGui.sameLine();
        ImInt rightIndex = new ImInt(indexOfZone(zoneIds, breakline.getRightZoneId()));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.breakline_right_zone"), rightIndex, zoneLabels)) {
            ctx.projectHistory().push(ctx.project());
            breakline.setRightZoneId(zoneIds[rightIndex.get()]);
            invalidatePreview();
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
        List<BuildingFootprint> buildings = listAvailableBuildings();
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
                    invalidatePreview();
                }
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.import_building_outline"), 0, 0)) {
                BuildingFootprint footprint = buildings.get(buildingIndex.get());
                if (footprint != null) {
                    ctx.projectHistory().push(ctx.project());
                    zone.setOuterPoints(footprint.getOuterPoints());
                    zone.setBuildingFootprintRef(footprint.getId());
                    invalidatePreview();
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
            invalidatePreview();
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
                invalidatePreview();
            }
        }
    }

    private void renderExcavationPitSettings(GradingZone zone) {
        Integer bottom = zone.getDesignSurface().getBottomElevation();
        int[] bottomElevation = {bottom != null ? bottom : 60};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_bottom_elevation"), bottomElevation, -64, 320)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.getDesignSurface().setBottomElevation(bottomElevation[0]);
            invalidatePreview();
        }

        int[] workingMargin = {zone.getDesignSurface().getWorkingMarginBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_working_margin"), workingMargin, 0, 8)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.getDesignSurface().setWorkingMarginBlocks(workingMargin[0]);
            invalidatePreview();
        }

        int[] slopePitch = {zone.getDesignSurface().getSlopePitchRatio()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_slope_pitch"), slopePitch, 1, 16)) {
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            zone.getDesignSurface().setSlopePitchRatio(slopePitch[0]);
            invalidatePreview();
        }
    }

    private List<BuildingFootprint> listAvailableBuildings() {
        com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("building");
        if (plugin instanceof BuildingPlugin buildingPlugin) {
            return buildingPlugin.listBuildingFootprints();
        }
        return List.of();
    }

    private BuildingFootprintLookup createBuildingFootprintLookup() {
        return id -> {
            if (id == null || id.isBlank()) {
                return null;
            }
            com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("building");
            if (plugin instanceof BuildingPlugin buildingPlugin) {
                return buildingPlugin.getBuildingFootprint(id);
            }
            return null;
        };
    }

    private RoadSurfaceLookup createRoadSurfaceLookup() {
        return (edgeId, planPoint) -> {
            if (edgeId == null || edgeId.isBlank() || planPoint == null) {
                return null;
            }
            com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("road_system");
            if (plugin instanceof RoadSystemPlugin roadPlugin) {
                return roadPlugin.sampleEarthworkDesignY(edgeId, planPoint);
            }
            return null;
        };
    }

    private List<RoadEarthworkSurfaceSampler.EdgeRef> listAvailableRoadEdges() {
        com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("road_system");
        if (plugin instanceof RoadSystemPlugin roadPlugin) {
            return roadPlugin.listEarthworkRoadEdges();
        }
        return List.of();
    }

    private void renderMaterialPropertiesSettings(GradingRegion region) {
        EarthMaterialProperties materials = region.getMaterialProperties();
        float[] reusableRatio = {materials.reusableRatio()};
        boolean reusableChanged = ImGui.sliderFloat("##reusable_ratio", reusableRatio, 0.50f, 1.00f,
            PlotI18n.tr("plugin.earthwork.reusable_ratio", String.format("%.2f", reusableRatio[0])));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (reusableChanged) {
            region.setMaterialProperties(materials.withReusableRatio(reusableRatio[0]));
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.reusable_ratio");

        float[] cutToFillRatio = {materials.cutToCompactedFillRatio()};
        boolean cutToFillChanged = ImGui.sliderFloat("##cut_to_compacted_fill_ratio", cutToFillRatio, 0.50f, 1.00f,
            PlotI18n.tr("plugin.earthwork.cut_to_compacted_fill_ratio", String.format("%.2f", cutToFillRatio[0])));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (cutToFillChanged) {
            region.setMaterialProperties(materials.withCutToCompactedFillRatio(cutToFillRatio[0]));
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.cut_to_compacted_fill_ratio");

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.earthwork.effective_cut_to_fill_ratio",
            region.getMaterialProperties().effectiveCutToCompactedFillRatio()));
    }

    private void renderFlatSurfaceSettings(GradingRegion region) {
        ctx.autoBalanceRef().set(region.isAutoBalance());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), ctx.autoBalanceRef())) {
            ctx.projectHistory().push(ctx.project());
            region.setAutoBalance(ctx.autoBalanceRef().get());
            invalidatePreview();
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
                invalidatePreview();
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
            invalidatePreview();
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
            invalidatePreview();
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
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_anchor_elevation");

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.slope_reset_anchor"))) {
            ctx.projectHistory().push(ctx.project());
            region.setSlopeAnchorCanvas(EarthworkGeometryUtils.computeCentroid(region.getOuterPoints()));
            initializeSurfaceDefaults(region, GradingSurfaceMode.SINGLE_SLOPE_PLANE);
            invalidatePreview();
        }
    }

    private void renderThreePointSurfaceSettings(GradingRegion region) {
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.three_point_reset"))) {
            ctx.projectHistory().push(ctx.project());
            initializeSurfaceDefaults(region, GradingSurfaceMode.THREE_POINT_PLANE);
            invalidatePreview();
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
                invalidatePreview();
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
                invalidatePreview();
            }

            int[] elevation = {region.getThreePointElevation(i)};
            boolean yChanged = ImGui.sliderInt("##three_point_y_" + i, elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.three_point_elevation", elevation[0]));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (yChanged) {
                region.setThreePointElevation(i, elevation[0]);
                invalidatePreview();
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
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fit_slope_balance");
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.fit_slope_hint"));
    }

    private void initializeSurfaceDefaults(GradingRegion region, GradingSurfaceMode mode) {
        com.plot.api.world.ICoordinateService transformer = ctx.host().coordinates();
        World world = getClientWorld();
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

    private void renderGenerateTab() {
        GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasRegion = region != null;

        if (!hasRegion) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.select_region_hint"));
            renderRegionSelector();
            return;
        }

        renderRegionSelector();
        ImGui.spacing();

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.calc_preview"), half, 0)) {
            ctx.previewManager().calculatePreview(
                ctx.project(), region, createBuildingFootprintLookup(), createRoadSurfaceLookup());
        }
        ImGui.sameLine();
        boolean hasPreview = ctx.previewManager().getLastGenerationResult() != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.clear_preview"), half, 0)) {
            clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.previewManager().calculatePreview(
                ctx.project(), region, createBuildingFootprintLookup(), createRoadSurfaceLookup())) {
                ctx.setBuildConfirmPending(true);
            }
        }

        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();
        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (ctx.config().isShowGrid() && preview != null) {
            renderGridPreview(region, preview);
        }

        if (preview != null) {
            ImGui.separator();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.earthwork.calc_results"));
            EarthworkVolumeReport volumes = preview.volumeReport;
            ImGui.text(PlotI18n.tr("plugin.earthwork.calculation_cell_count", preview.calculationCellCount));
            renderTerrainSnapshotInfo(preview.existingTerrainSnapshot);
            ImGui.text(PlotI18n.tr("plugin.earthwork.geometric_cut_volume", volumes.geometricCutVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.geometric_fill_volume", volumes.geometricFillVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.reusable_cut_volume", volumes.reusableCutVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.export_volume", volumes.exportVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.import_volume", volumes.importVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.compacted_fill_demand", volumes.compactedFillDemand()));
            if (preview.slopedSurface) {
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.resolved_elevation_slope_result",
                    preview.resolvedElevationMin,
                    preview.resolvedElevationMax));
            } else {
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.resolved_elevation_result",
                    preview.resolvedElevation));
            }
            ImGui.text(PlotI18n.tr("plugin.earthwork.block_count_result", volumes.totalChangedBlocks()));
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.block_change_breakdown",
                volumes.cutChangedBlocks(),
                volumes.fillChangedBlocks()));

            if (preview.projectReport != null
                && preview.projectReport.hasZoneBreakdown()) {
                renderProjectBalanceReport(preview.projectReport);
            }

            for (String warningKey : preview.warnings) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
            }

            boolean hasPlacements = !preview.placementRecords.isEmpty();
            if (!hasPlacements) {
                ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            }

            if (!hasPlacements) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.projection_ref"), half, 0)) {
                ctx.previewManager().projectPreview();
            }
            if (!hasPlacements) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();
            boolean buildDisabled = !hasPlacements
                || !buildReadiness.ready()
                || ctx.host().placement().isBusy();
            if (buildDisabled) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), half, 0)) {
                ctx.setBuildConfirmPending(true);
            }
            if (buildDisabled) {
                ImGui.endDisabled();
            }
        }
    }

    private void renderGridPreview(GradingRegion region, EarthworkGenerationResult result) {
        List<Vec2d> points = region.getOuterPoints();
        if (points.size() < 3 || result.gridSamples.isEmpty()) {
            return;
        }

        PolygonRegionUtils.RectBounds bounds = EarthworkGeometryUtils.computeBounds(points);
        float availWidth = ImGui.getContentRegionAvailX();
        float previewHeight = 140.0f;
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();

        ImGui.dummy(availWidth, previewHeight);
        ImDrawList drawList = ImGui.getWindowDrawList();

        double spanX = Math.max(bounds.width(), 1.0);
        double spanZ = Math.max(bounds.depth(), 1.0);
        float scale = (float) Math.min(
            (availWidth - 16.0f) / spanX,
            (previewHeight - 16.0f) / spanZ);

        int cutColor = 0x80FF4040;
        int fillColor = 0x804040FF;
        int borderColor = 0xFF606060;

        for (EarthworkGenerationResult.GridSample sample : result.gridSamples) {
            float cellX = originX + 8.0f + (float) ((sample.center.x - bounds.minX()) * scale);
            float cellY = originY + 8.0f + (float) ((sample.center.y - bounds.minZ()) * scale);
            float cellSize = Math.max(3.0f, scale * 0.8f);
            int color = sample.changeType == EarthworkGenerationResult.ChangeType.CUT ? cutColor : fillColor;
            drawList.addRectFilled(cellX, cellY, cellX + cellSize, cellY + cellSize, color);
        }

        float bx1 = originX + 8.0f;
        float by1 = originY + 8.0f;
        float bx2 = bx1 + (float) (spanX * scale);
        float by2 = by1 + (float) (spanZ * scale);
        drawList.addRect(bx1, by1, bx2, by2, borderColor);

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.grid_preview_legend"));
    }

    private void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##earthwork_build_confirm");
            ctx.setBuildConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##earthwork_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
            long blockCount = preview != null
                ? preview.volumeReport.totalChangedBlocks()
                : 0L;
            ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.build_confirm"), blockCount));

            TerrainSnapshot.ComparisonResult terrainComparison =
                ctx.previewManager().comparePreviewTerrainWithWorld(getClientWorld());
            boolean terrainStale = terrainComparison != null && terrainComparison.terrainChanged();
            if (terrainStale) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.earthwork.terrain_changed_since_preview",
                    terrainComparison.changedColumns(),
                    terrainComparison.totalColumns()));
            }

            com.plot.api.world.PlacementReadiness readiness =
                ctx.host().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            boolean canBuild = readiness.ready() && !ctx.host().placement().isBusy() && !terrainStale;
            if (terrainStale) {
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.recalculate_preview"), 180, 0)) {
                    GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
                    if (region != null) {
                        ctx.previewManager().calculatePreview(
                            ctx.project(), region, createBuildingFootprintLookup(), createRoadSurfaceLookup());
                    }
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
            }
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), 120, 0)) {
                ctx.buildManager().buildInWorld(ctx.project(), ctx.selectedRegionId());
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderTerrainSnapshotInfo(TerrainSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        TerrainSnapshot.Metadata metadata = snapshot.metadata();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.earthwork.terrain_snapshot_info",
            formatTerrainSnapshotTime(metadata.capturedAtEpochMs()),
            metadata.columnCount()));
        TerrainSnapshot.ComparisonResult comparison =
            ctx.previewManager().comparePreviewTerrainWithWorld(getClientWorld());
        if (comparison != null && comparison.terrainChanged()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.earthwork.terrain_changed_since_preview",
                comparison.changedColumns(),
                comparison.totalColumns()));
        }
    }

    private static String formatTerrainSnapshotTime(long epochMs) {
        if (epochMs <= 0L) {
            return "-";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(epochMs));
    }

    private void renderDeleteConfirmPopup() {
        if (ctx.deleteConfirmPending()) {
            ImGui.openPopup("##earthwork_delete_confirm");
            ctx.setDeleteConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##earthwork_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.delete_confirm"));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.delete"), 100, 0)) {
                if (!ctx.pendingDeleteRegionId().isEmpty()) {
                    ctx.projectHistory().push(ctx.project());
                    ctx.project().removeRegion(ctx.pendingDeleteRegionId());
                    ctx.terrainSnapshotCache().invalidateRegion(ctx.pendingDeleteRegionId());
                    ctx.terrainSnapshotCache().invalidateSite(ctx.project().getActiveSiteId());
                    if (ctx.pendingDeleteRegionId().equals(ctx.selectedRegionId())) {
                        ctx.setSelectedRegionId(ctx.project().getRegions().isEmpty()
                            ? ""
                            : ctx.project().getRegions().keySet().iterator().next());
                    }
                    clearPreview();
                }
                ctx.setPendingDeleteRegionId("");
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                ctx.setPendingDeleteRegionId("");
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderRegionSelector() {
        if (ctx.project().getRegionCount() == 0) {
            return;
        }
        String[] labels = ctx.project().getRegions().values().stream()
            .map(GradingRegion::getName)
            .toArray(String[]::new);
        String[] ids = ctx.project().getRegions().keySet().toArray(String[]::new);
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(ctx.selectedRegionId())) {
                current = i;
                break;
            }
        }
        ImInt regionIndex = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.select_region"), regionIndex, labels)) {
            ctx.setSelectedRegionId(ids[regionIndex.get()]);
        }
    }

    private void renderMaterialButton(String label, String currentBlockId, Consumer<String> onSelected) {
        ImGui.text(label);
        ImGui.sameLine();
        String display = currentBlockId == null || currentBlockId.isBlank()
            ? PlotI18n.tr("plugin.earthwork.cut_material_air")
            : currentBlockId;
        if (ImGui.button(display + "##" + label, 0, 0)) {
            UIUtils.openBlockPicker(
                currentBlockId == null || currentBlockId.isBlank() ? "minecraft:air" : currentBlockId,
                onSelected);
        }
    }

    private void updateSelectedRegions() {
        ctx.selectedRegions().clear();
        ctx.selectedRegions().addAll(
            EarthworkGeometryUtils.findAdoptableRegions(ctx.host().appState().getSelectedShapes()));
    }

    private void startPickSession() {
        ctx.threePointPickSession().cancel();
        ToolManager toolManager = ctx.host().tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        ctx.selectedRegions().clear();
        ctx.pickSession().begin();
        toolManager.setActiveTool(selectTool);
        ctx.host().appState().setCurrentTool(baseTool);
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_started"));
    }

    private void handlePickSessionTick() {
        EarthworkRegionPickSession.Outcome outcome = ctx.pickSession().tick(ctx.host().appState());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                ctx.selectedRegions().clear();
                ctx.selectedRegions().addAll(outcome.getRegions());
                adoptSelectedRegions();
            }
            case NEED_SELECTION -> ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_need_selection"));
            case NO_VALID -> ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_no_valid"));
            case CANCELLED -> ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_cancelled"));
            default -> {
                List<Shape> selected = ctx.host().appState().getSelectedShapes();
                ctx.setProjectStatus(PlotI18n.tr(ctx.pickSession().hintKeyForCurrentSelection(selected)));
            }
        }
    }

    private void startThreePointPick(GradingRegion region, int controlPointIndex) {
        if (region == null || controlPointIndex < 0 || controlPointIndex > 2) {
            return;
        }
        if (getClientWorld() == null) {
            ctx.setProjectStatus(PlotI18n.tr("status.plot.earthwork.three_point_pick_world_unavailable"));
            return;
        }
        ctx.pickSession().cancel();
        ctx.threePointPickSession().begin(controlPointIndex);
        ctx.setProjectStatus(PlotI18n.tr("status.plot.earthwork.three_point_pick_active", controlPointIndex + 1));
    }

    private void handleThreePointPickSessionTick() {
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
                    invalidatePreview();
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

    private void adoptSelectedRegions() {
        if (ctx.selectedRegions().isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
            return;
        }

        // 先收集有效轮廓，避免 0 认领仍 push 历史
        List<List<Vec2d>> validOutlines = new ArrayList<>();
        for (Shape shape : ctx.selectedRegions()) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() >= 3) {
                validOutlines.add(points);
            }
        }
        if (validOutlines.isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
            ctx.selectedRegions().clear();
            return;
        }

        ctx.projectHistory().push(ctx.project());
        int adopted = 0;
        for (List<Vec2d> points : validOutlines) {
            GradingRegion region = new GradingRegion(points);
            region.setName(PlotI18n.tr("plugin.earthwork.default_name", adopted + 1));
            region.setAutoBalance(ctx.config().isAutoBalance());
            region.setMaterialProperties(ctx.config().getDefaultMaterialProperties());
            region.setPreviewGridSize(ctx.config().getPreviewGridSize());
            if (!ctx.config().isAutoBalance()) {
                region.setManualTargetElevation(Math.round(ctx.config().getTargetElevation()));
            }
            ctx.project().addRegion(region);
            ctx.setSelectedRegionId(region.getId());
            adopted++;
        }

        ctx.selectedRegions().clear();
        clearPreview();
        ctx.setProjectStatus(adopted > 1
            ? PlotI18n.tr("plugin.earthwork.adopt_success_batch", adopted)
            : PlotI18n.tr("plugin.earthwork.adopt_success"));
    }

    private void clearPreview() { ctx.clearPreview(); }

    private void invalidatePreview() { ctx.invalidatePreview(); }

    private void locateRegion(GradingRegion region) {
        Vec2d centroid = EarthworkGeometryUtils.computeCentroid(region.getOuterPoints());
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            ctx.setSelectedRegionId(region.getId());
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.locate_success", region.getName()));
        }
    }

    private void syncSelectedRegionAfterHistory() {
        if (!ctx.selectedRegionId().isEmpty() && ctx.project().getRegion(ctx.selectedRegionId()) == null) {
            ctx.setSelectedRegionId(ctx.project().getRegions().isEmpty()
                ? ""
                : ctx.project().getRegions().keySet().iterator().next());
            ctx.setRegionNameEditingRegionId("");
        }
    }

    private World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
