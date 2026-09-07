package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 已认领电力线路的多选集合。 */
public final class PowerLineSelectionSet {
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

    public void clear() {
        selectedIds.clear();
        primaryId = "";
    }

    public void retainExisting(PowerLineProject project) {
        if (project == null) {
            clear();
            return;
        }
        selectedIds.removeIf(id -> project.getLine(id) == null);
        if (primaryId.isEmpty() || !selectedIds.contains(primaryId)
                || project.getLine(primaryId) == null) {
            primaryId = selectedIds.isEmpty() ? "" : selectedIds.getFirst();
        }
    }

    public List<PowerLineFootprint> resolve(PowerLineProject project) {
        if (project == null || selectedIds.isEmpty()) {
            return List.of();
        }
        List<PowerLineFootprint> lines = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            PowerLineFootprint line = project.getLine(id);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    public PowerLineFootprint primary(PowerLineProject project) {
        if (project == null || primaryId.isEmpty()) {
            return null;
        }
        return project.getLine(primaryId);
    }
}
