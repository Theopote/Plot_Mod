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
import com.plot.plugin.earthwork.material.EarthworkMaterialScope;
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
        renderAdoptSection();
        ImGui.separator();
        renderOverviewSection();
    }

    /** Quick 模式无区域时仅显示认领区。 */
    public void renderAdoptSection() {
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
            updateSelectedRegionsIfNeeded();
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

    private void renderOverviewSection() {
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_stats",
            ctx.project().getRegionCount(),
            String.format("%.1f", ctx.project().getTotalArea())));
        renderSiteOverlapWarnings();
        EarthworkSite site = ctx.project().getActiveSite();
        if (ctx.config().getWorkMode().showsLearningMetrics()) {
            renderProjectGlobalBalance(ctx.project());
            renderSiteMaterialModel(site);
        }
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
                EarthworkUiWidgets.locateRegion(ctx, region);
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

        ctx.projectHistory().push(ctx.project());
        int adopted = 0;
        int skippedDuplicates = 0;
        for (List<Vec2d> points : validOutlines) {
            if (hasMatchingRegionOutline(ctx.project(), points)) {
                skippedDuplicates++;
                continue;
            }
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
        if (adopted == 0 && skippedDuplicates > 0) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_duplicate_skipped", skippedDuplicates));
        } else if (adopted > 1) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_success_batch", adopted));
        } else if (adopted == 1) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_success"));
        } else {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
        }
    }

    private static boolean hasMatchingRegionOutline(EarthworkProject project, List<Vec2d> outline) {
        if (project == null || outline == null || outline.size() < 3) {
            return false;
        }
        long fingerprint = outlineFingerprint(outline);
        for (GradingRegion existing : project.getRegions().values()) {
            if (outlineFingerprint(existing.getOuterPoints()) == fingerprint) {
                return true;
            }
        }
        return false;
    }

    private static long outlineFingerprint(List<Vec2d> points) {
        long hash = 17L;
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            hash = 31L * hash + Double.hashCode(Math.rint(point.x * 100.0) / 100.0);
            hash = 31L * hash + Double.hashCode(Math.rint(point.y * 100.0) / 100.0);
        }
        hash = 31L * hash + points.size();
        return hash;
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
        ImGui.text(PlotI18n.tr("plugin.earthwork.reusable_cut_volume", balance.reusableCut()));
        ImGui.spacing();
        EarthworkUiWidgets.renderProjectMaterialBalance(balance.materialBalance());
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
            signature = 31 * signature + Double.hashCode(report.reusableCutVolume());
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
                    ctx.project().removeRegion(ctx.pendingDeleteRegionId());
                    ctx.terrainSnapshotCache().invalidateRegion(ctx.pendingDeleteRegionId());
                    ctx.terrainSnapshotCache().invalidateSite(ctx.project().getActiveSiteId());
                    if (ctx.pendingDeleteRegionId().equals(ctx.selectedRegionId())) {
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

    private void renderSiteMaterialModel(EarthworkSite site) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.earthwork.learn.slider_header"));
        EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.learn.slider_hint"));
        if (ImGui.treeNode(PlotI18n.tr("plugin.earthwork.learn.why_conversion"))) {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.learn.why_conversion_body"));
            ImGui.treePop();
        }
        EarthworkUiWidgets.renderMaterialConversionSliders(ctx, site.getMaterialModel(), updated ->
            EarthworkMaterialScope.updateSiteMaterial(ctx, site, updated));
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.apply_site_material_to_regions"), 0, 0)) {
            ctx.projectHistory().push(ctx.project());
            EarthworkMaterialScope.applySiteMaterialToAllRegions(ctx, ctx.project(), site);
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.save_site_material_as_adopt_default"), 0, 0)) {
            EarthworkMaterialScope.promoteSiteMaterialToAdoptDefault(ctx, site);
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_default_material_saved"));
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.site_material_model");
        ImGui.separator();
    }
}
