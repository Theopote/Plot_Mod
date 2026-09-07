package com.plot.plugin.building.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.preset.BuildingPresetApplier;
import com.plot.plugin.building.preset.BuildingPresetCatalog;
import com.plot.plugin.building.site.BuildingSiteElevationResolver;
import com.plot.plugin.earthwork.design.BuildingPadElevationService;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;

/** 建筑编辑 Tab：单体/批量参数、预设与附属构件。 */
public final class BuildingEditPanel {
    private final BuildingUiContext ctx;

    public BuildingEditPanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        BuildingFootprint building = ctx.selection().primary(ctx.project());
        if (building == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.select_building_hint"));
            BuildingUiWidgets.renderBuildingSelector(ctx);
            return;
        }

        BuildingUiWidgets.renderSelectionSummary(ctx);
        BuildingUiWidgets.renderBuildingSelector(ctx);

        if (!building.getId().equals(ctx.buildingNameEditingId())) {
            ctx.buildingNameBuffer().set(building.getName());
            ctx.setBuildingNameEditingId(building.getId());
        }
        if (ImGui.inputText(PlotI18n.tr("plugin.building.building_name"), ctx.buildingNameBuffer())) {
            building.setName(ctx.buildingNameBuffer().get());
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        if (ctx.selection().size() > 1) {
            renderDistrictMassingPanel(building);
        }

        ImGui.separator();
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.building.basic_massing"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            renderBasicMassing(building);
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.advanced_details"))) {
            renderAdvancedDetails(building);
        }
    }

    /** 片区多选：高度分布、预设、批量套用。 */
    private void renderDistrictMassingPanel(BuildingFootprint primary) {
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.building.district_tools"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        renderPresetSelector(primary);
        ImGui.spacing();
        renderBatchApplyPanel(primary);
        renderHeightDistributionPanel();
    }

    private void renderBasicMassing(BuildingFootprint building) {
        int[] floors = {building.getFloors()};
        boolean floorsChanged = ImGui.sliderInt(
            "##floors", floors, 1, 32, PlotI18n.tr("plugin.building.floors", floors[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (floorsChanged) {
            building.setFloors(floors[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floors");

        int[] floorHeight = {building.getFloorHeight()};
        boolean floorHeightChanged = ImGui.sliderInt("##floor_height", floorHeight, 2, 16,
            PlotI18n.tr("plugin.building.floor_height", floorHeight[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (floorHeightChanged) {
            building.setFloorHeight(floorHeight[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floor_height");

        int[] wallThickness = {building.getWallThickness()};
        boolean wallThicknessChanged = ImGui.sliderInt("##wall_thickness", wallThickness, 1, 8,
            PlotI18n.tr("plugin.building.wall_thickness", wallThickness[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (wallThicknessChanged) {
            building.setWallThickness(wallThickness[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.wall_thickness");

        BuildingUiWidgets.renderMaterialMixButton(ctx, PlotI18n.tr("plugin.building.wall_material"), building.getWallMaterial(),
            mix -> {
                ctx.projectHistory().push(ctx.project());
                building.setWallMaterial(mix);
                ctx.invalidatePreview();
            });

        renderRoofTypeSelector(building);
        if (building.getRoofType() != BuildingFootprint.RoofType.FLAT) {
            int[] pitch = {building.getRoofPitchRatio()};
            boolean pitchChanged = ImGui.sliderInt("##roof_pitch", pitch, 1, 16,
                PlotI18n.tr("plugin.building.roof_pitch", pitch[0]));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (pitchChanged) {
                building.setRoofPitchRatio(pitch[0]);
                ctx.invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.roof_pitch");
        }

        ctx.manualElevationRef().set(building.getManualBaseElevation() != null);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.manual_elevation"), ctx.manualElevationRef())) {
            ctx.projectHistory().push(ctx.project());
            if (ctx.manualElevationRef().get()) {
                building.setManualBaseElevation(64);
            } else {
                building.setManualBaseElevation(null);
            }
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.manual_elevation");
        if (ctx.manualElevationRef().get()) {
            int initial = building.getManualBaseElevation() != null ? building.getManualBaseElevation() : 64;
            int[] elevation = {initial};
            boolean elevationChanged = ImGui.sliderInt("##base_elevation", elevation, -64, 320, "Y=%d");
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (elevationChanged) {
                building.setManualBaseElevation(elevation[0]);
                ctx.invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.base_elevation");
        }
        renderEarthworkPadElevationHint(building);
    }

    private void renderAdvancedDetails(BuildingFootprint building) {
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.window_settings"))) {
            renderWindowSettings(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.door_settings"))) {
            renderDoorEditor(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.facade_materials"))) {
            renderFacadeMaterials(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.floor_plate"))) {
            BuildingUiWidgets.renderMaterialMixButton(ctx, PlotI18n.tr("plugin.building.floor_material"), building.getFloorMaterial(),
                mix -> {
                    ctx.projectHistory().push(ctx.project());
                    building.setFloorMaterial(mix);
                    ctx.invalidatePreview();
                });
        }
        renderAdvancedAccessories(building);
    }

    private void renderFacadeMaterials(BuildingFootprint building) {
        BuildingUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.building.roof_material"), building.getRoofMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                building.setRoofMaterial(blockId);
                ctx.invalidatePreview();
            });
        BuildingUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.building.foundation_material"),
            building.getFoundationFillMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                building.setFoundationFillMaterial(blockId);
                ctx.invalidatePreview();
            });
        UIUtils.renderEngineeringTooltip("hint.plot.building.foundation_material");
    }

    private void renderWindowSettings(BuildingFootprint building) {
        int[] windowSpacing = {building.getWindowSpacing()};
        boolean windowSpacingChanged = ImGui.sliderInt("##window_spacing", windowSpacing, 0, 32,
            PlotI18n.tr("plugin.building.window_spacing", windowSpacing[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowSpacingChanged) {
            building.setWindowSpacing(windowSpacing[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_spacing");
        int[] windowWidth = {building.getWindowWidth()};
        boolean windowWidthChanged = ImGui.sliderInt("##window_width", windowWidth, 1, 4,
            PlotI18n.tr("plugin.building.window_width", windowWidth[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowWidthChanged) {
            building.setWindowWidth(windowWidth[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_width");
        int[] windowHeight = {building.getWindowHeight()};
        boolean windowHeightChanged = ImGui.sliderInt("##window_height", windowHeight, 1, 6,
            PlotI18n.tr("plugin.building.window_height", windowHeight[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowHeightChanged) {
            building.setWindowHeight(windowHeight[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_height");
        int[] windowSill = {building.getWindowSillHeight()};
        boolean windowSillChanged = ImGui.sliderInt("##window_sill", windowSill, 0, 8,
            PlotI18n.tr("plugin.building.window_sill", windowSill[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowSillChanged) {
            building.setWindowSillHeight(windowSill[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_sill");
    }

    private void renderAdvancedAccessories(BuildingFootprint building) {
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.parapet_enabled"))) {
            renderParapetSettings(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.balcony_enabled"))) {
            renderBalconySettings(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.canopy_enabled"))) {
            renderCanopySettings(building);
        }
    }
    private void renderBatchApplyPanel(BuildingFootprint primary) {
        int count = ctx.selection().size();
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.batch_edit_hint", count, primary.getName()));

        ImBoolean floors = new ImBoolean(ctx.batchFieldMask().floors);
        ImBoolean floorHeight = new ImBoolean(ctx.batchFieldMask().floorHeight);
        ImBoolean wall = new ImBoolean(ctx.batchFieldMask().wallThickness);
        ImBoolean materials = new ImBoolean(ctx.batchFieldMask().materials);
        ImBoolean roof = new ImBoolean(ctx.batchFieldMask().roof);
        ImBoolean windows = new ImBoolean(ctx.batchFieldMask().windows);

        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_floors"), floors)) {
            ctx.batchFieldMask().floors = floors.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_floor_height"), floorHeight)) {
            ctx.batchFieldMask().floorHeight = floorHeight.get();
        }
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_wall"), wall)) {
            ctx.batchFieldMask().wallThickness = wall.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_materials"), materials)) {
            ctx.batchFieldMask().materials = materials.get();
        }
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_roof"), roof)) {
            ctx.batchFieldMask().roof = roof.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_windows"), windows)) {
            ctx.batchFieldMask().windows = windows.get();
        }

        boolean applyDisabled = !ctx.batchFieldMask().anyEnabled();
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
        int count = ctx.selection().size();
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.height_distribution_hint", count));

        BuildingHeightDistribution.Mode[] modes = BuildingHeightDistribution.Mode.values();
        String[] labels = new String[modes.length];
        int current = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.building.height_mode." + modes[i].name().toLowerCase());
            if (modes[i] == ctx.heightDistMode()) {
                current = i;
            }
        }
        ImInt modeIndex = new ImInt(current);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##height_dist_mode", modeIndex, labels)) {
            int picked = modeIndex.get();
            if (picked >= 0 && picked < modes.length) {
                ctx.setHeightDistMode(modes[picked]);
            }
        }

        if (ctx.heightDistMode() == BuildingHeightDistribution.Mode.UNIFORM) {
            int[] floors = {ctx.heightDistMaxFloors()};
            if (ImGui.sliderInt(
                    "##height_dist_uniform",
                    floors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.floors", floors[0]))) {
                ctx.setHeightDistMinFloors(floors[0]);
                ctx.setHeightDistMaxFloors(floors[0]);
            }
        } else {
            int[] minFloors = {ctx.heightDistMinFloors()};
            int[] maxFloors = {ctx.heightDistMaxFloors()};
            if (ImGui.sliderInt(
                    "##height_dist_min",
                    minFloors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.height_min_floors", minFloors[0]))) {
                ctx.setHeightDistMinFloors(minFloors[0]);
                if (ctx.heightDistMaxFloors() < ctx.heightDistMinFloors()) {
                    ctx.setHeightDistMaxFloors(ctx.heightDistMinFloors());
                }
            }
            if (ImGui.sliderInt(
                    "##height_dist_max",
                    maxFloors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.height_max_floors", maxFloors[0]))) {
                ctx.setHeightDistMaxFloors(maxFloors[0]);
                if (ctx.heightDistMinFloors() > ctx.heightDistMaxFloors()) {
                    ctx.setHeightDistMinFloors(ctx.heightDistMaxFloors());
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
        List<BuildingFootprint> targets = ctx.selection().resolve(ctx.project());
        if (targets.isEmpty()) {
            return;
        }
        ctx.projectHistory().push(ctx.project());
        BuildingHeightDistribution.Settings settings = BuildingHeightDistribution.Settings.of(
            ctx.heightDistMode(),
            ctx.heightDistMinFloors(),
            ctx.heightDistMaxFloors());
        BuildingHeightDistribution.ApplyResult result =
            BuildingHeightDistribution.apply(targets, settings);
        ctx.invalidatePreview();
        ctx.setProjectStatus(PlotI18n.tr(
            "plugin.building.height_distribution_applied",
            result.updated(),
            PlotI18n.tr("plugin.building.height_mode." + ctx.heightDistMode().name().toLowerCase())));
    }
    private void applyMassingToSelected(BuildingFootprint primary) {
        List<BuildingFootprint> targets = ctx.selection().resolve(ctx.project());
        if (targets.isEmpty()) {
            return;
        }
        ctx.projectHistory().push(ctx.project());
        BuildingBatchEditor.ApplyResult result =
            BuildingBatchEditor.apply(primary, targets, ctx.batchFieldMask());
        ctx.invalidatePreview();
        ctx.setProjectStatus(PlotI18n.tr("plugin.building.batch_apply_success", result.updated()));
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

        int selectedCount = ctx.selection().size();
        float buttonWidth = selectedCount > 1
            ? (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f
            : ImGui.getContentRegionAvailX();

        if (ImGui.button(PlotI18n.tr("plugin.building.apply_preset"), buttonWidth, 0)) {
            int picked = presetIndex.get();
            if (picked >= 0 && picked < ids.length) {
                ctx.projectHistory().push(ctx.project());
                BuildingPresetApplier.apply(ids[picked], building);
                ctx.invalidatePreview();
                ctx.setProjectStatus(PlotI18n.tr(
                    "plugin.building.preset_applied",
                    PlotI18n.tr("preset.building." + ids[picked])));
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
                    ctx.projectHistory().push(ctx.project());
                    BuildingBatchEditor.ApplyResult result =
                        BuildingBatchEditor.applyPreset(ids[picked], ctx.selection().resolve(ctx.project()));
                    ctx.invalidatePreview();
                    ctx.setProjectStatus(PlotI18n.tr(
                        "plugin.building.preset_applied_batch",
                        PlotI18n.tr("preset.building." + ids[picked]),
                        result.updated()));
                }
            }
        }
    }
    private void renderParapetSettings(BuildingFootprint building) {
        ImBoolean parapetRef = new ImBoolean(building.isParapetEnabled());
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.parapet_enabled"), parapetRef)) {
            ctx.projectHistory().push(ctx.project());
            building.setParapetEnabled(parapetRef.get());
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.parapet");
        if (parapetRef.get()) {
            int[] parapetHeight = {building.getParapetHeight()};
            if (ImGui.sliderInt("##parapet_height", parapetHeight, 1, 8,
                PlotI18n.tr("plugin.building.parapet_height", parapetHeight[0]))) {
                if (ImGui.isItemActivated()) {
                    ctx.projectHistory().push(ctx.project());
                }
                building.setParapetHeight(parapetHeight[0]);
                ctx.invalidatePreview();
            }
        }
    }

    private void renderBalconySettings(BuildingFootprint building) {
        boolean hasBalcony = !building.getBalconies().isEmpty();
        ImBoolean balconyRef = new ImBoolean(hasBalcony);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.balcony_enabled"), balconyRef)) {
            ctx.projectHistory().push(ctx.project());
            if (balconyRef.get() && !hasBalcony) {
                building.addBalcony(new BuildingFootprint.Balcony(1, 0.5, 1, 3, 2, null, null));
            } else if (!balconyRef.get()) {
                building.setBalconies(List.of());
            }
            ctx.invalidatePreview();
        }
        if (!balconyRef.get() || building.getBalconies().isEmpty()) {
            return;
        }
        BuildingFootprint.Balcony balcony = building.getBalconies().getFirst();
        int segmentCount = building.getOuterPoints().size();
        int[] wallSegment = {balcony.wallSegmentIndex};
        float[] positionRatio = {(float) balcony.positionRatio};
        int[] floor = {balcony.floor};
        int[] width = {balcony.width};
        int[] depth = {balcony.depth};
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
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
            ctx.invalidatePreview();
        }
    }

    private void renderCanopySettings(BuildingFootprint building) {
        boolean hasCanopy = !building.getCanopies().isEmpty();
        ImBoolean canopyRef = new ImBoolean(hasCanopy);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.canopy_enabled"), canopyRef)) {
            ctx.projectHistory().push(ctx.project());
            if (canopyRef.get() && !hasCanopy) {
                List<OpeningSpec> doors = building.doorOpenings();
                int wall = doors.isEmpty() ? 0 : doors.getFirst().wallSegmentIndex();
                double ratio = doors.isEmpty() ? 0.5 : doors.getFirst().positionRatio();
                building.addCanopy(new BuildingFootprint.Canopy(wall, ratio, 0, 3, 2, 3, null));
            } else if (!canopyRef.get()) {
                building.setCanopies(List.of());
            }
            ctx.invalidatePreview();
        }
    }

    static void renderEarthworkPadElevationHint(BuildingFootprint building) {
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
                ctx.projectHistory().push(ctx.project());
                building.setRoofType(roofTypes[index]);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.roof_type");
        if (!building.isSlopedRoofEligible()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.roof_rect_hint"));
        }
    }
    private void renderDoorEditor(BuildingFootprint building) {
        List<OpeningSpec> doors = building.doorOpenings();
        for (int i = 0; i < doors.size(); i++) {
            OpeningSpec door = doors.get(i);
            ImGui.pushID("door_" + i);
            ImGui.text(String.format(PlotI18n.tr("plugin.building.door_item"),
                door.wallSegmentIndex(), door.positionRatio(), door.floor() + 1));
            if (ImGui.button(PlotI18n.tr("plugin.building.remove_door"))) {
                ctx.projectHistory().push(ctx.project());
                building.removeDoorOpening(i);
                ctx.invalidatePreview();
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
            ctx.projectHistory().push(ctx.project());
            building.addOpening(OpeningSpec.door(
                wallSegment[0], positionRatio[0], floor[0], 1, 2));
            ctx.invalidatePreview();
        }
    }
}
