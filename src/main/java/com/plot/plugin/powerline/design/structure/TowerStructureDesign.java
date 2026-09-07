package com.plot.plugin.powerline.design.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 参数化塔体结构（与 ConductorAttachment 独立）。 */
public class TowerStructureDesign {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    public static final String DEFAULT_LEG_MATERIAL = "minecraft:iron_bars";
    public static final String DEFAULT_BRACE_MATERIAL = "minecraft:iron_bars";
    public static final String DEFAULT_ARM_MATERIAL = "minecraft:iron_bars";

    private List<TowerStation> stations = new ArrayList<>();
    private List<TowerBay> bays = new ArrayList<>();
    private List<TowerArm> arms = new ArrayList<>();
    private MaterialMix primaryMaterial = MaterialMix.single(DEFAULT_LEG_MATERIAL);
    private MaterialMix braceMaterial = MaterialMix.single(DEFAULT_BRACE_MATERIAL);
    private TowerMemberProfile legProfile = new TowerMemberProfile(1);
    private TowerMemberProfile braceProfile = new TowerMemberProfile(1);

    public List<TowerStation> getStations() {
        return stations;
    }

    public void setStations(List<TowerStation> stations) {
        this.stations = new ArrayList<>();
        if (stations == null) {
            return;
        }
        for (TowerStation station : stations) {
            if (station != null) {
                this.stations.add(station.copy());
            }
        }
        sortStations();
    }

    public void addStation(TowerStation station) {
        if (station != null) {
            stations.add(station.copy());
            sortStations();
        }
    }

    public void removeStation(String stationId) {
        if (stationId == null) {
            return;
        }
        stations.removeIf(station -> stationId.equals(station.getId()));
        bays.removeIf(bay ->
            stationId.equals(bay.getLowerStationId()) || stationId.equals(bay.getUpperStationId()));
    }

    public TowerStation findStation(String stationId) {
        if (stationId == null) {
            return null;
        }
        for (TowerStation station : stations) {
            if (stationId.equals(station.getId())) {
                return station;
            }
        }
        return null;
    }

    public List<TowerStation> sortedStations() {
        List<TowerStation> sorted = new ArrayList<>(stations);
        sorted.sort(Comparator.comparingDouble(TowerStation::getHeight));
        return sorted;
    }

    public List<TowerBay> getBays() {
        return bays;
    }

    public void setBays(List<TowerBay> bays) {
        this.bays = new ArrayList<>();
        if (bays == null) {
            return;
        }
        for (TowerBay bay : bays) {
            if (bay != null) {
                this.bays.add(bay.copy());
            }
        }
    }

    public void addBay(TowerBay bay) {
        if (bay != null) {
            bays.add(bay.copy());
        }
    }

    public TowerBay findBay(String lowerStationId, String upperStationId) {
        for (TowerBay bay : bays) {
            if (Objects.equals(lowerStationId, bay.getLowerStationId())
                    && Objects.equals(upperStationId, bay.getUpperStationId())) {
                return bay;
            }
        }
        return null;
    }

    public TowerBay findOrCreateBay(String lowerStationId, String upperStationId) {
        TowerBay existing = findBay(lowerStationId, upperStationId);
        if (existing != null) {
            return existing;
        }
        TowerBay bay = new TowerBay(lowerStationId, upperStationId);
        bays.add(bay);
        return bay;
    }

    public List<TowerArm> getArms() {
        return arms;
    }

    public void setArms(List<TowerArm> arms) {
        this.arms = new ArrayList<>();
        if (arms == null) {
            return;
        }
        for (TowerArm arm : arms) {
            if (arm != null) {
                this.arms.add(arm.copy());
            }
        }
    }

    public void addArm(TowerArm arm) {
        if (arm != null) {
            arms.add(arm.copy());
        }
    }

    public void removeArm(String armId) {
        if (armId == null) {
            return;
        }
        arms.removeIf(arm -> armId.equals(arm.getId()));
    }

    public MaterialMix getPrimaryMaterial() {
        return primaryMaterial;
    }

    public void setPrimaryMaterial(MaterialMix primaryMaterial) {
        this.primaryMaterial = primaryMaterial != null
            ? primaryMaterial.copy()
            : MaterialMix.single(DEFAULT_LEG_MATERIAL);
    }

    public MaterialMix getBraceMaterial() {
        return braceMaterial;
    }

    public void setBraceMaterial(MaterialMix braceMaterial) {
        this.braceMaterial = braceMaterial != null
            ? braceMaterial.copy()
            : MaterialMix.single(DEFAULT_BRACE_MATERIAL);
    }

    public TowerMemberProfile getLegProfile() {
        return legProfile;
    }

    public void setLegProfile(TowerMemberProfile legProfile) {
        this.legProfile = legProfile != null ? legProfile.copy() : new TowerMemberProfile(1);
    }

    public TowerMemberProfile getBraceProfile() {
        return braceProfile;
    }

    public void setBraceProfile(TowerMemberProfile braceProfile) {
        this.braceProfile = braceProfile != null ? braceProfile.copy() : new TowerMemberProfile(1);
    }

    public boolean hasStations() {
        return !stations.isEmpty();
    }

    public double maxHeight() {
        return sortedStations().stream()
            .mapToDouble(TowerStation::getHeight)
            .max()
            .orElse(0.0);
    }

    public double maxHalfWidth() {
        return sortedStations().stream()
            .mapToDouble(TowerStation::getHalfWidth)
            .max()
            .orElse(0.0);
    }

