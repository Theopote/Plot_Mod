package com.plot.plugin.powerline.design;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 杆塔分层造型。
 */
public class PoleDesign {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    private final String id;
    private String name;
    private List<PoleLayer> layers = new ArrayList<>();
    private List<ConductorAttachment> attachments = new ArrayList<>();
    private TowerStructureDesign towerStructure;
    private com.plot.plugin.powerline.engineering.TowerEngineeringMetadata engineeringMetadata;

    public PoleDesign(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public PoleDesign(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null && !name.isBlank() ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PoleLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<PoleLayer> layers) {
        this.layers = new ArrayList<>();
        if (layers == null) {
            return;
        }
        for (PoleLayer layer : layers) {
            if (layer != null) {
                this.layers.add(layer.copy());
            }
        }
    }

    public List<ConductorAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ConductorAttachment> attachments) {
        this.attachments = new ArrayList<>();
        if (attachments == null) {
            return;
        }
        for (ConductorAttachment attachment : attachments) {
            if (attachment != null) {
                this.attachments.add(attachment.copy());
            }
        }
    }

    public void addAttachment(ConductorAttachment attachment) {
        if (attachment != null) {
            attachments.add(attachment.copy());
        }
    }

    public void removeAttachment(String attachmentId) {
        if (attachmentId == null) {
            return;
        }
        attachments.removeIf(attachment -> attachmentId.equals(attachment.getId()));
    }

    public ConductorAttachment findAttachment(String attachmentId) {
        if (attachmentId == null) {
            return null;
        }
        for (ConductorAttachment attachment : attachments) {
            if (attachmentId.equals(attachment.getId())) {
                return attachment;
            }
        }
        return null;
    }

    public boolean hasEnabledAttachments() {
        for (ConductorAttachment attachment : attachments) {
            if (attachment.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    /** 为无挂点的旧设计/自定义杆型补上单导线挂点。 */
    public void ensureDefaultConductorAttachments() {
        if (hasEnabledAttachments()) {
            return;
        }
        double offset = totalHeight();
        if (offset <= 0.0) {
            offset = 10.0;
        }
        setAttachments(ConductorAttachmentPresets.singleConductor(offset));
    }

    public TowerStructureDesign getTowerStructure() {
        return towerStructure;
    }

    public void setTowerStructure(TowerStructureDesign towerStructure) {
        this.towerStructure = towerStructure != null ? towerStructure.copy() : null;
    }

    public boolean hasTowerStructure() {
        return towerStructure != null && towerStructure.hasStations();
    }

    public com.plot.plugin.powerline.engineering.TowerEngineeringMetadata getEngineeringMetadata() {
        return engineeringMetadata;
    }

    public void setEngineeringMetadata(
            com.plot.plugin.powerline.engineering.TowerEngineeringMetadata engineeringMetadata) {
        this.engineeringMetadata = engineeringMetadata != null
            ? engineeringMetadata.copy()
            : null;
    }

    public com.plot.plugin.powerline.engineering.TowerEngineeringMetadata effectiveEngineeringMetadata(
            com.plot.plugin.powerline.model.TowerRole role) {
        if (engineeringMetadata != null) {
            return engineeringMetadata;
        }
        return com.plot.plugin.powerline.engineering.TowerEngineeringMetadata.defaultsForRole(role);
    }

    public void clearTowerStructure() {
        this.towerStructure = null;
    }

    public int totalHeight() {
        if (hasTowerStructure()) {
            return (int) Math.round(towerStructure.maxHeight());
        }
        return layers.stream().mapToInt(PoleLayer::getHeight).sum();
    }

    /**
     * 自下而上遍历层栈时，最后一个 {@link PoleLayer.Shape#CROSSARM} 为导线悬挂层
     * （即物理位置最高的横担）。Legacy fallback 专用。
     */
    public int conductorCrossarmLayerIndex() {
        int index = -1;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).getShape() == PoleLayer.Shape.CROSSARM) {
                index = i;
            }
        }
        return index;
    }

