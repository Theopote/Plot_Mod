package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.GhostBlockOwners;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.PatternGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.PatternGenerator;
import com.plot.plugin.pattern.PatternGeometryUtils;
import com.plot.plugin.pattern.PatternRegionPickSession;
import com.plot.plugin.pattern.image.PatternImageFilePicker;
import com.plot.plugin.pattern.image.PatternImageStore;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.image.PatternPresetImageStore;
import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.pattern.model.PatternPresetLibrary;
import com.plot.ui.canvas.Canvas;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * 图案插件应用层动作：认领、预览、生成、持久化。
 */
public final class PatternActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PatternActions");

    private final PluginContext host;
    private final PatternPluginState state;
    private final Object projectLock;
    private PatternGenerator patternGenerator;
    private Path pluginDataDir;

    public PatternActions(PluginContext host, PatternPluginState state, Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
    }

    public void setPluginDataDir(Path pluginDataDir) {
        this.pluginDataDir = pluginDataDir;
    }

    public void setPatternGenerator(PatternGenerator patternGenerator) {
        this.patternGenerator = patternGenerator;
    }

    public PatternGenerator patternGenerator() {
        return patternGenerator;
    }

    public void syncSelectedFootprintAfterHistory() {
        state.getSelection().retainExisting(state.getProject());
        state.setFootprintNameEditingId("");
    }

    public void resetAfterProjectLoad() {
        state.setFootprintNameEditingId("");
        state.getPickSession().cancel();
        state.getSelectedRegions().clear();
        clearPreview();
    }

    public boolean calculatePreview(PatternFootprint footprint) {
        return calculatePreview(List.of(footprint), true);
    }

    public boolean calculatePreview(List<PatternFootprint> footprints, boolean autoProjectGhosts) {
        World world = getClientWorld();
        if (world == null || patternGenerator == null) {
            clearPreview();
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.generate_world_unavailable"));
            return false;
        }
        if (footprints == null || footprints.isEmpty()) {
            clearPreview();
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.select_footprint_hint"));
            return false;
        }

        state.setProjectStatus(PlotI18n.tr("plugin.pattern.generate_in_progress"));
        PatternGenerationResult merged = new PatternGenerationResult();
        try {
            for (PatternFootprint footprint : footprints) {
                PatternGenerationResult result = patternGenerator.generate(footprint, world);
                merged.mergeFrom(result);
            }
        } catch (Exception e) {
            LOGGER.error("铺装预览生成失败: {}", e.getMessage(), e);
            clearPreview();
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.generate_empty_result"));
            return false;
        }

        state.setLastGenerationResult(merged);
        if (!merged.hasPlacements()) {
            clearPreview();
            state.setProjectStatus(statusForIssue(merged.getIssue()));
            return false;
        }

        if (autoProjectGhosts) {
            projectPreview();
        }
        state.setProjectStatus(formatPreviewReadyStatus(merged));
        return true;
    }

    private static String statusForIssue(PatternGenerationIssue issue) {
        if (issue == null || issue == PatternGenerationIssue.NONE || issue.statusKey().isBlank()) {
            return PlotI18n.tr("plugin.pattern.generate_empty_result");
        }
        return PlotI18n.tr(issue.statusKey());
    }

    private static String formatPreviewReadyStatus(PatternGenerationResult result) {
        String message = PlotI18n.tr("plugin.pattern.generate_preview_ready", result.getBlockCount());
        if (result.getFallbackElevationCount() > 0) {
            message += " — " + PlotI18n.tr(
                "plugin.pattern.preview_fallback_elevation",
                result.getFallbackElevationCount(),
                result.getBlockCount());
        }
        if (result.getSkippedOverlapCount() > 0) {
            message += " — " + PlotI18n.tr(
                "plugin.pattern.preview_overlap_skipped",
                result.getSkippedOverlapCount());
        }
        return message;
    }

    public void projectPreview() {
        PatternGenerationResult lastGenerationResult = state.getLastGenerationResult();
        if (lastGenerationResult == null) {
            return;
        }
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            return;
        }
        LinkedHashMap<net.minecraft.util.math.BlockPos, String> ghosts =
            new LinkedHashMap<>(lastGenerationResult.placementRecords.size());
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            ghosts.put(record.pos, record.newBlockId);
        }
        ghostBlockManager.replaceGhostBlocks(GhostBlockOwners.PATTERN, ghosts);
    }

    public void clearPreview() {
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearGhostBlocks(GhostBlockOwners.PATTERN);
        }
        state.setLastGenerationResult(null);
    }

    public void invalidatePreview() {
        if (state.getLastGenerationResult() != null) {
            clearPreview();
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preview_invalidated"));
        }
    }

    public void buildInWorld() {
        PatternGenerationResult resultSnapshot;
        synchronized (projectLock) {
            PatternGenerationResult lastGenerationResult = state.getLastGenerationResult();
            if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
                state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_no_blocks"));
                return;
            }
            resultSnapshot = lastGenerationResult;
        }

        if (resultSnapshot.exceedsFallbackBuildThreshold()) {
            state.setProjectStatus(PlotI18n.tr(
                "plugin.pattern.build_fallback_elevation_blocked",
                resultSnapshot.getFallbackElevationCount(),
                resultSnapshot.getBlockCount()));
            return;
        }

        com.plot.api.world.PlacementReadiness readiness =
            host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            state.setProjectStatus(readiness.message());
            return;
        }

        if (host.placement().isBusy()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_in_progress_wait"));
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        PatternGenerateCommand command = new PatternGenerateCommand(records, host.projection(), host.placement());
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_in_progress", records.size()));
        command.executeScheduled(() -> {
            PatternGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (command.hasAppliedRecords()) {
                host.commands().pushExecuted(command);
            }
            applyBuildResultStatus(result);
            clearPreview();
        });
    }

    public void applyBuildResultStatus(PatternGenerateCommand.ExecutionResult result) {
        if (result == null || result.total() == 0) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_no_blocks"));
            return;
        }
        if (result.cancelled()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_cancelled", result.success(), result.total()));
            return;
        }
        if (result.isFullSuccess()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_success", result.success()));
            return;
        }
        if (result.isTotalFailure()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.build_failed", result.total()));
            return;
        }
        state.setProjectStatus(PlotI18n.tr(
            "plugin.pattern.build_partial",
            result.success(),
            result.total(),
            result.failed()));
    }

    public void locateFootprint(PatternFootprint footprint) {
        Vec2d centroid = footprint.computeCentroid();
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            state.getSelection().select(footprint.getId(), false);
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.locate_success", footprint.getName()));
        }
    }

    public void startPickSession() {
        ToolManager toolManager = host.tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        state.getSelectedRegions().clear();
        state.getPickSession().begin();
        toolManager.setActiveTool(selectTool);
        host.appState().setCurrentTool(baseTool);
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.pick_started"));
    }

    public void cancelPickSession() {
        state.getPickSession().cancel();
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.pick_cancelled"));
    }

    public void handlePickSessionTick() {
        PatternRegionPickSession.Outcome outcome = state.getPickSession().tick(host.appState());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                state.getSelectedRegions().clear();
                state.getSelectedRegions().addAll(outcome.getRegions());
                adoptSelectedRegions();
            }
            case NEED_SELECTION -> state.setProjectStatus(PlotI18n.tr("plugin.pattern.pick_need_selection"));
            case NO_VALID -> state.setProjectStatus(PlotI18n.tr("plugin.pattern.pick_no_valid"));
            case CANCELLED -> state.setProjectStatus(PlotI18n.tr("plugin.pattern.pick_cancelled"));
            default -> {
                List<Shape> selected = host.appState().getSelectedShapes();
                state.setProjectStatus(PlotI18n.tr(state.getPickSession().hintKeyForCurrentSelection(selected)));
            }
        }
    }

    public void updateSelectedRegions() {
        state.getSelectedRegions().clear();
        state.getSelectedRegions().addAll(
            PatternGeometryUtils.findAdoptableRegions(host.appState().getSelectedShapes()));
    }

    public void adoptSelectedRegions() {
        if (state.getSelectedRegions().isEmpty()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.adopt_no_selection"));
            return;
        }

        int skippedSmall = 0;
        for (Shape shape : state.getSelectedRegions()) {
            List<Vec2d> points = PatternGeometryUtils.extractRegionPoints(shape);
            if (points.size() < 3
                || Math.abs(com.plot.core.geometry.PolygonRegionUtils.signedAreaOfRing(points))
                    < PatternGeometryUtils.MIN_ADOPT_AREA_SQ) {
                skippedSmall++;
            }
        }

        List<PatternGeometryUtils.AdoptedRegionGroup> groups =
            PatternGeometryUtils.groupAdoptableRegionsWithHoles(state.getSelectedRegions());
        if (groups.isEmpty()) {
            if (skippedSmall > 0) {
                state.setProjectStatus(PlotI18n.tr("plugin.pattern.adopt_area_too_small"));
            } else {
                state.setProjectStatus(PlotI18n.tr("plugin.pattern.adopt_no_selection"));
            }
            return;
        }

        state.getProjectHistory().push(state.getProject());
        int adopted = 0;
        int holeCount = 0;
        boolean overlapWarning = false;
        List<String> adoptedIds = new ArrayList<>();
        for (PatternGeometryUtils.AdoptedRegionGroup group : groups) {
            List<Vec2d> points = group.outerPoints();
            if (PatternGeometryUtils.overlapsExistingFootprint(points, state.getProject())) {
                overlapWarning = true;
            }
            PatternFootprint footprint = new PatternFootprint(points);
            footprint.setHoles(group.holes());
            footprint.setName(nextDefaultFootprintName());
            state.getProject().addFootprint(footprint);
            adoptedIds.add(footprint.getId());
            holeCount += group.holes().size();
            adopted++;
        }

        state.getSelectedRegions().clear();
        if (adopted > 0) {
            state.getSelection().selectAll(adoptedIds);
            clearPreview();
        }
        state.setProjectStatus(resolveAdoptStatus(adopted, holeCount, overlapWarning, skippedSmall));
    }

    private String nextDefaultFootprintName() {
        int number = 1;
        while (footprintNameExists(PlotI18n.tr("plugin.pattern.default_name", number))) {
            number++;
        }
        return PlotI18n.tr("plugin.pattern.default_name", number);
    }

    private boolean footprintNameExists(String candidate) {
        for (PatternFootprint existing : state.getProject().getFootprints().values()) {
            if (candidate.equals(existing.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String resolveAdoptStatus(
            int adopted,
            int holeCount,
            boolean overlapWarning,
            int skippedSmall) {
        if (overlapWarning) {
            return PlotI18n.tr("plugin.pattern.adopt_overlap_warning", adopted);
        }
        if (adopted == 1 && holeCount > 0) {
            return PlotI18n.tr("plugin.pattern.adopt_success_with_holes", holeCount);
        }
        if (adopted > 1 && holeCount > 0) {
            return PlotI18n.tr("plugin.pattern.adopt_success_batch_with_holes", adopted, holeCount);
        }
        if (adopted > 1) {
            return PlotI18n.tr("plugin.pattern.adopt_success_batch", adopted);
        }
        if (skippedSmall > 0) {
            return PlotI18n.tr("plugin.pattern.adopt_success");
        }
        return PlotI18n.tr("plugin.pattern.adopt_success");
    }

    public void importImageForFootprint(PatternFootprint footprint) {
        if (footprint == null || pluginDataDir == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.import_image_failed", "plugin data unavailable"));
            return;
        }
        String footprintId = footprint.getId();
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.import_image_waiting"));
        java.util.Optional<java.nio.file.Path> optional = PatternImageFilePicker.pickImage(
            PlotI18n.tr("plugin.pattern.import_image_dialog_title"));
        applyImportedImage(footprintId, optional);
    }

    private void applyImportedImage(String footprintId, java.util.Optional<java.nio.file.Path> optional) {
        if (optional.isEmpty()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.import_image_cancelled"));
            return;
        }
        PatternFootprint footprint = state.getProject().getFootprint(footprintId);
        if (footprint == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.import_image_failed", "region not found"));
            return;
        }
        try {
            state.getProjectHistory().push(state.getProject());
            ImagePatternConfig imagePattern = footprint.getImagePattern();
            PatternImageStore.ImportedImage imported = PatternImageStore.importImage(
                pluginDataDir,
                footprintId,
                optional.get());
            imported.applyTo(imagePattern);
            footprint.setImagePattern(imagePattern);
            footprint.setSource(PatternSource.IMAGE);
            invalidatePreview();
            state.setProjectStatus(PlotI18n.tr(
                "plugin.pattern.import_image_success",
                imported.width(),
                imported.height()));
        } catch (IOException e) {
            LOGGER.error("导入图片失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr(
                "plugin.pattern.import_image_failed",
                e.getMessage()));
        }
    }

    public void clearFootprintHoles(PatternFootprint footprint) {
        if (footprint == null || footprint.getHoles().isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        footprint.setHoles(List.of());
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.geometry_holes_cleared"));
    }

    public void removeFootprintHole(PatternFootprint footprint, int holeIndex) {
        if (footprint == null) {
            return;
        }
        List<List<Vec2d>> holes = footprint.getHoles();
        if (holeIndex < 0 || holeIndex >= holes.size()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        List<List<Vec2d>> updated = new ArrayList<>(holes);
        updated.remove(holeIndex);
        footprint.setHoles(updated);
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.geometry_hole_removed"));
    }

    public void deleteFootprints(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        for (String id : ids) {
            PatternFootprint footprint = state.getProject().getFootprints().get(id);
            if (footprint != null
                && footprint.getSource() == PatternSource.IMAGE
                && pluginDataDir != null) {
                ImagePatternConfig imagePattern = footprint.getImagePattern();
                if (imagePattern != null && imagePattern.hasImage()) {
                    PatternImageStore.deleteImage(pluginDataDir, imagePattern.getImagePath());
                }
            }
            state.getProject().removeFootprint(id);
            state.getSelection().remove(id);
        }
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.delete_success", ids.size()));
    }

    public void onProjectLoaded(String filePath, Path projectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = projectsDir.resolve(targetFile);
        if (loadProjectFile(file)) {
            state.setCurrentProjectFile(targetFile);
        }
    }

    public void onProjectSaved(String filePath, Path projectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        state.setCurrentProjectFile(ProjectPathResolver.sidecarFileName(filePath));
        if (saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()))) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.project.saved", filePath));
        }
    }

    public void persistProject(Path projectsDir) {
        saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
    }

    public boolean loadProjectFile(Path file) {
        try {
            PatternProject loaded = PatternProject.loadFrom(file);
            state.setProject(loaded);
            state.getProjectHistory().clear();
            state.getSelection().clear();
            if (!state.getProject().getFootprints().isEmpty()) {
                state.getSelection().select(state.getProject().getFootprints().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载图案项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.project.load_failed", file.getFileName()));
            return false;
        }
    }

    public void loadProjectForCurrentProject(Path projectsDir, String defaultProjectFile) {
        Project current = host.appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath(), projectsDir);
            return;
        }
        Path file = projectsDir.resolve(defaultProjectFile);
        if (loadProjectFile(file)) {
            state.setCurrentProjectFile(defaultProjectFile);
        }
    }

    public boolean saveProjectFile(Path file) {
        if (file == null || state.getProject() == null) {
            return false;
        }
        try {
            String json = state.getProject().toJson();
            if (state.getContentFingerprint().isUnchanged(json, file)) {
                return true;
            }
            state.getProject().saveTo(file);
            state.getContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存图案项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.project.save_failed", file.getFileName()));
            return false;
        }
    }

    public World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

    // 预设管理功能
    public PatternPresetLibrary getPresetLibrary() {
        return state.getPresetLibrary();
    }

    public void saveAsPreset(PatternFootprint footprint, String name) {
        if (footprint == null || name == null || name.isBlank()) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_save_failed", "无效的名称"));
            return;
        }

        PatternPresetLibrary library = state.getPresetLibrary();
        if (library == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_library_unavailable"));
            return;
        }

        PatternPreset preset = PatternPreset.fromFootprint(footprint, name);
        if (preset == null) {
            return;
        }
        if (preset.getSource() == PatternSource.IMAGE) {
            ImagePatternConfig imageConfig = preset.getImageConfig();
            if (imageConfig != null && imageConfig.hasImage()) {
                try {
                    String assetPath = PatternPresetImageStore.copyFootprintImageToPresetAsset(
                        library.getPluginDataDir(),
                        preset.getId(),
                        imageConfig);
                    imageConfig.setImagePath(assetPath);
                    preset.setImageConfig(imageConfig);
                } catch (IOException e) {
                    state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_save_failed", e.getMessage()));
                    return;
                }
            }
        }
        library.addPreset(preset);
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_saved", name));
    }

    public void loadPreset(PatternPreset preset, PatternFootprint footprint) {
        if (preset == null || footprint == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_load_failed", "无效的预设"));
            return;
        }

        state.getProjectHistory().push(state.getProject());
        preset.applyToFootprint(footprint);
        preset.updateLastUsed();
        
        PatternPresetLibrary library = state.getPresetLibrary();
        if (library != null) {
            library.usePreset(preset.getId());
        }
        
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_loaded", preset.getDisplayName()));
    }

    public void deletePreset(String presetId) {
        PatternPresetLibrary library = state.getPresetLibrary();
        if (library == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_library_unavailable"));
            return;
        }

        PatternPreset preset = library.getPreset(presetId);
        if (preset != null && !preset.isBuiltIn()) {
            library.deletePreset(presetId);
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_deleted", preset.getName()));
        } else {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_delete_builtin"));
        }
    }

    public void updatePreset(PatternPreset preset) {
        PatternPresetLibrary library = state.getPresetLibrary();
        if (library == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_library_unavailable"));
            return;
        }

        if (preset != null && !preset.isBuiltIn()) {
            library.updatePreset(preset);
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_updated", preset.getName()));
        } else {
            state.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_update_builtin"));
        }
    }
}
