package com.plot.plugin.powerline.equipment;

/** 转角塔上的局部跳线定义。 */
public class JumperDefinition {
    private String attachmentId;
    private double sagRatio = 0.08;
    private boolean enabled = true;

    public JumperDefinition() {
    }

    public JumperDefinition(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public double getSagRatio() {
        return sagRatio;
    }

    public void setSagRatio(double sagRatio) {
        this.sagRatio = Math.max(0.0, Math.min(1.0, sagRatio));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JumperDefinition copy() {
        JumperDefinition copy = new JumperDefinition(attachmentId);
        copy.sagRatio = sagRatio;
        copy.enabled = enabled;
        return copy;
    }
}
