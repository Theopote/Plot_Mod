package com.plot.plugin.building.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 建筑项目（管理已认领的多个建筑轮廓）
 */
public class BuildingProject {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, BuildingFootprint> buildings = new LinkedHashMap<>();

    public Map<String, BuildingFootprint> getBuildings() {
        return Map.copyOf(buildings);
    }

    public BuildingFootprint getBuilding(String id) {
        return buildings.get(id);
    }

    public BuildingFootprint addBuilding(BuildingFootprint footprint) {
        if (footprint == null) {
            throw new IllegalArgumentException("Building footprint cannot be null");
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

    public static BuildingProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new BuildingProject();
        }
        ProjectData data = GSON.fromJson(json, ProjectData.class);
        return data != null ? data.toProject() : new BuildingProject();
    }

    public void saveTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson());
    }

    public static BuildingProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new BuildingProject();
        }
        return fromJson(Files.readString(file));
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

    static class BuildingData {
        String id;
        String name;
        List<Vec2dData> outerPoints = new ArrayList<>();
        boolean isRectangular;
        int floors;
        int floorHeight;
        int wallThickness;
        String wallMaterial;
        String floorMaterial;
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
                for (BuildingFootprint.DoorOpening door : building.getDoors()) {
                    DoorData doorData = new DoorData();
                    doorData.wallSegmentIndex = door.wallSegmentIndex;
                    doorData.positionRatio = door.positionRatio;
                    doorData.floor = door.floor;
                    doorData.width = door.width;
                    doorData.height = door.height;
                    buildingData.doors.add(doorData);
                }
                data.buildings.add(buildingData);
            }
            return data;
        }

        BuildingProject toProject() {
            BuildingProject project = new BuildingProject();
            for (BuildingData buildingData : buildings) {
                List<Vec2d> points = new ArrayList<>();
                for (Vec2dData pointData : buildingData.outerPoints) {
                    points.add(pointData.toVec2d());
                }
                BuildingFootprint footprint = new BuildingFootprint(
                    buildingData.id, points, buildingData.isRectangular);
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
                List<BuildingFootprint.DoorOpening> doors = new ArrayList<>();
                for (DoorData doorData : buildingData.doors) {
                    doors.add(new BuildingFootprint.DoorOpening(
                        doorData.wallSegmentIndex,
                        doorData.positionRatio,
                        doorData.floor,
                        doorData.width,
                        doorData.height
                    ));
                }
                footprint.setDoors(doors);
                project.addBuilding(footprint);
            }
            return project;
        }
    }
}
