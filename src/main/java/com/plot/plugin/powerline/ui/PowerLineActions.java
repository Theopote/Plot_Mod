package com.plot.plugin.powerline.ui;

import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.PowerLineGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.powerline.terrain.TerrainFitService;
import com.plot.api.world.PluginProjectionContext;
import com.plot.api.world.WorldProjectionUnavailableException;
import com.plot.plugin.powerline.PowerLinePathPickSession;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.manager.PowerLinePreviewManager;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.PowerLinePathUtils;
import com.plot.plugin.powerline.path.ClosedLoopLayoutException;
import com.plot.plugin.powerline.path.PowerLinePathLayout;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.parametric.TowerParametricBuildPolicy;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.style.LinePoleDesignOverrides;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 电力线路插件应用层动作。 */
public final class PowerLineActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PowerLineActions");

    private final PluginContext host;
    private final PowerLinePluginState state;
    private final Object projectLock;
    private final PowerLinePreviewManager previewManager;
    private final PowerLinePathPickSession pathPickSession = new PowerLinePathPickSession();
    private String lastPathPickStatusKey = "";
    private PowerLineGenerator generator;

    public PowerLineActions(PluginContext host, PowerLinePluginState state, Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
        this.previewManager = new PowerLinePreviewManager(host, state);
    }

    public PowerLinePreviewManager previewManager() {
        return previewManager;
    }

    public PowerLinePathPickSession pathPickSession() {
        return pathPickSession;
    }

    public void tickPathPickSession() {
        if (!pathPickSession.isActive()) {
            lastPathPickStatusKey = "";
            return;
        }
        PowerLinePathPickSession.Outcome outcome = pathPickSession.tick(host.appState());
        applyPathPickOutcome(outcome);
        if (!pathPickSession.isActive()) {
            lastPathPickStatusKey = "";
            return;
        }
        List<Shape> selected = host.appState().getSelectedShapes();
        String hintKey = pathPickSession.hintKeyForCurrentSelection(selected);
        if (hintKey.equals(lastPathPickStatusKey)) {
            return;
        }
        lastPathPickStatusKey = hintKey;
        if ("status.plot.powerline.pick_path_right_click_multi".equals(hintKey)) {
            state.setProjectStatus(
                PlotI18n.status(hintKey, pathPickSession.getAccumulatedCount()),
                ProjectStatusSeverity.INFO);
        } else {
            state.setProjectStatus(
                PlotI18n.status(hintKey),
                ProjectStatusSeverity.INFO);
        }
    }

    public void setGenerator(PowerLineGenerator generator) {
        this.generator = generator;
    }

    public void updateSelectedPaths() {
        state.setPathSelection(PowerLinePathUtils.analyzeSelection(
            host.appState().getSelectedShapes()));
    }

    public void applyPickedPaths() {
        if (state.getSelection().hasMultipleSelected()) {
            state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.selection.multi_edit_blocked", state.getSelection().size()),
                ProjectStatusSeverity.WARNING);
            return;
        }

        PowerLinePathSelectionAnalysis selection = state.getPathSelection();
        if (!selection.hasCanvasSelection()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.pick_no_selection"),
                ProjectStatusSeverity.WARNING);
            return;
        }
        if (!selection.canAdopt()) {
            state.setProjectStatus(
                selection.rejectedCurves().isEmpty()
                    ? PlotI18n.tr("plugin.powerline.path.pick_no_selection")
                    : PlotI18n.tr("plugin.powerline.path.reject_curve"),
                ProjectStatusSeverity.WARNING);
            return;
        }

        createLinesFromPickedPaths(selection);
    }

    public void confirmPathReplace() {
        if (!state.isPathReplacePending()) {
            return;
        }
        PowerLineFootprint line = state.getProject().getLine(state.getPathReplaceTargetLineId());
        PowerLinePathSelectionAnalysis selection = state.getPathSelection();
        if (line == null) {
            clearPathReplaceFlow();
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.select_line_hint"),
                ProjectStatusSeverity.WARNING);
            return;
        }
        if (!selection.canAdopt() || selection.adoptable().size() != 1) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.replace_select_one"),
                ProjectStatusSeverity.WARNING);
            return;
        }
        if (replaceCurrentLinePath(line, selection.adoptable().getFirst())) {
            clearPathReplaceFlow();
        }
    }

    public void cancelPathReplaceConfirm() {
        clearPathReplaceFlow();
        state.blockPathPickActivation(2);
        state.setProjectStatus(
            PlotI18n.status("status.plot.powerline.pick_path_cancelled"),
            ProjectStatusSeverity.INFO);
    }

    private void clearPathReplaceFlow() {
        state.clearPathReplacePick();
        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
    }

    private void createLinesFromPickedPaths(PowerLinePathSelectionAnalysis selection) {
        int created = 0;
        int skipped = selection.skippedCount();
        List<String> createdIds = new ArrayList<>();
        boolean curveRejected = !selection.rejectedCurves().isEmpty();

        for (Shape shape : selection.adoptable()) {
            if (applyPickedPath(shape, createdIds, created == 0)) {
                created++;
            } else {
                skipped++;
            }
        }

        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
        if (created > 0) {
            state.getSelection().selectAll(createdIds);
            invalidatePreview();
        }
        if (created == 0) {
            state.setProjectStatus(
                curveRejected
                    ? PlotI18n.tr("plugin.powerline.path.reject_curve")
                    : PlotI18n.tr("plugin.powerline.path.pick_no_selection"),
                ProjectStatusSeverity.WARNING);
        } else if (skipped > 0) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.create_success_partial", created, skipped),
                ProjectStatusSeverity.WARNING);
        } else if (created > 1) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.create_success_batch", created),
                ProjectStatusSeverity.SUCCESS);
        } else {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.create_success"),
                ProjectStatusSeverity.SUCCESS);
        }
    }

    private boolean applyPickedPath(Shape shape, List<String> createdLineIds, boolean captureSnapshot) {
        try {
            PowerLineFootprint line = PowerLinePathLayout.adopt(shape, host.coordinates());
            if (captureSnapshot) {
                pushWorkspaceSnapshot();
            }
            line.setName(nextDefaultLineName());
            com.plot.plugin.powerline.style.PowerLineStyleEditor.selectPreset(
                line,
                com.plot.plugin.powerline.style.PowerLineStylePresetCatalog.classicWood());
            state.getProject().addLine(line);
            createdLineIds.add(line.getId());
            return true;
        } catch (IllegalArgumentException | ClosedLoopLayoutException e) {
            return false;
        }
    }

    public boolean replaceCurrentLinePath(PowerLineFootprint line, Shape shape) {
        if (line == null || shape == null) {
            return false;
        }
        if (com.plot.plugin.powerline.path.PowerLinePathAdapters.tryFrom(shape) == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.invalid_selection"),
                ProjectStatusSeverity.WARNING);
            return false;
        }
        pushWorkspaceSnapshot();
        try {
            PowerLinePathLayout.applySnapshot(line, shape, host.coordinates());
            line.clearLayoutConstraints();
            line.clearPoleOverrides();
            invalidatePreview();
            state.getSelection().select(line.getId(), false);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.replace_success"),
                ProjectStatusSeverity.SUCCESS);
            return true;
        } catch (ClosedLoopLayoutException e) {
            discardPushedHistoryEntry();
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.reject_closed_loop"),
                ProjectStatusSeverity.WARNING);
            return false;
        } catch (IllegalArgumentException e) {
            discardPushedHistoryEntry();
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.invalid_selection"),
                ProjectStatusSeverity.WARNING);
            return false;
        }
    }

    private String nextDefaultLineName() {
        int number = 1;
        while (lineNameExists(PlotI18n.tr("plugin.powerline.default_name", number))) {
            number++;
        }
        return PlotI18n.tr("plugin.powerline.default_name", number);
    }

    private boolean lineNameExists(String candidate) {
        for (PowerLineFootprint existing : state.getProject().getLines().values()) {
            if (candidate.equals(existing.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean calculatePreview(PowerLineFootprint line) {
        return calculatePreview(line, false);
    }

    public boolean calculatePreview(PowerLineFootprint line, boolean enableAutoRefresh) {
        if (!calculatePreviewCore(line)) {
            return false;
        }
        applyTerrainFit(line);
        if (enableAutoRefresh) {
            state.setPreviewAutoRefreshEnabled(true);
        }
        return state.getLastGenerationResult() != null;
    }

    private boolean calculatePreviewCore(PowerLineFootprint line) {
        World world = getClientWorld();
        if (world == null || generator == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_world_unavailable"), ProjectStatusSeverity.ERROR);
            return false;
        }
        if (line == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.select_line_hint"), ProjectStatusSeverity.WARNING);
            return false;
        }

        if (PluginProjectionContext.tryCapture(host.coordinates()).isEmpty()) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.projection_unavailable"),
                ProjectStatusSeverity.ERROR);
            return false;
        }

        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        PowerLineGenerationResult result;
        try {
            result = generator.generate(line, terrain, designResolver());
        } catch (WorldProjectionUnavailableException e) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.projection_unavailable"),
                ProjectStatusSeverity.ERROR);
            return false;
        } catch (Exception e) {
            LOGGER.error("电力线路预览生成失败: {}", e.getMessage(), e);
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_empty_result"), ProjectStatusSeverity.WARNING);
            return false;
        }

        if (result.blockCount() == 0) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_empty_result"), ProjectStatusSeverity.WARNING);
            return false;
        }

        state.setLastGenerationResult(result);
        state.setPreviewKey(PowerLinePreviewKey.capture(line, state.getDesignProject(), host.coordinates()));
        state.setBuildRegionWorldFingerprint(
            com.plot.plugin.powerline.placement.BuildRegionWorldFingerprint.capture(
                result,
                host.projection()));
        previewManager.showLinePreview(result);
        return true;
    }

    private void applyTerrainFit(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        World world = getClientWorld();
        PowerLineGenerationResult result = state.getLastGenerationResult();
        if (world == null || result == null) {
            return;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        if (!TerrainFitService.hasTerrainIssues(result.toGeometryModel(), terrain)) {
            return;
        }
        pushWorkspaceSnapshot();
        TerrainFitService.FitResult fit = TerrainFitService.fit(
            line,
            result,
            terrain,
            host.coordinates(),
            () -> {
                if (!calculatePreviewCore(line)) {
                    return null;
                }
                return state.getLastGenerationResult();
            });
        if (fit.hasRemainingIssues()) {
            state.setProjectStatus(
                TerrainFitService.remainingIssuesHint(),
                ProjectStatusSeverity.WARNING);
        }
    }

    public void clearPreview() {
        state.setPreviewAutoRefreshEnabled(false);
        previewManager.clearLineCachedPreview();
    }

    /**
     * 生成参数、线路选择或杆塔设计变更后调用，丢弃过期预览。
     */
    public void invalidatePreview() {
        if (state.getLastGenerationResult() == null && state.getPreviewKey() == null) {
            return;
        }
        if (tryAutoRefreshPreview()) {
            return;
        }
        state.setPreviewAutoRefreshEnabled(false);
        previewManager.clearLineCachedPreview();
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.preview_invalidated"),
            ProjectStatusSeverity.INFO);
    }

    private boolean tryAutoRefreshPreview() {
        if (!state.isPreviewAutoRefreshEnabled()) {
            return false;
        }
        PowerLineFootprint line = state.getSelection().primary(state.getProject());
        if (line == null) {
            return false;
        }
        return refreshPreviewQuietly(line);
    }

    private boolean refreshPreviewQuietly(PowerLineFootprint line) {
        if (!calculatePreviewCore(line)) {
            return false;
        }
        return state.getLastGenerationResult() != null;
    }

    public boolean isPreviewValidFor(PowerLineFootprint line) {
        PowerLinePreviewKey key = state.getPreviewKey();
        PowerLineGenerationResult result = state.getLastGenerationResult();
        if (key == null || result == null || line == null) {
            return false;
        }
        if (result.footprint == null || !line.getId().equals(result.footprint.getId())) {
            return false;
        }
        return key.matches(line, state.getDesignProject(), host.coordinates());
    }

    public boolean hasValidPreview(PowerLineFootprint line) {
        return isPreviewValidFor(line) && state.getLastGenerationResult() != null;
    }

    /**
     * 若缓存预览与当前线路/参数不一致则静默清除（生成页每帧调用）。
     */
    public void syncPreviewValidity(PowerLineFootprint line) {
        if (state.getLastGenerationResult() == null) {
            return;
        }
        if (!isPreviewValidFor(line)) {
            if (tryAutoRefreshPreview()) {
                return;
            }
            clearPreview();
        }
    }

    public boolean isPreviewAutoRefreshEnabled() {
        return state.isPreviewAutoRefreshEnabled();
    }

    public void selectLine(String lineId, boolean multiToggle) {
        if (isLineSelectionFrozen()) {
            return;
        }
        String previousPrimary = state.getSelection().primaryId();
        state.getSelection().select(lineId, multiToggle);
        String newPrimary = state.getSelection().primaryId();
        if (!newPrimary.equals(previousPrimary)) {
            state.setPreviewAutoRefreshEnabled(false);
            invalidatePreview();
        }
    }

    public void selectAll(java.util.Collection<String> ids) {
        if (isLineSelectionFrozen()) {
            return;
        }
        String previousPrimary = state.getSelection().primaryId();
        state.getSelection().selectAll(ids);
        if (!state.getSelection().primaryId().equals(previousPrimary)) {
            state.setPreviewAutoRefreshEnabled(false);
            invalidatePreview();
        }
    }

    public void clearSelection() {
        if (isLineSelectionFrozen()) {
            return;
        }
        if (!state.getSelection().isEmpty()) {
            state.getSelection().clear();
            state.setPreviewAutoRefreshEnabled(false);
            invalidatePreview();
        }
    }

    public void buildInWorld() {
        PowerLineFootprint line = state.getSelection().primary(state.getProject());
        if (!ensurePreviewReadyForBuild(line)) {
            return;
        }

        World world = getClientWorld();
        if (world == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.projection_unavailable"),
                ProjectStatusSeverity.ERROR);
            return;
        }

        PowerLineGenerationResult resultSnapshot;
        int previewWorldFingerprint;
        synchronized (projectLock) {
            PowerLineGenerationResult last = state.getLastGenerationResult();
            if (last == null || last.placementRecords.isEmpty()) {
                state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_no_blocks"), ProjectStatusSeverity.WARNING);
                return;
            }
            resultSnapshot = last;
            previewWorldFingerprint = state.getBuildRegionWorldFingerprint();
        }

        if (!com.plot.plugin.powerline.placement.BuildRegionWorldFingerprint.matches(
                resultSnapshot,
                previewWorldFingerprint,
                host.projection())) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_world_stale"),
                ProjectStatusSeverity.WARNING);
            return;
        }

        if (hasParametricBuildBlocking(line)) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_blocked_parametric"),
                ProjectStatusSeverity.ERROR);
            return;
        }

        com.plot.api.world.PlacementReadiness readiness = host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            state.setProjectStatus(readiness.message(), ProjectStatusSeverity.ERROR);
            return;
        }
        if (host.placement().isBusy()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_in_progress_wait"),
                ProjectStatusSeverity.WARNING);
            return;
        }

        List<BlockRecord> records = com.plot.plugin.powerline.placement.BuildPlacementPreparer
            .prepareExecutionRecords(resultSnapshot.placementRecords.values(), host.projection());
        PowerLineGenerateCommand command = new PowerLineGenerateCommand(records, host.projection(), host.placement());
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.build_in_progress", records.size()),
            ProjectStatusSeverity.INFO);
        command.executeScheduled(() -> PowerLineUiExecutor.runOnClientThread(() -> {
            PowerLineGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (command.hasAppliedRecords()) {
                host.commands().pushExecuted(command);
            }
            if (result != null && result.isFullSuccess()) {
                state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_success", result.success()), ProjectStatusSeverity.SUCCESS);
            } else if (result != null && result.success() > 0) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.powerline.build_partial",
                    result.success(),
                    result.total()), ProjectStatusSeverity.WARNING);
            } else if (result != null && result.cancelled()) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.powerline.build_cancelled",
                    result.success(),
                    result.total()), ProjectStatusSeverity.WARNING);
            }
            clearPreview();
        }));
    }

    private boolean ensurePreviewReadyForBuild(PowerLineFootprint line) {
        syncPreviewValidity(line);
        if (line == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.select_line_hint"), ProjectStatusSeverity.WARNING);
            return false;
        }
        if (!hasValidPreview(line)) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.preview_stale"), ProjectStatusSeverity.WARNING);
            return false;
        }
        return true;
    }

    public boolean requestBuildConfirm(PowerLineFootprint line) {
        return ensurePreviewReadyForBuild(line);
    }

    public void deleteLines(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        pushWorkspaceSnapshot();
        for (String id : ids) {
            state.getProject().removeLine(id);
            state.getSelection().retainExisting(state.getProject());
        }
        invalidatePreview();
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.deleted", ids.size()),
            ProjectStatusSeverity.SUCCESS);
    }

    public PoleDesignResolver designResolver() {
        return new PoleDesignResolver(state.getDesignProject());
    }

    public boolean hasParametricBuildBlocking(PowerLineFootprint line) {
        net.minecraft.world.World world = getClientWorld();
        TerrainSampler terrain = world != null
            ? MinecraftTerrainSampler.of(world, host.coordinates())
            : null;
        return TowerParametricBuildPolicy.hasBlockingIssues(
            line,
            designResolver(),
            terrain,
            host.coordinates());
    }

    public void onProjectLoaded(String filePath, Path projectsDir, Path designProjectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = projectsDir.resolve(targetFile);
        boolean loaded = loadProjectFile(file);
        boolean designsLoaded = loadDesignProjectFile(designProjectsDir.resolve(targetFile));
        if (loaded) {
            state.setCurrentProjectFile(targetFile);
            if (!designsLoaded) {
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.project.loaded_designs_failed", filePath),
                    ProjectStatusSeverity.WARNING);
            }
        }
    }

    public void onProjectSaved(String filePath, Path projectsDir, Path designProjectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        state.setCurrentProjectFile(ProjectPathResolver.sidecarFileName(filePath));
        boolean saved = saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
        boolean designsSaved = saveDesignProjectFile(designProjectsDir.resolve(state.getCurrentProjectFile()));
        if (saved && designsSaved) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.saved", filePath), ProjectStatusSeverity.SUCCESS);
        } else if (saved) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.project.save_designs_failed", filePath),
                ProjectStatusSeverity.WARNING);
        }
    }

    public void persistProject(Path projectsDir, Path designProjectsDir) {
        boolean saved = saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
        boolean designsSaved = saveDesignProjectFile(designProjectsDir.resolve(state.getCurrentProjectFile()));
        if (saved && !designsSaved) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.project.save_designs_failed", state.getCurrentProjectFile()),
                ProjectStatusSeverity.WARNING);
        }
    }

    public boolean loadProjectFile(Path file) {
        try {
            PowerLineProject loaded = PowerLineProject.loadFrom(file);
            state.setProject(loaded);
            state.getProjectHistory().clear();
            state.getSelection().clear();
            if (!state.getProject().getLines().isEmpty()) {
                state.getSelection().select(state.getProject().getLines().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载电力线路项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.load_failed", file.getFileName()), ProjectStatusSeverity.ERROR);
            return false;
        }
    }

    public void loadProjectForCurrentProject(Path projectsDir, Path designProjectsDir, String defaultProjectFile) {
        Project current = host.appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath(), projectsDir, designProjectsDir);
            return;
        }
        if (loadProjectFile(projectsDir.resolve(defaultProjectFile))) {
            state.setCurrentProjectFile(defaultProjectFile);
        }
        loadDesignProjectFile(designProjectsDir.resolve(defaultProjectFile));
    }

    private void resetAfterProjectLoad() {
        state.setLineNameEditingId("");
        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
        state.clearPathReplacePick();
        state.setPoleDesignerOpen(false);
        state.setPoleDesignerEditingId("");
        state.getDesignDraftHistory().clear();
        stripSourceLinks(state.getProject());
        invalidatePreview();
    }

    private static void stripSourceLinks(PowerLineProject project) {
        if (project == null) {
            return;
        }
        for (PowerLineFootprint line : project.getLines().values()) {
            if (line.getSourceDescriptor() != null) {
                line.setClosedPath(line.getSourceDescriptor().closed());
            }
            line.clearSourceDescriptor();
        }
    }

    private boolean loadDesignProjectFile(Path file) {
        try {
            state.setDesignProject(PowerLineDesignProject.loadFrom(file));
            state.getDesignContentFingerprint().reset();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载杆塔设计工程失败: {}", e.getMessage(), e);
            state.setDesignProject(new PowerLineDesignProject());
            return false;
        }
    }

    private boolean saveDesignProjectFile(Path file) {
        if (file == null || state.getDesignProject() == null) {
            return false;
        }
        try {
            String json = state.getDesignProject().toJson();
            if (state.getDesignContentFingerprint().isUnchanged(json, file)) {
                return true;
            }
            state.getDesignProject().saveTo(file);
            state.getDesignContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存杆塔设计工程失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public void savePoleDesign(PoleDesign design) {
        if (design == null) {
            return;
        }
        pushWorkspaceSnapshot();
        state.getDesignProject().addDesign(design.copy());
        if (isDesignReferencedByAnyLine(design.getId())) {
            invalidatePreview();
        }
        state.setProjectStatus(PlotI18n.tr("plugin.powerline.design.saved", design.getName()), ProjectStatusSeverity.SUCCESS);
    }

    public int countUserTemplateReferences(String designId) {
        if (designId == null || designId.isBlank()) {
            return 0;
        }
        int count = 0;
        String presetId = com.plot.plugin.powerline.style.UserPoleDesignTemplateCatalog.presetIdFor(designId);
        for (PowerLineFootprint line : state.getProject().getLines().values()) {
            if (referencesUserTemplate(line, designId, presetId)) {
                count++;
            }
        }
        return count;
    }

    public boolean deleteUserPoleDesignTemplate(String designId) {
        if (designId == null
                || designId.isBlank()
                || com.plot.plugin.powerline.style.LinePoleDesignOverrides.isLineInstanceDesignId(designId)) {
            return false;
        }
        PoleDesign existing = state.getDesignProject().getDesign(designId);
        if (existing == null) {
            return false;
        }
        String name = existing.getName();
        pushWorkspaceSnapshot();
        detachUserTemplateFromLines(designId);
        state.getDesignProject().removeDesign(designId);
        invalidatePreview();
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.style.delete_user_template_done", name),
            ProjectStatusSeverity.SUCCESS);
        return true;
    }

    private void detachUserTemplateFromLines(String designId) {
        String presetId = com.plot.plugin.powerline.style.UserPoleDesignTemplateCatalog.presetIdFor(designId);
        for (PowerLineFootprint line : state.getProject().getLines().values()) {
            if (!referencesUserTemplate(line, designId, presetId)) {
                continue;
            }
            com.plot.plugin.powerline.style.LinePoleDesignOverrides.removeLineInstance(
                line,
                state.getDesignProject());
            if (presetId != null && presetId.equals(line.getStylePresetId())) {
                line.setStylePresetId(null);
            }
            if (designId.equals(line.getPoleDesignId())) {
                line.setPoleDesignId(null);
            }
            line.clearStyleOverrides();
        }
    }

    /** 将造型写入该线路私有实例，不覆盖内置或共享模板。 */
    public void saveLineInstancePoleDesign(PowerLineFootprint line, PoleDesign draft) {
        if (line == null || draft == null) {
            return;
        }
        pushWorkspaceSnapshot();
        LinePoleDesignOverrides.saveLineInstance(line, draft, state.getDesignProject());
        invalidatePreview();
    }

    private boolean isDesignReferencedByAnyLine(String designId) {
        return isUserTemplateReferencedByAnyLine(designId);
    }

    private boolean isUserTemplateReferencedByAnyLine(String designId) {
        return countUserTemplateReferences(designId) > 0;
    }

    private static boolean referencesUserTemplate(
            PowerLineFootprint line,
            String designId,
            String presetId) {
        if (line == null) {
            return false;
        }
        if (designId != null && designId.equals(line.getPoleDesignId())) {
            return true;
        }
        return presetId != null && presetId.equals(line.getStylePresetId());
    }

    private void pushWorkspaceSnapshot() {
        state.getProjectHistory().push(state.getProject(), state.getDesignProject());
    }

    private void restoreWorkspaceSnapshot(com.plot.plugin.powerline.model.PowerLineWorkspaceSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        state.setProject(snapshot.project());
        state.setDesignProject(snapshot.designProject());
        invalidatePreview();
    }

    private void discardPushedHistoryEntry() {
        if (state.getProjectHistory().canUndo()) {
            restoreWorkspaceSnapshot(state.getProjectHistory().undo(
                state.getProject(),
                state.getDesignProject()));
        }
    }

    private boolean saveProjectFile(Path file) {
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
            LOGGER.error("保存电力线路项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.save_failed", file.getFileName()), ProjectStatusSeverity.ERROR);
            return false;
        }
    }

    public void activatePathPickForCreate() {
        if (!beginPathPickSession(false, null)) {
            return;
        }
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.path.create_picking_hint"),
            ProjectStatusSeverity.INFO);
    }

    public void activatePathPickForReplace(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        if (!beginPathPickSession(true, line.getId())) {
            return;
        }
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.path.replace_picking_hint"),
            ProjectStatusSeverity.INFO);
    }

    private boolean beginPathPickSession(boolean replaceMode, String replaceTargetLineId) {
        if (state.getSelection().hasMultipleSelected()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.selection.multi_edit_blocked", state.getSelection().size()),
                ProjectStatusSeverity.WARNING);
            return false;
        }
        ToolManager toolManager = host.tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return false;
        }
        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
        if (replaceMode) {
            state.beginPathReplacePick(replaceTargetLineId);
        } else {
            state.clearPathReplacePick();
        }
        pathPickSession.begin(host.appState());
        toolManager.setActiveTool(selectTool);
        host.appState().setCurrentTool(baseTool);
        return true;
    }

    public void cancelPathPick() {
        boolean wasPicking = pathPickSession.isActive();
        if (wasPicking) {
            pathPickSession.cancel(host.appState());
            state.setProjectStatus(
                PlotI18n.status("status.plot.powerline.pick_path_cancelled"),
                ProjectStatusSeverity.INFO);
        }
        clearPathReplaceFlow();
        state.blockPathPickActivation(2);
        lastPathPickStatusKey = "";
    }

    private void applyPathPickOutcome(PowerLinePathPickSession.Outcome outcome) {
        switch (outcome.getResult()) {
            case SUCCESS -> {
                state.setPathSelection(PowerLinePathUtils.analyzeSelection(outcome.getPaths()));
                if (state.isPathReplacePending()) {
                    handleReplacePickComplete();
                } else {
                    applyPickedPaths();
                }
            }
            case NEED_SELECTION -> state.setProjectStatus(
                PlotI18n.status("status.plot.powerline.pick_path_need_selection"),
                ProjectStatusSeverity.WARNING);
            case NO_VALID -> state.setProjectStatus(
                PlotI18n.status("status.plot.powerline.pick_path_no_valid"),
                ProjectStatusSeverity.WARNING);
            case CANCELLED -> {
                clearPathReplaceFlow();
                state.blockPathPickActivation(2);
                state.setProjectStatus(
                    PlotI18n.status("status.plot.powerline.pick_path_cancelled"),
                    ProjectStatusSeverity.INFO);
            }
            default -> { }
        }
    }

    private void handleReplacePickComplete() {
        PowerLinePathSelectionAnalysis selection = state.getPathSelection();
        if (!selection.canAdopt()) {
            state.setProjectStatus(
                selection.rejectedCurves().isEmpty()
                    ? PlotI18n.tr("plugin.powerline.path.pick_no_selection")
                    : PlotI18n.tr("plugin.powerline.path.reject_curve"),
                ProjectStatusSeverity.WARNING);
            clearPathReplaceFlow();
            return;
        }
        if (selection.adoptable().size() != 1) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.path.replace_select_one"),
                ProjectStatusSeverity.WARNING);
            clearPathReplaceFlow();
            return;
        }
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.path.replace_ready"),
            ProjectStatusSeverity.SUCCESS);
        syncSelectionToPathReplaceTarget();
    }

    public boolean isPathReplaceConfirmPending() {
        if (!state.isPathReplacePending() || pathPickSession.isActive()) {
            return false;
        }
        PowerLinePathSelectionAnalysis selection = state.getPathSelection();
        return selection.canAdopt() && selection.adoptable().size() == 1;
    }

    public boolean isLineSelectionFrozen() {
        return isPathReplaceConfirmPending();
    }

    private void syncSelectionToPathReplaceTarget() {
        String targetId = state.getPathReplaceTargetLineId();
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        String previousPrimary = state.getSelection().primaryId();
        state.getSelection().select(targetId, false);
        if (!targetId.equals(previousPrimary)) {
            state.setPreviewAutoRefreshEnabled(false);
            invalidatePreview();
        }
    }

    private World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

}
