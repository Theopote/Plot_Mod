package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.equipment.InsulatorType;

import java.util.Objects;
import java.util.UUID;

/**
 * 杆塔局部坐标系下的导线挂点（不存世界坐标）。
 */
public class ConductorAttachment {
    public static final String DEFAULT_INSULATOR_MATERIAL = "minecraft:iron_bars";

    private String id;
    private String name;
    private double lateralOffset;
    private double verticalOffset;
    private double longitudinalOffset;
    private AttachmentRole role = AttachmentRole.PHASE_B;
    private MaterialMix insulatorMaterial = MaterialMix.single(DEFAULT_INSULATOR_MATERIAL);
    private int insulatorLength;
    private InsulatorType insulatorType = InsulatorType.SUSPENSION;
    private String insulatorAssemblyId;
    private BundleVisual bundleVisual = BundleVisual.SINGLE;
    /** 绑定的 {@link com.plot.plugin.powerline.design.structure.TowerArm} id（塔型设计器多层横担）。 */
    private String armId;
    private AttachmentBindingMode bindingMode = AttachmentBindingMode.FREE;
    /**
     * BOUND 模式横向归一化位置：{@code lateral = armReach * lateralScale * normalizedPosition}。
     */
    private double normalizedPosition;
    /** BOUND 模式相对横担挂线高度的竖向偏移。 */
    private double verticalAnchorOffset;
    private boolean enabled = true;

    public ConductorAttachment() {
        this.id = UUID.randomUUID().toString();
        this.name = "Attachment";
    }

