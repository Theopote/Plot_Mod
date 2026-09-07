package com.plot.plugin.powerline.engineering.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 自动塔型选择结果。 */
public class TowerSelectionResult {
    private String selectedDesignId;
    private final List<TowerCandidateScore> candidates = new ArrayList<>();
    private final List<String> reasons = new ArrayList<>();
    private boolean fallbackUsed;

    public String getSelectedDesignId() {
        return selectedDesignId;
    }

    public void setSelectedDesignId(String selectedDesignId) {
        this.selectedDesignId = selectedDesignId;
    }

    public List<TowerCandidateScore> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public void addCandidate(TowerCandidateScore candidate) {
        if (candidate != null) {
            candidates.add(candidate);
        }
    }

    public List<String> getReasons() {
        return Collections.unmodifiableList(reasons);
    }

    public void addReason(String reason) {
        if (reason != null && !reason.isBlank()) {
            reasons.add(reason);
        }
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public boolean hasSelection() {
        return selectedDesignId != null && !selectedDesignId.isBlank();
    }
}
