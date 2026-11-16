package com.plot.plugin.road.model.section;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.RoadParameterLimits;

import java.util.ArrayList;
import java.util.List;

/**
 * 行车道组（横断面核心）。车道行驶宽度为主；行车道总宽由
 * {@link CarriagewayWidthRules} 按车道 + 分隔占格推导。
 */
public class LaneGroup {
    private Integer laneCount;
    private Integer width;
    private MaterialMix material;
    private List<Lane> lanes = new ArrayList<>();
    /**
     * 均匀模式下的单车道行驶宽；{@code null} 表示未设置（解析时从存储总宽迁移或继承配置）。
     * 显式 {@link Lane#getWidth()} 优先于本字段。
     */
    private Integer defaultLaneWidth;

    public LaneGroup() {
    }

    public LaneGroup(Integer laneCount, Integer width, MaterialMix material) {
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

    public Integer getDefaultLaneWidth() {
        return defaultLaneWidth;
    }

    public void setDefaultLaneWidth(Integer defaultLaneWidth) {
        this.defaultLaneWidth = defaultLaneWidth != null
            ? RoadParameterLimits.clampLaneTravelWidth(defaultLaneWidth)
            : null;
    }

    public int getEffectiveDefaultLaneWidth() {
        return defaultLaneWidth != null
            ? RoadParameterLimits.clampLaneTravelWidth(defaultLaneWidth)
            : RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS;
    }

    public boolean hasConfiguredDefaultLaneWidth() {
        return defaultLaneWidth != null;
    }

    public MaterialMix getMaterial() {
        return material;
    }

    public void setMaterial(MaterialMix material) {
        this.material = material;
    }

    public void setMaterial(String material) {
        String normalized = RoadMaterialUtils.normalizeStoredMaterial(material);
        this.material = normalized != null ? MaterialMix.single(normalized) : null;
    }

    public List<Lane> getLanes() {
        return lanes != null ? List.copyOf(lanes) : List.of();
    }

    public void setLanes(List<Lane> lanes) {
        this.lanes = lanes != null ? new ArrayList<>(lanes) : new ArrayList<>();
    }

    public void setLaneWidthAt(int index, int width) {
        if (index < 0) {
            return;
        }
        syncLaneCount(Math.max(getEffectiveLaneCount(), index + 1));
        lanes.get(index).setWidth(RoadParameterLimits.clampLaneTravelWidth(width));
    }

    /** 清除各车道显式宽，回到均匀 {@link #defaultLaneWidth}。 */
    public void clearExplicitLaneWidths() {
        if (lanes == null) {
            return;
        }
        for (Lane lane : lanes) {
            if (lane != null) {
                lane.setWidth(null);
            }
        }
    }

    /** 设置均匀单车道宽并清除分车道覆盖（结构页主滑条）。 */
    public void applyUniformDefaultLaneWidth(int width) {
        setDefaultLaneWidth(width);
        clearExplicitLaneWidths();
    }

    /** 钳制各车道显式宽度与默认宽到 1–6。 */
    public void reclampLaneWidths(int ignoredCarriagewayWidth) {
        if (lanes != null) {
            for (Lane lane : lanes) {
                if (lane != null && lane.getWidth() != null && lane.getWidth() > 0) {
                    lane.setWidth(RoadParameterLimits.clampLaneTravelWidth(lane.getWidth()));
                }
            }
        }
        if (defaultLaneWidth != null) {
            defaultLaneWidth = RoadParameterLimits.clampLaneTravelWidth(defaultLaneWidth);
        }
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

    /** 是否存在任一车道显式行驶宽（旧档迁移时用于区分「均分总宽」与「已定车道宽」）。 */
    public boolean hasExplicitLaneWidths() {
        if (lanes == null || lanes.isEmpty()) {
            return false;
        }
        for (Lane lane : lanes) {
            if (lane != null && lane.getWidth() != null && lane.getWidth() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 旧档迁移：无显式分车道宽、且未配置均匀单车道宽时，由存储总宽反推。
     *
     * @return {@code true} 若执行了反推
     */
    public boolean migrateUniformLaneWidthFromStored(
            int storedCarriagewayWidth,
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int medianWidth) {
        if (hasExplicitLaneWidths()) {
            reclampLaneWidths(0);
            return false;
        }
        if (hasConfiguredDefaultLaneWidth()) {
            return false;
        }
        setDefaultLaneWidth(CarriagewayWidthRules.inferUniformLaneWidth(
            storedCarriagewayWidth,
            getEffectiveLaneCount(),
            centerStyle,
            laneDividers,
            medianWidth));
        return true;
    }

    /**
     * 解析各车道行驶宽度（不含分隔占格）。
     * 有显式宽的车道用显式值，其余用均匀默认宽。
     */
    public List<Integer> resolveLaneWidths() {
        int count = getEffectiveLaneCount();
        int uniform = getEffectiveDefaultLaneWidth();
        List<Integer> resolved = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Lane lane = lanes != null && i < lanes.size() ? lanes.get(i) : null;
            if (lane != null && lane.getWidth() != null && lane.getWidth() > 0) {
                resolved.add(RoadParameterLimits.clampLaneTravelWidth(lane.getWidth()));
            } else {
                resolved.add(uniform);
            }
        }
        return resolved;
    }

    /** 按当前车道宽与标线/分隔带推导行车道总宽并写回 {@link #width}。 */
    public int syncDerivedWidth(
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int medianWidth) {
        int derived = CarriagewayWidthRules.derivedCarriagewayWidth(
            resolveLaneWidths(), centerStyle, laneDividers, medianWidth);
        this.width = derived;
        return derived;
    }

    /**
     * 兼容旧调用签名：参数不再表示「总宽均分」，忽略之。
     */
    public List<Integer> resolveLaneWidths(int ignoredTotalWidth) {
        return resolveLaneWidths();
    }

    /**
     * 分隔线相对道路几何中心的横向偏移（按车道块 + 分隔占格推进）。
     */
    public List<Double> resolveLaneDividerOffsets(
            CenterLineStyle centerStyle,
            boolean laneDividers) {
        List<Integer> laneWidths = resolveLaneWidths();
        int lanes = laneWidths.size();
        int centerBlocks = CarriagewayWidthRules.centerLineBlocks(centerStyle);
        if (lanes <= 1) {
            if (centerBlocks <= 0) {
                return List.of();
            }
            if (centerBlocks == 2) {
                return List.of(-0.5, 0.5);
            }
            return List.of(0.0);
        }

        int total = CarriagewayWidthRules.derivedCarriagewayWidth(
            laneWidths, centerStyle, laneDividers, 0);
        double cursor = -total / 2.0;
        List<Double> offsets = new ArrayList<>();
        // 中央标线落在最靠近几何中心的车道缝：2→0，3→1，4→1
        int centerSeam = (lanes - 1) / 2;

        for (int i = 0; i < lanes; i++) {
            cursor += laneWidths.get(i);
            if (i >= lanes - 1) {
                break;
            }
            boolean isCenterSeam = i == centerSeam;
            int seamBlocks = 0;
            if (isCenterSeam && centerBlocks > 0) {
                seamBlocks = centerBlocks;
            } else if (laneDividers) {
                seamBlocks = 1;
            } else if (isCenterSeam && centerBlocks > 0) {
                seamBlocks = centerBlocks;
            }
            if (seamBlocks == 1) {
                offsets.add(cursor + 0.5);
            } else if (seamBlocks == 2) {
                offsets.add(cursor + 0.5);
                offsets.add(cursor + 1.5);
            }
            cursor += seamBlocks;
        }
        return offsets;
    }

    LaneGroup copy() {
        LaneGroup copy = new LaneGroup(laneCount, width, material);
        copy.defaultLaneWidth = defaultLaneWidth;
        if (lanes != null) {
            for (Lane lane : lanes) {
                copy.lanes.add(lane != null ? lane.copy() : new Lane());
            }
        }
        return copy;
    }
}
