package com.plot.plugin.earthwork.ui;

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
import com.plot.plugin.earthwork.grading.ZoneOverlapAnalyzer;
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


/** 土方总览 Tab：区域列表、重叠警告与删除确认。 */
public final class EarthworkOverviewPanel {
    private final EarthworkUiContext ctx;

    public EarthworkOverviewPanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
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
}
