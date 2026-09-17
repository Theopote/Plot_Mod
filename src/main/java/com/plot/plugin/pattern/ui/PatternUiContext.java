package com.plot.plugin.pattern.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Shape;
import com.plot.plugin.pattern.PatternGenerationResult;
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
        return state.getLastGenerationResult();
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

    public boolean calculatePreview(PatternFootprint footprint) {
        return actions.calculatePreview(footprint);
    }

    public boolean calculatePreview(List<PatternFootprint> footprints, boolean autoProjectGhosts) {
        return actions.calculatePreview(footprints, autoProjectGhosts);
    }

    public void projectPreview() {
        actions.projectPreview();
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
