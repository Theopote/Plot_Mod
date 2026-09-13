package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.AttachmentRole;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 相邻杆塔挂点配对：优先 ID 精确匹配，否则按 {@link AttachmentRole} 顺序 fallback。 */
final class ConductorAttachmentMatcher {

    record Pair(ResolvedAttachment start, ResolvedAttachment end) {
    }

    private ConductorAttachmentMatcher() {
    }

    static List<Pair> match(
            List<ResolvedAttachment> startAttachments,
            List<ResolvedAttachment> endAttachments,
            PowerLineGenerationResult result,
            Vec2d startPlan,
            Vec2d endPlan) {
        Map<String, ResolvedAttachment> startById = indexById(startAttachments, result, startPlan);
        Map<String, ResolvedAttachment> endById = indexById(endAttachments, result, endPlan);

        Set<ResolvedAttachment> matchedStart = newSet();
        Set<ResolvedAttachment> matchedEnd = newSet();
        List<Pair> pairs = new ArrayList<>();

        matchById(startById, endById, matchedStart, matchedEnd, pairs, result);
        matchByRoleOrder(
            startAttachments,
            endAttachments,
            startById,
            endById,
            matchedStart,
            matchedEnd,
            pairs,
            result);
        emitUnmatchedWarnings(
            startAttachments,
            endAttachments,
            startById,
            endById,
            matchedStart,
            matchedEnd,
            result,
            startPlan,
            endPlan);

        return pairs;
    }

    private static void matchById(
            Map<String, ResolvedAttachment> startById,
            Map<String, ResolvedAttachment> endById,
            Set<ResolvedAttachment> matchedStart,
            Set<ResolvedAttachment> matchedEnd,
            List<Pair> pairs,
            PowerLineGenerationResult result) {
        for (Map.Entry<String, ResolvedAttachment> entry : startById.entrySet()) {
            String id = entry.getKey();
            ResolvedAttachment startAttachment = entry.getValue();
            ResolvedAttachment endAttachment = endById.get(id);
            if (endAttachment == null) {
                continue;
            }
            if (startAttachment.role() != endAttachment.role()) {
                if (result != null) {
                    result.warnings.add(PowerLineGenerationI18n.attachmentRoleMismatch(
                        id,
                        startAttachment.role(),
                        endAttachment.role()));
                }
                continue;
            }
            pairs.add(new Pair(startAttachment, endAttachment));
            matchedStart.add(startAttachment);
            matchedEnd.add(endAttachment);
        }
    }

    private static void matchByRoleOrder(
            List<ResolvedAttachment> startAttachments,
            List<ResolvedAttachment> endAttachments,
            Map<String, ResolvedAttachment> startById,
            Map<String, ResolvedAttachment> endById,
            Set<ResolvedAttachment> matchedStart,
            Set<ResolvedAttachment> matchedEnd,
            List<Pair> pairs,
            PowerLineGenerationResult result) {
        Map<AttachmentRole, List<ResolvedAttachment>> unmatchedStartByRole =
            groupUnmatchedByRole(startAttachments, startById, matchedStart);
        Map<AttachmentRole, List<ResolvedAttachment>> unmatchedEndByRole =
            groupUnmatchedByRole(endAttachments, endById, matchedEnd);

        for (AttachmentRole role : roleOrder(unmatchedStartByRole, unmatchedEndByRole)) {
            List<ResolvedAttachment> startRoleList = unmatchedStartByRole.getOrDefault(role, List.of());
            List<ResolvedAttachment> endRoleList = unmatchedEndByRole.getOrDefault(role, List.of());
            int pairCount = Math.min(startRoleList.size(), endRoleList.size());
            for (int i = 0; i < pairCount; i++) {
                ResolvedAttachment startAttachment = startRoleList.get(i);
                ResolvedAttachment endAttachment = endRoleList.get(i);
                pairs.add(new Pair(startAttachment, endAttachment));
                matchedStart.add(startAttachment);
                matchedEnd.add(endAttachment);
                if (result != null) {
                    result.warnings.add(PowerLineGenerationI18n.attachmentRoleFallback(
                        startAttachment.id(),
                        endAttachment.id(),
                        role));
                }
            }
        }
    }

