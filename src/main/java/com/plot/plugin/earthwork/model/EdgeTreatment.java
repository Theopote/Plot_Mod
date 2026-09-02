package com.plot.plugin.earthwork.model;

import com.plot.plugin.earthwork.voxel.RetainingWallGenerator;
/**
 * 分区边界处理策略。
 */
public enum EdgeTreatment {
  /** 垂直切填（第一版默认）。 */
  VERTICAL,
  /** 按挖/填坡比放坡。 */
  CUT_FILL_SLOPE,
  /** 挡土墙（立面截止，实体由 {@code RetainingWallGenerator} 生成）。 */
  RETAINING_WALL,
  /** 边界带贴合现状地形。 */
  MATCH_EXISTING;

  public String i18nKey() {
    return "plugin.earthwork.edge_treatment." + name().toLowerCase();
  }

  public static EdgeTreatment fromId(String id) {
    if (id == null || id.isBlank()) {
      return VERTICAL;
    }
    try {
      return valueOf(id.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return VERTICAL;
    }
  }
}
