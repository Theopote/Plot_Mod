package com.plot.plugin;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.EarthworkGenerator;
import com.plot.plugin.earthwork.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkProjectHistory;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
    private EarthworkGenerator earthworkGenerator;

    private final List<Shape> selectedRegions = new ArrayList<>();
    private String selectedRegionId = "";
    private String projectStatus = "";
    private String currentProjectFile = DEFAULT_PROJECT_FILE;

    private EarthworkGenerator.EarthworkGenerationResult lastGenerationResult;
    private String pendingDeleteRegionId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

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
            earthworkGenerator = new EarthworkGenerator(CoordinateTransformer.getInstance());
        } catch (Exception e) {
            LOGGER.error("初始化土方生成器失败: {}", e.getMessage(), e);
        }

        EventBus.getInstance().subscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().subscribe(ProjectSavedEvent.class, projectSavedListener);
        loadProjectForCurrentProject();
    }

    @Override
    public void onDisable() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        if (config != null) {
            config.save();
        }

        EventBus.getInstance().unsubscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().unsubscribe(ProjectSavedEvent.class, projectSavedListener);
    }

    @Override
    public void render() {
        if (config == null) {
            return;
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

        renderDeleteConfirmPopup();
    }

    private void renderToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;

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

        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.save_project"), buttonWidth, 0)) {
            saveCurrentProject();
        }

        if (!projectStatus.isEmpty()) {
            ImGui.textColored((int) 0xFF80FF80FFL, projectStatus);
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
            ImGui.textColored((int) 0xFF80C0FFFFL,
                PlotI18n.tr("plugin.earthwork.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored((int) 0xFF80C0FFFFL, PlotI18n.tr("plugin.earthwork.build_in_progress_hint"));
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
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.no_regions"));
            return;
        }

        ImGui.beginChild("earthwork_overview_list", 0, 220, true);
        for (GradingRegion region : project.getRegions().values()) {
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
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr(
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
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.adopt_hint"));
        ImGui.spacing();
        updateSelectedRegions();

        if (!selectedRegions.isEmpty()) {
            ImGui.text(String.format(
                PlotI18n.tr("plugin.earthwork.regions_selected"),
                selectedRegions.size()));
        } else {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.draw_region_hint"));
        }

        ImGui.spacing();
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
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.select_region_hint"));
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

        autoBalanceRef.set(region.isAutoBalance());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), autoBalanceRef)) {
            projectHistory.push(project);
            region.setAutoBalance(autoBalanceRef.get());
        }

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
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.manual_elevation_disabled"));
            ImGui.endDisabled();
        }

        float[] fillFactor = {region.getFillFactor()};
        if (ImGui.sliderFloat("##fill_factor", fillFactor, 1.0f, 2.0f,
            PlotI18n.tr("plugin.earthwork.fill_factor", String.format("%.2f", fillFactor[0])))) {
            projectHistory.push(project);
            region.setFillFactor(fillFactor[0]);
        }

        int[] gridSize = {region.getGridSize()};
        if (ImGui.sliderInt("##region_grid_size", gridSize, 1, 20,
            PlotI18n.tr("plugin.earthwork.grid_size", gridSize[0]))) {
            projectHistory.push(project);
            region.setGridSize(gridSize[0]);
        }

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
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.select_region_hint"));
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
            ImGui.textColored((int) 0xFFFF8080FFL, buildReadiness.message());
        }

        if (config.isShowGrid() && lastGenerationResult != null) {
            renderGridPreview(region, lastGenerationResult);
        }

        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.earthwork.calc_results"));
            ImGui.text(PlotI18n.tr("plugin.earthwork.cut_volume_result", lastGenerationResult.cutVolume));
            ImGui.text(PlotI18n.tr("plugin.earthwork.fill_volume_result", lastGenerationResult.fillVolume));
            ImGui.text(PlotI18n.tr("plugin.earthwork.resolved_elevation_result", lastGenerationResult.resolvedElevation));
            ImGui.text(PlotI18n.tr("plugin.earthwork.block_count_result", lastGenerationResult.blockCount));

            for (String warningKey : lastGenerationResult.warnings) {
                ImGui.textColored((int) 0xFFFFA060FFL, PlotI18n.tr(warningKey));
            }

            boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
            if (!hasPlacements) {
                ImGui.textColored((int) 0xFFFFB060FFL, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
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
            renderBuildConfirmPopup();
        }
    }

    private void renderGridPreview(GradingRegion region, EarthworkGenerator.EarthworkGenerationResult result) {
        List<Vec2d> points = region.getOuterPoints();
        if (points.size() < 3 || result.gridSamples.isEmpty()) {
            return;
        }

        BuildingGeometryUtils.RectBounds bounds = EarthworkGeometryUtils.computeBounds(points);
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

        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.earthwork.grid_preview_legend"));
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
                ImGui.textColored((int) 0xFFFF6060FFL, readiness.message());
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
            openBlockPicker(
                currentBlockId == null || currentBlockId.isBlank() ? "minecraft:air" : currentBlockId,
                onSelected);
        }
    }

    private void openBlockPicker(String currentBlockId, Consumer<String> onSelected) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            client.setScreen(BlockConfigNativeScreen.forSingleSelection(
                client.currentScreen, currentBlockId, onSelected));
        });
    }

    private void updateSelectedRegions() {
        selectedRegions.clear();
        selectedRegions.addAll(
            EarthworkGeometryUtils.findAdoptableRegions(AppState.getInstance().getSelectedShapes()));
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
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.earthwork.build_no_blocks");
            return;
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

        List<BlockRecord> records = new ArrayList<>(lastGenerationResult.placementRecords.values());
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
        currentProjectFile = hashPath(filePath) + ".json";
        loadProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.earthwork.project.loaded", filePath);
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentProjectFile = hashPath(filePath) + ".json";
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.earthwork.project.saved", filePath);
    }

    private void saveCurrentProject() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.earthwork.project.manual_saved");
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

    private String hashPath(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(filePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return DEFAULT_PROJECT_FILE.replace(".json", "");
        }
    }
}