    public int wireHangHeightFromGround(int groundY) {
        if (hasTowerStructure()) {
            return groundY + (int) Math.round(towerStructure.maxHeight());
        }
        int currentY = groundY + 1;
        int wireHangY = groundY + totalHeight();
        for (PoleLayer layer : layers) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                wireHangY = currentY + layer.getHeight() - 1;
            }
            currentY += layer.getHeight();
        }
        return wireHangY;
    }

    public PoleDesign copy() {
        PoleDesign copy = new PoleDesign(id, name);
        copy.setLayers(layers);
        copy.setAttachments(attachments);
        copy.setTowerStructure(towerStructure);
        copy.setEngineeringMetadata(engineeringMetadata);
        return copy;
    }

    public String toJson() {
        return GSON.toJson(DesignData.from(this));
    }

    public static PoleDesign fromJson(String json) {
        DesignData data = GSON.fromJson(json, DesignData.class);
        if (data == null) {
            return null;
        }
        PoleDesign design = data.toDesign();
        design.ensureDefaultConductorAttachments();
        return design;
    }

    static class AttachmentData {
        String id;
        String name;
        double lateralOffset;
        double verticalOffset;
        double longitudinalOffset;
        String role;
        MaterialMix insulatorMaterial;
        int insulatorLength;
        boolean enabled = true;
    }

    static class LayerData {
        String shape;
        int height;
        int crossarmLength;
        MaterialMix material;
    }

    static class DesignData {
        String id;
        String name;
        List<LayerData> layers = new ArrayList<>();
        List<AttachmentData> attachments = new ArrayList<>();
        String towerStructureJson;

        static DesignData from(PoleDesign design) {
            DesignData data = new DesignData();
            data.id = design.id;
            data.name = design.name;
            for (PoleLayer layer : design.layers) {
                LayerData layerData = new LayerData();
                layerData.shape = layer.getShape().name();
                layerData.height = layer.getHeight();
                layerData.crossarmLength = layer.getCrossarmLength();
                layerData.material = layer.getMaterial();
                data.layers.add(layerData);
            }
            for (ConductorAttachment attachment : design.attachments) {
                AttachmentData attachmentData = new AttachmentData();
                attachmentData.id = attachment.getId();
                attachmentData.name = attachment.getName();
                attachmentData.lateralOffset = attachment.getLateralOffset();
                attachmentData.verticalOffset = attachment.getVerticalOffset();
                attachmentData.longitudinalOffset = attachment.getLongitudinalOffset();
                attachmentData.role = attachment.getRole().name();
                attachmentData.insulatorMaterial = attachment.getInsulatorMaterial();
                attachmentData.insulatorLength = attachment.getInsulatorLength();
                attachmentData.enabled = attachment.isEnabled();
                data.attachments.add(attachmentData);
            }
            if (design.towerStructure != null) {
                data.towerStructureJson = design.towerStructure.toJson();
            }
            return data;
        }

        PoleDesign toDesign() {
            PoleDesign design = new PoleDesign(id, name);
            List<PoleLayer> restored = new ArrayList<>();
            if (layers != null) {
                for (LayerData layerData : layers) {
                    if (layerData == null || layerData.shape == null) {
                        continue;
                    }
                    PoleLayer layer = new PoleLayer();
                    layer.setShape(PoleLayer.Shape.valueOf(layerData.shape));
                    layer.setHeight(layerData.height);
                    layer.setCrossarmLength(layerData.crossarmLength);
                    if (layerData.material != null) {
                        layer.setMaterial(layerData.material);
                    }
                    restored.add(layer);
                }
            }
            design.setLayers(restored);

            List<ConductorAttachment> restoredAttachments = new ArrayList<>();
            if (attachments != null) {
                for (AttachmentData attachmentData : attachments) {
                    if (attachmentData == null) {
                        continue;
                    }
                    ConductorAttachment attachment = new ConductorAttachment(
                        attachmentData.id,
                        attachmentData.name);
                    attachment.setLateralOffset(attachmentData.lateralOffset);
                    attachment.setVerticalOffset(attachmentData.verticalOffset);
                    attachment.setLongitudinalOffset(attachmentData.longitudinalOffset);
                    if (attachmentData.role != null) {
                        attachment.setRole(AttachmentRole.parseRole(attachmentData.role));
                    }
                    if (attachmentData.insulatorMaterial != null) {
                        attachment.setInsulatorMaterial(attachmentData.insulatorMaterial);
                    }
                    attachment.setInsulatorLength(attachmentData.insulatorLength);
                    attachment.setEnabled(attachmentData.enabled);
                    restoredAttachments.add(attachment);
                }
            }
            design.setAttachments(restoredAttachments);
            if (towerStructureJson != null && !towerStructureJson.isBlank()) {
                design.setTowerStructure(TowerStructureDesign.fromJson(towerStructureJson));
            }
            return design;
        }
    }
}
