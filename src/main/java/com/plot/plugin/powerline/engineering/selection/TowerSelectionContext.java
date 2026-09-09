package com.plot.plugin.powerline.engineering.selection;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerPoleSite;

/** 自动塔型选择上下文。 */
public class TowerSelectionContext {
    private PowerPoleSite site;
    private double incomingSpan;
    private double outgoingSpan;
    private double deflectionAngle;
    private double requiredAttachmentHeight;
    private double requiredGroundClearance;
    private TowerFamily family;

    public PowerPoleSite getSite() {
        return site;
    }

    public void setSite(PowerPoleSite site) {
        this.site = site;
    }

    public double getIncomingSpan() {
        return incomingSpan;
    }

    public void setIncomingSpan(double incomingSpan) {
        this.incomingSpan = Math.max(0.0, incomingSpan);
    }

    public double getOutgoingSpan() {
        return outgoingSpan;
    }

    public void setOutgoingSpan(double outgoingSpan) {
        this.outgoingSpan = Math.max(0.0, outgoingSpan);
    }

    public double getDeflectionAngle() {
        return deflectionAngle;
    }

    public void setDeflectionAngle(double deflectionAngle) {
        this.deflectionAngle = Math.max(0.0, deflectionAngle);
    }

    public double getRequiredAttachmentHeight() {
        return requiredAttachmentHeight;
    }

    public void setRequiredAttachmentHeight(double requiredAttachmentHeight) {
        this.requiredAttachmentHeight = Math.max(0.0, requiredAttachmentHeight);
    }

    public double getRequiredGroundClearance() {
        return requiredGroundClearance;
    }

    public void setRequiredGroundClearance(double requiredGroundClearance) {
        this.requiredGroundClearance = Math.max(0.0, requiredGroundClearance);
    }

    public TowerFamily getFamily() {
        return family;
    }

    public void setFamily(TowerFamily family) {
        this.family = family;
    }

    public double maxAdjacentSpan() {
        return Math.max(incomingSpan, outgoingSpan);
    }
}
