package com.plot.plugin.building.benchmark;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/** Shared district massing footprints for benchmark and scenario tests. */
public final class DistrictMassingFixtures {
    private DistrictMassingFixtures() {
    }

    public static BuildingFootprint massingFootprint(int index, int floors) {
        double x = (index % 20) * 12.0;
        double z = (index / 20) * 10.0;
        BuildingFootprint footprint = new BuildingFootprint("d-" + index, List.of(
            new Vec2d(x, z),
            new Vec2d(x + 8, z),
            new Vec2d(x + 8, z + 6),
            new Vec2d(x, z + 6)
        ), true);
        footprint.setName("B" + index);
        footprint.setFloors(floors);
        footprint.setFloorHeight(3);
        footprint.setWallThickness(1);
        footprint.setWindowSpacing(0);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        return footprint;
    }

    public static List<BuildingFootprint> district(int count, int floors) {
        List<BuildingFootprint> buildings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buildings.add(massingFootprint(i, floors));
        }
        return buildings;
    }

    /** Same geometry as {@code source} but distinct id (overlap / later-wins tests). */
    public static BuildingFootprint sameGeometry(BuildingFootprint source, String id) {
        BuildingFootprint copy = new BuildingFootprint(id, source.getOuterPoints(), true);
        copy.setName(source.getName() + "-overlap");
        copy.setFloors(source.getFloors());
        copy.setFloorHeight(source.getFloorHeight());
        copy.setWallThickness(source.getWallThickness());
        copy.setWindowSpacing(source.getWindowSpacing());
        copy.setRoofType(source.getRoofType());
        return copy;
    }
}
