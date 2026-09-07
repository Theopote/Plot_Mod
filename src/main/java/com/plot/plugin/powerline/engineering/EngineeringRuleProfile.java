package com.plot.plugin.powerline.engineering;

import java.util.Objects;

/** 可配置的工程规则配置（规划默认值，非规范认证）。 */
public class EngineeringRuleProfile {
    public static final String GENERIC_PLANNING_ID = "profile/generic_planning";

    private final String id;
    private String name;
    private String description;
    private ClearanceRules clearance = new ClearanceRules();
    private SpanRules span = new SpanRules();
    private AngleRules angle = new AngleRules();
    private TowerRules tower = new TowerRules();

    public EngineeringRuleProfile(String id, String name) {
        this.id = id != null && !id.isBlank() ? id : GENERIC_PLANNING_ID;
        this.name = name != null ? name : id;
        this.description = "Planning defaults — not a certified regulatory standard";
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ClearanceRules getClearance() {
        return clearance;
    }

    public void setClearance(ClearanceRules clearance) {
        this.clearance = clearance != null ? clearance.copy() : new ClearanceRules();
    }

    public SpanRules getSpan() {
        return span;
    }

    public void setSpan(SpanRules span) {
        this.span = span != null ? span.copy() : new SpanRules();
    }

    public AngleRules getAngle() {
        return angle;
    }

    public void setAngle(AngleRules angle) {
        this.angle = angle != null ? angle.copy() : new AngleRules();
    }

    public TowerRules getTower() {
        return tower;
    }

    public void setTower(TowerRules tower) {
        this.tower = tower != null ? tower.copy() : new TowerRules();
    }

    public EngineeringRuleProfile copy() {
        EngineeringRuleProfile copy = new EngineeringRuleProfile(id, name);
        copy.description = description;
        copy.clearance = clearance.copy();
        copy.span = span.copy();
        copy.angle = angle.copy();
        copy.tower = tower.copy();
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EngineeringRuleProfile other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
