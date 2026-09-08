package com.plot.plugin.powerline.engineering.selection;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.engineering.TowerEngineeringMetadata;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 基于工程元数据的确定性塔型选择（只读规划，不修改用户设计定义）。 */
public final class AutomaticTowerSelector {
    private final PoleDesignResolver designResolver;

    public AutomaticTowerSelector(PoleDesignResolver designResolver) {
        this.designResolver = designResolver;
    }

    public TowerSelectionResult select(TowerSelectionContext context) {
        TowerSelectionResult result = new TowerSelectionResult();
        if (context == null || context.getSite() == null || context.getFamily() == null) {
            result.addReason("missing_context");
            result.setFallbackUsed(true);
            return result;
        }

        TowerRole role = context.getSite().getRole();
        List<TowerCandidate> candidates = buildCandidates(context.getFamily(), role);
        if (candidates.isEmpty()) {
            String fallbackId = context.getFamily().getDesignId(role);
            if (fallbackId != null) {
                result.setSelectedDesignId(fallbackId);
                result.setFallbackUsed(true);
                result.addReason("family_default");
            } else {
                result.addReason("no_suitable_tower");
            }
            return result;
        }

        List<TowerCandidate> viable = new ArrayList<>();
        for (TowerCandidate candidate : candidates) {
            String filterReason = filterReason(candidate, context);
            if (filterReason != null) {
                result.addCandidate(new TowerCandidateScore(
                    candidate.getPoleDesignId(),
                    Double.NEGATIVE_INFINITY,
                    true,
                    filterReason));
                continue;
            }
            candidate.setScore(score(candidate, context));
            viable.add(candidate);
            result.addCandidate(new TowerCandidateScore(
                candidate.getPoleDesignId(),
                candidate.getScore(),
                false,
                "viable"));
        }

        if (viable.isEmpty()) {
            result.addReason("no_suitable_tower");
            String fallbackId = context.getFamily().getDesignId(role);
            if (fallbackId != null) {
                result.setSelectedDesignId(fallbackId);
                result.setFallbackUsed(true);
            }
            return result;
        }

        viable.sort((a, b) -> Double.compare(a.getScore(), b.getScore()));
        TowerCandidate best = viable.getFirst();
        result.setSelectedDesignId(best.getPoleDesignId());
        result.addReason(String.format(
            Locale.ROOT,
            "selected_score|%.1f|%.0f|%.0f|%.0f",
            best.getScore(),
            best.getNominalHeight(),
            best.getSupportedMaxSpan(),
            best.getSupportedMaxAngle()));
        if (context.getDeflectionAngle() > 0.5) {
            result.addReason(String.format(Locale.ROOT, "route_deflection|%.0f", context.getDeflectionAngle()));
        }
        if (context.maxAdjacentSpan() > 0.5) {
            result.addReason(String.format(Locale.ROOT, "adjacent_span|%.0f", context.maxAdjacentSpan()));
        }
        return result;
    }

    private List<TowerCandidate> buildCandidates(TowerFamily family, TowerRole role) {
        Set<String> designIds = new LinkedHashSet<>();
        for (TowerRole mappedRole : TowerRole.values()) {
            String id = family.getDesignId(mappedRole);
            if (id != null && !id.isBlank()) {
                designIds.add(id);
            }
        }
        List<TowerCandidate> candidates = new ArrayList<>();
        for (String designId : designIds) {
            PoleDesign design = designResolver.find(designId);
            if (design == null) {
                continue;
            }
            TowerEngineeringMetadata metadata = design.getEngineeringMetadata();
            if (metadata == null) {
                metadata = TowerEngineeringMetadata.defaultsForRole(role);
            }
            if (!metadata.supportsRole(role)) {
                continue;
            }
            TowerCandidate candidate = new TowerCandidate();
            candidate.setPoleDesignId(designId);
            candidate.setRole(role);
            candidate.setSupportedMaxAngle(metadata.getMaxRecommendedDeflectionAngle());
            candidate.setSupportedMaxSpan(metadata.getMaxRecommendedSpan());
            candidate.setNominalHeight(metadata.getNominalHeight());
            candidates.add(candidate);
        }
        return candidates;
    }

    private static String filterReason(TowerCandidate candidate, TowerSelectionContext context) {
        if (candidate.getSupportedMaxAngle() + 1e-6 < context.getDeflectionAngle()) {
            return "insufficient_angle";
        }
        if (candidate.getSupportedMaxSpan() + 1e-6 < context.maxAdjacentSpan()) {
            return "insufficient_span";
        }
        if (context.getRequiredAttachmentHeight() > 0.5
                && candidate.getNominalHeight() + 1e-6 < context.getRequiredAttachmentHeight()) {
            return "insufficient_height";
        }
        return null;
    }

    private static double score(TowerCandidate candidate, TowerSelectionContext context) {
        double heightExcess = Math.max(0.0, candidate.getNominalHeight() - context.getRequiredAttachmentHeight());
        double spanExcess = Math.max(0.0, candidate.getSupportedMaxSpan() - context.maxAdjacentSpan());
        double angleExcess = Math.max(0.0, candidate.getSupportedMaxAngle() - context.getDeflectionAngle());
        return heightExcess * 2.0 + spanExcess * 0.5 + angleExcess * 0.25 + candidate.getNominalHeight() * 0.01;
    }
}
