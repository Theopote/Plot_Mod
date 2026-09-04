package com.plot.plugin.building.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.OpeningKind;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.model.spec.WallFacadeSpec;

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

    public static class Canopy {
        public int wallSegmentIndex;
        public double positionRatio;
        public int floor;
        public int width = 3;
        public int depth = 2;
        public int clearance = 3;
        public String material;

        public Canopy() {
        }

        public Canopy(
                int wallSegmentIndex,
                double positionRatio,
                int floor,
                int width,
                int depth,
                int clearance,
                String material) {
            this.wallSegmentIndex = wallSegmentIndex;
            this.positionRatio = positionRatio;
            this.floor = floor;
            this.width = width;
            this.depth = depth;
            this.clearance = clearance;
            this.material = material;
        }

        public Canopy copy() {
            return new Canopy(wallSegmentIndex, positionRatio, floor, width, depth, clearance, material);
        }
    }

    public static class Balcony {
        public int wallSegmentIndex;
        public double positionRatio;
        public int floor;
        public int width = 3;
        public int depth = 2;
        public String slabMaterial;
        public String railingMaterial;

        public Balcony() {
        }

        public Balcony(
                int wallSegmentIndex,
                double positionRatio,
                int floor,
                int width,
                int depth,
                String slabMaterial,
                String railingMaterial) {
            this.wallSegmentIndex = wallSegmentIndex;
            this.positionRatio = positionRatio;
            this.floor = floor;
            this.width = width;
            this.depth = depth;
            this.slabMaterial = slabMaterial;
            this.railingMaterial = railingMaterial;
        }

        public Balcony copy() {
            return new Balcony(
                wallSegmentIndex, positionRatio, floor, width, depth, slabMaterial, railingMaterial);
        }
    }

    private final String id;
    private String name;
    private List<Vec2d> outerPoints;
    private boolean isRectangular;

    private int floors = 1;
    private int floorHeight = 3;
    private int wallThickness = 1;
    private MaterialMix wallMaterial = MaterialMix.single(DEFAULT_WALL_MATERIAL);
    private MaterialMix floorMaterial = MaterialMix.single(DEFAULT_FLOOR_MATERIAL);
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
    private List<FloorPlateSpec> floorPlates = new ArrayList<>();
    private List<WallFacadeSpec> wallFacades = new ArrayList<>();
    private List<OpeningSpec> openings = new ArrayList<>();

    private boolean parapetEnabled;
    private int parapetHeight = 1;
    private String parapetMaterial;
    private List<Canopy> canopies = new ArrayList<>();
    private List<Balcony> balconies = new ArrayList<>();

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

    public MaterialMix getWallMaterial() {
        return wallMaterial;
    }

    public void setWallMaterial(MaterialMix wallMaterial) {
        this.wallMaterial = wallMaterial != null && wallMaterial.getPrimaryMaterial() != null
            && !wallMaterial.getPrimaryMaterial().isBlank()
            ? wallMaterial
            : MaterialMix.single(DEFAULT_WALL_MATERIAL);
    }

    public void setWallMaterial(String wallMaterial) {
        this.wallMaterial = wallMaterial != null && !wallMaterial.isBlank()
            ? MaterialMix.single(wallMaterial.trim())
            : MaterialMix.single(DEFAULT_WALL_MATERIAL);
    }

    public MaterialMix getFloorMaterial() {
        return floorMaterial;
    }

    public void setFloorMaterial(MaterialMix floorMaterial) {
        this.floorMaterial = floorMaterial != null && floorMaterial.getPrimaryMaterial() != null
            && !floorMaterial.getPrimaryMaterial().isBlank()
            ? floorMaterial
            : MaterialMix.single(DEFAULT_FLOOR_MATERIAL);
    }

    public void setFloorMaterial(String floorMaterial) {
        this.floorMaterial = floorMaterial != null && !floorMaterial.isBlank()
            ? MaterialMix.single(floorMaterial.trim())
            : MaterialMix.single(DEFAULT_FLOOR_MATERIAL);
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
        syncDoorsFromOpeningsIfNeeded();
        return doors.stream().map(DoorOpening::copy).toList();
    }

    public void setDoors(List<DoorOpening> doors) {
        replaceOpeningsOfKind(OpeningKind.DOOR, toOpeningSpecs(doors));
        syncDoorsFromOpenings();
    }

    public void addDoor(DoorOpening door) {
        if (door != null) {
            openings.add(OpeningSpec.from(door));
            syncDoorsFromOpenings();
        }
    }

    public void removeDoor(int index) {
        int doorIndex = 0;
        for (int i = 0; i < openings.size(); i++) {
            if (openings.get(i).kind() == OpeningKind.DOOR) {
                if (doorIndex == index) {
                    openings.remove(i);
                    syncDoorsFromOpenings();
                    return;
                }
                doorIndex++;
            }
        }
    }

    /**
     * 显式立面开洞（门、拱、单窗等）。窗型阵列 pattern 仍由全局/分立面窗型参数控制。
     */
    public List<OpeningSpec> getOpenings() {
        syncDoorsFromOpeningsIfNeeded();
        return openings.stream()
            .map(this::copyOpening)
            .toList();
    }

    public void setOpenings(List<OpeningSpec> openings) {
        this.openings = openings != null
            ? openings.stream().map(this::copyOpening).collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
        syncDoorsFromOpenings();
    }

    public void addOpening(OpeningSpec opening) {
        if (opening != null) {
            openings.add(copyOpening(opening));
            syncDoorsFromOpenings();
        }
    }

    private void syncDoorsFromOpeningsIfNeeded() {
        if (openings.isEmpty() && !doors.isEmpty()) {
            for (DoorOpening door : doors) {
                openings.add(OpeningSpec.from(door));
            }
        }
    }

    private void syncDoorsFromOpenings() {
        doors = openings.stream()
            .filter(opening -> opening.kind() == OpeningKind.DOOR)
            .map(OpeningSpec::toLegacyDoorOpening)
            .map(DoorOpening::copy)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void replaceOpeningsOfKind(OpeningKind kind, List<OpeningSpec> replacements) {
        List<OpeningSpec> retained = openings.stream()
            .filter(opening -> opening.kind() != kind)
            .map(this::copyOpening)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        retained.addAll(replacements);
        openings = retained;
    }

    private static List<OpeningSpec> toOpeningSpecs(List<DoorOpening> doors) {
        if (doors == null || doors.isEmpty()) {
            return List.of();
        }
        return doors.stream().map(OpeningSpec::from).toList();
    }

    private OpeningSpec copyOpening(OpeningSpec opening) {
        return new OpeningSpec(
            opening.kind(),
            opening.wallSegmentIndex(),
            opening.positionRatio(),
            opening.floor(),
            opening.width(),
            opening.height(),
            opening.bottomOffset()
        );
    }

    /**
     * 分楼层轮廓板。为空时表示全楼统一使用 {@link #getOuterPoints()}。
     */
    public List<FloorPlateSpec> getFloorPlates() {
        return floorPlates.stream()
            .map(plate -> FloorPlateSpec.of(plate.floorStart(), plate.floorEnd(), plate.outerPoints()))
            .toList();
    }

    public void setFloorPlates(List<FloorPlateSpec> floorPlates) {
        this.floorPlates = floorPlates != null
            ? floorPlates.stream()
                .map(plate -> FloorPlateSpec.of(plate.floorStart(), plate.floorEnd(), plate.outerPoints()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    /**
     * 分墙段立面覆盖。为空时全楼使用全局窗型参数。
     */
    public List<WallFacadeSpec> getWallFacades() {
        return wallFacades.stream()
            .map(facade -> WallFacadeSpec.of(facade.wallSegmentIndex(), facade.windowPattern()))
            .toList();
    }

    public void setWallFacades(List<WallFacadeSpec> wallFacades) {
        this.wallFacades = wallFacades != null
            ? wallFacades.stream()
                .map(facade -> WallFacadeSpec.of(facade.wallSegmentIndex(), facade.windowPattern()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public boolean isParapetEnabled() {
        return parapetEnabled;
    }

    public void setParapetEnabled(boolean parapetEnabled) {
        this.parapetEnabled = parapetEnabled;
    }

    public int getParapetHeight() {
        return parapetHeight;
    }

    public void setParapetHeight(int parapetHeight) {
        this.parapetHeight = Math.max(1, Math.min(parapetHeight, 8));
    }

    public String getParapetMaterial() {
        return parapetMaterial;
    }

    public void setParapetMaterial(String parapetMaterial) {
        this.parapetMaterial = parapetMaterial;
    }

    public List<Canopy> getCanopies() {
        return canopies.stream().map(Canopy::copy).toList();
    }

    public void setCanopies(List<Canopy> canopies) {
        this.canopies = canopies != null
            ? canopies.stream().map(Canopy::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public void addCanopy(Canopy canopy) {
        if (canopy != null) {
            canopies.add(canopy.copy());
        }
    }

    public List<Balcony> getBalconies() {
        return balconies.stream().map(Balcony::copy).toList();
    }

    public void setBalconies(List<Balcony> balconies) {
        this.balconies = balconies != null
            ? balconies.stream().map(Balcony::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public void addBalcony(Balcony balcony) {
        if (balcony != null) {
            balconies.add(balcony.copy());
        }
    }

    public double computeArea() {
        return Math.abs(signedArea(outerPoints));
    }

    /**
     * 转为分层 {@link com.plot.plugin.building.model.spec.BuildingDefinition}。
     */
    public com.plot.plugin.building.model.spec.BuildingDefinition toDefinition() {
        return com.plot.plugin.building.model.spec.BuildingDefinition.fromFootprint(this);
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