    public ConductorAttachment(String id, String name) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "Attachment";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    public String getName() {
        return name != null && !name.isBlank() ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLateralOffset() {
        return lateralOffset;
    }

    public void setLateralOffset(double lateralOffset) {
        this.lateralOffset = clamp(lateralOffset, -32.0, 32.0);
    }

    public double getVerticalOffset() {
        return verticalOffset;
    }

    public void setVerticalOffset(double verticalOffset) {
        this.verticalOffset = clamp(verticalOffset, 1.0, 128.0);
    }

    public double getLongitudinalOffset() {
        return longitudinalOffset;
    }

    public void setLongitudinalOffset(double longitudinalOffset) {
        this.longitudinalOffset = clamp(longitudinalOffset, -16.0, 16.0);
    }

    public AttachmentRole getRole() {
        return role != null ? role : AttachmentRole.AUXILIARY;
    }

    public void setRole(AttachmentRole role) {
        this.role = role != null ? role : AttachmentRole.AUXILIARY;
    }

    public MaterialMix getInsulatorMaterial() {
        return insulatorMaterial;
    }

    public void setInsulatorMaterial(MaterialMix insulatorMaterial) {
        this.insulatorMaterial = insulatorMaterial != null
            ? insulatorMaterial.copy()
            : MaterialMix.single(DEFAULT_INSULATOR_MATERIAL);
    }

    public int getInsulatorLength() {
        return insulatorLength;
    }

    public void setInsulatorLength(int insulatorLength) {
        this.insulatorLength = Math.max(0, Math.min(16, insulatorLength));
    }

    public InsulatorType getInsulatorType() {
        return insulatorType != null ? insulatorType : InsulatorType.SUSPENSION;
    }

    public void setInsulatorType(InsulatorType insulatorType) {
        this.insulatorType = insulatorType != null ? insulatorType : InsulatorType.SUSPENSION;
    }

    public String getInsulatorAssemblyId() {
        return insulatorAssemblyId;
    }

    public void setInsulatorAssemblyId(String insulatorAssemblyId) {
        this.insulatorAssemblyId = insulatorAssemblyId != null && insulatorAssemblyId.isBlank()
            ? null
            : insulatorAssemblyId;
    }

    public BundleVisual getBundleVisual() {
        return bundleVisual != null ? bundleVisual : BundleVisual.SINGLE;
    }

    public void setBundleVisual(BundleVisual bundleVisual) {
        this.bundleVisual = bundleVisual != null ? bundleVisual : BundleVisual.SINGLE;
    }

    public String getArmId() {
        return armId;
    }

    public void setArmId(String armId) {
        this.armId = armId != null && armId.isBlank() ? null : armId;
    }

    public AttachmentBindingMode getBindingMode() {
        return bindingMode != null ? bindingMode : AttachmentBindingMode.FREE;
    }

    public void setBindingMode(AttachmentBindingMode bindingMode) {
        this.bindingMode = bindingMode != null ? bindingMode : AttachmentBindingMode.FREE;
    }

    public boolean isBound() {
        return getBindingMode() == AttachmentBindingMode.BOUND && armId != null && !armId.isBlank();
    }

    public double getNormalizedPosition() {
        return normalizedPosition;
    }

    public void setNormalizedPosition(double normalizedPosition) {
        this.normalizedPosition = clamp(normalizedPosition, -1.0, 1.0);
    }

    public double getVerticalAnchorOffset() {
        return verticalAnchorOffset;
    }

    public void setVerticalAnchorOffset(double verticalAnchorOffset) {
        this.verticalAnchorOffset = clamp(verticalAnchorOffset, -32.0, 32.0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ConductorAttachment copy() {
        ConductorAttachment copy = new ConductorAttachment(id, name);
        copy.lateralOffset = lateralOffset;
        copy.verticalOffset = verticalOffset;
        copy.longitudinalOffset = longitudinalOffset;
        copy.role = role;
        copy.insulatorMaterial = insulatorMaterial != null
            ? insulatorMaterial.copy()
            : MaterialMix.single(DEFAULT_INSULATOR_MATERIAL);
        copy.insulatorLength = insulatorLength;
        copy.insulatorType = insulatorType;
        copy.insulatorAssemblyId = insulatorAssemblyId;
        copy.bundleVisual = bundleVisual;
        copy.armId = armId;
        copy.bindingMode = bindingMode;
        copy.normalizedPosition = normalizedPosition;
        copy.verticalAnchorOffset = verticalAnchorOffset;
        copy.enabled = enabled;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConductorAttachment other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && Objects.equals(name, other.name)
            && Double.compare(lateralOffset, other.lateralOffset) == 0
            && Double.compare(verticalOffset, other.verticalOffset) == 0
            && Double.compare(longitudinalOffset, other.longitudinalOffset) == 0
            && role == other.role
            && insulatorLength == other.insulatorLength
            && insulatorType == other.insulatorType
            && Objects.equals(insulatorAssemblyId, other.insulatorAssemblyId)
            && bundleVisual == other.bundleVisual
            && Objects.equals(armId, other.armId)
            && bindingMode == other.bindingMode
            && Double.compare(normalizedPosition, other.normalizedPosition) == 0
            && Double.compare(verticalAnchorOffset, other.verticalAnchorOffset) == 0
            && enabled == other.enabled
            && materialEquals(insulatorMaterial, other.insulatorMaterial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            lateralOffset,
            verticalOffset,
            longitudinalOffset,
            role,
            insulatorLength,
            insulatorType,
            insulatorAssemblyId,
            bundleVisual,
            armId,
            bindingMode,
            normalizedPosition,
            verticalAnchorOffset,
            enabled,
            insulatorMaterial != null ? insulatorMaterial.getPrimaryMaterial() : null,
            insulatorMaterial != null ? insulatorMaterial.getAccentMaterial() : null,
            insulatorMaterial != null ? insulatorMaterial.getAccentRatio() : 0f);
    }

    private static boolean materialEquals(MaterialMix a, MaterialMix b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Objects.equals(a.getPrimaryMaterial(), b.getPrimaryMaterial())
            && Objects.equals(blankToNull(a.getAccentMaterial()), blankToNull(b.getAccentMaterial()))
            && Float.compare(a.getAccentRatio(), b.getAccentRatio()) == 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
