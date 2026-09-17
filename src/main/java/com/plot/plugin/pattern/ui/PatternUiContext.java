package com.plot.plugin.pattern.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.context.PluginContext;
import imgui.ImGui;
import com.plot.core.model.Shape;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.PatternGenerationSnapshot;
import com.plot.plugin.pattern.PatternGenerator;
import com.plot.plugin.pattern.PatternRegionPickSession;
import com.plot.plugin.pattern.PatternSelectionSet;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.plugin.pattern.model.PatternProjectHistory;
import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.pattern.model.PatternPresetLibrary;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 图案 UI 共享依赖与状态访问。
 */
public final class PatternUiContext {
    private final PluginContext host;
    private final PatternPluginState state;
    private final Object projectLock;
    private final PatternActions actions;
    private int projectionCaptureFrame = -1;
    private WorldProjectionSnapshot projectionSnapshot = WorldProjectionSnapshot.UNKNOWN;

    public PatternUiContext(PluginContext host, PatternPluginState state, Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
        this.actions = new PatternActions(host, state, projectLock);
    }

    public void setPluginDataDir(Path pluginDataDir) {
        actions.setPluginDataDir(pluginDataDir);
    }

    public void setPatternGenerator(PatternGenerator patternGenerator) {
        actions.setPatternGenerator(patternGenerator);
    }

    public PatternActions actions() {
        return actions;
    }

    public PluginContext host() {
        return host;
    }

    public ICoordinateService coordinates() {
        return host.coordinates();
    }

    /** 每帧捕获一次视图投影，供区域 Tab 方块数统计复用。 */
    public WorldProjectionSnapshot currentProjection() {
        int frame = ImGui.getFrameCount();
        if (frame != projectionCaptureFrame) {
            projectionCaptureFrame = frame;
            try {
                projectionSnapshot = host.coordinates().captureProjection();
            } catch (RuntimeException ignored) {
                projectionSnapshot = WorldProjectionSnapshot.UNKNOWN;
            }
        }
        return projectionSnapshot;
    }

    public PatternPluginState state() {
        return state;
    }

    public Object projectLock() {
        return projectLock;
    }

    public PatternProject project() {
        return state.getProject();
    }

    public void setProject(PatternProject project) {
        state.setProject(project);
    }

    public PatternProjectHistory projectHistory() {
        return state.getProjectHistory();
    }

    public PatternSelectionSet selection() {
        return state.getSelection();
    }

    public PatternRegionPickSession pickSession() {
        return state.getPickSession();
    }

    public List<Shape> selectedRegions() {
        return state.getSelectedRegions();
    }

    public PatternGenerationResult lastGenerationResult() {
        return actions.currentPreviewResult();
    }

    public PatternGenerationSnapshot generationSnapshot() {
        return state.getGenerationSnapshot();
    }

    public boolean hasValidPreview() {
        return actions.hasValidPreview();
    }

    public void pushProjectHistory() {
        actions.pushProjectHistory();
    }

    public void selectFootprint(String id, boolean multiToggle) {
        selection().select(id, multiToggle);
        actions.onSelectionChanged();
    }

    public void selectAllFootprints(java.util.Collection<String> ids) {
        selection().selectAll(ids);
        actions.onSelectionChanged();
    }

    public String projectStatus() {
        return state.getProjectStatus();
    }

    public void setProjectStatus(String status) {
        state.setProjectStatus(status);
    }

    public String footprintNameEditingId() {
        return state.getFootprintNameEditingId();
    }

    public void setFootprintNameEditingId(String id) {
        state.setFootprintNameEditingId(id);
    }

    public void beginFootprintNameRename(PatternFootprint footprint) {
        if (footprint == null) {
            return;
        }
        state.beginFootprintNameRename(footprint.getId(), footprint.getName());
        selectFootprint(footprint.getId(), false);
    }

    public boolean consumeFootprintNameFocusPending() {
        return state.consumeFootprintNameFocusPending();
    }

    public void tickFootprintNameRenameCooldown() {
        state.tickFootprintNameRenameCooldown();
    }

    public boolean isFootprintNameOutsideClickReady() {
        return state.isFootprintNameOutsideClickReady();
    }

    public void commitFootprintNameRename(PatternFootprint footprint) {
        if (footprint == null || !footprint.getId().equals(state.getFootprintNameEditingId())) {
            state.endFootprintNameRename();
            return;
        }
        String trimmed = state.getFootprintNameBuffer().get().trim();
        if (!trimmed.isEmpty() && !trimmed.equals(footprint.getName())) {
            pushProjectHistory();
            footprint.setName(trimmed);
        }
        state.endFootprintNameRename();
    }

    public void cancelFootprintNameRename(PatternFootprint footprint) {
        if (footprint != null && footprint.getId().equals(state.getFootprintNameEditingId())) {
            state.getFootprintNameBuffer().set(state.getFootprintNameBeforeRename());
        }
        state.endFootprintNameRename();
    }

    public imgui.type.ImString footprintNameBuffer() {
        return state.getFootprintNameBuffer();
    }

    public List<String> pendingDeleteFootprintIds() {
        return state.getPendingDeleteFootprintIds();
    }

    public boolean deleteConfirmPending() {
        return state.isDeleteConfirmPending();
    }

    public void setDeleteConfirmPending(boolean pending) {
        state.setDeleteConfirmPending(pending);
    }

    public boolean buildConfirmPending() {
        return state.isBuildConfirmPending();
    }

    public void setBuildConfirmPending(boolean pending) {
        state.setBuildConfirmPending(pending);
    }

    public void syncSelectedFootprintAfterHistory() {
        actions.syncSelectedFootprintAfterHistory();
    }

    public boolean updatePreview(PatternFootprint footprint) {
        return actions.updatePreview(footprint);
    }

    public boolean updatePreview(List<PatternFootprint> footprints) {
        return actions.updatePreview(footprints);
    }

    public void clearPreview() {
        actions.clearPreview();
    }

    public void buildInWorld() {
        actions.buildInWorld();
    }

    public void locateFootprint(PatternFootprint footprint) {
        actions.locateFootprint(footprint);
    }

    public void startPickSession() {
        actions.startPickSession();
    }

    public void cancelPickSession() {
        actions.cancelPickSession();
    }

    public void handlePickSessionTick() {
        actions.handlePickSessionTick();
    }

    public void adoptSelectedRegions() {
        actions.adoptSelectedRegions();
    }

    public void importImageForFootprint(PatternFootprint footprint) {
        actions.importImageForFootprint(footprint);
    }

    public imgui.type.ImString imageImportPathBuffer() {
        return state.getImageImportPathBuffer();
    }

    public void importImageFromPath(String footprintId, String pathText) {
        actions.importImageFromPath(footprintId, pathText);
    }

    public void deleteFootprints(List<String> ids) {
        actions.deleteFootprints(ids);
    }

    public void onProjectLoaded(String filePath, Path projectsDir) {
        actions.onProjectLoaded(filePath, projectsDir);
    }

    public void onProjectSaved(String filePath, Path projectsDir) {
        actions.onProjectSaved(filePath, projectsDir);
    }

    public void persistProject(Path projectsDir) {
        actions.persistProject(projectsDir);
    }

    public boolean loadProjectFile(Path file) {
        return actions.loadProjectFile(file);
    }

    public void setCurrentProjectFile(String file) {
        state.setCurrentProjectFile(file);
    }

    public PatternPresetLibrary presetLibrary() {
        return state.getPresetLibrary();
    }

    public void setPresetLibrary(PatternPresetLibrary presetLibrary) {
        state.setPresetLibrary(presetLibrary);
    }
}
