package com.plot.plugin.powerline.equipment;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachment;

import java.util.Objects;
import java.util.UUID;

/** 绝缘子串定义（可选，优先于 attachment 上的 legacy 字段）。 */
public class InsulatorAssembly {
    private String id;
    private InsulatorType type = InsulatorType.SUSPENSION;
    private int length;
    private MaterialMix material = MaterialMix.single(ConductorAttachment.DEFAULT_INSULATOR_MATERIAL);

    public InsulatorAssembly() {
        this.id = UUID.randomUUID().toString();
    }

    public InsulatorAssembly(String id, InsulatorType type, int length) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.type = type != null ? type : InsulatorType.SUSPENSION;
        setLength(length);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    public InsulatorType getType() {
        return type != null ? type : InsulatorType.SUSPENSION;
    }

    public void setType(InsulatorType type) {
        this.type = type != null ? type : InsulatorType.SUSPENSION;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = Math.max(0, Math.min(16, length));
    }

    public MaterialMix getMaterial() {
        return material;
    }

    public void setMaterial(MaterialMix material) {
        this.material = material != null
            ? material.copy()
            : MaterialMix.single(ConductorAttachment.DEFAULT_INSULATOR_MATERIAL);
    }

    public InsulatorAssembly copy() {
        InsulatorAssembly copy = new InsulatorAssembly(id, type, length);
        copy.material = material != null ? material.copy() : material;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof InsulatorAssembly other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && getType() == other.getType()
            && length == other.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getType(), length);
    }
}
