package com.plot.plugin.powerline.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfigData;
import com.plot.plugin.powerline.path.PowerLineSourceDescriptor;
import com.plot.plugin.powerline.style.StyleOverrides;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.math.BlockPos;

/**
 * 电力线路项目（管理已认领的线路）。
 */
public class PowerLineProject {
    /** Current on-disk schema. */
    public static final int SCHEMA_VERSION = 9;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    private final Map<String, PowerLineFootprint> lines = new LinkedHashMap<>();
    private final List<PlacedSingleTower> placedSingleTowers = new ArrayList<>();

    public Map<String, PowerLineFootprint> getLines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(lines));
    }

    public PowerLineFootprint getLine(String id) {
        return lines.get(id);
    }

    public PowerLineFootprint addLine(PowerLineFootprint line) {
        if (line == null) {
            throw new IllegalArgumentException("Power line footprint cannot be null");
        }
        if (line.getId() == null || line.getId().isBlank()) {
            throw new IllegalArgumentException("Power line id cannot be blank");
        }
        lines.put(line.getId(), line);
        return line;
    }

    public void removeLine(String id) {
        lines.remove(id);
    }

    public int getLineCount() {
        return lines.size();
    }

    public List<PlacedSingleTower> getPlacedSingleTowers() {
        return Collections.unmodifiableList(new ArrayList<>(placedSingleTowers));
    }

    public void addPlacedSingleTower(PlacedSingleTower tower) {
        if (tower != null) {
            placedSingleTowers.add(tower);
        }
    }

    public void removePlacedSingleTower(String towerId) {
        if (towerId == null || towerId.isBlank()) {
            return;
        }
        placedSingleTowers.removeIf(tower -> towerId.equals(tower.getId()));
    }

    public void removeLastPlacedSingleTower() {
        if (!placedSingleTowers.isEmpty()) {
            placedSingleTowers.removeLast();
        }
    }

    public double getTotalPathLength() {
        return lines.values().stream().mapToDouble(PowerLineFootprint::computePathLength).sum();
    }

    public double getTotalWorldPathLength(com.plot.api.world.ICoordinateService coordinates) {
        return lines.values().stream()
            .mapToDouble(line -> line.computeWorldPathLength(coordinates))
            .sum();
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    public static PowerLineProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new PowerLineProject();
        }
        try {
            ProjectData data = GSON.fromJson(json, ProjectData.class);
            return data != null ? data.toProject() : new PowerLineProject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid power line project JSON", e);
        }
    }

    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static PowerLineProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new PowerLineProject();
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse power line project: " + file.getFileName(), e);
        }
    }

    PowerLineProject deepCopy() {
        return fromJson(toJson());
    }

    static class Vec2dData {
        double x;
        double y;

        Vec2dData() {
        }

        Vec2dData(Vec2d vec) {
            this.x = vec.x;
            this.y = vec.y;
        }

        Vec2d toVec2d() {
            return new Vec2d(x, y);
        }
    }

    static class PoleOverrideData {
        double pathDistance;
        String roleOverride;
        String poleDesignOverrideId;

        static PoleOverrideData from(PoleOverride override) {
            PoleOverrideData data = new PoleOverrideData();
            data.pathDistance = override.getPathDistance();
            if (override.getRoleOverride() != null) {
                data.roleOverride = override.getRoleOverride().name();
            }
            data.poleDesignOverrideId = override.getPoleDesignOverrideId();
            return data;
        }

        PoleOverride toOverride() {
            PoleOverride override = new PoleOverride(pathDistance);
            if (roleOverride != null && !roleOverride.isBlank()) {
                try {
                    override.setRoleOverride(TowerRole.valueOf(roleOverride));
                } catch (IllegalArgumentException ignored) {
                    // ignore unknown roles in legacy files
                }
            }
            override.setPoleDesignOverrideId(poleDesignOverrideId);
            return override;
        }
    }

    static class LayoutConstraintData {
        double requiredStationing;
        String reason;

        static LayoutConstraintData from(PoleLayoutConstraint constraint) {
            LayoutConstraintData data = new LayoutConstraintData();
            data.requiredStationing = constraint.getRequiredStationing();
            data.reason = constraint.getReason();
            return data;
        }

        PoleLayoutConstraint toConstraint() {
            return new PoleLayoutConstraint(requiredStationing, reason);
        }
    }

    static class BlockRecordData {
        int x;
        int y;
        int z;
        String previousBlockId;
        String newBlockId;

        static BlockRecordData from(BlockRecord record) {
            BlockRecordData data = new BlockRecordData();
            data.x = record.pos.getX();
            data.y = record.pos.getY();
            data.z = record.pos.getZ();
            data.previousBlockId = record.previousBlockId;
            data.newBlockId = record.newBlockId;
            return data;
        }

        BlockRecord toRecord() {
            return new BlockRecord(new BlockPos(x, y, z), previousBlockId, newBlockId);
        }
    }

    static class PlacedSingleTowerData {
        String id;
        double planX;
        double planY;
        int rotationQuadrant;
        String designLabel;
        String styleLineId;
        String placementStatus;
        Integer expectedBlockCount;
        List<BlockRecordData> blockRecords = new ArrayList<>();

        static PlacedSingleTowerData from(PlacedSingleTower tower) {
            PlacedSingleTowerData data = new PlacedSingleTowerData();
            data.id = tower.getId();
            data.planX = tower.getPlanPoint().x;
            data.planY = tower.getPlanPoint().y;
            data.rotationQuadrant = tower.getRotationQuadrant();
            data.designLabel = tower.getDesignLabel();
            data.styleLineId = tower.getStyleLineId();
            data.placementStatus = tower.getPlacementStatus().name();
            data.expectedBlockCount = tower.getExpectedBlockCount();
            for (BlockRecord record : tower.getBlockRecords()) {
                data.blockRecords.add(BlockRecordData.from(record));
            }
            return data;
        }

        PlacedSingleTower toTower() {
            if (id == null || id.isBlank()) {
                return null;
            }
            List<BlockRecord> records = new ArrayList<>();
            if (blockRecords != null) {
                for (BlockRecordData recordData : blockRecords) {
                    if (recordData != null) {
                        records.add(recordData.toRecord());
                    }
                }
            }
            SingleTowerPlacementStatus status = SingleTowerPlacementStatus.FULL;
            if (placementStatus != null && !placementStatus.isBlank()) {
                try {
                    status = SingleTowerPlacementStatus.valueOf(placementStatus);
                } catch (IllegalArgumentException ignored) {
                    status = SingleTowerPlacementStatus.fromCounts(
                        records.size(),
                        expectedBlockCount != null ? expectedBlockCount : records.size());
                }
            }
            int expected = expectedBlockCount != null
                ? expectedBlockCount
                : records.size();
            return new PlacedSingleTower(
                id,
                planX,
                planY,
                rotationQuadrant,
                designLabel,
                styleLineId,
                records,
                status,
                expected);
        }
    }

    static class StyleOverridesData {
        Double sagRatio;
        Double maxSagDepth;
        MaterialMix wireMaterial;
        MaterialMix poleMaterial;
        MaterialMix topWireMaterial;
        Double preferredSpacing;
        Double recommendedMinSpacing;
        String poleDesignId;
        String towerFamilyId;

        static StyleOverridesData from(StyleOverrides overrides) {
            StyleOverridesData data = new StyleOverridesData();
            if (overrides == null || overrides.isEmpty()) {
                return data;
            }
            data.sagRatio = overrides.getSagRatio();
            data.maxSagDepth = overrides.getMaxSagDepth();
            data.wireMaterial = overrides.getWireMaterial();
            data.poleMaterial = overrides.getPoleMaterial();
            data.topWireMaterial = overrides.getTopWireMaterial();
            data.preferredSpacing = overrides.getPreferredSpacing();
            data.recommendedMinSpacing = overrides.getRecommendedMinSpacing();
            data.poleDesignId = overrides.getPoleDesignId();
            data.towerFamilyId = overrides.getTowerFamilyId();
            return data;
        }

        void applyTo(StyleOverrides overrides) {
            if (overrides == null) {
                return;
            }
            overrides.clear();
            overrides.setSagRatio(sagRatio);
            overrides.setMaxSagDepth(maxSagDepth);
            overrides.setWireMaterial(wireMaterial);
            overrides.setPoleMaterial(poleMaterial);
            overrides.setTopWireMaterial(topWireMaterial);
            overrides.setPreferredSpacing(preferredSpacing);
            overrides.setRecommendedMinSpacing(recommendedMinSpacing);
            overrides.setPoleDesignId(poleDesignId);
            overrides.setTowerFamilyId(towerFamilyId);
        }
    }

    static class LineData {
        String id;
        String name;
        List<Vec2dData> pathPoints = new ArrayList<>();
        String roadId;
        double minPoleSpacing = 15.0;
        double maxPoleSpacing = 30.0;
        double cornerAngleThreshold = 5.0;
        double poleHeight = 10.0;
        double sagRatio = 0.15;
        double maxSagDepth = com.plot.plugin.powerline.PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        MaterialMix wireMaterial;
        MaterialMix poleMaterial;
        String poleDesignId;
        String towerFamilyId;
        String stylePresetId;
        MaterialMix topWireMaterial;
        List<PoleOverrideData> poleOverrides = new ArrayList<>();
        List<LayoutConstraintData> layoutConstraints = new ArrayList<>();
        boolean lineChecksEnabled = true;
        boolean terrainAvoidanceEnabled = true;
        boolean automaticTowerSelectionEnabled;
        boolean perSiteParametricHeightEnabled;
        boolean spacingCustomized;
        String poleSpacingMode;
        int targetTowerCount = 2;
        StyleOverridesData styleOverrides;
        TowerGeneratorConfigData parametricTowerConfig;
        String sourceShapeId;
        String sourceKind;
        boolean sourceClosed;
        List<Vec2dData> sourcePolylinePoints = new ArrayList<>();
        List<Vec2dData> sourceBezierControlPoints = new ArrayList<>();
    }

    static class ProjectData {
        int schemaVersion = SCHEMA_VERSION;
        List<LineData> lines = new ArrayList<>();
        List<PlacedSingleTowerData> placedSingleTowers = new ArrayList<>();

        static ProjectData from(PowerLineProject project) {
            ProjectData data = new ProjectData();
            data.schemaVersion = SCHEMA_VERSION;
            for (PowerLineFootprint line : project.lines.values()) {
                LineData lineData = new LineData();
                lineData.id = line.getId();
                lineData.name = line.getName();
                for (Vec2d point : line.getPathPoints()) {
                    lineData.pathPoints.add(new Vec2dData(point));
                }
                lineData.roadId = line.getRoadId();
                lineData.minPoleSpacing = line.getMinPoleSpacing();
                lineData.maxPoleSpacing = line.getMaxPoleSpacing();
                lineData.cornerAngleThreshold = line.getCornerAngleThreshold();
                lineData.poleHeight = line.getPoleHeight();
                lineData.sagRatio = line.getSagRatio();
                lineData.maxSagDepth = line.getMaxSagDepth();
                lineData.wireMaterial = line.getWireMaterial();
                lineData.poleMaterial = line.getPoleMaterial();
                lineData.poleDesignId = line.getPoleDesignId();
                lineData.towerFamilyId = line.getTowerFamilyId();
                lineData.stylePresetId = line.getStylePresetId();
                lineData.topWireMaterial = line.getTopWireMaterial();
                for (PoleOverride override : line.getPoleOverrides()) {
                    lineData.poleOverrides.add(PoleOverrideData.from(override));
                }
                for (PoleLayoutConstraint constraint : line.getLayoutConstraints()) {
                    lineData.layoutConstraints.add(LayoutConstraintData.from(constraint));
                }
                lineData.lineChecksEnabled = line.isLineChecksEnabled();
                lineData.terrainAvoidanceEnabled = line.isTerrainAvoidanceEnabled();
                lineData.automaticTowerSelectionEnabled = line.isAutomaticTowerSelectionEnabled();
                lineData.perSiteParametricHeightEnabled = line.isPerSiteParametricHeightEnabled();
                lineData.spacingCustomized = line.isSpacingCustomized();
                lineData.poleSpacingMode = line.getPoleSpacingMode().name();
                lineData.targetTowerCount = line.getTargetTowerCount();
                com.plot.plugin.powerline.style.PowerLineStyleEditor.syncOverridesFromFootprint(line);
                lineData.styleOverrides = StyleOverridesData.from(line.getStyleOverrides());
                lineData.parametricTowerConfig = TowerGeneratorConfigData.from(line.getParametricTowerConfig());
                PowerLineSourceDescriptor source = line.getSourceDescriptor();
                if (source != null) {
                    lineData.sourceShapeId = source.shapeId();
                    lineData.sourceKind = source.kind().name();
                    lineData.sourceClosed = source.closed();
                    for (Vec2d point : source.polylinePoints()) {
                        lineData.sourcePolylinePoints.add(new Vec2dData(point));
                    }
                    for (Vec2d point : source.bezierControlPoints()) {
                        lineData.sourceBezierControlPoints.add(new Vec2dData(point));
                    }
                }
                data.lines.add(lineData);
            }
            for (PlacedSingleTower tower : project.placedSingleTowers) {
                data.placedSingleTowers.add(PlacedSingleTowerData.from(tower));
            }
            return data;
        }

        PowerLineProject toProject() {
            PowerLineProject project = new PowerLineProject();
            if (lines == null) {
                lines = new ArrayList<>();
            }
            for (LineData lineData : lines) {
                if (lineData == null || lineData.id == null || lineData.id.isBlank()) {
                    continue;
                }
                if (lineData.pathPoints == null || lineData.pathPoints.size() < 2) {
                    continue;
                }
                List<Vec2d> points = new ArrayList<>(lineData.pathPoints.size());
                for (Vec2dData pointData : lineData.pathPoints) {
                    if (pointData != null) {
                        points.add(pointData.toVec2d());
                    }
                }
                if (points.size() < 2) {
                    continue;
                }
                PowerLineFootprint footprint = new PowerLineFootprint(
                    lineData.id,
                    lineData.roadId != null ? lineData.roadId : lineData.id);
                footprint.setPathPoints(points);
                if (lineData.name != null) {
                    footprint.setName(lineData.name);
                }
                footprint.setMinPoleSpacing(lineData.minPoleSpacing);
                footprint.setMaxPoleSpacing(lineData.maxPoleSpacing);
                footprint.setCornerAngleThreshold(lineData.cornerAngleThreshold);
                footprint.setPoleHeight(lineData.poleHeight);
                footprint.setSagRatio(lineData.sagRatio);
                footprint.setMaxSagDepth(lineData.maxSagDepth);
                if (lineData.wireMaterial != null) {
                    footprint.setWireMaterial(lineData.wireMaterial);
                }
                if (lineData.poleMaterial != null) {
                    footprint.setPoleMaterial(lineData.poleMaterial);
                }
                footprint.setPoleDesignId(lineData.poleDesignId);
                footprint.setTowerFamilyId(lineData.towerFamilyId);
                footprint.setStylePresetId(lineData.stylePresetId);
                if (lineData.topWireMaterial != null) {
                    footprint.setTopWireMaterial(lineData.topWireMaterial);
                }
                if (lineData.poleOverrides != null) {
                    List<PoleOverride> overrides = new ArrayList<>();
                    for (PoleOverrideData overrideData : lineData.poleOverrides) {
                        if (overrideData != null) {
                            overrides.add(overrideData.toOverride());
                        }
                    }
                    footprint.setPoleOverrides(overrides);
                }
                if (lineData.layoutConstraints != null) {
                    List<PoleLayoutConstraint> constraints = new ArrayList<>();
                    for (LayoutConstraintData constraintData : lineData.layoutConstraints) {
                        if (constraintData != null) {
                            constraints.add(constraintData.toConstraint());
                        }
                    }
                    footprint.setLayoutConstraints(constraints);
                }
                footprint.setLineChecksEnabled(lineData.lineChecksEnabled);
                footprint.setTerrainAvoidanceEnabled(lineData.terrainAvoidanceEnabled);
                footprint.setAutomaticTowerSelectionEnabled(lineData.automaticTowerSelectionEnabled);
                footprint.setPerSiteParametricHeightEnabled(lineData.perSiteParametricHeightEnabled);
                footprint.setSpacingCustomized(lineData.spacingCustomized);
                if (lineData.poleSpacingMode != null && !lineData.poleSpacingMode.isBlank()) {
                    try {
                        footprint.setPoleSpacingMode(PoleSpacingMode.valueOf(lineData.poleSpacingMode));
                    } catch (IllegalArgumentException ignored) {
                        footprint.setPoleSpacingMode(PoleSpacingMode.AUTO_SPACING);
                    }
                }
                footprint.setTargetTowerCount(lineData.targetTowerCount);
                StyleOverridesData overridesData = lineData.styleOverrides != null
                    ? lineData.styleOverrides
                    : new StyleOverridesData();
                overridesData.applyTo(footprint.getStyleOverrides());
                if (lineData.parametricTowerConfig != null) {
                    footprint.setParametricTowerConfig(lineData.parametricTowerConfig.toConfig());
                }
                if (lineData.sourceKind != null && !lineData.sourceKind.isBlank()) {
                    try {
                        PowerLineSourceDescriptor.Kind kind =
                            PowerLineSourceDescriptor.Kind.valueOf(lineData.sourceKind);
                        List<Vec2d> polyline = new ArrayList<>();
                        if (lineData.sourcePolylinePoints != null) {
                            for (Vec2dData pointData : lineData.sourcePolylinePoints) {
                                if (pointData != null) {
                                    polyline.add(pointData.toVec2d());
                                }
                            }
                        }
                        List<Vec2d> bezierControls = new ArrayList<>();
                        if (lineData.sourceBezierControlPoints != null) {
                            for (Vec2dData pointData : lineData.sourceBezierControlPoints) {
                                if (pointData != null) {
                                    bezierControls.add(pointData.toVec2d());
                                }
                            }
                        }
                        footprint.setSourceDescriptor(switch (kind) {
                            case POLYLINE -> PowerLineSourceDescriptor.polyline(
                                lineData.sourceShapeId, polyline);
                            case BEZIER -> PowerLineSourceDescriptor.bezier(
                                lineData.sourceShapeId, bezierControls, lineData.sourceClosed);
                        });
                    } catch (IllegalArgumentException ignored) {
                        // keep legacy polyline-only footprint
                    }
                }
                project.addLine(footprint);
            }
            if (placedSingleTowers != null) {
                for (PlacedSingleTowerData towerData : placedSingleTowers) {
                    if (towerData == null) {
                        continue;
                    }
                    PlacedSingleTower tower = towerData.toTower();
                    if (tower != null) {
                        project.addPlacedSingleTower(tower);
                    }
                }
            }
            return project;
        }
    }
}
