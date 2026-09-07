package com.plot.plugin.powerline.geometry;

import com.plot.plugin.powerline.design.AttachmentRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 单跨导线的可分析几何（独立于 BlockRecord）。 */
public class ConductorSpanGeometry {
    private String spanId;
    private String attachmentId;
    private AttachmentRole role;
    private int startPoleIndex;
    private int endPoleIndex;
    private String startPoleSiteId;
    private String endPoleSiteId;
    private final List<ConductorSample> samples = new ArrayList<>();
    private double spanLength;
    private double minimumGroundClearance = Double.MAX_VALUE;

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public AttachmentRole getRole() {
        return role;
    }

    public void setRole(AttachmentRole role) {
        this.role = role;
    }

    public int getStartPoleIndex() {
        return startPoleIndex;
    }

    public void setStartPoleIndex(int startPoleIndex) {
        this.startPoleIndex = startPoleIndex;
    }

    public int getEndPoleIndex() {
        return endPoleIndex;
    }

    public void setEndPoleIndex(int endPoleIndex) {
        this.endPoleIndex = endPoleIndex;
    }

    public String getStartPoleSiteId() {
        return startPoleSiteId;
    }

    public void setStartPoleSiteId(String startPoleSiteId) {
        this.startPoleSiteId = startPoleSiteId;
    }

    public String getEndPoleSiteId() {
        return endPoleSiteId;
    }

    public void setEndPoleSiteId(String endPoleSiteId) {
        this.endPoleSiteId = endPoleSiteId;
    }

    public List<ConductorSample> getSamples() {
        return Collections.unmodifiableList(samples);
    }

    public void addSample(ConductorSample sample) {
        if (sample != null) {
            samples.add(sample);
        }
    }

    public double getSpanLength() {
        return spanLength;
    }

    public void setSpanLength(double spanLength) {
        this.spanLength = spanLength;
    }

    public double getMinimumGroundClearance() {
        return minimumGroundClearance;
    }

    public void setMinimumGroundClearance(double minimumGroundClearance) {
        this.minimumGroundClearance = minimumGroundClearance;
    }
}
