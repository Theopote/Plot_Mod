package com.plot.plugin;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.EarthworkGenerator;
import com.plot.plugin.earthwork.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.EarthworkRegionListHelper;
import com.plot.plugin.earthwork.EarthworkRegionPickSession;
import com.plot.plugin.earthwork.EarthworkThreePointPickSession;
import com.plot.plugin.earthwork.TerrainSurfaceSampler;
import com.plot.plugin.earthwork.GradingSurfaceResolver;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkProjectHistory;
import com.plot.plugin.common.ProjectPathHasher;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
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

    // 多线程访问的字段需要同步保护（UI线程 + 异步方块放置）
    private final Object projectLock = new Object();
    private final List<Shape> selectedRegions = new ArrayList<>();
    private volatile String selectedRegionId = "";
    private volatile String projectStatus = "";
    private String currentProjectFile = DEFAULT_PROJECT_FILE;

    private volatile EarthworkGenerator.EarthworkGenerationResult lastGenerationResult;
    private String pendingDeleteRegionId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

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
            CoordinateTransformer transformer = CoordinateTransformer.getInstance();
            if (transformer == null) {
                throw new IllegalStateException("CoordinateTransformer未初始化，插件无法启动");
            }
            earthworkGenerator = new EarthworkGenerator(transformer);
        } catch (Exception e) {
            LOGGER.error("初始化土方生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("土方插件初始化失败", e);
        }

        EventBus.getInstance().subscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().subscribe(ProjectSavedEvent.class, projectSavedListener);
        loadProjectForCurrentProject();
    }

    @Override
    public void onDeactivate() {
        if (isEnabled()) {
            persistProject();
        }
    }

    @Override
    public void onDisable() {
        pickSession.cancel();
        threePointPickSession.cancel();
        persistProject();
        if (config != null) {
            config.save();
        }

        // 安全地取消事件订阅
        try {
            EventBus eventBus = EventBus.getInstance();
            if (eventBus != null) {
                eventBus.unsubscribe(ProjectLoadedEvent.class, projectLoadedListener);
                eventBus.unsubscribe(ProjectSavedEvent.class, projectSavedListener);
            }
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
        BlockPlacementScheduler scheduler = BlockPlacementScheduler.getInstance();
        if (!scheduler.isBusy()) {
            return;
        }

        BlockPlacementScheduler.ProgressSnapshot progress = scheduler.getProgressSnapshot();
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
            String stats = region.getLastCutVolume() > 0 || region.getLastFillVolume() > 0
                ? PlotI18n.tr("plugin.earthwork.overview_stats",
                    region.getLastCutVolume(), region.getLastFillVolume(), region.getLastResolvedElevation())
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
        GradingRegion region = project.getRegion(selectedRegionId);
        if (region == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.select_region_hint"));
            renderRegionSelector();
            renderGlobalGridSettings();
            return;
        }

        renderRegionSelector();
        ImGui.separator();

        regionNameBuffer.set(region.getName());
        if (ImGui.inputText(PlotI18n.tr("plugin.earthwork.region_name"), regionNameBuffer)) {
            projectHistory.push(project);
            region.setName(regionNameBuffer.get());
        }

        renderSurfaceModeSettings(region);

        float[] fillFactor = {region.getFillFactor()};
        if (ImGui.sliderFloat("##fill_factor", fillFactor, 1.0f, 2.0f,
            PlotI18n.tr("plugin.earthwork.fill_factor", String.format("%.2f", fillFactor[0])))) {
            projectHistory.push(project);
            region.setFillFactor(fillFactor[0]);
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fill_factor");

        int[] gridSize = {region.getGridSize()};
        if (ImGui.sliderInt("##region_grid_size", gridSize, 1, 20,
            PlotI18n.tr("plugin.earthwork.grid_size", gridSize[0]))) {
            projectHistory.push(project);
            region.setGridSize(gridSize[0]);
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.grid_size");

        renderMaterialButton(PlotI18n.tr("plugin.earthwork.cut_material"), region.getCutExposeMaterial(),
            blockId -> {
                projectHistory.push(project);
                region.setCutExposeMaterial(blockId);
            });
        renderMaterialButton(PlotI18n.tr("plugin.earthwork.fill_material"), region.getFillMaterial(),
            blockId -> {
                projectHistory.push(project);
                region.setFillMaterial(blockId);
            });

        ImGui.separator();
        renderGlobalGridSettings();
    }

    private void renderSurfaceModeSettings(GradingRegion region) {
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

    private void renderFlatSurfaceSettings(GradingRegion region) {
        autoBalanceRef.set(region.isAutoBalance());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), autoBalanceRef)) {
            projectHistory.push(project);
            region.setAutoBalance(autoBalanceRef.get());
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.auto_balance");

        if (!region.isAutoBalance()) {
            int initial = region.getManualTargetElevation() != null ? region.getManualTargetElevation() : 64;
            int[] elevation = {initial};
            if (ImGui.sliderInt("##target_elevation", elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.target_elevation", elevation[0]))) {
                projectHistory.push(project);
                region.setManualTargetElevation(elevation[0]);
            }
        } else {
            ImGui.beginDisabled();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.manual_elevation_disabled"));
            ImGui.endDisabled();
        }
    }

    private void renderFixedSlopeSettings(GradingRegion region) {
        float[] direction = {(float) region.getSlopeDirectionDegrees()};
        if (ImGui.sliderFloat("##slope_direction", direction, 0.0f, 359.0f,
            PlotI18n.tr("plugin.earthwork.slope_direction", String.format("%.0f", direction[0])))) {
            projectHistory.push(project);
            region.setSlopeDirectionDegrees(direction[0]);
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_direction");

        int[] pitch = {region.getSlopePitchRatio()};
        if (ImGui.sliderInt("##slope_pitch", pitch, 1, 32,
            PlotI18n.tr("plugin.earthwork.slope_pitch", pitch[0]))) {
            projectHistory.push(project);
            region.setSlopePitchRatio(pitch[0]);
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_pitch");

        int anchorInitial = region.getSlopeAnchorElevation() != null ? region.getSlopeAnchorElevation() : 64;
        int[] anchorElevation = {anchorInitial};
        if (ImGui.sliderInt("##slope_anchor_elevation", anchorElevation, -64, 320,
            PlotI18n.tr("plugin.earthwork.slope_anchor_elevation", anchorElevation[0]))) {
            projectHistory.push(project);
            region.setSlopeAnchorElevation(anchorElevation[0]);
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.slope_anchor_elevation");

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.slope_reset_anchor"))) {
            projectHistory.push(project);
            region.setSlopeAnchorCanvas(EarthworkGeometryUtils.computeCentroid(region.getOuterPoints()));
            initializeSurfaceDefaults(region, GradingSurfaceMode.FIXED_SLOPE);
        }
    }

    private void renderThreePointSurfaceSettings(GradingRegion region) {
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.three_point_reset"))) {
            projectHistory.push(project);
            initializeSurfaceDefaults(region, GradingSurfaceMode.THREE_POINT);
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.three_point_reset");

        var bounds = EarthworkGeometryUtils.computeBounds(region.getOuterPoints());
        for (int i = 0; i < 3; i++) {
            ImGui.separator();
            ImGui.text(PlotI18n.tr("plugin.earthwork.three_point_label", i + 1));

            float[] canvasX = {(float) region.getThreePointCanvasX(i)};
            if (ImGui.sliderFloat("##three_point_x_" + i, canvasX,
                (float) bounds.minX(), (float) bounds.maxX(),
                PlotI18n.tr("plugin.earthwork.three_point_canvas_x", String.format("%.1f", canvasX[0])))) {
                projectHistory.push(project);
                region.setThreePointCanvasX(i, canvasX[0]);
            }

            float[] canvasZ = {(float) region.getThreePointCanvasY(i)};
            if (ImGui.sliderFloat("##three_point_z_" + i, canvasZ,
                (float) bounds.minZ(), (float) bounds.maxZ(),
                PlotI18n.tr("plugin.earthwork.three_point_canvas_z", String.format("%.1f", canvasZ[0])))) {
                projectHistory.push(project);
                region.setThreePointCanvasY(i, canvasZ[0]);
            }

            int[] elevation = {region.getThreePointElevation(i)};
            if (ImGui.sliderInt("##three_point_y_" + i, elevation, -64, 320,
                PlotI18n.tr("plugin.earthwork.three_point_elevation", elevation[0]))) {
                projectHistory.push(project);
                region.setThreePointElevation(i, elevation[0]);
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
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fit_slope_balance");
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.fit_slope_hint"));
    }

    private void initializeSurfaceDefaults(GradingRegion region, GradingSurfaceMode mode) {
        List<Vec2d> sampleCenters = EarthworkGeometryUtils.collectSampleCenters(
            region.getOuterPoints(), region.getGridSize());
        List<Integer> sampleHeights = sampleHeightsFromWorld(region, sampleCenters);
        CoordinateTransformer transformer = CoordinateTransformer.getInstance();

        if (mode == GradingSurfaceMode.THREE_POINT) {
            GradingSurfaceResolver.initializeThreePointDefaults(
                region, sampleCenters, sampleHeights, transformer);
        } else if (mode == GradingSurfaceMode.FIXED_SLOPE) {
            GradingSurfaceResolver.initializeFixedSlopeDefaults(
                region, sampleCenters, sampleHeights, transformer);
        }
    }

    private List<Integer> sampleHeightsFromWorld(GradingRegion region, List<Vec2d> sampleCenters) {
        List<Integer> sampleHeights = new ArrayList<>();
        World world = getClientWorld();
        CoordinateTransformer transformer = CoordinateTransformer.getInstance();
        if (world == null || transformer == null) {
            for (int i = 0; i < sampleCenters.size(); i++) {
                sampleHeights.add(64);
            }
            return sampleHeights;
        }
        for (Vec2d center : sampleCenters) {
            sampleHeights.add(TerrainSurfaceSampler.sampleAtCanvas(world, center, transformer));
        }
        return sampleHeights;
    }

    private void renderGlobalGridSettings() {
        ImGui.text(PlotI18n.tr("plugin.earthwork.grid_settings"));
        showGridRef.set(config.isShowGrid());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.show_grid"), showGridRef)) {
            config.setShowGrid(showGridRef.get());
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

        BlockProjectionHandler.PlacementReadiness buildReadiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
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
            ImGui.text(PlotI18n.tr("plugin.earthwork.cut_volume_result", lastGenerationResult.cutVolume));
            ImGui.text(PlotI18n.tr("plugin.earthwork.fill_volume_result", lastGenerationResult.fillVolume));
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
            ImGui.text(PlotI18n.tr("plugin.earthwork.block_count_result", lastGenerationResult.blockCount));

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
                || BlockPlacementScheduler.getInstance().isBusy();
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
            int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
            ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.build_confirm"), blockCount));

            BlockProjectionHandler.PlacementReadiness readiness =
                BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            boolean canBuild = readiness.ready() && !BlockPlacementScheduler.getInstance().isBusy();
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
                    if (pendingDeleteRegionId.equals(selectedRegionId)) {
                        selectedRegionId = project.getRegions().isEmpty()
                            ? ""
                            : project.getRegions().keySet().iterator().next();
                    }
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
            EarthworkGeometryUtils.findAdoptableRegions(AppState.getInstance().getSelectedShapes()));
    }

    private void startPickSession() {
        threePointPickSession.cancel();
        ToolManager toolManager = ToolManager.getInstance();
        if (toolManager == null) {
            return;
        }
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        selectedRegions.clear();
        pickSession.begin();
        toolManager.setActiveTool(selectTool);
        AppState.getInstance().setCurrentTool(baseTool);
        projectStatus = PlotI18n.tr("plugin.earthwork.pick_started");
    }

    private void handlePickSessionTick() {
        EarthworkRegionPickSession.Outcome outcome = pickSession.tick(AppState.getInstance());
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
                List<Shape> selected = AppState.getInstance().getSelectedShapes();
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
            threePointPickSession.tick(AppState.getInstance(), region.getOuterPoints());
        switch (outcome.getResult()) {
            case PICKED -> {
                EarthworkThreePointPickSession.PickResult pick = outcome.getPick();
                if (pick != null) {
                    projectHistory.push(project);
                    region.setThreePointControl(
                        outcome.getControlPointIndex(),
                        pick.canvasPoint(),
                        pick.elevation());
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

        projectHistory.push(project);
        int adopted = 0;
        for (Shape shape : selectedRegions) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() < 3) {
                continue;
            }
            GradingRegion region = new GradingRegion(points);
            region.setName(PlotI18n.tr("plugin.earthwork.default_name", adopted + 1));
            region.setAutoBalance(config.isAutoBalance());
            region.setFillFactor(config.getFillFactor());
            region.setGridSize(config.getGridSize());
            if (!config.isAutoBalance()) {
                region.setManualTargetElevation(Math.round(config.getTargetElevation()));
            }
            project.addRegion(region);
            selectedRegionId = region.getId();
            adopted++;
        }

        selectedRegions.clear();
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

        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        lastGenerationResult = earthworkGenerator.generate(region, world);
        if (lastGenerationResult == null) {
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
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
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
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastGenerationResult = null;
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

        BlockProjectionHandler.PlacementReadiness readiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            projectStatus = readiness.message();
            return;
        }

        if (BlockPlacementScheduler.getInstance().isBusy()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_in_progress_wait");
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        EarthworkGenerateCommand command = new EarthworkGenerateCommand(records);
        projectStatus = PlotI18n.tr("plugin.earthwork.build_in_progress", records.size());
        command.executeScheduled(() -> {
            EarthworkGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (result != null && result.cancelled()) {
                projectStatus = PlotI18n.tr("plugin.earthwork.build_cancelled", result.success(), result.total());
                return;
            }
            CommandManager.getInstance().pushExecuted(command);
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
        Canvas canvas = AppState.getInstance().getCanvas();
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
        currentProjectFile = ProjectPathHasher.projectFileName(filePath);
        loadProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.earthwork.project.loaded", filePath);
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentProjectFile = ProjectPathHasher.projectFileName(filePath);
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.earthwork.project.saved", filePath);
    }

    private void persistProject() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
    }

    private void loadProjectFile(Path file) {
        try {
            project = EarthworkProject.loadFrom(file);
            projectHistory.clear();
            selectedRegionId = project.getRegions().isEmpty()
                ? ""
                : project.getRegions().keySet().iterator().next();
        } catch (IOException e) {
            LOGGER.error("加载土方项目失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.earthwork.project.load_failed", file.getFileName());
        }
    }

    private void loadProjectForCurrentProject() {
        Project current = AppState.getInstance().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath());
            return;
        }
        currentProjectFile = DEFAULT_PROJECT_FILE;
        loadProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.earthwork.project.default_loaded");
    }

    private void saveProjectFile(Path file) {
        try {
            project.saveTo(file);
        } catch (IOException e) {
            LOGGER.error("保存土方项目失败: {}", e.getMessage(), e);
        }
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }
}
