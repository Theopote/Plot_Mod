package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.earthwork.EarthworkRegionListHelper;
import com.plot.plugin.earthwork.EarthworkRegionPickSession;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.adopt.EarthworkAdoptDefaults;
import com.plot.plugin.earthwork.grading.ZoneOverlapAnalyzer;
import com.plot.plugin.earthwork.manager.EarthworkPreviewIdentity;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.solver.ProjectGlobalBalanceAggregator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 土方区域 Tab：拾取/认领分区 + 项目总览与区域列表。 */
public final class EarthworkRegionPanel {
    private final EarthworkUiContext ctx;
    private long lastSelectionVersion = Long.MIN_VALUE;
    private EarthworkSite cachedOverlapSite;
    private int cachedOverlapSignature;
    private List<ZoneOverlapAnalyzer.ZoneOverlap> cachedOverlaps = List.of();
    private EarthworkProject cachedBalanceProject;
    private int cachedBalanceSignature;
    private ProjectGlobalBalanceAggregator.AggregatedBalance cachedBalance;

    public EarthworkRegionPanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderCanvasSection();
        ImGui.separator();
        renderAdoptedSection();
    }

    /** Quick 模式无区域时仅显示认领区。 */
    public void renderAdoptSection() {
        renderPickControls(true);
    }

    private void renderCanvasSection() {
        if (ctx.pickSession().isActive()) {
            renderPickSessionState();
            renderCanvasPreview();
            return;
        }

        renderPickControls(false);
    }

    private void renderPickControls(boolean quickMode) {
        if (!quickMode) {
            ImGui.checkbox(
                PlotI18n.tr("plugin.earthwork.overlay.show_regions"),
                ctx.showRegionOverlayRef());
            ImGui.sameLine();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.pick_region"), 0, 0)) {
            startPickSession();
        }
        ImGui.sameLine();
        if (!ctx.pickSession().isActive()) {
            updateSelectedRegionsIfNeeded();
        }
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

        if (quickMode) {
            ImGui.spacing();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.adopt_hint"));
        } else {
            ImGui.spacing();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.draw_region_hint"));
        }

        if (!ctx.selectedRegions().isEmpty()) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.regions_selected", ctx.selectedRegions().size()));
        }
    }

    private void renderPickSessionState() {
        int count = ctx.pickSession().getAccumulatedCount();
        if (count > 0) {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.earthwork.regions_selected", count));
        } else {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.earthwork.pick_started"));
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("status.plot.earthwork.pick_region_active"));
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.cancel_pick") + "##earthwork_cancel_pick", 0, 0)) {
            ctx.pickSession().cancel();
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_cancelled"));
        }
    }

    private void renderCanvasPreview() {
        List<Shape> previewShapes = resolvePickPreviewShapes();
        if (previewShapes.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.pick_preview_waiting"));
            return;
        }

        List<EarthworkOverviewRenderer.RegionMapItem> mapItems = new ArrayList<>();
        for (int i = 0; i < previewShapes.size(); i++) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(previewShapes.get(i));
            if (points.size() < 3) {
                continue;
            }
            mapItems.add(new EarthworkOverviewRenderer.RegionMapItem(
                points,
                true,
                EarthworkOverviewRenderer.regionColor(i),
                i));
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.pick_preview_title", previewShapes.size()));
        EarthworkOverviewRenderer.renderRegionPreviewMap(
            "earthwork_canvas_pick_map",
            mapItems,
            PlotI18n.tr("plugin.earthwork.pick_preview_waiting"),
            hitIndex -> focusCanvasShape(previewShapes.get(hitIndex)));

        ImGui.beginChild("earthwork_canvas_pick_thumbs", 0, 132, true);
        for (int i = 0; i < previewShapes.size(); i++) {
            Shape shape = previewShapes.get(i);
            ImGui.pushID(shape.getId());
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            EarthworkOverviewRenderer.renderRegionThumbnail(points, true, i);
            ImGui.sameLine();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.earthwork.pick_preview_item", i + 1));
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private List<Shape> resolvePickPreviewShapes() {
        List<Shape> accumulated = ctx.pickSession().getAccumulatedRegions();
        if (!accumulated.isEmpty()) {
            return accumulated;
        }
        return EarthworkGeometryUtils.findAdoptableRegions(ctx.host().appState().getSelectedShapes());
    }

    private void focusCanvasShape(Shape shape) {
        if (shape == null) {
            return;
        }
        List<Shape> selected = new ArrayList<>(ctx.host().appState().getSelectedShapes());
        if (!selected.contains(shape)) {
            selected.add(shape);
            ctx.host().appState().setSelectedShapes(selected);
        }
    }

    private void renderAdoptedSection() {
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_stats",
            ctx.project().getRegionCount(),
            String.format("%.1f", ctx.project().getTotalArea())));
        renderSiteOverlapWarnings();
        EarthworkSite site = ctx.project().getActiveSite();
        if (ctx.config().getWorkMode().showsEngineeringMetrics()) {
            renderProjectGlobalBalance(ctx.project());
        }
        if (site != null && !site.getExclusionZones().isEmpty()) {
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

        EarthworkOverviewRenderer.renderProjectMap(
            ctx.project(),
            ctx.selectedRegionId().isEmpty() ? List.of() : List.of(ctx.selectedRegionId()),
                ctx::setSelectedRegionId);

        ImGui.spacing();
        List<GradingRegion> regions = EarthworkRegionListHelper.sorted(ctx.project(), ctx.regionSortMode());
        for (GradingRegion region : regions) {
            renderAdoptedRegionRow(region);
        }
    }

    private void renderAdoptedRegionRow(GradingRegion region) {
        ImGui.pushID(region.getId());
        boolean selected = region.getId().equals(ctx.selectedRegionId());
        int colorIndex = EarthworkOverviewRenderer.stableColorIndex(region.getId());

        if (EarthworkOverviewRenderer.renderRegionThumbnail(
                region.getOuterPoints(),
                selected,
                colorIndex)) {
            ctx.setSelectedRegionId(region.getId());
        }
        ImGui.sameLine();
        float columnWidth = Math.max(1f, ImGui.getContentRegionAvailX() - 8f);

        ImGui.beginGroup();
        if (ImGui.selectable(
                region.getName() + "##row",
                selected,
                0,
                columnWidth,
                ImGui.getTextLineHeight())) {
            ctx.setSelectedRegionId(region.getId());
        }

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
            EarthworkUiWidgets.locateRegion(ctx, region);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.delete"), 60, 0)) {
            ctx.setPendingDeleteRegionId(region.getId());
            ctx.setDeleteConfirmPending(true);
        }
        ImGui.endGroup();
        ImGui.popID();
    }

    private void updateSelectedRegionsIfNeeded() {
        long version = ctx.host().appState().getStateVersion();
        if (version == lastSelectionVersion) {
            return;
        }
        lastSelectionVersion = version;
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

    public void tickPickSession() {
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

    private void adoptSelectedRegions() {
        if (ctx.selectedRegions().isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
            return;
        }

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

        List<List<Vec2d>> toAdopt = new ArrayList<>();
        int skippedDuplicates = 0;
        java.util.HashSet<Long> batchFingerprints = new java.util.HashSet<>();
        for (List<Vec2d> points : validOutlines) {
            long fingerprint = EarthworkPreviewIdentity.normalizedOutlineFingerprint(points);
            if (hasMatchingRegionOutline(ctx.project(), points)
                || !batchFingerprints.add(fingerprint)) {
                skippedDuplicates++;
            } else {
                toAdopt.add(points);
            }
        }
        if (toAdopt.isEmpty()) {
            ctx.selectedRegions().clear();
            lastSelectionVersion = Long.MIN_VALUE;
            if (skippedDuplicates > 0) {
                ctx.setProjectStatus(PlotI18n.tr(
                    "plugin.earthwork.adopt_duplicate_skipped", skippedDuplicates));
            } else {
                ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
            }
            return;
        }

        ctx.projectHistory().push(ctx.project());
        int adopted = 0;
        for (List<Vec2d> points : toAdopt) {
            GradingRegion region = new GradingRegion(points);
            region.setName(PlotI18n.tr("plugin.earthwork.default_name",
                ctx.project().getRegionCount() + 1));
            EarthworkAdoptDefaults.applyToNewRegion(
                region,
                ctx.config(),
                ctx.project().getActiveSite(),
                getClientWorld(),
                ctx.host().coordinates());
            ctx.project().addRegion(region);
            ctx.setSelectedRegionId(region.getId());
            adopted++;
        }

        ctx.selectedRegions().clear();
        // 强制下次从画布重读选区，避免本地清空后仍命中旧 selection version
        lastSelectionVersion = Long.MIN_VALUE;
        ctx.clearPreview();
        if (adopted > 1) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_success_batch", adopted));
        } else {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_success"));
        }
    }

    private static boolean hasMatchingRegionOutline(EarthworkProject project, List<Vec2d> outline) {
        if (project == null || outline == null || outline.size() < 3) {
            return false;
        }
        long fingerprint = EarthworkPreviewIdentity.normalizedOutlineFingerprint(outline);
        for (GradingRegion existing : project.getRegions().values()) {
            if (EarthworkPreviewIdentity.normalizedOutlineFingerprint(existing.getOuterPoints())
                == fingerprint) {
                return true;
            }
        }
        return false;
    }

    private static World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

    private void renderSiteOverlapWarnings() {
        EarthworkSite site = ctx.project().getActiveSite();
        if (site == null || site.getZoneCount() < 2) {
            return;
        }
        List<ZoneOverlapAnalyzer.ZoneOverlap> overlaps = resolveOverlaps(site);
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

    private void renderProjectGlobalBalance(EarthworkProject project) {
        if (project == null || project.getSiteCount() == 0) {
            return;
        }
        ProjectGlobalBalanceAggregator.AggregatedBalance balance = resolveProjectBalance(project);
        if (balance.sitesWithVolume() == 0) {
            return;
        }
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_global_balance_header"));
        if (balance.sitesWithVolume() > 1) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.earthwork.project_global_balance_sites", balance.sitesWithVolume()));
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_total_cut", balance.totalCut()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_total_fill", balance.totalFill()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_net_volume", balance.totalCut() - balance.totalFill()));
        if (balance.sitesWithVolume() > 1) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.earthwork.site_volume_header"));
            for (var snapshot : balance.bySite().values()) {
                ImGui.bulletText(PlotI18n.tr(
                    "plugin.earthwork.site_volume_item",
                    snapshot.siteName(),
                    snapshot.volumes().geometricCutVolume(),
                    snapshot.volumes().geometricFillVolume()));
            }
        }
        ImGui.spacing();
    }

    private List<ZoneOverlapAnalyzer.ZoneOverlap> resolveOverlaps(EarthworkSite site) {
        int signature = overlapSignature(site);
        if (site != cachedOverlapSite || signature != cachedOverlapSignature) {
            cachedOverlapSite = site;
            cachedOverlapSignature = signature;
            cachedOverlaps = Collections.unmodifiableList(ZoneOverlapAnalyzer.findOverlaps(site));
        }
        return cachedOverlaps;
    }

    private ProjectGlobalBalanceAggregator.AggregatedBalance resolveProjectBalance(EarthworkProject project) {
        int signature = projectBalanceSignature(project);
        if (project != cachedBalanceProject || signature != cachedBalanceSignature || cachedBalance == null) {
            cachedBalanceProject = project;
            cachedBalanceSignature = signature;
            cachedBalance = ProjectGlobalBalanceAggregator.aggregate(project);
        }
        return cachedBalance;
    }

    private static int overlapSignature(EarthworkSite site) {
        int signature = 1;
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null) {
                continue;
            }
            signature = 31 * signature + zone.getId().hashCode();
            signature = 31 * signature + zone.getPriority();
            signature = 31 * signature + Boolean.hashCode(zone.isEnabled());
            for (var point : zone.getOuterPoints()) {
                signature = 31 * signature + Double.hashCode(point.x);
                signature = 31 * signature + Double.hashCode(point.y);
            }
        }
        return signature;
    }

    private static int projectBalanceSignature(EarthworkProject project) {
        int signature = 1;
        for (EarthworkSite site : project.getSites().values()) {
            if (site == null) {
                continue;
            }
            var report = site.getLastReport();
            signature = 31 * signature + site.getId().hashCode();
            signature = 31 * signature + Long.hashCode(report.geometricCutVolume());
            signature = 31 * signature + Long.hashCode(report.geometricFillVolume());
            signature = 31 * signature + site.getZoneCount();
            for (GradingZone zone : site.getGradingZones().values()) {
                if (zone == null || zone.getRegion() == null) {
                    continue;
                }
                signature = 31 * signature + zone.getId().hashCode();
                signature = 31 * signature + Long.hashCode(
                    EarthworkPreviewIdentity.designRevision(project, zone.getRegion()));
            }
        }
        return signature;
    }

    public void renderDeleteConfirmPopup() {
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
                    String deletedId = ctx.pendingDeleteRegionId();
                    ctx.project().removeRegion(deletedId);
                    ctx.terrainSnapshotCache().invalidateRegion(deletedId);
                    ctx.terrainSnapshotCache().invalidateSite(ctx.project().getActiveSiteId());
                    com.plot.plugin.earthwork.overlay.EarthworkRegionTriangulationCache.invalidate(deletedId);
                    if (deletedId.equals(ctx.selectedRegionId())) {
                        ctx.setSelectedRegionId(ctx.project().getRegions().isEmpty()
                            ? ""
                            : ctx.project().getRegions().keySet().iterator().next());
                    }
                    ctx.clearPreview();
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

}
