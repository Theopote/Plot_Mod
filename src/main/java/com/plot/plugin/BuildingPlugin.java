package com.plot.plugin;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.command.commands.BuildingGenerateCommand;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.event.EventListener;
import com.plot.api.world.IBlockProjectionService;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingFootprintPickSession;
import com.plot.plugin.building.BuildingFootprintValidator;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.BuildingProjectHistory;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.preset.BuildingPresetApplier;
import com.plot.plugin.building.preset.BuildingPresetCatalog;
import com.plot.plugin.building.site.BuildingSiteElevationResolver;
import com.plot.plugin.earthwork.design.BuildingPadElevationService;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.component.UIUtils;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTreeNodeFlags;
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

public class BuildingPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingPlugin");
    private static final String DEFAULT_PROJECT_FILE = "default.json";

    private final Object projectLock = new Object();

    private BuildingProject project = new BuildingProject();
    private final BuildingProjectHistory projectHistory = new BuildingProjectHistory();
    private BuildingGenerator buildingGenerator;

    private final BuildingFootprintPickSession pickSession = new BuildingFootprintPickSession();
    private final List<Shape> selectedFootprints = new ArrayList<>();
    /** Overview / Edit / Generate 共用的已认领建筑多选集 */
    private final BuildingSelectionSet selection = new BuildingSelectionSet();
    private volatile String projectStatus = "";
    private String currentProjectFile = DEFAULT_PROJECT_FILE;

    private volatile BuildingGenerationResult lastGenerationResult;
    private volatile DistrictGenerationResult lastDistrictResult;
    /** 最近一次片区落地报告（清除预览后仍保留） */
    private volatile DistrictBuildReport lastDistrictBuildReport;
    private String buildingNameEditingId = "";
    private final List<String> pendingDeleteBuildingIds = new ArrayList<>();
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;
    /** 最近一次成功保存的内容指纹，避免 onDeactivate + onDisable 重复写盘 */
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    private final ImBoolean manualElevationRef = new ImBoolean(false);
    private final ImString buildingNameBuffer = new ImString(64);
    private BuildingListHelper.SortMode buildingSortMode = BuildingListHelper.SortMode.INSERTION;
    /** Phase B：Apply to Selected 字段开关 */
    private final BuildingBatchEditor.FieldMask batchFieldMask = BuildingBatchEditor.FieldMask.allMassing();
    /** Phase E：高度分布 */
    private BuildingHeightDistribution.Mode heightDistMode = BuildingHeightDistribution.Mode.RANDOM;
    private int heightDistMinFloors = 4;
    private int heightDistMaxFloors = 8;

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
            buildingGenerator = new BuildingGenerator(ctx().coordinates(), ctx().projection());
        } catch (Exception e) {
            LOGGER.error("初始化建筑生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("建筑插件初始化失败", e);
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
        persistProject();
        pickSession.cancel();

        try {
            ctx().events().unsubscribeOwner(this);
        } catch (Exception e) {
            LOGGER.error("取消事件订阅失败: {}", e.getMessage(), e);
        }
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
        if (ImGui.button(PlotI18n.tr("plugin.building.undo"), buttonWidth, 0)) {
            project = projectHistory.undo(project);
            syncSelectedBuildingAfterHistory();
            buildingNameEditingId = "";
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
        if (ImGui.button(PlotI18n.tr("plugin.building.redo"), buttonWidth, 0)) {
            project = projectHistory.redo(project);
            syncSelectedBuildingAfterHistory();
            buildingNameEditingId = "";
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
                PlotI18n.tr("plugin.building.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.building.build_in_progress_hint"));
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
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.no_buildings"));
            return;
        }

        selection.retainExisting(project);
        renderSelectionSummary();

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.building.select_all"), buttonWidth, 0)) {
            selection.selectAll(project.getBuildings().keySet());
        }
        ImGui.sameLine();
        boolean clearDisabled = selection.isEmpty();
        if (clearDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_selection"), buttonWidth, 0)) {
            selection.clear();
        }
        if (clearDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean deleteDisabled = selection.isEmpty();
        if (deleteDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.delete_selected"), buttonWidth, 0)) {
            pendingDeleteBuildingIds.clear();
            pendingDeleteBuildingIds.addAll(selection.ids());
            deleteConfirmPending = true;
        }
        if (deleteDisabled) {
            ImGui.endDisabled();
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.multi_select_hint"));

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##building_sort", buildingSortMode.label())) {
            for (BuildingListHelper.SortMode mode : BuildingListHelper.SortMode.values()) {
                boolean selected = mode == buildingSortMode;
                if (ImGui.selectable(mode.label(), selected)) {
                    buildingSortMode = mode;
                }
            }
            ImGui.endCombo();
        }

        ImGui.beginChild("building_overview_list", 0, 220, true);
        for (BuildingFootprint building : BuildingListHelper.sorted(project, buildingSortMode)) {
            ImGui.pushID(building.getId());
            boolean selected = selection.contains(building.getId());
            if (ImGui.selectable(building.getName() + "##row", selected)) {
                selection.select(building.getId(), ImGui.getIO().getKeyCtrl());
            }

            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.overview_item",
                String.format("%.1f", building.computeArea()),
                building.getFloors(),
                building.isSlopedRoofEligible()
                    ? PlotI18n.tr("plugin.building.shape_rect")
                    : PlotI18n.tr("plugin.building.shape_polygon")));

            if (ImGui.button(PlotI18n.tr("plugin.building.locate"), 60, 0)) {
                locateBuilding(building);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.building.delete"), 60, 0)) {
                pendingDeleteBuildingIds.clear();
                pendingDeleteBuildingIds.add(building.getId());
                deleteConfirmPending = true;
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderSelectionSummary() {
        if (selection.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.selection_empty"));
            return;
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.selection_summary",
            selection.size(),
            String.format("%.1f", selection.totalArea(project))));
        BuildingFootprint primary = selection.primary(project);
        if (primary != null && selection.size() > 1) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.selection_primary", primary.getName()));
        }
    }

    private void renderAdoptTab() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.adopt_hint"));
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
            ImGui.text(PlotI18n.tr(
                "plugin.building.footprints_selected_detail",
                selectedFootprints.size(),
                String.format("%.1f", computeSelectedFootprintArea())));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.draw_footprint_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.building.select_all_closed"), 0, 0)) {
            selectAllClosedShapesOnCanvas();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.building.pick_footprint"), 0, 0)) {
            startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = selectedFootprints.isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        String adoptLabel = selectedFootprints.size() > 1
            ? PlotI18n.tr("plugin.building.adopt_footprint_batch", selectedFootprints.size())
            : PlotI18n.tr("plugin.building.adopt_footprint");
        if (ImGui.button(adoptLabel, 0, 0)) {
            adoptSelectedFootprints();
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderEditTab() {
        selection.retainExisting(project);
        BuildingFootprint building = selection.primary(project);
        if (building == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.select_building_hint"));
            renderBuildingSelector();
            return;
        }

        renderSelectionSummary();
        renderBuildingSelector();
        if (selection.size() > 1) {
            renderBatchApplyPanel(building);
            renderHeightDistributionPanel();
        }
        ImGui.separator();

        if (!building.getId().equals(buildingNameEditingId)) {
            buildingNameBuffer.set(building.getName());
            buildingNameEditingId = building.getId();
        }
        if (ImGui.inputText(PlotI18n.tr("plugin.building.building_name"), buildingNameBuffer)) {
            building.setName(buildingNameBuffer.get());
        }
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }

        renderPresetSelector(building);

        int[] floors = {building.getFloors()};
        boolean floorsChanged = ImGui.sliderInt(
            "##floors", floors, 1, 32, PlotI18n.tr("plugin.building.floors", floors[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (floorsChanged) {
            building.setFloors(floors[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floors");

        int[] floorHeight = {building.getFloorHeight()};
        boolean floorHeightChanged = ImGui.sliderInt("##floor_height", floorHeight, 2, 16,
            PlotI18n.tr("plugin.building.floor_height", floorHeight[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (floorHeightChanged) {
            building.setFloorHeight(floorHeight[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floor_height");

        int[] wallThickness = {building.getWallThickness()};
        boolean wallThicknessChanged = ImGui.sliderInt("##wall_thickness", wallThickness, 1, 8,
            PlotI18n.tr("plugin.building.wall_thickness", wallThickness[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (wallThicknessChanged) {
            building.setWallThickness(wallThickness[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.wall_thickness");

        renderMaterialMixButton(PlotI18n.tr("plugin.building.wall_material"), building.getWallMaterial(),
            mix -> {
                projectHistory.push(project);
                building.setWallMaterial(mix);
                invalidatePreview();
            });
        renderMaterialMixButton(PlotI18n.tr("plugin.building.floor_material"), building.getFloorMaterial(),
            mix -> {
                projectHistory.push(project);
                building.setFloorMaterial(mix);
                invalidatePreview();
            });
        renderMaterialButton(PlotI18n.tr("plugin.building.roof_material"), building.getRoofMaterial(),
            blockId -> {
                projectHistory.push(project);
                building.setRoofMaterial(blockId);
                invalidatePreview();
            });
        renderMaterialButton(PlotI18n.tr("plugin.building.foundation_material"),
            building.getFoundationFillMaterial(),
            blockId -> {
                projectHistory.push(project);
                building.setFoundationFillMaterial(blockId);
                invalidatePreview();
            });
        UIUtils.renderEngineeringTooltip("hint.plot.building.foundation_material");

        renderRoofTypeSelector(building);
        if (building.getRoofType() != BuildingFootprint.RoofType.FLAT) {
            int[] pitch = {building.getRoofPitchRatio()};
            boolean pitchChanged = ImGui.sliderInt("##roof_pitch", pitch, 1, 16,
                PlotI18n.tr("plugin.building.roof_pitch", pitch[0]));
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
            }
            if (pitchChanged) {
                building.setRoofPitchRatio(pitch[0]);
                invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.roof_pitch");
        }

        manualElevationRef.set(building.getManualBaseElevation() != null);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.manual_elevation"), manualElevationRef)) {
            projectHistory.push(project);
            if (manualElevationRef.get()) {
                building.setManualBaseElevation(64);
            } else {
                building.setManualBaseElevation(null);
            }
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.manual_elevation");
        if (manualElevationRef.get()) {
            int initial = building.getManualBaseElevation() != null ? building.getManualBaseElevation() : 64;
            int[] elevation = {initial};
            boolean elevationChanged = ImGui.sliderInt("##base_elevation", elevation, -64, 320, "Y=%d");
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
            }
            if (elevationChanged) {
                building.setManualBaseElevation(elevation[0]);
                invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.base_elevation");
        }
        renderEarthworkPadElevationHint(building);
        renderAccessorySettings(building);

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.window_settings"));
        int[] windowSpacing = {building.getWindowSpacing()};
        boolean windowSpacingChanged = ImGui.sliderInt("##window_spacing", windowSpacing, 0, 32,
            PlotI18n.tr("plugin.building.window_spacing", windowSpacing[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (windowSpacingChanged) {
            building.setWindowSpacing(windowSpacing[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_spacing");
        int[] windowWidth = {building.getWindowWidth()};
        boolean windowWidthChanged = ImGui.sliderInt("##window_width", windowWidth, 1, 4,
            PlotI18n.tr("plugin.building.window_width", windowWidth[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (windowWidthChanged) {
            building.setWindowWidth(windowWidth[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_width");
        int[] windowHeight = {building.getWindowHeight()};
        boolean windowHeightChanged = ImGui.sliderInt("##window_height", windowHeight, 1, 6,
            PlotI18n.tr("plugin.building.window_height", windowHeight[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (windowHeightChanged) {
            building.setWindowHeight(windowHeight[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_height");
        int[] windowSill = {building.getWindowSillHeight()};
        boolean windowSillChanged = ImGui.sliderInt("##window_sill", windowSill, 0, 8,
            PlotI18n.tr("plugin.building.window_sill", windowSill[0]));
        if (ImGui.isItemActivated()) {
            projectHistory.push(project);
        }
        if (windowSillChanged) {
            building.setWindowSillHeight(windowSill[0]);
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_sill");

        renderDoorEditor(building);
    }

    private void renderBatchApplyPanel(BuildingFootprint primary) {
        int count = selection.size();
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.building.batch_edit"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.batch_edit_hint", count, primary.getName()));

        ImBoolean floors = new ImBoolean(batchFieldMask.floors);
        ImBoolean floorHeight = new ImBoolean(batchFieldMask.floorHeight);
        ImBoolean wall = new ImBoolean(batchFieldMask.wallThickness);
        ImBoolean materials = new ImBoolean(batchFieldMask.materials);
        ImBoolean roof = new ImBoolean(batchFieldMask.roof);
        ImBoolean windows = new ImBoolean(batchFieldMask.windows);

        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_floors"), floors)) {
            batchFieldMask.floors = floors.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_floor_height"), floorHeight)) {
            batchFieldMask.floorHeight = floorHeight.get();
        }
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_wall"), wall)) {
            batchFieldMask.wallThickness = wall.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_materials"), materials)) {
            batchFieldMask.materials = materials.get();
        }
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_roof"), roof)) {
            batchFieldMask.roof = roof.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_windows"), windows)) {
            batchFieldMask.windows = windows.get();
        }

        boolean applyDisabled = !batchFieldMask.anyEnabled();
        if (applyDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.apply_to_selected", count),
                ImGui.getContentRegionAvailX(),
                0)) {
            applyMassingToSelected(primary);
        }
        if (applyDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderHeightDistributionPanel() {
        int count = selection.size();
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.building.height_distribution"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.height_distribution_hint", count));

        BuildingHeightDistribution.Mode[] modes = BuildingHeightDistribution.Mode.values();
        String[] labels = new String[modes.length];
        int current = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.building.height_mode." + modes[i].name().toLowerCase());
            if (modes[i] == heightDistMode) {
                current = i;
            }
        }
        ImInt modeIndex = new ImInt(current);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##height_dist_mode", modeIndex, labels)) {
            int picked = modeIndex.get();
            if (picked >= 0 && picked < modes.length) {
                heightDistMode = modes[picked];
            }
        }

        if (heightDistMode == BuildingHeightDistribution.Mode.UNIFORM) {
            int[] floors = {heightDistMaxFloors};
            if (ImGui.sliderInt(
                    "##height_dist_uniform",
                    floors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.floors", floors[0]))) {
                heightDistMinFloors = floors[0];
                heightDistMaxFloors = floors[0];
            }
        } else {
            int[] minFloors = {heightDistMinFloors};
            int[] maxFloors = {heightDistMaxFloors};
            if (ImGui.sliderInt(
                    "##height_dist_min",
                    minFloors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.height_min_floors", minFloors[0]))) {
                heightDistMinFloors = minFloors[0];
                if (heightDistMaxFloors < heightDistMinFloors) {
                    heightDistMaxFloors = heightDistMinFloors;
                }
            }
            if (ImGui.sliderInt(
                    "##height_dist_max",
                    maxFloors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.height_max_floors", maxFloors[0]))) {
                heightDistMaxFloors = maxFloors[0];
                if (heightDistMinFloors > heightDistMaxFloors) {
                    heightDistMinFloors = heightDistMaxFloors;
                }
            }
        }

        if (ImGui.button(
                PlotI18n.tr("plugin.building.apply_height_distribution", count),
                ImGui.getContentRegionAvailX(),
                0)) {
            applyHeightDistribution();
        }
    }

    private void applyHeightDistribution() {
        List<BuildingFootprint> targets = selection.resolve(project);
        if (targets.isEmpty()) {
            return;
        }
        projectHistory.push(project);
        BuildingHeightDistribution.Settings settings = BuildingHeightDistribution.Settings.of(
            heightDistMode,
            heightDistMinFloors,
            heightDistMaxFloors);
        BuildingHeightDistribution.ApplyResult result =
            BuildingHeightDistribution.apply(targets, settings);
        invalidatePreview();
        projectStatus = PlotI18n.tr(
            "plugin.building.height_distribution_applied",
            result.updated(),
            PlotI18n.tr("plugin.building.height_mode." + heightDistMode.name().toLowerCase()));
    }

    private void applyMassingToSelected(BuildingFootprint primary) {
        List<BuildingFootprint> targets = selection.resolve(project);
        if (targets.isEmpty()) {
            return;
        }
        projectHistory.push(project);
        BuildingBatchEditor.ApplyResult result =
            BuildingBatchEditor.apply(primary, targets, batchFieldMask);
        invalidatePreview();
        projectStatus = PlotI18n.tr("plugin.building.batch_apply_success", result.updated());
    }

    private void renderPresetSelector(BuildingFootprint building) {
        List<BuildingPresetCatalog.BuildingPreset> presets = BuildingPresetCatalog.all();
        String[] labels = presets.stream()
            .map(p -> PlotI18n.tr("preset.building." + p.id()))
            .toArray(String[]::new);
        String[] ids = presets.stream()
            .map(BuildingPresetCatalog.BuildingPreset::id)
            .toArray(String[]::new);

        int currentIndex = 0;
        String currentPreset = building.getPresetId();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(currentPreset)) {
                currentIndex = i;
                break;
            }
        }

        ImGui.text(PlotI18n.tr("plugin.building.preset_section"));
        ImInt presetIndex = new ImInt(currentIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##building_preset", presetIndex, labels)) {
            // selection only; apply on button
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.preset");

        if (!currentPreset.isBlank()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.preset_active", PlotI18n.tr("preset.building." + currentPreset)));
        }

        int selectedCount = selection.size();
        float buttonWidth = selectedCount > 1
            ? (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f
            : ImGui.getContentRegionAvailX();

        if (ImGui.button(PlotI18n.tr("plugin.building.apply_preset"), buttonWidth, 0)) {
            int picked = presetIndex.get();
            if (picked >= 0 && picked < ids.length) {
                projectHistory.push(project);
                BuildingPresetApplier.apply(ids[picked], building);
                invalidatePreview();
                projectStatus = PlotI18n.tr(
                    "plugin.building.preset_applied",
                    PlotI18n.tr("preset.building." + ids[picked]));
            }
        }

        if (selectedCount > 1) {
            ImGui.sameLine();
            if (ImGui.button(
                    PlotI18n.tr("plugin.building.apply_preset_to_selected", selectedCount),
                    buttonWidth,
                    0)) {
                int picked = presetIndex.get();
                if (picked >= 0 && picked < ids.length) {
                    projectHistory.push(project);
                    BuildingBatchEditor.ApplyResult result =
                        BuildingBatchEditor.applyPreset(ids[picked], selection.resolve(project));
                    invalidatePreview();
                    projectStatus = PlotI18n.tr(
                        "plugin.building.preset_applied_batch",
                        PlotI18n.tr("preset.building." + ids[picked]),
                        result.updated());
                }
            }
        }
    }

    private void renderAccessorySettings(BuildingFootprint building) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.accessory_settings"));

        ImBoolean parapetRef = new ImBoolean(building.isParapetEnabled());
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.parapet_enabled"), parapetRef)) {
            projectHistory.push(project);
            building.setParapetEnabled(parapetRef.get());
            invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.parapet");
        if (parapetRef.get()) {
            int[] parapetHeight = {building.getParapetHeight()};
            if (ImGui.sliderInt("##parapet_height", parapetHeight, 1, 8,
                PlotI18n.tr("plugin.building.parapet_height", parapetHeight[0]))) {
                if (ImGui.isItemActivated()) {
                    projectHistory.push(project);
                }
                building.setParapetHeight(parapetHeight[0]);
                invalidatePreview();
            }
        }

        boolean hasBalcony = !building.getBalconies().isEmpty();
        ImBoolean balconyRef = new ImBoolean(hasBalcony);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.balcony_enabled"), balconyRef)) {
            projectHistory.push(project);
            if (balconyRef.get() && !hasBalcony) {
                building.addBalcony(new BuildingFootprint.Balcony(1, 0.5, 1, 3, 2, null, null));
            } else if (!balconyRef.get()) {
                building.setBalconies(List.of());
            }
            invalidatePreview();
        }
        if (balconyRef.get() && !building.getBalconies().isEmpty()) {
            BuildingFootprint.Balcony balcony = building.getBalconies().getFirst();
            int segmentCount = building.getOuterPoints().size();
            int[] wallSegment = {balcony.wallSegmentIndex};
            float[] positionRatio = {(float) balcony.positionRatio};
            int[] floor = {balcony.floor};
            int[] width = {balcony.width};
            int[] depth = {balcony.depth};
            if (ImGui.isItemActivated()) {
                projectHistory.push(project);
            }
            boolean wallChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.building.door_wall"), wallSegment, 0, Math.max(0, segmentCount - 1));
            boolean posChanged = ImGui.sliderFloat(
                PlotI18n.tr("plugin.building.door_position"), positionRatio, 0.0f, 1.0f);
            boolean floorChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.building.door_floor"), floor, 0, Math.max(0, building.getFloors() - 1));
            boolean widthChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.building.balcony_width"), width, 1, 8);
            boolean depthChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.building.balcony_depth"), depth, 1, 4);
            if (wallChanged || posChanged || floorChanged || widthChanged || depthChanged) {
                building.setBalconies(List.of(new BuildingFootprint.Balcony(
                    wallSegment[0], positionRatio[0], floor[0], width[0], depth[0],
                    balcony.slabMaterial, balcony.railingMaterial)));
                invalidatePreview();
            }
        }

        boolean hasCanopy = !building.getCanopies().isEmpty();
        ImBoolean canopyRef = new ImBoolean(hasCanopy);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.canopy_enabled"), canopyRef)) {
            projectHistory.push(project);
            if (canopyRef.get() && !hasCanopy) {
                List<OpeningSpec> doors = building.doorOpenings();
                int wall = doors.isEmpty() ? 0 : doors.getFirst().wallSegmentIndex();
                double ratio = doors.isEmpty() ? 0.5 : doors.getFirst().positionRatio();
                building.addCanopy(new BuildingFootprint.Canopy(wall, ratio, 0, 3, 2, 3, null));
            } else if (!canopyRef.get()) {
                building.setCanopies(List.of());
            }
            invalidatePreview();
        }
    }

    private void renderEarthworkPadElevationHint(BuildingFootprint building) {
        BuildingPadElevationService.PadElevationStatus status =
            BuildingSiteElevationResolver.describePadLink(building);
        if (!status.isLinked()) {
            return;
        }
        boolean manual = building.getManualBaseElevation() != null;
        switch (status.mode()) {
            case EARTHWORK_OWNED -> {
                if (manual) {
                    if (status.resolvedElevation() != null) {
                        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                            "plugin.building.manual_overrides_earthwork_pad",
                            status.resolvedElevation()));
                    } else {
                        ImGui.textColored(PluginUiColors.WARNING,
                            PlotI18n.tr("plugin.building.manual_overrides_earthwork_pad_pending"));
                    }
                } else if (status.resolvedElevation() != null) {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                        "plugin.building.earthwork_pad_controls_base",
                        status.resolvedElevation(),
                        status.zoneName()));
                } else {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                        "plugin.building.earthwork_pad_linked_pending",
                        status.zoneName()));
                }
            }
            case BUILDING_LINKED -> ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.earthwork_pad_follows_building",
                status.zoneName()));
            default -> {
            }
        }
    }

    private void renderRoofTypeSelector(BuildingFootprint building) {
        BuildingFootprint.RoofType[] roofTypes = BuildingFootprint.RoofType.values();
        String[] labels = {
            PlotI18n.tr("plugin.building.roof_flat"),
            PlotI18n.tr("plugin.building.roof_gable"),
            PlotI18n.tr("plugin.building.roof_hip")
        };
        ImInt roofTypeIndex = new ImInt(building.getRoofType().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.building.roof_type"), roofTypeIndex, labels)) {
            int index = roofTypeIndex.get();
            if (index >= 0 && index < roofTypes.length) {
                projectHistory.push(project);
                building.setRoofType(roofTypes[index]);
                invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.roof_type");
        if (!building.isSlopedRoofEligible()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.roof_rect_hint"));
        }
    }

    private void renderDoorEditor(BuildingFootprint building) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.door_settings"));
        List<OpeningSpec> doors = building.doorOpenings();
        for (int i = 0; i < doors.size(); i++) {
            OpeningSpec door = doors.get(i);
            ImGui.pushID("door_" + i);
            ImGui.text(String.format(PlotI18n.tr("plugin.building.door_item"),
                door.wallSegmentIndex(), door.positionRatio(), door.floor() + 1));
            if (ImGui.button(PlotI18n.tr("plugin.building.remove_door"))) {
                projectHistory.push(project);
                building.removeDoorOpening(i);
                invalidatePreview();
            }
            ImGui.popID();
        }

        int segmentCount = building.getOuterPoints().size();
        int[] wallSegment = {0};
        float[] positionRatio = {0.5f};
        int[] floor = {0};
        ImGui.sliderInt(PlotI18n.tr("plugin.building.door_wall"), wallSegment, 0, Math.max(0, segmentCount - 1));
        UIUtils.renderEngineeringTooltip("hint.plot.building.door_wall");
        ImGui.sliderFloat(PlotI18n.tr("plugin.building.door_position"), positionRatio, 0.0f, 1.0f);
        UIUtils.renderEngineeringTooltip("hint.plot.building.door_position");
        ImGui.sliderInt(PlotI18n.tr("plugin.building.door_floor"), floor, 0, Math.max(0, building.getFloors() - 1));
        UIUtils.renderEngineeringTooltip("hint.plot.building.door_floor");
        if (ImGui.button(PlotI18n.tr("plugin.building.add_door"))) {
            projectHistory.push(project);
            building.addOpening(OpeningSpec.door(
                wallSegment[0], positionRatio[0], floor[0], 1, 2));
            invalidatePreview();
        }
    }

    private void renderBuildingSelector() {
        if (project.getBuildingCount() == 0) {
            return;
        }
        List<BuildingFootprint> buildings = BuildingListHelper.sorted(project, buildingSortMode);
        String[] labels = buildings.stream()
            .map(BuildingFootprint::getName)
            .toArray(String[]::new);
        String[] ids = buildings.stream()
            .map(BuildingFootprint::getId)
            .toArray(String[]::new);
        String primaryId = selection.primaryId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(primaryId)) {
                current = i;
                break;
            }
        }
        ImInt buildingIndex = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.building.select_building"), buildingIndex, labels)) {
            selection.select(ids[buildingIndex.get()], false);
        }
    }

    private void renderGenerateTab() {
        selection.retainExisting(project);
        BuildingFootprint building = selection.primary(project);
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasBuilding = building != null;

        if (!hasBuilding) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.select_building_hint"));
            renderBuildingSelector();
            if (lastDistrictBuildReport != null) {
                renderDistrictBuildReport();
            }
            return;
        }

        renderSelectionSummary();
        renderBuildingSelector();
        ImGui.spacing();
        renderEarthworkPadElevationHint(building);

        int selectedCount = selection.size();
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx().projection().checkWorldModificationReadiness();

        if (selectedCount > 1) {
            renderDistrictGenerateActions(selectedCount, half, buildReadiness);
        } else {
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
        }

        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        if (lastDistrictResult != null && lastDistrictResult.buildingsAttempted() > 1) {
            renderDistrictPreviewStats(half, buildReadiness);
        } else if (lastGenerationResult != null) {
            renderSinglePreviewStats(half, buildReadiness);
        }

        if (lastDistrictBuildReport != null) {
            renderDistrictBuildReport();
        }
    }

    private void renderDistrictGenerateActions(
            int selectedCount,
            float half,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        if (ImGui.button(
                PlotI18n.tr("plugin.building.preview_selected", selectedCount),
                half,
                0)) {
            calculateDistrictPreview(selection.resolve(project), true);
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

        if (ImGui.button(
                PlotI18n.tr("plugin.building.preview_all", project.getBuildingCount()),
                ImGui.getContentRegionAvailX(),
                0)) {
            calculateDistrictPreview(new ArrayList<>(project.getBuildings().values()), true);
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.building.district_generate_section"));

        boolean generateDisabled = !buildReadiness.ready() || ctx().placement().isBusy();
        if (generateDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate_selected", selectedCount),
                half,
                0)) {
            requestDistrictGenerate(selection.resolve(project));
        }
        ImGui.sameLine();
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate_all", project.getBuildingCount()),
                half,
                0)) {
            requestDistrictGenerate(new ArrayList<>(project.getBuildings().values()));
        }
        if (generateDisabled) {
            ImGui.endDisabled();
        }
    }

    private void requestDistrictGenerate(List<BuildingFootprint> buildings) {
        if (calculateDistrictPreview(buildings, true)) {
            buildConfirmPending = true;
        }
    }

    private void renderDistrictPreviewStats(
            float half,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        DistrictGenerationResult district = lastDistrictResult;
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.district_preview_results"));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_buildings_result",
            district.buildingsGenerated(),
            district.buildingsAttempted()));
        if (district.buildingsSkipped() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skipped_result",
                district.buildingsSkipped()));
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_area_result",
            String.format("%.1f", district.totalArea())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_volume_result",
            String.format("%.0f", district.totalVolume())));
        ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", district.totalCutVolume()));
        ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", district.totalFillVolume()));
        ImGui.text(PlotI18n.tr("plugin.building.block_count_result", district.totalBlocks()));

        if (district.hasBuildingOverlap()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_overlap_summary",
                district.overlappingBuildingPairs().size(),
                district.conflictingBlockCount()));
            int shown = 0;
            for (var pair : district.overlappingBuildingPairs()) {
                if (shown >= 5) {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                        "plugin.building.district_overlap_more",
                        district.overlappingBuildingPairs().size() - shown));
                    break;
                }
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_overlap_pair",
                    pair.buildingNameA(),
                    pair.buildingNameB()));
                shown++;
            }
        }

        if (district.hasSiteConditionSummary()) {
            ImGui.text(PlotI18n.tr("plugin.building.district_site_conditions"));
            if (district.waterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_water",
                    district.waterSiteCount()));
            }
            if (district.partialWaterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_partial_water",
                    district.partialWaterSiteCount()));
            }
            if (district.steepSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_steep",
                    district.steepSiteCount()));
            }
            if (district.structureConflictBuildingCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_structure_conflict",
                    district.structureConflictBuildingCount()));
            }
            if (district.heavyEarthworkSiteCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_heavy_earthwork",
                    district.heavyEarthworkSiteCount()));
            }
        }

        for (String warningKey : district.warnings()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
        }

        for (DistrictGenerationResult.BuildingOutcome skipped : district.skippedOutcomes()) {
            String reason = skipped.skipReason() != null
                ? PlotI18n.tr(skipped.skipReason().i18nKey())
                : "";
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skip_item",
                skipped.buildingName(),
                reason));
        }

        boolean hasPlacements = district.hasPlacements();
        if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.building.generate_empty_result"));
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
            || ctx().placement().isBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.generate_from_preview"), half, 0)) {
            buildConfirmPending = true;
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderDistrictBuildReport() {
        DistrictBuildReport report = lastDistrictBuildReport;
        if (report == null || !report.isDistrict()) {
            return;
        }
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.district_build_report"));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_buildings_result",
            report.buildingsGenerated(),
            report.buildingsAttempted()));
        if (report.buildingsSkipped() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skipped_result",
                report.buildingsSkipped()));
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_area_result",
            String.format("%.1f", report.totalArea())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_volume_result",
            String.format("%.0f", report.totalVolume())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_placed_result",
            report.placedBlocks(),
            report.plannedBlocks()));
        if (report.overlappingPairCount() > 0 || report.conflictingBlockCount() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_overlap_summary",
                report.overlappingPairCount(),
                report.conflictingBlockCount()));
        }
        if (report.hasSiteConditionSummary()) {
            ImGui.text(PlotI18n.tr("plugin.building.district_site_conditions"));
            if (report.waterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_water",
                    report.waterSiteCount()));
            }
            if (report.partialWaterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_partial_water",
                    report.partialWaterSiteCount()));
            }
            if (report.steepSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_steep",
                    report.steepSiteCount()));
            }
            if (report.structureConflictBuildingCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_structure_conflict",
                    report.structureConflictBuildingCount()));
            }
            if (report.heavyEarthworkSiteCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_heavy_earthwork",
                    report.heavyEarthworkSiteCount()));
            }
        }
        if (report.failedBlocks() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_failed_blocks",
                report.failedBlocks()));
        }
        if (report.cancelled()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.district_build_cancelled"));
        }

        for (DistrictBuildReport.SkipItem skipped : report.skipped()) {
            String reason = skipped.reasonKey().isBlank() ? "" : PlotI18n.tr(skipped.reasonKey());
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skip_item",
                skipped.buildingName(),
                reason));
        }

        if (ImGui.button(PlotI18n.tr("plugin.building.clear_build_report"), 0, 0)) {
            lastDistrictBuildReport = null;
        }
    }

    private void renderSinglePreviewStats(
            float half,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        ImGui.separator();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.preview_projection_hint"));
        ImGui.text(PlotI18n.tr("plugin.building.calc_results"));
        if (lastGenerationResult.sitePreview != null) {
            var site = lastGenerationResult.sitePreview;
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_foundation_elevation",
                site.foundationElevation()));
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_foundation_source",
                PlotI18n.tr("plugin.building.site_source_" + site.source().name().toLowerCase())));
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_terrain_range",
                site.minGroundElevation(),
                site.maxGroundElevation()));
            if (site.waterCoverageRatio() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.site_water_coverage",
                    String.format("%.0f", site.waterCoverageRatio() * 100)));
            }
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_cut_fill",
                site.estimatedCut(),
                site.estimatedFill()));
        }
        ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", lastGenerationResult.cutVolume));
        ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", lastGenerationResult.fillVolume));
        ImGui.text(PlotI18n.tr("plugin.building.block_count_result", lastGenerationResult.blockCount));
        ImGui.text(PlotI18n.tr("plugin.building.roof_type_result",
            PlotI18n.tr("plugin.building.roof_" + lastGenerationResult.effectiveRoofType.name().toLowerCase())));

        for (String warningKey : lastGenerationResult.warnings) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
        }

        boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
        if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.building.generate_empty_result"));
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
            || ctx().placement().isBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.build"), half, 0)) {
            buildConfirmPending = true;
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderBuildConfirmPopup() {
        if (buildConfirmPending) {
            ImGui.openPopup("##building_build_confirm");
            buildConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##building_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
            if (lastDistrictResult != null && lastDistrictResult.buildingsAttempted() > 1) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.build_confirm_district",
                    lastDistrictResult.buildingsGenerated(),
                    blockCount));
            } else {
                ImGui.text(String.format(PlotI18n.tr("plugin.building.build_confirm"), blockCount));
            }

            com.plot.api.world.PlacementReadiness readiness =
                ctx().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            boolean canBuild = readiness.ready() && !ctx().placement().isBusy();
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
            int count = pendingDeleteBuildingIds.size();
            if (count > 1) {
                ImGui.text(PlotI18n.tr("plugin.building.delete_confirm_batch", count));
            } else {
                ImGui.text(PlotI18n.tr("plugin.building.delete_confirm"));
            }
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.building.delete"), 100, 0)) {
                if (!pendingDeleteBuildingIds.isEmpty()) {
                    projectHistory.push(project);
                    for (String id : pendingDeleteBuildingIds) {
                        project.removeBuilding(id);
                        selection.remove(id);
                    }
                    selection.retainExisting(project);
                    clearPreview();
                }
                pendingDeleteBuildingIds.clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                pendingDeleteBuildingIds.clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderMaterialMixButton(
            String label,
            MaterialMix currentMix,
            java.util.function.Consumer<MaterialMix> onSelected) {
        MaterialMix mix = currentMix != null
            ? currentMix
            : MaterialMix.single(BuildingFootprint.DEFAULT_WALL_MATERIAL);
        String displayName = RoadMaterialUtils.getDisplayName(mix.getPrimaryMaterial());
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            displayName += " + " + RoadMaterialUtils.getDisplayName(mix.getAccentMaterial());
        }
        ImGui.text(label);
        ImGui.sameLine();
        if (ImGui.button(displayName + "##" + label, 0, 0)) {
            java.util.List<String> initial = new java.util.ArrayList<>();
            if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
                initial.add(mix.getPrimaryMaterial());
            }
            if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
                initial.add(mix.getAccentMaterial());
            }
            openPalettePicker(initial, blockIds ->
                onSelected.accept(RoadUiWidgets.fromPaletteSelection(blockIds, mix.getAccentRatio())));
        }

        boolean hasAccentMaterial = mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank();
        if (hasAccentMaterial) {
            RoadUiWidgets.renderAccentRatioSlider(mix, onSelected::accept, label, null);
        }
    }

    private void openPalettePicker(java.util.List<String> initialBlockIds, java.util.function.Consumer<java.util.List<String>> onConfirm) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            client.setScreen(BlockConfigNativeScreen.forPaletteSelection(
                client.currentScreen, initialBlockIds, onConfirm));
        });
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
        ToolManager toolManager = ctx().tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        selectedFootprints.clear();
        pickSession.begin();
        toolManager.setActiveTool(selectTool);
        ctx().appState().setCurrentTool(baseTool);
        projectStatus = PlotI18n.tr("plugin.building.pick_started");
    }

    private void handlePickSessionTick() {
        BuildingFootprintPickSession.Outcome outcome = pickSession.tick(ctx().appState());
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
                List<Shape> selected = ctx().appState().getSelectedShapes();
                projectStatus = PlotI18n.tr(pickSession.hintKeyForCurrentSelection(selected));
            }
        }
    }

    private void updateSelectedFootprints() {
        selectedFootprints.clear();
        selectedFootprints.addAll(
            BuildingGeometryUtils.findAdoptableFootprints(ctx().appState().getSelectedShapes()));
    }

    private void selectAllClosedShapesOnCanvas() {
        List<Shape> adoptable = BuildingGeometryUtils.findAdoptableFootprints(ctx().appState().getShapes());
        ctx().appState().setSelectedShapes(new ArrayList<>(adoptable));
        updateSelectedFootprints();
        projectStatus = adoptable.isEmpty()
            ? PlotI18n.tr("plugin.building.pick_no_valid")
            : PlotI18n.tr("plugin.building.select_all_closed_success", adoptable.size());
    }

    private double computeSelectedFootprintArea() {
        double area = 0.0;
        for (Shape shape : selectedFootprints) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            area += Math.abs(BuildingFootprint.signedArea(points));
        }
        return area;
    }

    private void adoptSelectedFootprints() {
        if (selectedFootprints.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.building.adopt_no_selection");
            return;
        }

        projectHistory.push(project);
        int adopted = 0;
        int skipped = 0;
        List<String> adoptedIds = new ArrayList<>();
        List<String> rejectHints = new ArrayList<>();
        for (Shape shape : selectedFootprints) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            BuildingFootprintValidator.Result validation = BuildingFootprintValidator.validate(points);
            if (!validation.valid()) {
                skipped++;
                if (rejectHints.size() < 5 && validation.reason() != null) {
                    rejectHints.add(PlotI18n.tr(validation.reason().i18nKey()));
                }
                continue;
            }
            boolean rectangular = BuildingGeometryUtils.isSlopedRoofEligible(validation.cleanedPoints());
            BuildingFootprint footprint = new BuildingFootprint(validation.cleanedPoints(), rectangular);
            footprint.setName(PlotI18n.tr("plugin.building.default_name", adopted + 1));
            project.addBuilding(footprint);
            adoptedIds.add(footprint.getId());
            adopted++;
        }

        selectedFootprints.clear();
        if (adopted > 0) {
            selection.selectAll(adoptedIds);
            clearPreview();
        }
        if (adopted == 0) {
            projectStatus = skipped > 0
                ? PlotI18n.tr("plugin.building.adopt_all_invalid", skipped)
                : PlotI18n.tr("plugin.building.adopt_no_selection");
        } else if (skipped > 0) {
            projectStatus = PlotI18n.tr("plugin.building.adopt_success_batch_partial", adopted, skipped);
            if (!rejectHints.isEmpty()) {
                projectStatus = projectStatus + " — " + String.join("; ", rejectHints);
            }
        } else if (adopted > 1) {
            projectStatus = PlotI18n.tr("plugin.building.adopt_success_batch", adopted);
        } else {
            projectStatus = PlotI18n.tr("plugin.building.adopt_success");
        }
    }

    private boolean calculatePreview(BuildingFootprint building) {
        return calculateDistrictPreview(List.of(building), false);
    }

    /**
     * @param autoProjectGhosts 片区预览成功后自动投影虚影，便于一眼看到整片体量
     */
    private boolean calculateDistrictPreview(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts) {
        World world = getClientWorld();
        if (world == null || buildingGenerator == null) {
            projectStatus = PlotI18n.tr("plugin.building.generate_world_unavailable");
            return false;
        }
        if (buildings == null || buildings.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.building.select_building_hint");
            return false;
        }

        com.plot.api.world.IGhostBlockService ghostBlockManager = ctx().ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        DistrictGenerationResult district;
        try {
            district = buildingGenerator.generateDistrict(buildings, world);
        } catch (Exception e) {
            LOGGER.error("片区预览生成失败: {}", e.getMessage(), e);
            lastDistrictResult = null;
            lastGenerationResult = null;
            projectStatus = PlotI18n.tr("plugin.building.generate_empty_result");
            return false;
        }

        lastDistrictResult = district;
        lastGenerationResult = district.toMergedResult();
        if (!district.hasPlacements()) {
            projectStatus = PlotI18n.tr("plugin.building.generate_empty_result");
            return false;
        }

        if (autoProjectGhosts) {
            projectPreview();
        }

        if (district.buildingsAttempted() > 1) {
            if (district.buildingsSkipped() > 0) {
                projectStatus = PlotI18n.tr(
                    "plugin.building.district_preview_partial",
                    district.buildingsGenerated(),
                    district.buildingsAttempted(),
                    district.totalBlocks());
            } else {
                projectStatus = PlotI18n.tr(
                    "plugin.building.district_preview_ready",
                    district.buildingsGenerated(),
                    district.totalBlocks());
            }
        } else if (!lastGenerationResult.warnings.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.building.generate_preview_ready")
                + " — "
                + PlotI18n.tr(lastGenerationResult.warnings.getFirst());
        } else {
            projectStatus = PlotI18n.tr("plugin.building.generate_preview_ready");
        }
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
        java.util.LinkedHashMap<net.minecraft.util.math.BlockPos, String> ghosts =
            new java.util.LinkedHashMap<>(lastGenerationResult.placementRecords.size());
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            ghosts.put(record.pos, record.newBlockId);
        }
        ghostBlockManager.addGhostBlocks(ghosts);
    }

    private void clearPreview() {
        com.plot.api.world.IGhostBlockService ghostBlockManager = ctx().ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastGenerationResult = null;
        lastDistrictResult = null;
    }

    /** 参数/工程变更后使预览失效，避免按过期几何落地。 */
    private void invalidatePreview() {
        if (lastGenerationResult != null) {
            clearPreview();
            projectStatus = PlotI18n.tr("plugin.building.preview_invalidated");
        }
    }

    private void buildInWorld() {
        final BuildingGenerationResult resultSnapshot;
        final DistrictGenerationResult districtSnapshot;
        synchronized (projectLock) {
            if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
                projectStatus = PlotI18n.tr("plugin.building.build_no_blocks");
                return;
            }
            resultSnapshot = lastGenerationResult;
            districtSnapshot = lastDistrictResult;
        }

        com.plot.api.world.PlacementReadiness readiness =
            ctx().projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            projectStatus = readiness.message();
            return;
        }

        if (ctx().placement().isBusy()) {
            projectStatus = PlotI18n.tr("plugin.building.build_in_progress_wait");
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        BuildingGenerateCommand command = new BuildingGenerateCommand(records, ctx().projection(), ctx().placement());
        if (districtSnapshot != null && districtSnapshot.buildingsAttempted() > 1) {
            projectStatus = PlotI18n.tr(
                "plugin.building.district_build_in_progress",
                districtSnapshot.buildingsGenerated(),
                records.size());
        } else {
            projectStatus = PlotI18n.tr("plugin.building.build_in_progress", records.size());
        }
        command.executeScheduled(() -> {
            BuildingGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (districtSnapshot != null && districtSnapshot.buildingsAttempted() > 1) {
                lastDistrictBuildReport = DistrictBuildReport.from(districtSnapshot, result);
            }
            // 取消时若已写入部分方块，仍入历史以便撤销半成品
            if (result != null && result.cancelled()) {
                if (result.success() > 0) {
                    ctx().commands().pushExecuted(command);
                }
                applyBuildResultStatus(result, districtSnapshot);
                clearPreview();
                return;
            }
            ctx().commands().pushExecuted(command);
            applyBuildResultStatus(result, districtSnapshot);
            clearPreview();
        });
    }

    private void applyBuildResultStatus(
            BuildingGenerateCommand.ExecutionResult result,
            DistrictGenerationResult district) {
        if (result == null || result.total() == 0) {
            projectStatus = PlotI18n.tr("plugin.building.build_no_blocks");
            return;
        }
        if (district != null && district.buildingsAttempted() > 1) {
            if (result.cancelled()) {
                projectStatus = PlotI18n.tr(
                    "plugin.building.district_build_cancelled_status",
                    result.success(),
                    result.total(),
                    district.buildingsGenerated(),
                    district.buildingsSkipped());
                return;
            }
            if (result.isFullSuccess()) {
                if (district.buildingsSkipped() > 0) {
                    projectStatus = PlotI18n.tr(
                        "plugin.building.district_build_success_partial",
                        district.buildingsGenerated(),
                        district.buildingsAttempted(),
                        result.success(),
                        district.buildingsSkipped());
                } else {
                    projectStatus = PlotI18n.tr(
                        "plugin.building.district_build_success",
                        district.buildingsGenerated(),
                        result.success());
                }
                return;
            }
            if (result.isTotalFailure()) {
                projectStatus = PlotI18n.tr(
                    "plugin.building.district_build_failed",
                    district.buildingsGenerated(),
                    result.total());
                return;
            }
            projectStatus = PlotI18n.tr(
                "plugin.building.district_build_partial",
                district.buildingsGenerated(),
                result.success(),
                result.total(),
                result.failed(),
                district.buildingsSkipped());
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
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            selection.select(building.getId(), false);
            projectStatus = PlotI18n.tr("plugin.building.locate_success", building.getName());
        }
    }

    private void syncSelectedBuildingAfterHistory() {
        selection.retainExisting(project);
        buildingNameEditingId = "";
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
            projectStatus = PlotI18n.tr("plugin.building.project.loaded", filePath);
        }
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentProjectFile = ProjectPathResolver.sidecarFileName(filePath);
        if (saveProjectFile(getProjectsDir().resolve(currentProjectFile))) {
            projectStatus = PlotI18n.tr("plugin.building.project.saved", filePath);
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
            BuildingProject loaded = BuildingProject.loadFrom(file);
            project = loaded;
            projectHistory.clear();
            selection.clear();
            if (!project.getBuildings().isEmpty()) {
                selection.select(project.getBuildings().keySet().iterator().next(), false);
            }
            buildingNameEditingId = "";
            pickSession.cancel();
            selectedFootprints.clear();
            clearPreview();
            lastDistrictBuildReport = null;
            return true;
        } catch (IOException e) {
            LOGGER.error("加载建筑项目失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.building.project.load_failed", file.getFileName());
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
            projectStatus = PlotI18n.tr("plugin.building.project.default_loaded");
        }
    }

    private boolean saveProjectFile(Path file) {
        if (file == null || project == null) {
            return false;
        }
        try {
            String json = project.toJson();
            if (contentFingerprint.isUnchanged(json, file)) {
                LOGGER.debug("建筑项目内容未变，跳过重复保存: {}", file.getFileName());
                return true;
            }
            project.saveTo(file);
            contentFingerprint.markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存建筑项目失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.building.project.save_failed", file.getFileName());
            return false;
        }
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }

    public BuildingFootprint getBuildingFootprint(String id) {
        synchronized (projectLock) {
            return project.getBuilding(id);
        }
    }

    public List<BuildingFootprint> listBuildingFootprints() {
        synchronized (projectLock) {
            return new ArrayList<>(project.getBuildings().values());
        }
    }
}
