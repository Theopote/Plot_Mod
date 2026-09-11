package com.plot.plugin.powerline.design;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorMode;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.engineering.TowerEngineeringMetadata;
import com.plot.plugin.powerline.equipment.InsulatorType;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.EnumSet;
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
    private TowerEngineeringMetadata engineeringMetadata;
    private TowerGeneratorConfig generatorConfig;

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
        if (attachments != null && !attachments.isEmpty()) {
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

    public TowerEngineeringMetadata getEngineeringMetadata() {
        return engineeringMetadata;
    }

    public void setEngineeringMetadata(TowerEngineeringMetadata engineeringMetadata) {
        this.engineeringMetadata = engineeringMetadata != null
            ? engineeringMetadata.copy()
            : null;
    }

    public void clearTowerStructure() {
        this.towerStructure = null;
    }

    public TowerGeneratorConfig getGeneratorConfig() {
        return generatorConfig != null ? generatorConfig.copy() : null;
    }

    public void setGeneratorConfig(TowerGeneratorConfig generatorConfig) {
        this.generatorConfig = generatorConfig != null ? generatorConfig.copy() : null;
    }

    public boolean isParametricMode() {
        return generatorConfig != null && generatorConfig.isParametric();
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
        copy.setGeneratorConfig(generatorConfig);
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
        TowerArmAttachmentBinding.ensureV2Bindings(design);
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
        String bundleVisual;
        String armId;
        String bindingMode;
        Double normalizedPosition;
        Double verticalAnchorOffset;
        String insulatorType;
        String insulatorAssemblyId;
        boolean enabled = true;
    }

    static class LayerData {
        String shape;
        int height;
        int crossarmLength;
        MaterialMix material;
    }

    static class EngineeringMetadataData {
        double nominalHeight;
        double maxRecommendedSpan;
        double maxRecommendedDeflectionAngle;
        List<String> supportedRoles = new ArrayList<>();

        static EngineeringMetadataData from(TowerEngineeringMetadata metadata) {
            if (metadata == null) {
                return null;
            }
            EngineeringMetadataData data = new EngineeringMetadataData();
            data.nominalHeight = metadata.getNominalHeight();
            data.maxRecommendedSpan = metadata.getMaxRecommendedSpan();
            data.maxRecommendedDeflectionAngle = metadata.getMaxRecommendedDeflectionAngle();
            for (TowerRole role : metadata.getSupportedRoles()) {
                if (role != null) {
                    data.supportedRoles.add(role.name());
                }
            }
            return data;
        }

        TowerEngineeringMetadata toMetadata() {
            TowerEngineeringMetadata metadata = new TowerEngineeringMetadata();
            metadata.setNominalHeight(nominalHeight);
            metadata.setMaxRecommendedSpan(maxRecommendedSpan);
            metadata.setMaxRecommendedDeflectionAngle(maxRecommendedDeflectionAngle);
            EnumSet<TowerRole> roles = EnumSet.noneOf(TowerRole.class);
            if (supportedRoles != null) {
                for (String raw : supportedRoles) {
                    TowerRole role = parseTowerRole(raw);
                    if (role != null) {
                        roles.add(role);
                    }
                }
            }
            if (!roles.isEmpty()) {
                metadata.setSupportedRoles(roles);
            }
            return metadata;
        }

        private static TowerRole parseTowerRole(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return TowerRole.valueOf(raw.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    static class DesignData {
        String id;
        String name;
        List<LayerData> layers = new ArrayList<>();
        List<AttachmentData> attachments = new ArrayList<>();
        String towerStructureJson;
        EngineeringMetadataData engineeringMetadata;
        GeneratorConfigData generatorConfig;

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
                TowerArmAttachmentBinding.ResolvedLocalOffsets resolved =
                    TowerArmAttachmentBinding.resolveLocalOffsets(attachment, design.towerStructure);
                attachmentData.lateralOffset = resolved.lateral();
                attachmentData.verticalOffset = resolved.vertical();
                attachmentData.longitudinalOffset = resolved.longitudinal();
                attachmentData.role = attachment.getRole().name();
                attachmentData.insulatorMaterial = attachment.getInsulatorMaterial();
                attachmentData.insulatorLength = attachment.getInsulatorLength();
                if (attachment.getBundleVisual() != BundleVisual.SINGLE) {
                    attachmentData.bundleVisual = attachment.getBundleVisual().name();
                }
                attachmentData.armId = attachment.getArmId();
                if (attachment.isBound()) {
                    attachmentData.bindingMode = AttachmentBindingMode.BOUND.name();
                    attachmentData.normalizedPosition = attachment.getNormalizedPosition();
                    attachmentData.verticalAnchorOffset = attachment.getVerticalAnchorOffset();
                } else if (attachment.getBindingMode() == AttachmentBindingMode.FREE) {
                    attachmentData.bindingMode = AttachmentBindingMode.FREE.name();
                }
                attachmentData.insulatorType = attachment.getInsulatorType().name();
                attachmentData.insulatorAssemblyId = attachment.getInsulatorAssemblyId();
                attachmentData.enabled = attachment.isEnabled();
                data.attachments.add(attachmentData);
            }
            if (design.towerStructure != null) {
                data.towerStructureJson = design.towerStructure.toJson();
            }
            data.engineeringMetadata = EngineeringMetadataData.from(design.engineeringMetadata);
            data.generatorConfig = GeneratorConfigData.from(design.generatorConfig);
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
                    PoleLayer.Shape shape = PoleLayer.Shape.parseOrNull(layerData.shape);
                    if (shape == null) {
                        continue;
                    }
                    PoleLayer layer = new PoleLayer();
                    layer.setShape(shape);
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
                    attachment.setBundleVisual(BundleVisual.parse(attachmentData.bundleVisual));
                    attachment.setArmId(attachmentData.armId);
                    AttachmentBindingMode bindingMode = AttachmentBindingMode.parse(attachmentData.bindingMode);
                    if (bindingMode != null) {
                        attachment.setBindingMode(bindingMode);
                    }
                    if (attachmentData.normalizedPosition != null) {
                        attachment.setNormalizedPosition(attachmentData.normalizedPosition);
                    }
                    if (attachmentData.verticalAnchorOffset != null) {
                        attachment.setVerticalAnchorOffset(attachmentData.verticalAnchorOffset);
                    }
                    attachment.setInsulatorType(InsulatorType.parse(attachmentData.insulatorType));
                    attachment.setInsulatorAssemblyId(attachmentData.insulatorAssemblyId);
                    attachment.setEnabled(attachmentData.enabled);
                    restoredAttachments.add(attachment);
                }
            }
            design.setAttachments(restoredAttachments);
            if (towerStructureJson != null && !towerStructureJson.isBlank()) {
                design.setTowerStructure(TowerStructureDesign.fromJson(towerStructureJson));
            }
            if (engineeringMetadata != null) {
                design.setEngineeringMetadata(engineeringMetadata.toMetadata());
            }
            if (generatorConfig != null) {
                design.setGeneratorConfig(generatorConfig.toConfig());
            }
            return design;
        }
    }

    static class GeneratorConfigData {
        String profileId;
        String mode;
        double height;
        double baseWidth;
        double armSpan;
        double depthScale;
        double waistRatio;
        String density;

        static GeneratorConfigData from(TowerGeneratorConfig config) {
            if (config == null) {
                return null;
            }
            GeneratorConfigData data = new GeneratorConfigData();
            data.profileId = config.profileId();
            data.mode = config.mode().name();
            data.height = config.parameters().height();
            data.baseWidth = config.parameters().baseWidth();
            data.armSpan = config.parameters().armSpan();
            data.depthScale = config.parameters().depthScale();
            data.waistRatio = config.parameters().waistRatio();
            data.density = config.parameters().density().name();
            return data;
        }

        TowerGeneratorConfig toConfig() {
            if (profileId == null || profileId.isBlank()) {
                return null;
            }
            TowerGeneratorMode parsedMode = TowerGeneratorMode.PARAMETRIC;
            if (mode != null && !mode.isBlank()) {
                try {
                    parsedMode = TowerGeneratorMode.valueOf(mode.trim());
                } catch (IllegalArgumentException ignored) {
                    parsedMode = TowerGeneratorMode.PARAMETRIC;
                }
            }
            StructureDensity parsedDensity = StructureDensity.MEDIUM;
            if (density != null && !density.isBlank()) {
                try {
                    parsedDensity = StructureDensity.valueOf(density.trim());
                } catch (IllegalArgumentException ignored) {
                    parsedDensity = StructureDensity.MEDIUM;
                }
            }
            double parsedWaistRatio = waistRatio > 0.0 ? waistRatio : TowerParameterSet.DEFAULT_WAIST_RATIO;
            return new TowerGeneratorConfig(
                profileId,
                parsedMode,
                new TowerParameterSet(height, baseWidth, armSpan, depthScale, parsedWaistRatio, parsedDensity));
        }
    }
}