    private static List<AttachmentRole> roleOrder(
            Map<AttachmentRole, List<ResolvedAttachment>> startByRole,
            Map<AttachmentRole, List<ResolvedAttachment>> endByRole) {
        List<AttachmentRole> order = new ArrayList<>();
        Set<AttachmentRole> seen = new HashSet<>();
        for (AttachmentRole role : startByRole.keySet()) {
            if (seen.add(role)) {
                order.add(role);
            }
        }
        for (AttachmentRole role : endByRole.keySet()) {
            if (seen.add(role)) {
                order.add(role);
            }
        }
        return order;
    }

    private static Map<AttachmentRole, List<ResolvedAttachment>> groupUnmatchedByRole(
            List<ResolvedAttachment> attachments,
            Map<String, ResolvedAttachment> indexed,
            Set<ResolvedAttachment> matched) {
        Map<AttachmentRole, List<ResolvedAttachment>> grouped = new LinkedHashMap<>();
        if (attachments == null) {
            return grouped;
        }
        for (ResolvedAttachment attachment : attachments) {
            if (attachment == null || !indexed.containsValue(attachment) || matched.contains(attachment)) {
                continue;
            }
            grouped.computeIfAbsent(attachment.role(), ignored -> new ArrayList<>()).add(attachment);
        }
        return grouped;
    }

    private static void emitUnmatchedWarnings(
            List<ResolvedAttachment> startAttachments,
            List<ResolvedAttachment> endAttachments,
            Map<String, ResolvedAttachment> startById,
            Map<String, ResolvedAttachment> endById,
            Set<ResolvedAttachment> matchedStart,
            Set<ResolvedAttachment> matchedEnd,
            PowerLineGenerationResult result,
            Vec2d startPlan,
            Vec2d endPlan) {
        if (result == null) {
            return;
        }
        for (ResolvedAttachment attachment : listIndexed(startAttachments, startById)) {
            if (!matchedStart.contains(attachment)) {
                result.warnings.add(PowerLineGenerationI18n.missingAttachmentDownstream(
                    attachment.name(),
                    attachment.id(),
                    endPlan != null ? endPlan.x : 0.0,
                    endPlan != null ? endPlan.y : 0.0));
            }
        }
        for (ResolvedAttachment attachment : listIndexed(endAttachments, endById)) {
            if (!matchedEnd.contains(attachment)) {
                result.warnings.add(PowerLineGenerationI18n.missingAttachmentUpstream(
                    attachment.name(),
                    attachment.id(),
                    startPlan != null ? startPlan.x : 0.0,
                    startPlan != null ? startPlan.y : 0.0));
            }
        }
    }

    private static List<ResolvedAttachment> listIndexed(
            List<ResolvedAttachment> attachments,
            Map<String, ResolvedAttachment> indexed) {
        List<ResolvedAttachment> listed = new ArrayList<>();
        if (attachments == null) {
            return listed;
        }
        for (ResolvedAttachment attachment : attachments) {
            if (attachment != null && indexed.containsValue(attachment)) {
                listed.add(attachment);
            }
        }
        return listed;
    }

    private static Map<String, ResolvedAttachment> indexById(
            List<ResolvedAttachment> attachments,
            PowerLineGenerationResult result,
            Vec2d polePosition) {
        LinkedHashMap<String, ResolvedAttachment> indexed = new LinkedHashMap<>();
        if (attachments == null) {
            return indexed;
        }
        for (ResolvedAttachment attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String id = attachment.id();
            if (indexed.containsKey(id)) {
                if (result != null) {
                    result.warnings.add(PowerLineGenerationI18n.duplicateAttachmentId(
                        attachment.name(),
                        id,
                        polePosition != null ? polePosition.x : 0.0,
                        polePosition != null ? polePosition.y : 0.0));
                }
            }
            indexed.put(id, attachment);
        }
        return indexed;
    }

    private static Set<ResolvedAttachment> newSet() {
        return newSetWithExpectedSize(4);
    }

    private static Set<ResolvedAttachment> newSetWithExpectedSize(int expected) {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>(Math.max(4, expected)));
    }
}
