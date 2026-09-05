package com.plot.plugin.building;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 已认领建筑的多选集合（District Massing Phase A）。
 * <p>
 * Overview / Edit / Generate 共用同一选中集；{@link #primaryId()} 为单栋编辑/生成的主目标。
 */
public final class BuildingSelectionSet {

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

    /**
     * 主建筑 id（编辑/单栋预览目标）。空表示无选中。
     */
    public String primaryId() {
        return primaryId != null ? primaryId : "";
    }

    /**
     * 单击：单选；Ctrl+单击：切换加入/移出。
     */
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

    public void addAll(Collection<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                selectedIds.add(id);
                if (primaryId.isEmpty()) {
                    primaryId = id;
                }
            }
        }
    }

    public void setPrimary(String id) {
        if (id == null || id.isBlank() || !selectedIds.contains(id)) {
            return;
        }
        primaryId = id;
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

    /**
     * 删除/撤销后剔除已不存在的 id，不自动回填。
     */
    public void retainExisting(BuildingProject project) {
        if (project == null) {
            clear();
            return;
        }
        selectedIds.removeIf(id -> project.getBuilding(id) == null);
        if (primaryId.isEmpty() || !selectedIds.contains(primaryId)
                || project.getBuilding(primaryId) == null) {
            primaryId = selectedIds.isEmpty() ? "" : selectedIds.getFirst();
        }
    }

    public List<BuildingFootprint> resolve(BuildingProject project) {
        if (project == null || selectedIds.isEmpty()) {
            return List.of();
        }
        List<BuildingFootprint> buildings = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            BuildingFootprint building = project.getBuilding(id);
            if (building != null) {
                buildings.add(building);
            }
        }
        return buildings;
    }

    public BuildingFootprint primary(BuildingProject project) {
        if (project == null || primaryId.isEmpty()) {
            return null;
        }
        return project.getBuilding(primaryId);
    }

    public double totalArea(BuildingProject project) {
        double area = 0.0;
        for (BuildingFootprint building : resolve(project)) {
            area += building.computeArea();
        }
        return area;
    }
}
