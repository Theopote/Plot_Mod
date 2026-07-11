package com.plot.plugin.building.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已认领的建筑轮廓及生成参数
 */
public class BuildingFootprint {
    public static final String DEFAULT_WALL_MATERIAL = "minecraft:stone_bricks";
    public static final String DEFAULT_FLOOR_MATERIAL = "minecraft:oak_planks";
    public static final String DEFAULT_ROOF_MATERIAL = "minecraft:stone_bricks";
    public static final String DEFAULT_FOUNDATION_FILL = "minecraft:stone";

    public enum RoofType {
        FLAT, GABLE, HIP
    }

    public static class DoorOpening {
        public int wallSegmentIndex;
        public double positionRatio;
        public int floor;
        public int width = 1;
        public int height = 2;

        public DoorOpening() {
        }

        public DoorOpening(int wallSegmentIndex, double positionRatio, int floor, int width, int height) {
            this.wallSegmentIndex = wallSegmentIndex;
            this.positionRatio = positionRatio;
            this.floor = floor;
            this.width = width;
            this.height = height;
        }

        public DoorOpening copy() {
            return new DoorOpening(wallSegmentIndex, positionRatio, floor, width, height);
        }
    }

    private final String id;
    private String name;
    private List<Vec2d> outerPoints;
    private boolean isRectangular;

    private int floors = 1;
    private int floorHeight = 3;
    private int wallThickness = 1;
    private String wallMaterial = DEFAULT_WALL_MATERIAL;
    private String floorMaterial = DEFAULT_FLOOR_MATERIAL;
    private String roofMaterial = DEFAULT_ROOF_MATERIAL;
    private String foundationFillMaterial = DEFAULT_FOUNDATION_FILL;

    private RoofType roofType = RoofType.FLAT;
    private int roofPitchRatio = 1;

    private Integer manualBaseElevation;
    private int windowSpacing = 4;
    private int windowWidth = 1;
    private int windowHeight = 2;
    private int windowSillHeight = 1;

    private List<DoorOpening> doors = new ArrayList<>();

    public BuildingFootprint(List<Vec2d> outerPoints, boolean isRectangular) {
        this(UUID.randomUUID().toString(), outerPoints, isRectangular);
    }

    public BuildingFootprint(String id, List<Vec2d> outerPoints, boolean isRectangular) {
        this.id = id;
        this.outerPoints = copyPoints(outerPoints);
        this.isRectangular = isRectangular;
        this.name = id.substring(0, Math.min(8, id.length()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isBlank() ? name.trim() : this.name;
    }

    public List<Vec2d> getOuterPoints() {
        return copyPoints(outerPoints);
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.outerPoints = copyPoints(outerPoints);
    }

    public boolean isRectangular() {
        return isRectangular;
    }

    public void setRectangular(boolean rectangular) {
        isRectangular = rectangular;
    }

    public int getFloors() {
        return floors;
    }

    public void setFloors(int floors) {
        this.floors = Math.max(1, Math.min(64, floors));
    }

    public int getFloorHeight() {
        return floorHeight;
    }

    public void setFloorHeight(int floorHeight) {
        this.floorHeight = Math.max(2, Math.min(16, floorHeight));
    }

    public int getWallThickness() {
        return wallThickness;
    }

    public void setWallThickness(int wallThickness) {
        this.wallThickness = Math.max(1, Math.min(8, wallThickness));
    }

    public String getWallMaterial() {
        return wallMaterial;
    }

    public void setWallMaterial(String wallMaterial) {
        this.wallMaterial = wallMaterial != null && !wallMaterial.isBlank()
            ? wallMaterial.trim() : DEFAULT_WALL_MATERIAL;
    }

    public String getFloorMaterial() {
        return floorMaterial;
    }

    public void setFloorMaterial(String floorMaterial) {
        this.floorMaterial = floorMaterial != null && !floorMaterial.isBlank()
            ? floorMaterial.trim() : DEFAULT_FLOOR_MATERIAL;
    }

    public String getRoofMaterial() {
        return roofMaterial;
    }

    public void setRoofMaterial(String roofMaterial) {
        this.roofMaterial = roofMaterial != null && !roofMaterial.isBlank()
            ? roofMaterial.trim() : DEFAULT_ROOF_MATERIAL;
    }

    public String getFoundationFillMaterial() {
        return foundationFillMaterial;
    }

    public void setFoundationFillMaterial(String foundationFillMaterial) {
        this.foundationFillMaterial = foundationFillMaterial != null && !foundationFillMaterial.isBlank()
            ? foundationFillMaterial.trim() : DEFAULT_FOUNDATION_FILL;
    }

    public RoofType getRoofType() {
        return roofType;
    }

    public void setRoofType(RoofType roofType) {
        this.roofType = roofType != null ? roofType : RoofType.FLAT;
    }

    public int getRoofPitchRatio() {
        return roofPitchRatio;
    }

    public void setRoofPitchRatio(int roofPitchRatio) {
        this.roofPitchRatio = Math.max(1, Math.min(16, roofPitchRatio));
    }

    public Integer getManualBaseElevation() {
        return manualBaseElevation;
    }

    public void setManualBaseElevation(Integer manualBaseElevation) {
        this.manualBaseElevation = manualBaseElevation;
    }

    public int getWindowSpacing() {
        return windowSpacing;
    }

    public void setWindowSpacing(int windowSpacing) {
        this.windowSpacing = Math.max(0, Math.min(32, windowSpacing));
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = Math.max(1, Math.min(4, windowWidth));
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = Math.max(1, Math.min(6, windowHeight));
    }

    public int getWindowSillHeight() {
        return windowSillHeight;
    }

    public void setWindowSillHeight(int windowSillHeight) {
        this.windowSillHeight = Math.max(0, Math.min(8, windowSillHeight));
    }

    public List<DoorOpening> getDoors() {
        return doors.stream().map(DoorOpening::copy).toList();
    }

    public void setDoors(List<DoorOpening> doors) {
        this.doors = doors != null
            ? doors.stream().map(DoorOpening::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public void addDoor(DoorOpening door) {
        if (door != null) {
            doors.add(door.copy());
        }
    }

    public void removeDoor(int index) {
        if (index >= 0 && index < doors.size()) {
            doors.remove(index);
        }
    }

    public double computeArea() {
        return Math.abs(signedArea(outerPoints));
    }

    public static double signedArea(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % n);
            area += a.x * b.y - b.x * a.y;
        }
        return area / 2.0;
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point != null ? point.copy() : new Vec2d(0, 0));
            }
        }
        return copy;
    }
}