    public double maxHalfDepth() {
        return sortedStations().stream()
            .mapToDouble(TowerStation::getHalfDepth)
            .max()
            .orElse(0.0);
    }

    public TowerStructureDesign copy() {
        TowerStructureDesign copy = new TowerStructureDesign();
        copy.setStations(stations);
        copy.setBays(bays);
        copy.setArms(arms);
        copy.setPrimaryMaterial(primaryMaterial);
        copy.setBraceMaterial(braceMaterial);
        copy.setLegProfile(legProfile);
        copy.setBraceProfile(braceProfile);
        return copy;
    }

    public String toJson() {
        return GSON.toJson(StructureData.from(this));
    }

    public static TowerStructureDesign fromJson(String json) {
        StructureData data = GSON.fromJson(json, StructureData.class);
        return data != null ? data.toDesign() : null;
    }

    private void sortStations() {
        stations.sort(Comparator.comparingDouble(TowerStation::getHeight));
    }

    static class StationData {
        String id;
        double height;
        double halfWidth;
        double halfDepth;
    }

    static class BayData {
        String lowerStationId;
        String upperStationId;
        String frontBackBracing;
        String sideBracing;
        boolean horizontalRing = true;
    }

    static class ArmData {
        String id;
        double baseHeight;
        String side;
        double lateralReach;
        double longitudinalHalfWidth;
        double verticalDrop;
        String bracing;
        MaterialMix material;
    }

    static class StructureData {
        List<StationData> stations = new ArrayList<>();
        List<BayData> bays = new ArrayList<>();
        List<ArmData> arms = new ArrayList<>();
        MaterialMix primaryMaterial;
        MaterialMix braceMaterial;
        int legThickness = 1;
        int braceThickness = 1;

        static StructureData from(TowerStructureDesign design) {
            StructureData data = new StructureData();
            for (TowerStation station : design.stations) {
                StationData stationData = new StationData();
                stationData.id = station.getId();
                stationData.height = station.getHeight();
                stationData.halfWidth = station.getHalfWidth();
                stationData.halfDepth = station.getHalfDepth();
                data.stations.add(stationData);
            }
            for (TowerBay bay : design.bays) {
                BayData bayData = new BayData();
                bayData.lowerStationId = bay.getLowerStationId();
                bayData.upperStationId = bay.getUpperStationId();
                bayData.frontBackBracing = bay.getFrontBackBracing().name();
                bayData.sideBracing = bay.getSideBracing().name();
                bayData.horizontalRing = bay.isHorizontalRing();
                data.bays.add(bayData);
            }
            for (TowerArm arm : design.arms) {
                ArmData armData = new ArmData();
                armData.id = arm.getId();
                armData.baseHeight = arm.getBaseHeight();
                armData.side = arm.getSide().name();
                armData.lateralReach = arm.getLateralReach();
                armData.longitudinalHalfWidth = arm.getLongitudinalHalfWidth();
                armData.verticalDrop = arm.getVerticalDrop();
                armData.bracing = arm.getBracing().name();
                armData.material = arm.getMaterial();
                data.arms.add(armData);
            }
            data.primaryMaterial = design.primaryMaterial;
            data.braceMaterial = design.braceMaterial;
            data.legThickness = design.legProfile.getThickness();
            data.braceThickness = design.braceProfile.getThickness();
            return data;
        }

        TowerStructureDesign toDesign() {
            TowerStructureDesign design = new TowerStructureDesign();
            if (stations != null) {
                for (StationData stationData : stations) {
                    if (stationData == null) {
                        continue;
                    }
                    design.addStation(new TowerStation(
                        stationData.id,
                        stationData.height,
                        stationData.halfWidth,
                        stationData.halfDepth));
                }
            }
            if (bays != null) {
                for (BayData bayData : bays) {
                    if (bayData == null) {
                        continue;
                    }
                    TowerBay bay = new TowerBay(bayData.lowerStationId, bayData.upperStationId);
                    if (bayData.frontBackBracing != null) {
                        bay.setFrontBackBracing(BracingPattern.valueOf(bayData.frontBackBracing));
                    }
                    if (bayData.sideBracing != null) {
                        bay.setSideBracing(BracingPattern.valueOf(bayData.sideBracing));
                    }
                    bay.setHorizontalRing(bayData.horizontalRing);
                    design.addBay(bay);
                }
            }
            if (arms != null) {
                for (ArmData armData : arms) {
                    if (armData == null) {
                        continue;
                    }
                    TowerArm arm = new TowerArm(armData.id, armData.baseHeight, armData.lateralReach);
                    if (armData.side != null) {
                        arm.setSide(TowerArmSide.valueOf(armData.side));
                    }
                    arm.setLongitudinalHalfWidth(armData.longitudinalHalfWidth);
                    arm.setVerticalDrop(armData.verticalDrop);
                    if (armData.bracing != null) {
                        arm.setBracing(BracingPattern.valueOf(armData.bracing));
                    }
                    if (armData.material != null) {
                        arm.setMaterial(armData.material);
                    }
                    design.addArm(arm);
                }
            }
            if (primaryMaterial != null) {
                design.setPrimaryMaterial(primaryMaterial);
            }
            if (braceMaterial != null) {
                design.setBraceMaterial(braceMaterial);
            }
            design.setLegProfile(new TowerMemberProfile(legThickness));
            design.setBraceProfile(new TowerMemberProfile(braceThickness));
            return design;
        }
    }
}
