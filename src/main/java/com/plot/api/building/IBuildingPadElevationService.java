package com.plot.api.building;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * 土方插件向建筑提供的垫层设计标高查询（打破 Building ↔ Earthwork 编译环）。
 * <p>
 * 由 {@code earthwork_balance} 插件实现；建筑侧仅依赖本接口与 {@link BuildingPadElevationStatus}。
 */
public interface IBuildingPadElevationService {

    /**
     * 解析 EARTHWORK_OWNED 垫层设计标高；BUILDING_LINKED 或未关联时返回 {@code null}。
     */
    Integer resolveEarthworkOwnedPadElevation(String buildingId, List<Vec2d> footprintPoints);

    /** 描述建筑与垫层的关联模式与当前可解析标高。 */
    BuildingPadElevationStatus describePadLink(String buildingId, List<Vec2d> footprintPoints);
}
