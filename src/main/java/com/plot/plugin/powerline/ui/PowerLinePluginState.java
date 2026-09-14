package com.plot.plugin.powerline.ui;

import com.plot.core.persistence.ContentFingerprint;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.PowerLineSelectionSet;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.PoleDesignDraftHistory;
import com.plot.plugin.powerline.model.PowerLineProjectHistory;
import com.plot.plugin.powerline.style.StyleCategory;
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
    /** 非空时表示路径拾取用于替换该线路，完成拾取后需确认再应用。 */
    private String pathReplaceTargetLineId = "";
    /** 工程级覆盖中展开杆塔角色列表的线路 id。 */
    private String poleRoleInspectorOpenLineId = "";

    private volatile PowerLineGenerationResult lastGenerationResult;
    private PowerLinePreviewKey previewKey;
    private boolean previewAutoRefreshEnabled;
    private final ImString lineNameBuffer = new ImString(64);
    private String lineNameEditingId = "";
    private String lineNameBeforeRename = "";
    private boolean lineNameFocusPending;
    private int lineNameIgnoreOutsideClickFrames;

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
    /** 画廊中临时强制展开的 preset 分类（选中新 preset 后一帧）。 */
    private StyleCategory styleGalleryOpenCategory;
    /** 取消拾取后短暂屏蔽「拾取路径」，避免按钮换位误触。 */
    private int pathPickActivationBlockFrames;

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

    public String getPathReplaceTargetLineId() {
        return pathReplaceTargetLineId;
    }

    public boolean isPathReplacePending() {
        return pathReplaceTargetLineId != null && !pathReplaceTargetLineId.isBlank();
    }

    public void beginPathReplacePick(String lineId) {
        this.pathReplaceTargetLineId = lineId != null ? lineId : "";
    }

    public void clearPathReplacePick() {
        this.pathReplaceTargetLineId = "";
    }

    public boolean isPoleRoleInspectorOpen(String lineId) {
        return lineId != null
            && !lineId.isBlank()
            && lineId.equals(poleRoleInspectorOpenLineId);
    }

    public void setPoleRoleInspectorOpen(String lineId, boolean open) {
        this.poleRoleInspectorOpenLineId = open && lineId != null && !lineId.isBlank()
            ? lineId
            : "";
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

    public boolean isPreviewAutoRefreshEnabled() {
        return previewAutoRefreshEnabled;
    }

    public void setPreviewAutoRefreshEnabled(boolean previewAutoRefreshEnabled) {
        this.previewAutoRefreshEnabled = previewAutoRefreshEnabled;
    }

    public ImString getLineNameBuffer() {
        return lineNameBuffer;
    }

    public String getLineNameEditingId() {
        return lineNameEditingId;
    }

    public void setLineNameEditingId(String lineNameEditingId) {
        if (lineNameEditingId == null || lineNameEditingId.isBlank()) {
            endLineNameRename();
            return;
        }
        this.lineNameEditingId = lineNameEditingId;
    }

    public String getLineNameBeforeRename() {
        return lineNameBeforeRename;
    }

    public boolean isLineNameFocusPending() {
        return lineNameFocusPending;
    }

    public void beginLineNameRename(String lineId, String currentName) {
        lineNameBeforeRename = currentName != null ? currentName : "";
        lineNameBuffer.set(lineNameBeforeRename);
        lineNameEditingId = lineId != null ? lineId : "";
        lineNameFocusPending = !lineNameEditingId.isEmpty();
        lineNameIgnoreOutsideClickFrames = 2;
    }

    public void endLineNameRename() {
        lineNameEditingId = "";
        lineNameBeforeRename = "";
        lineNameFocusPending = false;
        lineNameIgnoreOutsideClickFrames = 0;
    }

    public void tickLineNameRenameCooldown() {
        if (lineNameIgnoreOutsideClickFrames > 0) {
            lineNameIgnoreOutsideClickFrames--;
        }
    }

    public boolean isLineNameOutsideClickReady() {
        return lineNameIgnoreOutsideClickFrames == 0;
    }

    public boolean consumeLineNameFocusPending() {
        if (!lineNameFocusPending) {
            return false;
        }
        lineNameFocusPending = false;
        return true;
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

    public void notifyStyleGalleryCategory(StyleCategory category) {
        this.styleGalleryOpenCategory = category;
    }

    public StyleCategory getStyleGalleryOpenCategory() {
        return styleGalleryOpenCategory;
    }

    public void clearStyleGalleryOpenCategory() {
        styleGalleryOpenCategory = null;
    }

    public void blockPathPickActivation(int frames) {
        if (frames > 0) {
            pathPickActivationBlockFrames = Math.max(pathPickActivationBlockFrames, frames);
        }
    }

    public void tickPathPickActivationBlock() {
        if (pathPickActivationBlockFrames > 0) {
            pathPickActivationBlockFrames--;
        }
    }

    public boolean isPathPickActivationBlocked() {
        return pathPickActivationBlockFrames > 0;
    }

}
