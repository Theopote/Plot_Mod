package com.plot.plugin.powerline.ui;

import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.PowerLineSelectionSet;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.PowerLineProjectHistory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 电力线路 UI 共享依赖与状态访问。 */
public final class PowerLineUiContext {
    private final PluginContext host;
    private final PowerLinePluginState state;
    private final Object projectLock;
    private final PowerLineActions actions;

    public PowerLineUiContext(PluginContext host, PowerLinePluginState state, Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
        this.actions = new PowerLineActions(host, state, projectLock);
    }

    public void setGenerator(PowerLineGenerator generator) {
        actions.setGenerator(generator);
    }

    public PluginContext host() {
        return host;
    }

    public PowerLinePluginState state() {
        return state;
    }

    public PowerLineActions actions() {
        return actions;
    }

    public PowerLineProject project() {
        return state.getProject();
    }

    public void setProject(PowerLineProject project) {
        state.setProject(project);
    }

    public PowerLineProjectHistory projectHistory() {
        return state.getProjectHistory();
    }

    public PowerLineSelectionSet selection() {
        return state.getSelection();
    }

    public PowerLinePathSelectionAnalysis pathSelection() {
        return state.getPathSelection();
    }

    public PowerLineGenerationResult lastGenerationResult() {
        return state.getLastGenerationResult();
    }

    public String projectStatus() {
        return state.getProjectStatus();
    }

    public void pushEditSnapshot() {
        state.getProjectHistory().push(state.getProject());
    }

    public void setProjectStatus(String status) {
        state.setProjectStatus(status);
    }

    public void updateSelectedPaths() {
        actions.updateSelectedPaths();
    }

    public void adoptSelectedPaths() {
        actions.adoptSelectedPaths();
    }

    public boolean calculatePreview(PowerLineFootprint line) {
        return actions.calculatePreview(line);
    }

    public boolean autoAdjustTerrain(PowerLineFootprint line) {
        return actions.autoAdjustTerrain(line);
    }

    public LineEngineeringReport cachedEngineeringReport(PowerLineFootprint line) {
        return actions.cachedEngineeringReport(line);
    }

    public LineEngineeringReport cachedTerrainReport(PowerLineFootprint line) {
        return actions.cachedTerrainReport(line);
    }

    public void clearPreview() {
        actions.clearPreview();
    }

    public void invalidatePreview() {
        actions.invalidatePreview();
    }

    public boolean hasValidPreview(PowerLineFootprint line) {
        return actions.hasValidPreview(line);
    }

    public void syncPreviewValidity(PowerLineFootprint line) {
        actions.syncPreviewValidity(line);
    }

    public void selectLine(String lineId, boolean multiToggle) {
        actions.selectLine(lineId, multiToggle);
    }

    public void selectAll(java.util.Collection<String> lineIds) {
        actions.selectAll(lineIds);
    }

    public void clearSelection() {
        actions.clearSelection();
    }

    public boolean requestBuildConfirm(PowerLineFootprint line) {
        return actions.requestBuildConfirm(line);
    }

    public void buildInWorld() {
        actions.buildInWorld();
    }

    public void locateLine(PowerLineFootprint line) {
        actions.locateLine(line);
    }

    public boolean hasMinSpacingWarning(PowerLineFootprint line) {
        return actions.hasMinSpacingWarning(line);
    }

    public void deleteLines(List<String> ids) {
        actions.deleteLines(ids);
    }

    public void onProjectLoaded(String filePath, Path projectsDir, Path designProjectsDir) {
        actions.onProjectLoaded(filePath, projectsDir, designProjectsDir);
    }

    public void onProjectSaved(String filePath, Path projectsDir, Path designProjectsDir) {
        actions.onProjectSaved(filePath, projectsDir, designProjectsDir);
    }

    public void persistProject(Path projectsDir, Path designProjectsDir) {
        actions.persistProject(projectsDir, designProjectsDir);
    }

    public void loadProjectForCurrentProject(
            Path projectsDir,
            Path designProjectsDir,
            String defaultFile) {
        actions.loadProjectForCurrentProject(projectsDir, designProjectsDir, defaultFile);
    }

    public PoleDesignResolver designResolver() {
        return actions.designResolver();
    }

    public PowerLineDesignProject designProject() {
        return state.getDesignProject();
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

    public void loadProjectForCurrentProject(Path projectsDir, String defaultFile) {
        actions.loadProjectForCurrentProject(projectsDir, defaultFile);
    }

    public void activatePathPickTool() {
        actions.activatePathPickTool();
    }

    public List<String> pendingDeleteLineIds() {
        return state.getPendingDeleteLineIds();
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

    public imgui.type.ImString lineNameBuffer() {
        return state.getLineNameBuffer();
    }

    public String lineNameEditingId() {
        return state.getLineNameEditingId();
    }

    public void setLineNameEditingId(String id) {
        state.setLineNameEditingId(id);
    }
}
