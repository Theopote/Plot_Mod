package com.plot.plugin.powerline.ui;

import com.plot.core.persistence.ContentFingerprint;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.PowerLineSelectionSet;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.PoleDesignDraftHistory;
import com.plot.plugin.powerline.model.PowerLineProjectHistory;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/** 电力线路插件可变 UI/会话状态。 */
public final class PowerLinePluginState {
    private PowerLineProject project = new PowerLineProject();
    private PowerLineDesignProject designProject = new PowerLineDesignProject();
    private final PowerLineProjectHistory projectHistory = new PowerLineProjectHistory();
    private final PoleDesignDraftHistory designDraftHistory = new PoleDesignDraftHistory();
    private final PowerLineSelectionSet selection = new PowerLineSelectionSet();
    private PowerLinePathSelectionAnalysis pathSelection = PowerLinePathSelectionAnalysis.EMPTY;

    private volatile PowerLineGenerationResult lastGenerationResult;
    private PowerLinePreviewKey previewKey;
    private final ImString lineNameBuffer = new ImString(64);
    private String lineNameEditingId = "";

    private final List<String> pendingDeleteLineIds = new ArrayList<>();
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private volatile String projectStatus = "";
    private ProjectStatusSeverity projectStatusSeverity = ProjectStatusSeverity.INFO;
    private String currentProjectFile = "default.json";
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();
    private final ContentFingerprint.Tracker designContentFingerprint = new ContentFingerprint.Tracker();

    private boolean poleDesignerOpen = false;
    private String poleDesignerEditingId = "";
    private final PowerLineValidationUiState validationState = new PowerLineValidationUiState();
    private final List<PlacedSingleTower> placedSingleTowers = new ArrayList<>();

    public PowerLineDesignProject getDesignProject() {
        return designProject;
    }

    public void setDesignProject(PowerLineDesignProject designProject) {
        this.designProject = designProject != null ? designProject : new PowerLineDesignProject();
    }

    public ContentFingerprint.Tracker getDesignContentFingerprint() {
        return designContentFingerprint;
    }

    public boolean isPoleDesignerOpen() {
        return poleDesignerOpen;
    }

    public void setPoleDesignerOpen(boolean poleDesignerOpen) {
        this.poleDesignerOpen = poleDesignerOpen;
    }

    public String getPoleDesignerEditingId() {
        return poleDesignerEditingId;
    }

    public void setPoleDesignerEditingId(String poleDesignerEditingId) {
        this.poleDesignerEditingId = poleDesignerEditingId != null ? poleDesignerEditingId : "";
    }

    public PowerLineProject getProject() {
        return project;
    }

    public void setProject(PowerLineProject project) {
        this.project = project != null ? project : new PowerLineProject();
    }

    public PowerLineProjectHistory getProjectHistory() {
        return projectHistory;
    }

    public PoleDesignDraftHistory getDesignDraftHistory() {
        return designDraftHistory;
    }

    public PowerLineSelectionSet getSelection() {
        return selection;
    }

    public PowerLinePathSelectionAnalysis getPathSelection() {
        return pathSelection;
    }

    public void setPathSelection(PowerLinePathSelectionAnalysis pathSelection) {
        this.pathSelection = pathSelection != null ? pathSelection : PowerLinePathSelectionAnalysis.EMPTY;
    }

    public PowerLineGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public void setLastGenerationResult(PowerLineGenerationResult lastGenerationResult) {
        this.lastGenerationResult = lastGenerationResult;
    }

    public PowerLinePreviewKey getPreviewKey() {
        return previewKey;
    }

    public void setPreviewKey(PowerLinePreviewKey previewKey) {
        this.previewKey = previewKey;
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

    public ProjectStatusSeverity getProjectStatusSeverity() {
        return projectStatusSeverity;
    }

    public void setProjectStatus(String projectStatus) {
        setProjectStatus(projectStatus, ProjectStatusSeverity.INFO);
    }

    public void setProjectStatus(String projectStatus, ProjectStatusSeverity severity) {
        this.projectStatus = projectStatus != null ? projectStatus : "";
        this.projectStatusSeverity = severity != null ? severity : ProjectStatusSeverity.INFO;
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

    public PowerLineValidationUiState getValidationState() {
        return validationState;
    }

    public List<PlacedSingleTower> getPlacedSingleTowers() {
        return List.copyOf(placedSingleTowers);
    }

    public void addPlacedSingleTower(PlacedSingleTower placement) {
        if (placement != null) {
            placedSingleTowers.add(placement);
        }
    }

    public void removeLastPlacedSingleTower() {
        if (!placedSingleTowers.isEmpty()) {
            placedSingleTowers.removeLast();
        }
    }
}
