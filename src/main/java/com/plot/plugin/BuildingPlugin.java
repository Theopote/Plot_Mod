package com.plot.plugin;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.BuildingGenerateCommand;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.building.BuildingFootprintPickSession;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.BuildingProjectHistory;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

public class BuildingPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingPlugin");
    private static final String DEFAULT_PROJECT_FILE = "default.json";

    private BuildingProject project = new BuildingProject();
    private final BuildingProjectHistory projectHistory = new BuildingProjectHistory();
    private BuildingGenerator buildingGenerator;

    private final BuildingFootprintPickSession pickSession = new BuildingFootprintPickSession();
    private final List<Shape> selectedFootprints = new ArrayList<>();
    private String selectedBuildingId = "";
    private String projectStatus = "";
    private String currentProjectFile = DEFAULT_PROJECT_FILE;

    private BuildingGenerator.BuildingGenerationResult lastGenerationResult;
    private String pendingDeleteBuildingId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private final ImBoolean manualElevationRef = new ImBoolean(false);
    private final ImString buildingNameBuffer = new ImString(64);

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

    public BuildingPlugin() {
        super(
            "building",
            "plugin.building.name",
            "plugin.building.desc",
            ExtensionPanelIcons.BUILDING
        );
    }

    @Override
    public void onEnable() {
        try {
            buildingGenerator = new BuildingGenerator(CoordinateTransformer.getInstance());
        } catch (Exception e) {
            LOGGER.error("初始化建筑生成器失败: {}", e.getMessage(), e);
        }

        EventBus.getInstance().subscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().subscribe(ProjectSavedEvent.class, projectSavedListener);
        loadProjectForCurrentProject();
    }

    @Override
    public void onDisable() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        pickSession.cancel();

        EventBus.getInstance().unsubscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().unsubscribe(ProjectSavedEvent.class, projectSavedListener);
    }

    @Override
    public void render() {
        if (pickSession.isActive()) {
            handlePickSessionTick();
        }

        renderToolbar();

        renderActivePlacementControls();

        if (ImGui.beginTabBar("##building_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.building.tab.overview"))) {
                renderOverviewTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.building.tab.adopt"))) {
                renderAdoptTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.building.tab.edit"))) {
                renderEditTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.building.tab.generate"))) {
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
        if (ImGui.button(PlotI18n.tr("plugin.building.undo"), buttonWidth, 0)) {
            project = projectHistory.undo(project);
            syncSelectedBuildingAfterHistory();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !projectHistory.canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.redo"), buttonWidth, 0)) {
            project = projectHistory.redo(project);
            syncSelectedBuildingAfterHistory();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.building.save_project"), buttonWidth, 0)) {
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
                PlotI18n.tr("plugin.building.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored((int) 0xFF80C0FFFFL, PlotI18n.tr("plugin.building.build_in_progress_hint"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.building.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
        ImGui.separator();
    }

    private void renderOverviewTab() {
        ImGui.text(PlotI18n.tr("plugin.building.project_stats",
            project.getBuildingCount(),
            String.format("%.1f", project.getTotalArea())));

        if (project.getBuildingCount() == 0) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.building.no_buildings"));
            return;
        }

        ImGui.beginChild("building_overview_list", 0, 220, true);
        for (BuildingFootprint building : project.getBuildings().values()) {
            ImGui.pushID(building.getId());
            boolean selected = building.getId().equals(selectedBuildingId);
            if (ImGui.selectable(building.getName() + "##row", selected)) {
                selectedBuildingId = building.getId();
            }

            ImGui.sameLine();
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr(
                "plugin.building.overview_item",
                String.format("%.1f", building.computeArea()),
                building.getFloors(),
                building.isRectangular()
                    ? PlotI18n.tr("plugin.building.shape_rect")
                    : PlotI18n.tr("plugin.building.shape_polygon")));

            if (ImGui.button(PlotI18n.tr("plugin.building.locate"), 60, 0)) {
                locateBuilding(building);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.building.delete"), 60, 0)) {
                pendingDeleteBuildingId = building.getId();
                deleteConfirmPending = true;
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderAdoptTab() {
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.building.adopt_hint"));
        ImGui.spacing();

        if (pickSession.isActive()) {
            int count = pickSession.getAccumulatedCount();
            if (count > 0) {
                ImGui.text(String.format(PlotI18n.tr("plugin.building.footprints_selected"), count));
            }
        } else {
            updateSelectedFootprints();
        }

        if (!selectedFootprints.isEmpty()) {
            ImGui.text(String.format(
                PlotI18n.tr("plugin.building.footprints_selected"),
                selectedFootprints.size()));
        } else {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.building.draw_footprint_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.building.pick_footprint"), 0, 0)) {
            startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = selectedFootprints.isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.adopt_footprint"), 0, 0)) {
            adoptSelectedFootprints();
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderEditTab() {
        BuildingFootprint building = project.getBuilding(selectedBuildingId);
        if (building == null) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.building.select_building_hint"));
            renderBuildingSelector();
            return;
        }

        renderBuildingSelector();
        ImGui.separator();

        buildingNameBuffer.set(building.getName());
        if (ImGui.inputText(PlotI18n.tr("plugin.building.building_name"), buildingNameBuffer)) {
            projectHistory.push(project);
            building.setName(buildingNameBuffer.get());
        }

        int[] floors = {building.getFloors()};
        if (ImGui.sliderInt("##floors", floors, 1, 32, PlotI18n.tr("plugin.building.floors", floors[0]))) {
            projectHistory.push(project);
            building.setFloors(floors[0]);
        }

        int[] floorHeight = {building.getFloorHeight()};
        if (ImGui.sliderInt("##floor_height", floorHeight, 2, 16,
            PlotI18n.tr("plugin.building.floor_height", floorHeight[0]))) {
            projectHistory.push(project);
            building.setFloorHeight(floorHeight[0]);
        }

        int[] wallThickness = {building.getWallThickness()};
        if (ImGui.sliderInt("##wall_thickness", wallThickness, 1, 8,
            PlotI18n.tr("plugin.building.wall_thickness", wallThickness[0]))) {
            projectHistory.push(project);
            building.setWallThickness(wallThickness[0]);
        }

        renderMaterialButton(PlotI18n.tr("plugin.building.wall_material"), building.getWallMaterial(),
            blockId -> {
                projectHistory.push(project);
                building.setWallMaterial(blockId);
            });
        renderMaterialButton(PlotI18n.tr("plugin.building.floor_material"), building.getFloorMaterial(),
            blockId -> {
                projectHistory.push(project);
                building.setFloorMaterial(blockId);
            });
        renderMaterialButton(PlotI18n.tr("plugin.building.roof_material"), building.getRoofMaterial(),
            blockId -> {
                projectHistory.push(project);
                building.setRoofMaterial(blockId);
            });
        renderMaterialButton(PlotI18n.tr("plugin.building.foundation_material"),
            building.getFoundationFillMaterial(),
            blockId -> {
                projectHistory.push(project);
                building.setFoundationFillMaterial(blockId);
            });

        renderRoofTypeSelector(building);
        if (building.getRoofType() != BuildingFootprint.RoofType.FLAT) {
            int[] pitch = {building.getRoofPitchRatio()};
            if (ImGui.sliderInt("##roof_pitch", pitch, 1, 16,
                PlotI18n.tr("plugin.building.roof_pitch", pitch[0]))) {
                projectHistory.push(project);
                building.setRoofPitchRatio(pitch[0]);
            }
        }

        manualElevationRef.set(building.getManualBaseElevation() != null);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.manual_elevation"), manualElevationRef)) {
            projectHistory.push(project);
            if (manualElevationRef.get()) {
                building.setManualBaseElevation(64);
            } else {
                building.setManualBaseElevation(null);
            }
        }
        if (manualElevationRef.get()) {
            int initial = building.getManualBaseElevation() != null ? building.getManualBaseElevation() : 64;
            int[] elevation = {initial};
            if (ImGui.sliderInt("##base_elevation", elevation, -64, 320, "Y=%d")) {
                projectHistory.push(project);
                building.setManualBaseElevation(elevation[0]);
            }
        }

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.window_settings"));
        int[] windowSpacing = {building.getWindowSpacing()};
        if (ImGui.sliderInt("##window_spacing", windowSpacing, 0, 32,
            PlotI18n.tr("plugin.building.window_spacing", windowSpacing[0]))) {
            projectHistory.push(project);
            building.setWindowSpacing(windowSpacing[0]);
        }
        int[] windowWidth = {building.getWindowWidth()};
        if (ImGui.sliderInt("##window_width", windowWidth, 1, 4,
            PlotI18n.tr("plugin.building.window_width", windowWidth[0]))) {
            projectHistory.push(project);
            building.setWindowWidth(windowWidth[0]);
        }
        int[] windowHeight = {building.getWindowHeight()};
        if (ImGui.sliderInt("##window_height", windowHeight, 1, 6,
            PlotI18n.tr("plugin.building.window_height", windowHeight[0]))) {
            projectHistory.push(project);
            building.setWindowHeight(windowHeight[0]);
        }
        int[] windowSill = {building.getWindowSillHeight()};
        if (ImGui.sliderInt("##window_sill", windowSill, 0, 8,
            PlotI18n.tr("plugin.building.window_sill", windowSill[0]))) {
            projectHistory.push(project);
            building.setWindowSillHeight(windowSill[0]);
        }

        renderDoorEditor(building);
    }

    private void renderRoofTypeSelector(BuildingFootprint building) {
        String[] labels = {
            PlotI18n.tr("plugin.building.roof_flat"),
            PlotI18n.tr("plugin.building.roof_gable"),
            PlotI18n.tr("plugin.building.roof_hip")
        };
        ImInt roofTypeIndex = new ImInt(building.getRoofType().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.building.roof_type"), roofTypeIndex, labels)) {
            projectHistory.push(project);
            building.setRoofType(BuildingFootprint.RoofType.values()[roofTypeIndex.get()]);
        }
        if (!building.isRectangular()) {
            ImGui.textColored((int) 0xFFFFA060FFL, PlotI18n.tr("plugin.building.roof_rect_hint"));
        }
    }

    private void renderDoorEditor(BuildingFootprint building) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.door_settings"));
        List<BuildingFootprint.DoorOpening> doors = new ArrayList<>(building.getDoors());
        for (int i = 0; i < doors.size(); i++) {
            BuildingFootprint.DoorOpening door = doors.get(i);
            ImGui.pushID("door_" + i);
            ImGui.text(String.format(PlotI18n.tr("plugin.building.door_item"),
                door.wallSegmentIndex, door.positionRatio, door.floor + 1));
            if (ImGui.button(PlotI18n.tr("plugin.building.remove_door"))) {
                projectHistory.push(project);
                building.removeDoor(i);
            }
            ImGui.popID();
        }

        int segmentCount = building.getOuterPoints().size();
        int[] wallSegment = {0};
        float[] positionRatio = {0.5f};
        int[] floor = {0};
        ImGui.sliderInt(PlotI18n.tr("plugin.building.door_wall"), wallSegment, 0, Math.max(0, segmentCount - 1));
        ImGui.sliderFloat(PlotI18n.tr("plugin.building.door_position"), positionRatio, 0.0f, 1.0f);
        ImGui.sliderInt(PlotI18n.tr("plugin.building.door_floor"), floor, 0, Math.max(0, building.getFloors() - 1));
        if (ImGui.button(PlotI18n.tr("plugin.building.add_door"))) {
            projectHistory.push(project);
            building.addDoor(new BuildingFootprint.DoorOpening(
                wallSegment[0], positionRatio[0], floor[0], 1, 2));
        }
    }

    private void renderBuildingSelector() {
        if (project.getBuildingCount() == 0) {
            return;
        }
        String[] labels = project.getBuildings().values().stream()
            .map(BuildingFootprint::getName)
            .toArray(String[]::new);
        String[] ids = project.getBuildings().keySet().toArray(String[]::new);
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(selectedBuildingId)) {
                current = i;
                break;
            }
        }
        ImInt buildingIndex = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.building.select_building"), buildingIndex, labels)) {
            selectedBuildingId = ids[buildingIndex.get()];
        }
    }

    private void renderGenerateTab() {
        BuildingFootprint building = project.getBuilding(selectedBuildingId);
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasBuilding = building != null;

        if (!hasBuilding) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.building.select_building_hint"));
            renderBuildingSelector();
            return;
        }

        renderBuildingSelector();
        ImGui.spacing();

        if (ImGui.button(PlotI18n.tr("plugin.building.calc_preview"), half, 0)) {
            calculatePreview(building);
        }
        ImGui.sameLine();
        boolean hasPreview = lastGenerationResult != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_preview"), half, 0)) {
            clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (ImGui.button(PlotI18n.tr("plugin.building.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (calculatePreview(building)) {
                buildConfirmPending = true;
            }
        }

        BlockProjectionHandler.PlacementReadiness buildReadiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
        if (!buildReadiness.ready()) {
            ImGui.textColored((int) 0xFFFF8080FFL, buildReadiness.message());
        }

        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.building.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.building.calc_results"));
            ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", lastGenerationResult.cutVolume));
            ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", lastGenerationResult.fillVolume));
            ImGui.text(PlotI18n.tr("plugin.building.block_count_result", lastGenerationResult.blockCount));
            ImGui.text(PlotI18n.tr("plugin.building.roof_type_result",
                PlotI18n.tr("plugin.building.roof_" + lastGenerationResult.effectiveRoofType.name().toLowerCase())));

            for (String warningKey : lastGenerationResult.warnings) {
                ImGui.textColored((int) 0xFFFFA060FFL, PlotI18n.tr(warningKey));
            }

            boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
            if (!hasPlacements) {
                ImGui.textColored((int) 0xFFFFB060FFL, PlotI18n.tr("plugin.building.generate_empty_result"));
            }

            if (!hasPlacements) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.building.projection_ref"), half, 0)) {
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
            if (ImGui.button(PlotI18n.tr("plugin.building.build"), half, 0)) {
                buildConfirmPending = true;
            }
            if (buildDisabled) {
                ImGui.endDisabled();
            }
            renderBuildConfirmPopup();
        }
    }

    private void renderBuildConfirmPopup() {
        if (buildConfirmPending) {
            ImGui.openPopup("##building_build_confirm");
            buildConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##building_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
            ImGui.text(String.format(PlotI18n.tr("plugin.building.build_confirm"), blockCount));

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
            if (ImGui.button(PlotI18n.tr("plugin.building.build"), 120, 0)) {
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
            ImGui.openPopup("##building_delete_confirm");
            deleteConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##building_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr("plugin.building.delete_confirm"));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.building.delete"), 100, 0)) {
                if (!pendingDeleteBuildingId.isEmpty()) {
                    projectHistory.push(project);
                    project.removeBuilding(pendingDeleteBuildingId);
                    if (pendingDeleteBuildingId.equals(selectedBuildingId)) {
                        selectedBuildingId = project.getBuildings().isEmpty()
                            ? ""
                            : project.getBuildings().keySet().iterator().next();
                    }
                }
                pendingDeleteBuildingId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                pendingDeleteBuildingId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderMaterialButton(String label, String currentBlockId, Consumer<String> onSelected) {
        ImGui.text(label);
        ImGui.sameLine();
        if (ImGui.button(currentBlockId + "##" + label, 0, 0)) {
            openBlockPicker(currentBlockId, onSelected);
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

    private void startPickSession() {
        ToolManager toolManager = ToolManager.getInstance();
        if (toolManager == null) {
            return;
        }
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        selectedFootprints.clear();
        pickSession.begin();
        toolManager.setActiveTool(selectTool);
        AppState.getInstance().setCurrentTool(baseTool);
        projectStatus = PlotI18n.tr("plugin.building.pick_started");
    }

    private void handlePickSessionTick() {
        BuildingFootprintPickSession.Outcome outcome = pickSession.tick(AppState.getInstance());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                selectedFootprints.clear();
                selectedFootprints.addAll(outcome.getFootprints());
                projectStatus = PlotI18n.tr("plugin.building.pick_success", selectedFootprints.size());
            }
            case NEED_SELECTION -> projectStatus = PlotI18n.tr("plugin.building.pick_need_selection");
            case NO_VALID -> projectStatus = PlotI18n.tr("plugin.building.pick_no_valid");
            case CANCELLED -> projectStatus = PlotI18n.tr("plugin.building.pick_cancelled");
            default -> {
                List<Shape> selected = AppState.getInstance().getSelectedShapes();
                projectStatus = PlotI18n.tr(pickSession.hintKeyForCurrentSelection(selected));
            }
        }
    }

    private void updateSelectedFootprints() {
        selectedFootprints.clear();
        selectedFootprints.addAll(
            BuildingGeometryUtils.findAdoptableFootprints(AppState.getInstance().getSelectedShapes()));
    }

    private void adoptSelectedFootprints() {
        if (selectedFootprints.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.building.adopt_no_selection");
            return;
        }

        projectHistory.push(project);
        int adopted = 0;
        for (Shape shape : selectedFootprints) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            if (points.size() < 3) {
                continue;
            }
            boolean rectangular = shape instanceof RectangleShape
                || BuildingGeometryUtils.detectRectangular(points);
            BuildingFootprint footprint = new BuildingFootprint(points, rectangular);
            footprint.setName(PlotI18n.tr("plugin.building.default_name", adopted + 1));
            project.addBuilding(footprint);
            selectedBuildingId = footprint.getId();
            adopted++;
        }

        selectedFootprints.clear();
        projectStatus = adopted > 1
            ? PlotI18n.tr("plugin.building.adopt_success_batch", adopted)
            : PlotI18n.tr("plugin.building.adopt_success");
    }

    private boolean calculatePreview(BuildingFootprint building) {
        World world = getClientWorld();
        if (world == null || buildingGenerator == null) {
            projectStatus = PlotI18n.tr("plugin.building.generate_world_unavailable");
            return false;
        }

        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        lastGenerationResult = buildingGenerator.generate(building, world);
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.building.generate_empty_result");
            return false;
        }

        projectStatus = PlotI18n.tr("plugin.building.generate_preview_ready");
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
            ghostBlockManager.addGhostBlock(record.pos, record.newBlockId);
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
            projectStatus = PlotI18n.tr("plugin.building.build_no_blocks");
            return;
        }

        BlockProjectionHandler.PlacementReadiness readiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            projectStatus = readiness.message();
            return;
        }

        if (BlockPlacementScheduler.getInstance().isBusy()) {
            projectStatus = PlotI18n.tr("plugin.building.build_in_progress_wait");
            return;
        }

        List<BlockRecord> records = new ArrayList<>(lastGenerationResult.placementRecords.values());
        BuildingGenerateCommand command = new BuildingGenerateCommand(records);
        projectStatus = PlotI18n.tr("plugin.building.build_in_progress", records.size());
        command.executeScheduled(() -> {
            BuildingGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (result != null && result.cancelled()) {
                projectStatus = PlotI18n.tr("plugin.building.build_cancelled", result.success(), result.total());
                return;
            }
            CommandManager.getInstance().pushExecuted(command);
            applyBuildResultStatus(result);
            clearPreview();
        });
    }

    private void applyBuildResultStatus(BuildingGenerateCommand.ExecutionResult result) {
        if (result == null || result.total() == 0) {
            projectStatus = PlotI18n.tr("plugin.building.build_no_blocks");
            return;
        }
        if (result.cancelled()) {
            projectStatus = PlotI18n.tr("plugin.building.build_cancelled", result.success(), result.total());
            return;
        }
        if (result.isFullSuccess()) {
            projectStatus = PlotI18n.tr("plugin.building.build_success", result.success());
            return;
        }
        if (result.isTotalFailure()) {
            projectStatus = PlotI18n.tr("plugin.building.build_failed", result.total());
            return;
        }
        projectStatus = PlotI18n.tr(
            "plugin.building.build_partial",
            result.success(),
            result.total(),
            result.failed());
    }

    private void locateBuilding(BuildingFootprint building) {
        Vec2d centroid = BuildingGeometryUtils.computeCentroid(building.getOuterPoints());
        Canvas canvas = AppState.getInstance().getCanvas();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            selectedBuildingId = building.getId();
            projectStatus = PlotI18n.tr("plugin.building.locate_success", building.getName());
        }
    }

    private void syncSelectedBuildingAfterHistory() {
        if (!selectedBuildingId.isEmpty() && project.getBuilding(selectedBuildingId) == null) {
            selectedBuildingId = project.getBuildings().isEmpty()
                ? ""
                : project.getBuildings().keySet().iterator().next();
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
        projectStatus = PlotI18n.tr("plugin.building.project.loaded", filePath);
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentProjectFile = hashPath(filePath) + ".json";
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.building.project.saved", filePath);
    }

    private void saveCurrentProject() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
        projectStatus = PlotI18n.tr("plugin.building.project.manual_saved");
    }

    private void loadProjectFile(Path file) {
        try {
            project = BuildingProject.loadFrom(file);
            projectHistory.clear();
            selectedBuildingId = project.getBuildings().isEmpty()
                ? ""
                : project.getBuildings().keySet().iterator().next();
        } catch (IOException e) {
            LOGGER.error("加载建筑项目失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.building.project.load_failed", file.getFileName());
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
        projectStatus = PlotI18n.tr("plugin.building.project.default_loaded");
    }

    private void saveProjectFile(Path file) {
        try {
            project.saveTo(file);
        } catch (IOException e) {
            LOGGER.error("保存建筑项目失败: {}", e.getMessage(), e);
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
