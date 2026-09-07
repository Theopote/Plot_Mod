package com.plot.plugin.powerline.engineering.selection;

/** 候选塔型评分明细。 */
public record TowerCandidateScore(
        String poleDesignId,
        double score,
        boolean filtered,
        String reason) {
}
