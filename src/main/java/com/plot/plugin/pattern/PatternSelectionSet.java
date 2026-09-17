package com.plot.plugin.pattern;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 已认领铺装区域的多选集合。
 */
public final class PatternSelectionSet {
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private String primaryId = "";

    public Set<String> ids() {
        return Collections.unmodifiableSet(selectedIds);
    }

    public int size() {
        return selectedIds.size();
    }

    public boolean isEmpty() {
        return selectedIds.isEmpty();
    }

    public boolean contains(String id) {
        return id != null && selectedIds.contains(id);
    }

    public String primaryId() {
        return primaryId != null ? primaryId : "";
    }

    public void select(String id, boolean multiToggle) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (multiToggle) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
                if (id.equals(primaryId)) {
                    primaryId = selectedIds.isEmpty() ? "" : selectedIds.getFirst();
                }
            } else {
                selectedIds.add(id);
                primaryId = id;
            }
            return;
        }
        selectedIds.clear();
        selectedIds.add(id);
        primaryId = id;
    }

    public void selectAll(Collection<String> ids) {
        selectedIds.clear();
        primaryId = "";
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                selectedIds.add(id);
            }
        }
        if (!selectedIds.isEmpty()) {
            primaryId = selectedIds.getFirst();
        }
    }

    public void remove(String id) {
        if (id == null) {
            return;
        }
        selectedIds.remove(id);
        if (id.equals(primaryId)) {
            primaryId = selectedIds.isEmpty() ? "" : selectedIds.getFirst();
        }
    }

    public void clear() {
        selectedIds.clear();
        primaryId = "";
    }

    public void retainExisting(PatternProject project) {
        if (project == null) {
            clear();
            return;
        }
        selectedIds.removeIf(id -> project.getFootprint(id) == null);
        if (primaryId.isEmpty() || !selectedIds.contains(primaryId)
                || project.getFootprint(primaryId) == null) {
            primaryId = selectedIds.isEmpty() ? "" : selectedIds.getFirst();
        }
    }

    public List<PatternFootprint> resolve(PatternProject project) {
        if (project == null || selectedIds.isEmpty()) {
            return List.of();
        }
        List<PatternFootprint> footprints = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            PatternFootprint footprint = project.getFootprint(id);
            if (footprint != null) {
                footprints.add(footprint);
            }
        }
        return footprints;
    }

    public PatternFootprint primary(PatternProject project) {
        if (project == null || primaryId.isEmpty()) {
            return null;
        }
        return project.getFootprint(primaryId);
    }

    public int totalBlockCount(PatternProject project) {
        int count = 0;
        for (PatternFootprint footprint : resolve(project)) {
            count += footprint.computeBlockCount();
        }
        return count;
    }
}
