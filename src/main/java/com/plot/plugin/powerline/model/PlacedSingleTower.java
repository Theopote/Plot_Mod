package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 已落地单塔记录（独立于 {@link PowerLineFootprint}）。 */
public final class PlacedSingleTower {
    private final String id;
    private final double planX;
    private final double planY;
    private final int rotationQuadrant;
    private final String designId;
    private final String designLabel;
    private final String styleLineId;
    private final String stylePresetId;
    private final TowerRole towerRole;
    private final List<BlockRecord> blockRecords;
    private final SingleTowerPlacementStatus placementStatus;
    private final int expectedBlockCount;

    public PlacedSingleTower(
            Vec2d planPoint,
            int rotationQuadrant,
            String designId,
            String designLabel,
            String styleLineId,
            String stylePresetId,
            TowerRole towerRole,
            List<BlockRecord> blockRecords) {
        this(
            UUID.randomUUID().toString(),
            planPoint != null ? planPoint.x : 0.0,
            planPoint != null ? planPoint.y : 0.0,
            rotationQuadrant,
            designId,
            designLabel,
            styleLineId,
            stylePresetId,
            towerRole,
            blockRecords,
            SingleTowerPlacementStatus.FULL,
            blockRecords != null ? blockRecords.size() : 0);
    }

    /** @deprecated prefer {@link #PlacedSingleTower(Vec2d, int, String, String, String, String, TowerRole, List)} */
    @Deprecated
    public PlacedSingleTower(
            Vec2d planPoint,
            int rotationQuadrant,
            String designLabel,
            String styleLineId,
            List<BlockRecord> blockRecords) {
        this(
            planPoint,
            rotationQuadrant,
            "",
            designLabel,
            styleLineId,
            "",
            null,
            blockRecords);
    }

    PlacedSingleTower(
            String id,
            double planX,
            double planY,
            int rotationQuadrant,
            String designLabel,
            String styleLineId,
            List<BlockRecord> blockRecords) {
        this(
            id,
            planX,
            planY,
            rotationQuadrant,
            "",
            designLabel,
            styleLineId,
            "",
            null,
            blockRecords,
            SingleTowerPlacementStatus.FULL,
            blockRecords != null ? blockRecords.size() : 0);
    }

    PlacedSingleTower(
            String id,
            double planX,
            double planY,
            int rotationQuadrant,
            String designLabel,
            String styleLineId,
            List<BlockRecord> blockRecords,
            SingleTowerPlacementStatus placementStatus,
            int expectedBlockCount) {
        this(
            id,
            planX,
            planY,
            rotationQuadrant,
            "",
            designLabel,
            styleLineId,
            "",
            null,
            blockRecords,
            placementStatus,
            expectedBlockCount);
    }

    PlacedSingleTower(
            String id,
            double planX,
            double planY,
            int rotationQuadrant,
            String designId,
            String designLabel,
            String styleLineId,
            String stylePresetId,
            TowerRole towerRole,
            List<BlockRecord> blockRecords,
            SingleTowerPlacementStatus placementStatus,
            int expectedBlockCount) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.planX = planX;
        this.planY = planY;
        this.rotationQuadrant = rotationQuadrant;
        this.designId = designId != null ? designId : "";
        this.designLabel = designLabel != null ? designLabel : "";
        this.styleLineId = styleLineId;
        this.stylePresetId = stylePresetId != null ? stylePresetId : "";
        this.towerRole = towerRole != null ? towerRole : TowerRole.SUSPENSION;
        this.blockRecords = copyRecords(blockRecords);
        this.expectedBlockCount = Math.max(0, expectedBlockCount);
        this.placementStatus = placementStatus != null
            ? placementStatus
            : SingleTowerPlacementStatus.fromCounts(this.blockRecords.size(), this.expectedBlockCount);
    }

    public String getId() {
        return id;
    }

    public Vec2d getPlanPoint() {
        return new Vec2d(planX, planY);
    }

    public int getRotationQuadrant() {
        return rotationQuadrant;
    }

    public String getDesignId() {
        return designId;
    }

    public String getDesignLabel() {
        return designLabel;
    }

    public String getStyleLineId() {
        return styleLineId;
    }

    public String getStylePresetId() {
        return stylePresetId;
    }

    public TowerRole getTowerRole() {
        return towerRole;
    }

    public List<BlockRecord> getBlockRecords() {
        return blockRecords;
    }

    public int getBlockCount() {
        return blockRecords.size();
    }

    public SingleTowerPlacementStatus getPlacementStatus() {
        return placementStatus;
    }

    public int getExpectedBlockCount() {
        return expectedBlockCount;
    }

    public int getPlacedBlockCount() {
        return blockRecords.size();
    }

    /** 按实际落地方块更新登记簿记录（保留 id 与预期方块数）。 */
    public PlacedSingleTower withAppliedPlacement(
            List<BlockRecord> appliedRecords,
            SingleTowerPlacementStatus status) {
        return new PlacedSingleTower(
            id,
            planX,
            planY,
            rotationQuadrant,
            designId,
            designLabel,
            styleLineId,
            stylePresetId,
            towerRole,
            appliedRecords,
            status,
            expectedBlockCount);
    }

    private static List<BlockRecord> copyRecords(List<BlockRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return List.copyOf(records);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PlacedSingleTower other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
