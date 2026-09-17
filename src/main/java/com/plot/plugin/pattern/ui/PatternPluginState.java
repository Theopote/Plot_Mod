package com.plot.plugin.pattern.ui;

import com.plot.core.model.Shape;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.PatternRegionPickSession;
import com.plot.plugin.pattern.PatternSelectionSet;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.plugin.pattern.model.PatternProjectHistory;
import com.plot.plugin.pattern.model.PatternPresetLibrary;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/** 图案插件可变 UI/会话状态。 */
public final class PatternPluginState {
    private PatternProject project = new PatternProject();
    private final PatternProjectHistory projectHistory = new PatternProjectHistory();
    private final PatternSelectionSet selection = new PatternSelectionSet();
    private final PatternRegionPickSession pickSession = new PatternRegionPickSession();
    private final List<Shape> selectedRegions = new ArrayList<>();

    private volatile PatternGenerationResult lastGenerationResult;
    private String footprintNameEditingId = "";
    private String footprintNameBeforeRename = "";
    private final ImString footprintNameBuffer = new ImString(64);
    private final ImString imageImportPathBuffer = new ImString(512);
    private boolean footprintNameFocusPending = false;
    private int footprintNameIgnoreOutsideClickFrames = 0;

    private final List<String> pendingDeleteFootprintIds = new ArrayList<>();
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;
    private volatile String projectStatus = "";
    private String currentProjectFile = "default.json";
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();
    private PatternPresetLibrary presetLibrary;

    public PatternProject getProject() {
        return project;
    }

    public void setProject(PatternProject project) {
        this.project = project != null ? project : new PatternProject();
    }

    public PatternProjectHistory getProjectHistory() {
        return projectHistory;
    }

    public PatternSelectionSet getSelection() {
        return selection;
    }

    public PatternRegionPickSession getPickSession() {
        return pickSession;
    }

    public List<Shape> getSelectedRegions() {
        return selectedRegions;
    }

    public PatternGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public void setLastGenerationResult(PatternGenerationResult lastGenerationResult) {
        this.lastGenerationResult = lastGenerationResult;
    }

    public String getFootprintNameEditingId() {
        return footprintNameEditingId;
    }

    public void setFootprintNameEditingId(String footprintNameEditingId) {
        if (footprintNameEditingId == null || footprintNameEditingId.isBlank()) {
            endFootprintNameRename();
            return;
        }
        this.footprintNameEditingId = footprintNameEditingId;
    }

    public String getFootprintNameBeforeRename() {
        return footprintNameBeforeRename;
    }

    public void beginFootprintNameRename(String footprintId, String currentName) {
        footprintNameBeforeRename = currentName != null ? currentName : "";
        footprintNameBuffer.set(footprintNameBeforeRename);
        footprintNameEditingId = footprintId != null ? footprintId : "";
        footprintNameFocusPending = !footprintNameEditingId.isEmpty();
        footprintNameIgnoreOutsideClickFrames = 2;
    }

    public void endFootprintNameRename() {
        footprintNameEditingId = "";
        footprintNameBeforeRename = "";
        footprintNameFocusPending = false;
        footprintNameIgnoreOutsideClickFrames = 0;
    }

    public void tickFootprintNameRenameCooldown() {
        if (footprintNameIgnoreOutsideClickFrames > 0) {
            footprintNameIgnoreOutsideClickFrames--;
        }
    }

    public boolean isFootprintNameOutsideClickReady() {
        return footprintNameIgnoreOutsideClickFrames == 0;
    }

    public boolean consumeFootprintNameFocusPending() {
        if (!footprintNameFocusPending) {
            return false;
        }
        footprintNameFocusPending = false;
        return true;
    }

    public ImString getFootprintNameBuffer() {
        return footprintNameBuffer;
    }

    public ImString getImageImportPathBuffer() {
        return imageImportPathBuffer;
    }

    public List<String> getPendingDeleteFootprintIds() {
        return pendingDeleteFootprintIds;
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

    public PatternPresetLibrary getPresetLibrary() {
        return presetLibrary;
    }

    public void setPresetLibrary(PatternPresetLibrary presetLibrary) {
        this.presetLibrary = presetLibrary;
    }
}
