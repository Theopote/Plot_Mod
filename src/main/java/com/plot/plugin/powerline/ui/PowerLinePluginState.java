package com.plot.plugin.powerline.ui;

import com.plot.core.model.Shape;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineSelectionSet;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.PowerLineProjectHistory;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/** 电力线路插件可变 UI/会话状态。 */
public final class PowerLinePluginState {
    private PowerLineProject project = new PowerLineProject();
    private final PowerLineProjectHistory projectHistory = new PowerLineProjectHistory();
    private final PowerLineSelectionSet selection = new PowerLineSelectionSet();
    private final List<Shape> selectedPaths = new ArrayList<>();

    private volatile PowerLineGenerationResult lastGenerationResult;
    private final ImString lineNameBuffer = new ImString(64);
    private String lineNameEditingId = "";

    private final List<String> pendingDeleteLineIds = new ArrayList<>();
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private volatile String projectStatus = "";
    private String currentProjectFile = "default.json";
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    public PowerLineProject getProject() {
        return project;
    }

    public void setProject(PowerLineProject project) {
        this.project = project != null ? project : new PowerLineProject();
    }

    public PowerLineProjectHistory getProjectHistory() {
        return projectHistory;
    }

    public PowerLineSelectionSet getSelection() {
        return selection;
    }

    public List<Shape> getSelectedPaths() {
        return selectedPaths;
    }

    public PowerLineGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public void setLastGenerationResult(PowerLineGenerationResult lastGenerationResult) {
        this.lastGenerationResult = lastGenerationResult;
    }

    public ImString getLineNameBuffer() {
        return lineNameBuffer;
    }

    public String getLineNameEditingId() {
        return lineNameEditingId;
    }

    public void setLineNameEditingId(String lineNameEditingId) {
        this.lineNameEditingId = lineNameEditingId != null ? lineNameEditingId : "";
    }

    public List<String> getPendingDeleteLineIds() {
        return pendingDeleteLineIds;
    }

    public boolean isDeleteConfirmPending() {
        return deleteConfirmPending;
    }

    public void setDeleteConfirmPending(boolean deleteConfirmPending) {
        this.deleteConfirmPending = deleteConfirmPending;
    }

    public boolean isBuildConfirmPending() {
        return buildConfirmPending;
    }

    public void setBuildConfirmPending(boolean buildConfirmPending) {
        this.buildConfirmPending = buildConfirmPending;
    }

    public String getProjectStatus() {
        return projectStatus;
    }

    public void setProjectStatus(String projectStatus) {
        this.projectStatus = projectStatus != null ? projectStatus : "";
    }

    public String getCurrentProjectFile() {
        return currentProjectFile;
    }

    public void setCurrentProjectFile(String currentProjectFile) {
        this.currentProjectFile = currentProjectFile != null ? currentProjectFile : "default.json";
    }

    public ContentFingerprint.Tracker getContentFingerprint() {
        return contentFingerprint;
    }
}
