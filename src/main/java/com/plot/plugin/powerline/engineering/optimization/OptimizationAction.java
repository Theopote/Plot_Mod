package com.plot.plugin.powerline.engineering.optimization;

/** 工程优化建议动作。 */
public class OptimizationAction {
    private OptimizationActionType type;
    private String poleSiteId;
    private int poleIndex;
    private double stationing;
    private String spanId;
    private String currentDesignId;
    private String proposedDesignId;
    private String message;

    public OptimizationActionType getType() {
        return type;
    }

    public void setType(OptimizationActionType type) {
        this.type = type;
    }

    public String getPoleSiteId() {
        return poleSiteId;
    }

    public void setPoleSiteId(String poleSiteId) {
        this.poleSiteId = poleSiteId;
    }

    public int getPoleIndex() {
        return poleIndex;
    }

    public void setPoleIndex(int poleIndex) {
        this.poleIndex = poleIndex;
    }

    public double getStationing() {
        return stationing;
    }

    public void setStationing(double stationing) {
        this.stationing = Math.max(0.0, stationing);
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getCurrentDesignId() {
        return currentDesignId;
    }

    public void setCurrentDesignId(String currentDesignId) {
        this.currentDesignId = currentDesignId;
    }

    public String getProposedDesignId() {
        return proposedDesignId;
    }

    public void setProposedDesignId(String proposedDesignId) {
        this.proposedDesignId = proposedDesignId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
