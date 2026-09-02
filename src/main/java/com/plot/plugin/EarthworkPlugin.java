package com.plot.plugin;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.api.world.ICoordinateService;
import com.plot.infrastructure.event.EventListener;
import com.plot.api.world.IBlockProjectionService;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.EarthworkGenerator;
import com.plot.plugin.earthwork.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.BuildingFootprintLookup;
import com.plot.plugin.earthwork.EarthworkRegionListHelper;
import com.plot.plugin.earthwork.EarthworkRegionPickSession;
import com.plot.plugin.earthwork.EarthworkThreePointPickSession;
import com.plot.plugin.earthwork.EarthworkVolumeReport;
import com.plot.plugin.earthwork.TerrainSnapshot;
import com.plot.plugin.earthwork.TerrainSnapshotCache;
import com.plot.plugin.earthwork.GradingSurfaceResolver;
import com.plot.plugin.earthwork.model.EarthMaterialProperties;
import com.plot.plugin.earthwork.RoadCorridorBaker;
import com.plot.plugin.earthwork.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.RetainingEdge;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkProjectHistory;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.core.plugin.PluginManager;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 土方平衡插件
 */
public class EarthworkPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkPlugin");
    private static final String DEFAULT_PROJECT_FILE = "default.json";
    private static final String CUT_GHOST_BLOCK = "minecraft:red_stained_glass";
    private static final String FILL_GHOST_BLOCK = "minecraft:light_blue_stained_glass";

    private EarthworkConfig config;
    private EarthworkProject project = new EarthworkProject();
    private final EarthworkProjectHistory projectHistory = new EarthworkProjectHistory();
    private final EarthworkRegionPickSession pickSession = new EarthworkRegionPickSession();
    private final EarthworkThreePointPickSession threePointPickSession = new EarthworkThreePointPickSession();
    private EarthworkGenerator earthworkGenerator;
    private final TerrainSnapshotCache terrainSnapshotCache = new TerrainSnapshotCache();

    // 多线程访问的字段需要同步保护（UI线程 + 异步方块放置）
    private final Object projectLock = new Object();
    private final List<Shape> selectedRegions = new ArrayList<>();
    private volatile String selectedRegionId = "";
    private volatile String projectStatus = "";
    private String currentProjectFile = DEFAULT_PROJECT_FILE;

    private volatile EarthworkGenerator.EarthworkGenerationResult lastGenerationResult;
    private String regionNameEditingRegionId = "";
    private String pendingDeleteRegionId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;
    /** 最近一次成功保存的内容指纹，避免 onDeactivate + onDisable 重复写盘 */
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    private EarthworkRegionListHelper.SortMode regionSortMode =
        EarthworkRegionListHelper.SortMode.INSERTION;

    private final ImBoolean autoBalanceRef = new ImBoolean(true);
    private final ImBoolean showGridRef = new ImBoolean(true);
    private final ImString regionNameBuffer = new ImString(64);

    private final EventListener projectLoadedListener = event -> {
        if (event instanceof ProjectLoadedEvent loaded) {
            onProjectLoaded(loaded.getFilePath());
        }
    };
    private final EventListener projectSavedListener = event -> {
        if (event instanceof ProjectSavedEvent saved) {
            onProjectSaved(saved.getFilePath());
        }
    };

    public EarthworkPlugin() {
        super(
            "earthwork_balance",
            "plugin.earthwork_balance.name",
            "plugin.earthwork_balance.desc",
            ExtensionPanelIcons.EARTHWORK
        );
    }

    @Override
    public void onEnable() {
        config = EarthworkConfig.load(EarthworkConfig.class, getId());
        if (config == null) {
            config = new EarthworkConfig(getId());
        }
        autoBalanceRef.set(config.isAutoBalance());
        showGridRef.set(config.isShowGrid());

        try {
            earthworkGenerator = new EarthworkGenerator(ctx().coordinates());
        } catch (Exception e) {
            LOGGER.error("初始化土方生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("土方插件初始化失败", e);
        }

        ctx().events().subscribe(this, ProjectLoadedEvent.class, projectLoadedListener);
        ctx().events().subscribe(this, ProjectSavedEvent.class, projectSavedListener);
        loadProjectForCurrentProject();
    }

    @Override
    public void onDeactivate() {
        if (isEnabled()) {
            persistProject();
        }
        super.onDeactivate();
    }

    @Override
    public void onDisable() {
        pickSession.cancel();
        threePointPickSession.cancel();
        persistProject();
        if (config != null) {
            config.save();
        }

        try {
            ctx().events().unsubscribeOwner(this);
        } catch (Exception e) {
            LOGGER.error("取消事件订阅失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void render() {
        if (config == null) {
            return;
        }

        if (pickSession.isActive()) {
            handlePickSessionTick();
        }
        if (threePointPickSession.isActive()) {
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

    @Override
    public void renderDeferredModals() {
        renderDeleteConfirmPopup();
        renderBuildConfirmPopup();
    }

    private void renderToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        boolean undoDisabled = !projectHistory.canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.undo"), buttonWidth, 0)) {
            project = projectHistory.undo(project);
            syncSelectedRegionAfterHistory();
            regionNameEditingRegionId = "";
            clearPreview();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !projectHistory.canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.redo"), buttonWidth, 0)) {
            project = projectHistory.redo(project);
            syncSelectedRegionAfterHistory();
            regionNameEditingRegionId = "";
            clearPreview();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!projectStatus.isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, projectStatus);
        }
        ImGui.separator();
    }

    private void renderActivePlacementControls() {
        com.plot.api.world.IBlockPlacementService scheduler = ctx().placement();
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
            project.getRegionCount(),
            String.format("%.1f", project.getTotalArea())));

        if (project.getRegionCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.no_regions"));
            return;
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##earthwork_region_sort", regionSortMode.label())) {
            for (EarthworkRegionListHelper.SortMode mode : EarthworkRegionListHelper.SortMode.values()) {
                boolean selected = mode == regionSortMode;
                if (ImGui.selectable(mode.label(), selected)) {
                    regionSortMode = mode;
                }
            }
            ImGui.endCombo();
        }

        ImGui.beginChild("earthwork_overview_list", 0, 220, true);
        for (GradingRegion region : EarthworkRegionListHelper.sorted(project, regionSortMode)) {
            ImGui.pushID(region.getId());
            boolean selected = region.getId().equals(selectedRegionId);
            if (ImGui.selectable(region.getName() + "##row", selected)) {
                selectedRegionId = region.getId();
            }

            ImGui.sameLine();
            String stats = region.getLastVolumeReport().hasGeometricVolume()
                ? PlotI18n.tr("plugin.earthwork.overview_stats",
                    region.getLastVolumeReport().geometricCutVolume(),
                    region.getLastVolumeReport().geometricFillVolume(),
                    region.getLastResolvedElevation())
                : PlotI18n.tr("plugin.earthwork.overview_no_stats");
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.earthwork.overview_item",
                String.format("%.1f", region.computeArea()),
                stats));

            if (ImGui.button(PlotI18n.tr("plugin.earthwork.locate"), 60, 0)) {
                locateRegion(region);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.delete"), 60, 0)) {
                pendingDeleteRegionId = region.getId();
                deleteConfirmPending = true;
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderAdoptTab() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.adopt_hint"));
        ImGui.spacing();

        if (pickSession.isActive()) {
            int count = pickSession.getAccumulatedCount();
            if (count > 0) {
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.earthwork.regions_selected"),
                    count));
            }
        } else {
            updateSelectedRegions();
        }

        if (!selectedRegions.isEmpty()) {
            ImGui.text(String.format(
                PlotI18n.tr("plugin.earthwork.regions_selected"),
                selectedRegions.size()));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.draw_region_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.pick_region"), 0, 0)) {
            startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = selectedRegions.isEmpty();
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

        GradingRegion region = project.getRegion(selectedRegionId);
        if (region == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.select_region_hint"));
            return;
        }

        if (!region.getId().equals(regionNameEditingRegionId)) {
            regionNameBuffer.set(region.getName());
            regionNameEditingRegionId = region.getId();
        }
        if (ImGui.inputText(PlotI18n.tr("plugin.earthwork.region_name"), regionNameBuffer)) {
            region.setName(regionNameBuffer.get());
        }
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }

        renderSurfaceModeSettings(region);
        renderZoneTypeSettings(region);

        renderMaterialPropertiesSettings(region);

        int[] previewGridSize = {region.getPreviewGridSize()};
        boolean previewGridChanged = ImGui.sliderInt("##preview_grid_size", previewGridSize, 1, 20,
            PlotI18n.tr("plugin.earthwork.preview_grid_size", previewGridSize[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (previewGridChanged) {
            region.setPreviewGridSize(previewGridSize[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.preview_grid_size");

        renderMaterialButton(PlotI18n.tr("plugin.earthwork.cut_material"), region.getCutExposeMaterial(),
            blockId -> {
                projectHistory.push(project);
                region.setCutExposeMaterial(blockId);
                invalidatePreview();
            });
        renderMaterialButton(PlotI18n.tr("plugin.earthwork.fill_material"), region.getFillMaterial(),
            blockId -> {
                projectHistory.push(project);
                region.setFillMaterial(blockId);
                invalidatePreview();
            });
    }

    private void renderSurfaceModeSettings(GradingRegion region) {
        GradingZone zone = project.getZone(region.getId());
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
                projectHistory.push(project);
                region.setSurfaceMode(modes[selected]);
                initializeSurfaceDefaults(region, modes[selected]);
                invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.surface_mode");

        switch (region.getSurfaceMode()) {
            case FLAT -> renderFlatSurfaceSettings(region);
            case FIXED_SLOPE -> renderFixedSlopeSettings(region);
            case THREE_POINT -> renderThreePointSurfaceSettings(region);
            case FIT_SLOPE -> renderFitSlopeSettings(region);
        }
    }

    private void renderZoneTypeSettings(GradingRegion region) {
        GradingZone zone = project.getZone(region.getId());
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
                projectHistory.push(project);
                zone.setType(types[picked]);
                invalidatePreview();
            }
        }

        int[] priority = {zone.getPriority()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.zone_priority"), priority, 0, 200)) {
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
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

    private void renderCompositionSettings() {
        EarthworkSite site = project.getActiveSite();
        CompositionPolicy policy = site.getCompositionPolicy();

        ImGui.text(PlotI18n.tr("plugin.earthwork.composition_settings"));
        int[] blendWidth = {policy.getBlendWidthBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.blend_width_blocks"), blendWidth, 0, 16)) {
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
            }
            policy.setBlendWidthBlocks(blendWidth[0]);
            invalidatePreview();
        }

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
                projectHistory.push(project);
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
                    projectHistory.push(project);
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
        renderRetainingEdgeSettings(site);
    }

    private void renderRetainingEdgeSettings(EarthworkSite site) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.retaining_edges_header"));
        for (RetainingEdge edge : site.getRetainingEdges()) {
            ImGui.pushID(edge.getId());
            ImGui.text(edge.getName().isBlank() ? edge.getId() : edge.getName());
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
                projectHistory.push(project);
                site.removeRetainingEdge(edge.getId());
                invalidatePreview();
                ImGui.popID();
                continue;
            }
            int[] topElevation = {edge.getTopElevation()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.retaining_top_elevation"), topElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    projectHistory.push(project);
                }
                edge.setTopElevation(topElevation[0]);
                invalidatePreview();
            }
            int[] bottomElevation = {edge.getBottomElevation()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.retaining_bottom_elevation"), bottomElevation, -64, 320)) {
                if (ImGui.isItemActivated()) {
                    projectHistory.push(project);
                }
                edge.setBottomElevation(bottomElevation[0]);
                invalidatePreview();
            }
            renderMaterialButton(PlotI18n.tr("plugin.earthwork.retaining_wall_material"), edge.getWallMaterial(),
                blockId -> {
                    projectHistory.push(project);
                    edge.setWallMaterial(blockId);
                    invalidatePreview();
                });
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.add_retaining_edge"), 0, 0)) {
            List<Vec2d> points = extractBreaklinePointsFromSelection();
            if (points.size() < 2) {
                points = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
            }
            projectHistory.push(project);
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
                projectHistory.push(project);
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

    private List<Vec2d> extractBreaklinePointsFromSelection() {
        List<Vec2d> points = new ArrayList<>();
        for (Shape shape : ctx().appState().getSelectedShapes()) {
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
                projectHistory.push(project);
                zone.setRoadEdgeRef(ids[picked]);
                invalidatePreview();
            }
        }
        if (zone.getDesignSurface().hasBakedElevation()) {
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.baked_samples",
                zone.getDesignSurface().getBakedElevationGrid().sampleCount()));
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.bake_road_elevations"), 0, 0)) {
            bakeRoadCorridorElevations(zone);
        }
    }

    private void bakeRoadCorridorElevations(GradingZone zone) {
        if (zone == null || zone.getRoadEdgeRef().isBlank()) {
            return;
        }
        World world = getClientWorld();
        if (world == null) {
            projectStatus = PlotI18n.tr("plugin.earthwork.generate_world_unavailable");
            return;
        }
        projectHistory.push(project);
        EarthworkSite site = project.getActiveSite();
        TerrainSnapshot terrain = terrainSnapshotCache.captureFreshSite(site, world, ctx().coordinates());
        int bakedCount = RoadCorridorBaker.bake(zone, terrain, createRoadSurfaceLookup());
        if (bakedCount <= 0) {
            projectStatus = PlotI18n.tr("plugin.earthwork.bake_road_failed");
        } else {
            projectStatus = PlotI18n.tr("plugin.earthwork.bake_road_success", bakedCount);
        }
        invalidatePreview();
    }

    private void renderBreaklineRow(EarthworkSite site, Breakline breakline, List<GradingZone> zones) {
        ImGui.pushID(breakline.getId());
        ImGui.text(breakline.getName());
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.delete"))) {
            projectHistory.push(project);
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
            projectHistory.push(project);
            breakline.setLeftZoneId(zoneIds[leftIndex.get()]);
            invalidatePreview();
        }
        ImGui.sameLine();
        ImInt rightIndex = new ImInt(indexOfZone(zoneIds, breakline.getRightZoneId()));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.breakline_right_zone"), rightIndex, zoneLabels)) {
            projectHistory.push(project);
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
                    projectHistory.push(project);
                    zone.setBuildingFootprintRef(ids[picked]);
                    zone.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
                    invalidatePreview();
                }
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.import_building_outline"), 0, 0)) {
                BuildingFootprint footprint = buildings.get(buildingIndex.get());
                if (footprint != null) {
                    projectHistory.push(project);
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
            projectHistory.push(project);
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
                    projectHistory.push(project);
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
                projectHistory.push(project);
            }
            zone.getDesignSurface().setBottomElevation(bottomElevation[0]);
            invalidatePreview();
        }

        int[] workingMargin = {zone.getDesignSurface().getWorkingMarginBlocks()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_working_margin"), workingMargin, 0, 8)) {
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
            }
            zone.getDesignSurface().setWorkingMarginBlocks(workingMargin[0]);
            invalidatePreview();
        }

        int[] slopePitch = {zone.getDesignSurface().getSlopePitchRatio()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.earthwork.pit_slope_pitch"), slopePitch, 1, 16)) {
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
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
            projectHistory.push(project);
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
            projectHistory.push(project);
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
        autoBalanceRef.set(region.isAutoBalance());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), autoBalanceRef)) {
            projectHistory.push(project);
            region.setAutoBalance(autoBalanceRef.get());
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.auto_balance");

        if (!region.isAutoBalance()) {
            int initial = region.getManualTargetElevation() != null ? region.getManualTargetElevation() : 64;
            int[] elevation = {initial};
            boolean elevationChanged = ImGui.sliderInt("##target_elevation", elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.target_elevation", elevation[0]));
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
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
            projectHistory.push(project);
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
            projectHistory.push(project);
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
            projectHistory.push(project);
        }
        if (anchorChanged) {
            region.setSlopeAnchorElevation(anchorElevation[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_anchor_elevation");

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.slope_reset_anchor"))) {
            projectHistory.push(project);
            region.setSlopeAnchorCanvas(EarthworkGeometryUtils.computeCentroid(region.getOuterPoints()));
            initializeSurfaceDefaults(region, GradingSurfaceMode.FIXED_SLOPE);
            invalidatePreview();
        }
    }

    private void renderThreePointSurfaceSettings(GradingRegion region) {
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.three_point_reset"))) {
            projectHistory.push(project);
            initializeSurfaceDefaults(region, GradingSurfaceMode.THREE_POINT);
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
                projectHistory.push(project);
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
                projectHistory.push(project);
            }
            if (zChanged) {
                region.setThreePointCanvasY(i, canvasZ[0]);
                invalidatePreview();
            }

            int[] elevation = {region.getThreePointElevation(i)};
            boolean yChanged = ImGui.sliderInt("##three_point_y_" + i, elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.three_point_elevation", elevation[0]));
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
            }
            if (yChanged) {
                region.setThreePointElevation(i, elevation[0]);
                invalidatePreview();
            }

            boolean pickingThisPoint = threePointPickSession.isActive()
                && threePointPickSession.getControlPointIndex() == i;
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
            projectHistory.push(project);
            region.setFitSlopeBalanceCutFill(balanceRef.get());
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fit_slope_balance");
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.fit_slope_hint"));
    }

    private void initializeSurfaceDefaults(GradingRegion region, GradingSurfaceMode mode) {
        com.plot.api.world.ICoordinateService transformer = ctx().coordinates();
        World world = getClientWorld();
        if (world == null || transformer == null) {
            return;
        }
        TerrainSnapshot terrain = terrainSnapshotCache.getOrCapture(region, world, transformer);
        if (terrain.isEmpty()) {
            return;
        }

        if (mode == GradingSurfaceMode.THREE_POINT) {
            GradingSurfaceResolver.initializeThreePointDefaults(
                region, terrain.centers(), terrain.groundHeights(), transformer);
        } else if (mode == GradingSurfaceMode.FIXED_SLOPE) {
            GradingSurfaceResolver.initializeFixedSlopeDefaults(
                region, terrain.centers(), terrain.groundHeights(), transformer);
        }
    }

    private void renderGlobalGridSettings() {
        ImGui.text(PlotI18n.tr("plugin.earthwork.grid_settings"));
        showGridRef.set(config.isShowGrid());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.show_grid"), showGridRef)) {
            config.setShowGrid(showGridRef.get());
            config.save();
        }
    }

    private void renderGenerateTab() {
        GradingRegion region = project.getRegion(selectedRegionId);
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
            calculatePreview(region);
        }
        ImGui.sameLine();
        boolean hasPreview = lastGenerationResult != null;
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
            if (calculatePreview(region)) {
                buildConfirmPending = true;
            }
        }

        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx().projection().checkWorldModificationReadiness();
        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        if (config.isShowGrid() && lastGenerationResult != null) {
            renderGridPreview(region, lastGenerationResult);
        }

        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.earthwork.calc_results"));
            EarthworkVolumeReport volumes = lastGenerationResult.volumeReport;
            ImGui.text(PlotI18n.tr("plugin.earthwork.calculation_cell_count", lastGenerationResult.calculationCellCount));
            renderTerrainSnapshotInfo(lastGenerationResult.existingTerrainSnapshot);
            ImGui.text(PlotI18n.tr("plugin.earthwork.geometric_cut_volume", volumes.geometricCutVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.geometric_fill_volume", volumes.geometricFillVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.reusable_cut_volume", volumes.reusableCutVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.export_volume", volumes.exportVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.import_volume", volumes.importVolume()));
            ImGui.text(PlotI18n.tr("plugin.earthwork.compacted_fill_demand", volumes.compactedFillDemand()));
            if (lastGenerationResult.slopedSurface) {
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.resolved_elevation_slope_result",
                    lastGenerationResult.resolvedElevationMin,
                    lastGenerationResult.resolvedElevationMax));
            } else {
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.resolved_elevation_result",
                    lastGenerationResult.resolvedElevation));
            }
            ImGui.text(PlotI18n.tr("plugin.earthwork.block_count_result", volumes.totalChangedBlocks()));
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.block_change_breakdown",
                volumes.cutChangedBlocks(),
                volumes.fillChangedBlocks()));

            if (lastGenerationResult.siteGeneration
                && lastGenerationResult.siteVolumeReport != null
                && lastGenerationResult.siteVolumeReport.byZone().size() > 1) {
                ImGui.separator();
                ImGui.text(PlotI18n.tr("plugin.earthwork.zone_volume_header"));
                for (Map.Entry<String, EarthworkVolumeReport> entry
                    : lastGenerationResult.siteVolumeReport.byZone().entrySet()) {
                    GradingRegion zoneRegion = project.getRegion(entry.getKey());
                    String zoneName = zoneRegion != null ? zoneRegion.getName() : entry.getKey();
                    EarthworkVolumeReport zoneVolumes = entry.getValue();
                    ImGui.text(PlotI18n.tr(
                        "plugin.earthwork.zone_volume_item",
                        zoneName,
                        zoneVolumes.geometricCutVolume(),
                        zoneVolumes.geometricFillVolume()));
                }
            }

            for (String warningKey : lastGenerationResult.warnings) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
            }

            boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
            if (!hasPlacements) {
                ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            }

            if (!hasPlacements) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.projection_ref"), half, 0)) {
                projectPreview();
            }
            if (!hasPlacements) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();
            boolean buildDisabled = !hasPlacements
                || !buildReadiness.ready()
                || ctx().placement().isBusy();
            if (buildDisabled) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), half, 0)) {
                buildConfirmPending = true;
            }
            if (buildDisabled) {
                ImGui.endDisabled();
            }
        }
    }

    private void renderGridPreview(GradingRegion region, EarthworkGenerator.EarthworkGenerationResult result) {
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

        for (EarthworkGenerator.GridSample sample : result.gridSamples) {
            float cellX = originX + 8.0f + (float) ((sample.center.x - bounds.minX()) * scale);
            float cellY = originY + 8.0f + (float) ((sample.center.y - bounds.minZ()) * scale);
            float cellSize = Math.max(3.0f, scale * 0.8f);
            int color = sample.changeType == EarthworkGenerator.ChangeType.CUT ? cutColor : fillColor;
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
        if (buildConfirmPending) {
            ImGui.openPopup("##earthwork_build_confirm");
            buildConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##earthwork_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            long blockCount = lastGenerationResult != null
                ? lastGenerationResult.volumeReport.totalChangedBlocks()
                : 0L;
            ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.build_confirm"), blockCount));

            TerrainSnapshot.ComparisonResult terrainComparison = comparePreviewTerrainWithWorld();
            boolean terrainStale = terrainComparison != null && terrainComparison.terrainChanged();
            if (terrainStale) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.earthwork.terrain_changed_since_preview",
                    terrainComparison.changedColumns(),
                    terrainComparison.totalColumns()));
            }

            com.plot.api.world.PlacementReadiness readiness =
                ctx().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            boolean canBuild = readiness.ready() && !ctx().placement().isBusy() && !terrainStale;
            if (terrainStale) {
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.recalculate_preview"), 180, 0)) {
                    GradingRegion region = project.getRegion(selectedRegionId);
                    if (region != null) {
                        calculatePreview(region);
                    }
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
            }
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), 120, 0)) {
                buildInWorld();
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
        TerrainSnapshot.ComparisonResult comparison = comparePreviewTerrainWithWorld();
        if (comparison != null && comparison.terrainChanged()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.earthwork.terrain_changed_since_preview",
                comparison.changedColumns(),
                comparison.totalColumns()));
        }
    }

    private TerrainSnapshot.ComparisonResult comparePreviewTerrainWithWorld() {
        if (lastGenerationResult == null || lastGenerationResult.existingTerrainSnapshot.isEmpty()) {
            return null;
        }
        World world = getClientWorld();
        if (world == null) {
            return null;
        }
        return lastGenerationResult.existingTerrainSnapshot.compareWithCurrentWorld(world);
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
        if (deleteConfirmPending) {
            ImGui.openPopup("##earthwork_delete_confirm");
            deleteConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##earthwork_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.delete_confirm"));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.delete"), 100, 0)) {
                if (!pendingDeleteRegionId.isEmpty()) {
                    projectHistory.push(project);
                    project.removeRegion(pendingDeleteRegionId);
                    terrainSnapshotCache.invalidateRegion(pendingDeleteRegionId);
                    terrainSnapshotCache.invalidateSite(project.getActiveSiteId());
                    if (pendingDeleteRegionId.equals(selectedRegionId)) {
                        selectedRegionId = project.getRegions().isEmpty()
                            ? ""
                            : project.getRegions().keySet().iterator().next();
                    }
                    clearPreview();
                }
                pendingDeleteRegionId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                pendingDeleteRegionId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderRegionSelector() {
        if (project.getRegionCount() == 0) {
            return;
        }
        String[] labels = project.getRegions().values().stream()
            .map(GradingRegion::getName)
            .toArray(String[]::new);
        String[] ids = project.getRegions().keySet().toArray(String[]::new);
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(selectedRegionId)) {
                current = i;
                break;
            }
        }
        ImInt regionIndex = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.select_region"), regionIndex, labels)) {
            selectedRegionId = ids[regionIndex.get()];
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
        selectedRegions.clear();
        selectedRegions.addAll(
            EarthworkGeometryUtils.findAdoptableRegions(ctx().appState().getSelectedShapes()));
    }

    private void startPickSession() {
        threePointPickSession.cancel();
        ToolManager toolManager = ctx().tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        selectedRegions.clear();
        pickSession.begin();
        toolManager.setActiveTool(selectTool);
        ctx().appState().setCurrentTool(baseTool);
        projectStatus = PlotI18n.tr("plugin.earthwork.pick_started");
    }

    private void handlePickSessionTick() {
        EarthworkRegionPickSession.Outcome outcome = pickSession.tick(ctx().appState());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                selectedRegions.clear();
                selectedRegions.addAll(outcome.getRegions());
                adoptSelectedRegions();
            }
            case NEED_SELECTION -> projectStatus = PlotI18n.tr("plugin.earthwork.pick_need_selection");
            case NO_VALID -> projectStatus = PlotI18n.tr("plugin.earthwork.pick_no_valid");
            case CANCELLED -> projectStatus = PlotI18n.tr("plugin.earthwork.pick_cancelled");
            default -> {
                List<Shape> selected = ctx().appState().getSelectedShapes();
                projectStatus = PlotI18n.tr(pickSession.hintKeyForCurrentSelection(selected));
            }
        }
    }

    private void startThreePointPick(GradingRegion region, int controlPointIndex) {
        if (region == null || controlPointIndex < 0 || controlPointIndex > 2) {
            return;
        }
        if (getClientWorld() == null) {
            projectStatus = PlotI18n.tr("status.plot.earthwork.three_point_pick_world_unavailable");
            return;
        }
        pickSession.cancel();
        threePointPickSession.begin(controlPointIndex);
        projectStatus = PlotI18n.tr("status.plot.earthwork.three_point_pick_active", controlPointIndex + 1);
    }

    private void handleThreePointPickSessionTick() {
        GradingRegion region = project.getRegion(selectedRegionId);
        if (region == null) {
            threePointPickSession.cancel();
            return;
        }

        EarthworkThreePointPickSession.Outcome outcome =
            threePointPickSession.tick(ctx().appState(), region.getOuterPoints(), ctx().coordinates());
        switch (outcome.getResult()) {
            case PICKED -> {
                EarthworkThreePointPickSession.PickResult pick = outcome.getPick();
                if (pick != null) {
                    projectHistory.push(project);
                    region.setThreePointControl(
                        outcome.getControlPointIndex(),
                        pick.canvasPoint(),
                        pick.elevation());
                    invalidatePreview();
                    projectStatus = PlotI18n.tr(
                        "status.plot.earthwork.three_point_pick_success",
                        outcome.getControlPointIndex() + 1);
                }
            }
            case OUTSIDE_REGION -> projectStatus =
                PlotI18n.tr("status.plot.earthwork.three_point_pick_outside_region");
            case WORLD_UNAVAILABLE -> projectStatus =
                PlotI18n.tr("status.plot.earthwork.three_point_pick_world_unavailable");
            case CANCELLED -> projectStatus =
                PlotI18n.tr("status.plot.earthwork.three_point_pick_cancelled");
            default -> projectStatus = PlotI18n.tr(
                "status.plot.earthwork.three_point_pick_active",
                outcome.getControlPointIndex() + 1);
        }
    }

    private void adoptSelectedRegions() {
        if (selectedRegions.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.adopt_no_selection");
            return;
        }

        // 先收集有效轮廓，避免 0 认领仍 push 历史
        List<List<Vec2d>> validOutlines = new ArrayList<>();
        for (Shape shape : selectedRegions) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() >= 3) {
                validOutlines.add(points);
            }
        }
        if (validOutlines.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.adopt_no_selection");
            selectedRegions.clear();
            return;
        }

        projectHistory.push(project);
        int adopted = 0;
        for (List<Vec2d> points : validOutlines) {
            GradingRegion region = new GradingRegion(points);
            region.setName(PlotI18n.tr("plugin.earthwork.default_name", adopted + 1));
            region.setAutoBalance(config.isAutoBalance());
            region.setMaterialProperties(config.getDefaultMaterialProperties());
            region.setPreviewGridSize(config.getPreviewGridSize());
            if (!config.isAutoBalance()) {
                region.setManualTargetElevation(Math.round(config.getTargetElevation()));
            }
            project.addRegion(region);
            selectedRegionId = region.getId();
            adopted++;
        }

        selectedRegions.clear();
        clearPreview();
        projectStatus = adopted > 1
            ? PlotI18n.tr("plugin.earthwork.adopt_success_batch", adopted)
            : PlotI18n.tr("plugin.earthwork.adopt_success");
    }

    private boolean calculatePreview(GradingRegion region) {
        World world = getClientWorld();
        if (world == null || earthworkGenerator == null) {
            projectStatus = PlotI18n.tr("plugin.earthwork.generate_world_unavailable");
            return false;
        }

        com.plot.api.world.IGhostBlockService ghostBlockManager = ctx().ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        EarthworkSite site = project.getActiveSite();
        try {
            if (site.delegatesToLegacyGenerator()) {
                TerrainSnapshot terrain = terrainSnapshotCache.captureFresh(
                    region, world, ctx().coordinates());
                lastGenerationResult = earthworkGenerator.generate(region, world, terrain);
            } else {
                TerrainSnapshot terrain = terrainSnapshotCache.captureFreshSite(
                    site, world, ctx().coordinates());
                lastGenerationResult = earthworkGenerator.generateSite(
                    site, world, terrain, region,
                    createBuildingFootprintLookup(),
                    createRoadSurfaceLookup());
            }
        } catch (Exception e) {
            LOGGER.error("土方预览生成失败: {}", e.getMessage(), e);
            lastGenerationResult = null;
            projectStatus = PlotI18n.tr("plugin.earthwork.generate_empty_result");
            return false;
        }
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.generate_empty_result");
            return false;
        }

        projectStatus = PlotI18n.tr("plugin.earthwork.generate_preview_ready");
        return true;
    }

    private void projectPreview() {
        if (lastGenerationResult == null) {
            return;
        }
        com.plot.api.world.IGhostBlockService ghostBlockManager = ctx().ghosts();
        if (ghostBlockManager == null) {
            return;
        }
        ghostBlockManager.clearAllGhostBlocks();
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            EarthworkGenerator.ChangeType changeType = lastGenerationResult.changeTypes.get(record.pos);
            String ghostBlock = changeType == EarthworkGenerator.ChangeType.CUT
                ? CUT_GHOST_BLOCK
                : FILL_GHOST_BLOCK;
            ghostBlockManager.addGhostBlock(record.pos, ghostBlock);
        }
    }

    private void clearPreview() {
        com.plot.api.world.IGhostBlockService ghostBlockManager = ctx().ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastGenerationResult = null;
    }

    /** 参数/工程变更后使预览失效，并清零区域上次统计，避免陈旧数据误导。 */
    private void invalidatePreview() {
        boolean hadPreview = lastGenerationResult != null;
        clearPreview();
        if (project != null) {
            EarthworkSite site = project.getActiveSite();
            site.setLastReport(EarthworkVolumeReport.empty());
            for (GradingRegion region : project.getRegions().values()) {
                region.setLastVolumeReport(EarthworkVolumeReport.empty());
            }
        }
        if (hadPreview) {
            projectStatus = PlotI18n.tr("plugin.earthwork.preview_invalidated");
        }
    }

    private void buildInWorld() {
        // 创建不可变快照，避免异步任务中的并发问题
        final EarthworkGenerator.EarthworkGenerationResult resultSnapshot;
        synchronized (projectLock) {
            if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
                projectStatus = PlotI18n.tr("plugin.earthwork.build_no_blocks");
                return;
            }
            resultSnapshot = lastGenerationResult;
        }

        com.plot.api.world.PlacementReadiness readiness =
            ctx().projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            projectStatus = readiness.message();
            return;
        }

        if (ctx().placement().isBusy()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_in_progress_wait");
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        final String builtRegionId = selectedRegionId;
        EarthworkGenerateCommand command = new EarthworkGenerateCommand(records, ctx().projection(), ctx().placement());
        projectStatus = PlotI18n.tr("plugin.earthwork.build_in_progress", records.size());
        command.executeScheduled(() -> {
            EarthworkGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            // 取消时若已写入部分方块，仍入历史以便撤销半成品
            if (result != null && result.cancelled()) {
                if (result.success() > 0) {
                    ctx().commands().pushExecuted(command);
                    terrainSnapshotCache.invalidateRegion(builtRegionId);
                    terrainSnapshotCache.invalidateSite(project.getActiveSiteId());
                }
                projectStatus = PlotI18n.tr(
                    "plugin.earthwork.build_cancelled", result.success(), result.total());
                clearPreview();
                return;
            }
            ctx().commands().pushExecuted(command);
            if (result != null && result.success() > 0) {
                terrainSnapshotCache.invalidateRegion(builtRegionId);
                terrainSnapshotCache.invalidateSite(project.getActiveSiteId());
            }
            applyBuildResultStatus(result);
            clearPreview();
        });
    }

    private void applyBuildResultStatus(EarthworkGenerateCommand.ExecutionResult result) {
        if (result == null || result.total() == 0) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_no_blocks");
            return;
        }
        if (result.cancelled()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_cancelled", result.success(), result.total());
            return;
        }
        if (result.isFullSuccess()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_success", result.success());
            return;
        }
        if (result.isTotalFailure()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_failed", result.total());
            return;
        }
        projectStatus = PlotI18n.tr(
            "plugin.earthwork.build_partial",
            result.success(),
            result.total(),
            result.failed());
    }

    private void locateRegion(GradingRegion region) {
        Vec2d centroid = EarthworkGeometryUtils.computeCentroid(region.getOuterPoints());
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            selectedRegionId = region.getId();
            projectStatus = PlotI18n.tr("plugin.earthwork.locate_success", region.getName());
        }
    }

    private void syncSelectedRegionAfterHistory() {
        if (!selectedRegionId.isEmpty() && project.getRegion(selectedRegionId) == null) {
            selectedRegionId = project.getRegions().isEmpty()
                ? ""
                : project.getRegions().keySet().iterator().next();
            regionNameEditingRegionId = "";
        }
    }

    private World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

    private void onProjectLoaded(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = getProjectsDir().resolve(targetFile);
        // 仅加载成功后才绑定路径，避免失败时把旧工程写进新文件
        if (loadProjectFile(file)) {
            currentProjectFile = targetFile;
            projectStatus = PlotI18n.tr("plugin.earthwork.project.loaded", filePath);
        }
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentProjectFile = ProjectPathResolver.sidecarFileName(filePath);
        if (saveProjectFile(getProjectsDir().resolve(currentProjectFile))) {
            projectStatus = PlotI18n.tr("plugin.earthwork.project.saved", filePath);
        }
    }

    private void persistProject() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
    }

    /**
     * @return true 若加载成功（含文件不存在时返回空项目）
     */
    private boolean loadProjectFile(Path file) {
        try {
            EarthworkProject loaded = EarthworkProject.loadFrom(file);
            project = loaded;
            projectHistory.clear();
            selectedRegionId = project.getRegions().isEmpty()
                ? ""
                : project.getRegions().keySet().iterator().next();
            regionNameEditingRegionId = "";
            pickSession.cancel();
            threePointPickSession.cancel();
            selectedRegions.clear();
            terrainSnapshotCache.clear();
            clearPreview();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载土方项目失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.earthwork.project.load_failed", file.getFileName());
            return false;
        }
    }

    private void loadProjectForCurrentProject() {
        Project current = ctx().appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath());
            return;
        }
        Path file = getProjectsDir().resolve(DEFAULT_PROJECT_FILE);
        if (loadProjectFile(file)) {
            currentProjectFile = DEFAULT_PROJECT_FILE;
            projectStatus = PlotI18n.tr("plugin.earthwork.project.default_loaded");
        }
    }

    private boolean saveProjectFile(Path file) {
        if (file == null || project == null) {
            return false;
        }
        try {
            String json = project.toJson();
            if (contentFingerprint.isUnchanged(json, file)) {
                LOGGER.debug("土方项目内容未变，跳过重复保存: {}", file.getFileName());
                return true;
            }
            project.saveTo(file);
            contentFingerprint.markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存土方项目失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.earthwork.project.save_failed", file.getFileName());
            return false;
        }
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }
}
