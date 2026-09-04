package com.plot.plugin.building.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.OpeningKind;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.model.spec.WallFacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 建筑项目（管理已认领的多个建筑轮廓）
 */
public class BuildingProject {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    private final Map<String, BuildingFootprint> buildings = new LinkedHashMap<>();

    public Map<String, BuildingFootprint> getBuildings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
    }

    public BuildingFootprint getBuilding(String id) {
        return buildings.get(id);
    }

    public BuildingFootprint addBuilding(BuildingFootprint footprint) {
        if (footprint == null) {
            throw new IllegalArgumentException("Building footprint cannot be null");
        }
        if (footprint.getId() == null || footprint.getId().isBlank()) {
            throw new IllegalArgumentException("Building footprint id cannot be blank");
        }
        buildings.put(footprint.getId(), footprint);
        return footprint;
    }

    public void removeBuilding(String id) {
        buildings.remove(id);
    }

    public int getBuildingCount() {
        return buildings.size();
    }

    public double getTotalArea() {
        return buildings.values().stream().mapToDouble(BuildingFootprint::computeArea).sum();
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    /**
     * 解析 JSON。损坏内容抛 {@link IllegalArgumentException}，不得静默变成空项目。
     */
    public static BuildingProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new BuildingProject();
        }
        try {
            ProjectData data = GSON.fromJson(json, ProjectData.class);
            return data != null ? data.toProject() : new BuildingProject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid building project JSON", e);
        }
    }

    /**
     * 原子保存：先写临时文件再 rename。
     */
    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static BuildingProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new BuildingProject();
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse building project: " + file.getFileName(), e);
        }
    }

    BuildingProject deepCopy() {
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

    static class DoorData {
        int wallSegmentIndex;
        double positionRatio;
        int floor;
        int width;
        int height;
    }

    static class FloorPlateData {
        int floorStart;
        int floorEnd;
        List<Vec2dData> outerPoints = new ArrayList<>();
    }

    static class WallFacadeData {
        int wallSegmentIndex;
        int windowSpacing;
        int windowWidth;
        int windowHeight;
        int windowSillHeight;
    }

    static class OpeningData {
        String kind;
        int wallSegmentIndex;
        double positionRatio;
        int floor;
        int width;
        int height;
        int bottomOffset;
    }

    static class BuildingData {
        String id;
        String name;
        List<Vec2dData> outerPoints = new ArrayList<>();
        boolean isRectangular;
        int floors;
        int floorHeight;
        int wallThickness;
        MaterialMix wallMaterial;
        MaterialMix floorMaterial;
        String roofMaterial;
        String foundationFillMaterial;
        String roofType;
        int roofPitchRatio;
        Integer manualBaseElevation;
        int windowSpacing;
        int windowWidth;
        int windowHeight;
        int windowSillHeight;
        List<DoorData> doors = new ArrayList<>();
        List<FloorPlateData> floorPlates = new ArrayList<>();
        List<WallFacadeData> wallFacades = new ArrayList<>();
        List<OpeningData> openings = new ArrayList<>();
        Boolean parapetEnabled;
        Integer parapetHeight;
        String parapetMaterial;
        List<CanopyData> canopies = new ArrayList<>();
        List<BalconyData> balconies = new ArrayList<>();
        String presetId;
    }

    static class CanopyData {
        int wallSegmentIndex;
        double positionRatio;
        int floor;
        int width;
        int depth;
        int clearance;
        String material;
    }

    static class BalconyData {
        int wallSegmentIndex;
        double positionRatio;
        int floor;
        int width;
        int depth;
        String slabMaterial;
        String railingMaterial;
    }

    static class ProjectData {
        List<BuildingData> buildings = new ArrayList<>();

        static ProjectData from(BuildingProject project) {
            ProjectData data = new ProjectData();
            for (BuildingFootprint building : project.buildings.values()) {
                BuildingData buildingData = new BuildingData();
                buildingData.id = building.getId();
                buildingData.name = building.getName();
                for (Vec2d point : building.getOuterPoints()) {
                    buildingData.outerPoints.add(new Vec2dData(point));
                }
                buildingData.isRectangular = building.isRectangular();
                buildingData.floors = building.getFloors();
                buildingData.floorHeight = building.getFloorHeight();
                buildingData.wallThickness = building.getWallThickness();
                buildingData.wallMaterial = building.getWallMaterial();
                buildingData.floorMaterial = building.getFloorMaterial();
                buildingData.roofMaterial = building.getRoofMaterial();
                buildingData.foundationFillMaterial = building.getFoundationFillMaterial();
                buildingData.roofType = building.getRoofType().name();
                buildingData.roofPitchRatio = building.getRoofPitchRatio();
                buildingData.manualBaseElevation = building.getManualBaseElevation();
                buildingData.windowSpacing = building.getWindowSpacing();
                buildingData.windowWidth = building.getWindowWidth();
                buildingData.windowHeight = building.getWindowHeight();
                buildingData.windowSillHeight = building.getWindowSillHeight();
                for (FloorPlateSpec plate : building.getFloorPlates()) {
                    FloorPlateData plateData = new FloorPlateData();
                    plateData.floorStart = plate.floorStart();
                    plateData.floorEnd = plate.floorEnd();
                    for (Vec2d point : plate.outerPoints()) {
                        plateData.outerPoints.add(new Vec2dData(point));
                    }
                    buildingData.floorPlates.add(plateData);
                }
                for (WallFacadeSpec facade : building.getWallFacades()) {
                    WallFacadeData facadeData = new WallFacadeData();
                    facadeData.wallSegmentIndex = facade.wallSegmentIndex();
                    WindowPatternSpec pattern = facade.windowPattern();
                    facadeData.windowSpacing = pattern.spacing();
                    facadeData.windowWidth = pattern.width();
                    facadeData.windowHeight = pattern.height();
                    facadeData.windowSillHeight = pattern.sillHeight();
                    buildingData.wallFacades.add(facadeData);
                }
                for (OpeningSpec opening : building.getOpenings()) {
                    OpeningData openingData = new OpeningData();
                    openingData.kind = opening.kind().name();
                    openingData.wallSegmentIndex = opening.wallSegmentIndex();
                    openingData.positionRatio = opening.positionRatio();
                    openingData.floor = opening.floor();
                    openingData.width = opening.width();
                    openingData.height = opening.height();
                    openingData.bottomOffset = opening.bottomOffset();
                    buildingData.openings.add(openingData);
                    if (opening.kind() == OpeningKind.DOOR) {
                        DoorData doorData = new DoorData();
                        doorData.wallSegmentIndex = opening.wallSegmentIndex();
                        doorData.positionRatio = opening.positionRatio();
                        doorData.floor = opening.floor();
                        doorData.width = opening.width();
                        doorData.height = opening.height();
                        buildingData.doors.add(doorData);
                    }
                }
                buildingData.parapetEnabled = building.isParapetEnabled();
                if (building.isParapetEnabled()) {
                    buildingData.parapetHeight = building.getParapetHeight();
                    buildingData.parapetMaterial = building.getParapetMaterial();
                }
                for (BuildingFootprint.Canopy canopy : building.getCanopies()) {
                    CanopyData canopyData = new CanopyData();
                    canopyData.wallSegmentIndex = canopy.wallSegmentIndex;
                    canopyData.positionRatio = canopy.positionRatio;
                    canopyData.floor = canopy.floor;
                    canopyData.width = canopy.width;
                    canopyData.depth = canopy.depth;
                    canopyData.clearance = canopy.clearance;
                    canopyData.material = canopy.material;
                    buildingData.canopies.add(canopyData);
                }
                for (BuildingFootprint.Balcony balcony : building.getBalconies()) {
                    BalconyData balconyData = new BalconyData();
                    balconyData.wallSegmentIndex = balcony.wallSegmentIndex;
                    balconyData.positionRatio = balcony.positionRatio;
                    balconyData.floor = balcony.floor;
                    balconyData.width = balcony.width;
                    balconyData.depth = balcony.depth;
                    balconyData.slabMaterial = balcony.slabMaterial;
                    balconyData.railingMaterial = balcony.railingMaterial;
                    buildingData.balconies.add(balconyData);
                }
                buildingData.presetId = building.getPresetId();
                data.buildings.add(buildingData);
            }
            return data;
        }

        BuildingProject toProject() {
            BuildingProject project = new BuildingProject();
            for (BuildingData buildingData : buildings) {
                if (buildingData.outerPoints == null || buildingData.outerPoints.size() < 3) {
                    continue;
                }
                List<Vec2d> points = new ArrayList<>();
                for (Vec2dData pointData : buildingData.outerPoints) {
                    if (pointData != null) {
                        points.add(pointData.toVec2d());
                    }
                }
                if (points.size() < 3) {
                    continue;
                }
                String id = buildingData.id != null && !buildingData.id.isBlank()
                    ? buildingData.id
                    : UUID.randomUUID().toString();
                BuildingFootprint footprint = new BuildingFootprint(
                    id, points, buildingData.isRectangular);
                footprint.setName(buildingData.name);
                footprint.setFloors(buildingData.floors);
                footprint.setFloorHeight(buildingData.floorHeight);
                footprint.setWallThickness(buildingData.wallThickness);
                if (buildingData.wallMaterial != null) {
                    footprint.setWallMaterial(buildingData.wallMaterial);
                }
                if (buildingData.floorMaterial != null) {
                    footprint.setFloorMaterial(buildingData.floorMaterial);
                }
                if (buildingData.roofMaterial != null) {
                    footprint.setRoofMaterial(buildingData.roofMaterial);
                }
                if (buildingData.foundationFillMaterial != null) {
                    footprint.setFoundationFillMaterial(buildingData.foundationFillMaterial);
                }
                if (buildingData.roofType != null) {
                    try {
                        footprint.setRoofType(BuildingFootprint.RoofType.valueOf(buildingData.roofType));
                    } catch (IllegalArgumentException ignored) {
                        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
                    }
                }
                footprint.setRoofPitchRatio(buildingData.roofPitchRatio);
                footprint.setManualBaseElevation(buildingData.manualBaseElevation);
                footprint.setWindowSpacing(buildingData.windowSpacing);
                footprint.setWindowWidth(buildingData.windowWidth);
                footprint.setWindowHeight(buildingData.windowHeight);
                footprint.setWindowSillHeight(buildingData.windowSillHeight);
                if (buildingData.openings != null && !buildingData.openings.isEmpty()) {
                    List<OpeningSpec> openings = new ArrayList<>();
                    for (OpeningData openingData : buildingData.openings) {
                        OpeningKind kind = parseOpeningKind(openingData.kind);
                        openings.add(new OpeningSpec(
                            kind,
                            openingData.wallSegmentIndex,
                            openingData.positionRatio,
                            openingData.floor,
                            openingData.width,
                            openingData.height,
                            openingData.bottomOffset
                        ));
                    }
                    footprint.setOpenings(openings);
                } else if (buildingData.doors != null && !buildingData.doors.isEmpty()) {
                    // legacy JSON: doors[] → OpeningSpec.DOOR
                    List<OpeningSpec> openings = new ArrayList<>();
                    for (DoorData doorData : buildingData.doors) {
                        openings.add(OpeningSpec.door(
                            doorData.wallSegmentIndex,
                            doorData.positionRatio,
                            doorData.floor,
                            doorData.width,
                            doorData.height
                        ));
                    }
                    footprint.setOpenings(openings);
                }
                if (buildingData.floorPlates != null && !buildingData.floorPlates.isEmpty()) {
                    List<FloorPlateSpec> plates = new ArrayList<>();
                    for (FloorPlateData plateData : buildingData.floorPlates) {
                        if (plateData.outerPoints == null || plateData.outerPoints.size() < 3) {
                            continue;
                        }
                        List<Vec2d> platePoints = new ArrayList<>();
                        for (Vec2dData pointData : plateData.outerPoints) {
                            if (pointData != null) {
                                platePoints.add(pointData.toVec2d());
                            }
                        }
                        if (platePoints.size() >= 3) {
                            plates.add(FloorPlateSpec.of(
                                plateData.floorStart,
                                plateData.floorEnd,
                                platePoints));
                        }
                    }
                    footprint.setFloorPlates(plates);
                }
                if (buildingData.wallFacades != null && !buildingData.wallFacades.isEmpty()) {
                    List<WallFacadeSpec> facades = new ArrayList<>();
                    for (WallFacadeData facadeData : buildingData.wallFacades) {
                        facades.add(WallFacadeSpec.of(
                            facadeData.wallSegmentIndex,
                            new WindowPatternSpec(
                                facadeData.windowSpacing,
                                facadeData.windowWidth,
                                facadeData.windowHeight,
                                facadeData.windowSillHeight
                            )
                        ));
                    }
                    footprint.setWallFacades(facades);
                }
                if (Boolean.TRUE.equals(buildingData.parapetEnabled)) {
                    footprint.setParapetEnabled(true);
                    if (buildingData.parapetHeight != null) {
                        footprint.setParapetHeight(buildingData.parapetHeight);
                    }
                    footprint.setParapetMaterial(buildingData.parapetMaterial);
                }
                if (buildingData.canopies != null) {
                    for (CanopyData canopyData : buildingData.canopies) {
                        footprint.addCanopy(new BuildingFootprint.Canopy(
                            canopyData.wallSegmentIndex,
                            canopyData.positionRatio,
                            canopyData.floor,
                            canopyData.width,
                            canopyData.depth,
                            canopyData.clearance,
                            canopyData.material));
                    }
                }
                if (buildingData.balconies != null) {
                    for (BalconyData balconyData : buildingData.balconies) {
                        footprint.addBalcony(new BuildingFootprint.Balcony(
                            balconyData.wallSegmentIndex,
                            balconyData.positionRatio,
                            balconyData.floor,
                            balconyData.width,
                            balconyData.depth,
                            balconyData.slabMaterial,
                            balconyData.railingMaterial));
                    }
                }
                if (buildingData.presetId != null) {
                    footprint.setPresetId(buildingData.presetId);
                }
                project.addBuilding(footprint);
            }
            return project;
        }

        private static OpeningKind parseOpeningKind(String kind) {
            if (kind == null || kind.isBlank()) {
                return OpeningKind.DOOR;
            }
            try {
                return OpeningKind.valueOf(kind.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return OpeningKind.DOOR;
            }
        }
    }
}
