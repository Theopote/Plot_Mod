package com.plot.plugin.road.model.section;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 行车道组（横断面核心）。支持均分车道或显式 {@link Lane} 列表。
 */
public class LaneGroup {
    private Integer laneCount;
    private Integer width;
    private String material;
    private List<Lane> lanes = new ArrayList<>();

    public LaneGroup() {
    }

    public LaneGroup(Integer laneCount, Integer width, String material) {
        this.laneCount = laneCount;
        this.width = width;
        this.material = material;
    }

    public Integer getLaneCount() {
        return laneCount;
    }

    public void setLaneCount(Integer laneCount) {
        this.laneCount = laneCount;
    }

    public int getEffectiveLaneCount() {
        if (lanes != null && !lanes.isEmpty()) {
            return lanes.size();
        }
        return laneCount != null && laneCount > 0 ? laneCount : 1;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public List<Lane> getLanes() {
        return lanes != null ? List.copyOf(lanes) : List.of();
    }

    public void setLanes(List<Lane> lanes) {
        this.lanes = lanes != null ? new ArrayList<>(lanes) : new ArrayList<>();
    }

    public void syncLaneCount(int count) {
        laneCount = Math.max(1, count);
        if (lanes == null) {
            lanes = new ArrayList<>();
        }
        while (lanes.size() < laneCount) {
            lanes.add(new Lane());
        }
        while (lanes.size() > laneCount) {
            lanes.removeLast();
        }
    }

    /**
     * 解析各车道宽度（方块数），余数分配给靠近中心的车道。
     */
    public List<Integer> resolveLaneWidths(int totalWidth) {
        int count = getEffectiveLaneCount();
        int safeWidth = Math.max(count, totalWidth);
        if (lanes != null && !lanes.isEmpty()) {
            List<Integer> explicit = new ArrayList<>(lanes.size());
            int assigned = 0;
            for (Lane lane : lanes) {
                int laneWidth = lane != null && lane.getWidth() != null && lane.getWidth() > 0
                    ? lane.getWidth()
                    : 0;
                explicit.add(laneWidth);
                assigned += laneWidth;
            }
            boolean allExplicit = explicit.stream().allMatch(w -> w > 0);
            if (allExplicit && assigned == safeWidth) {
                return explicit;
            }
        }
        int base = safeWidth / count;
        int remainder = safeWidth % count;
        List<Integer> resolved = new ArrayList<>(count);
        int leftExtra = remainder / 2;
        int rightExtra = remainder - leftExtra;
        for (int i = 0; i < count; i++) {
            int laneWidth = base;
            if (i < leftExtra || i >= count - rightExtra) {
                laneWidth += 1;
            }
            resolved.add(Math.max(1, laneWidth));
        }
        return resolved;
    }

    /**
     * 车道分隔线相对中心线的横向偏移（米/方块），不含中央分隔带。
     */
    public List<Double> resolveLaneDividerOffsets(int totalWidth) {
        List<Integer> laneWidths = resolveLaneWidths(totalWidth);
        if (laneWidths.size() <= 1) {
            return List.of();
        }
        List<Double> offsets = new ArrayList<>(laneWidths.size() - 1);
        double halfWidth = totalWidth / 2.0;
        double cursor = -halfWidth;
        for (int i = 0; i < laneWidths.size() - 1; i++) {
            cursor += laneWidths.get(i);
            offsets.add(cursor);
        }
        return offsets;
    }

    LaneGroup copy() {
        LaneGroup copy = new LaneGroup(laneCount, width, material);
        if (lanes != null) {
            for (Lane lane : lanes) {
                copy.lanes.add(lane != null ? lane.copy() : new Lane());
            }
        }
        return copy;
    }
}
