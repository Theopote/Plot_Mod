package com.plot.plugin.powerline.ui.tower;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelopeResolver;
import com.plot.plugin.powerline.design.parametric.TowerConstraintResult;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorMode;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.ParametricFootprintSync;
import com.plot.plugin.powerline.ui.PowerLineUiContext;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Parametric edit session: recompile, envelope resolution, and footprint sync. */
public final class TowerDesignerSession {
    private final PowerLineUiContext ctx;
    private TowerConstraintResult lastConstraintResult;

    public TowerDesignerSession(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public TowerConstraintResult lastConstraintResult() {
        return lastConstraintResult;
    }

    public void refreshConstraints(PoleDesign draft) {
        if (draft == null || !draft.isParametricMode()) {
            lastConstraintResult = null;
            return;
        }
        lastConstraintResult = TowerParametricEditor.recompile(draft, resolveConstraintEnvelope());
    }

    public void applyParametricChange(PoleDesign draft, UnaryOperator<TowerParameterSet> change) {
        TowerDesignerParameterController.ApplyResult result =
            TowerDesignerParameterController.applyParametricChange(draft, resolveConstraintEnvelope(), change);
        lastConstraintResult = result.constraintResult();
        syncParametricConfigToSelectedLine(draft);
    }

    public void switchProfile(PoleDesign draft, String profileId) {
        TowerProfileUiCatalog.find(profileId).ifPresent(option -> {
            String previousProfileId = draft.getGeneratorConfig() != null
                ? draft.getGeneratorConfig().profileId()
                : null;
            option.enabler().accept(draft);
            if (draft.getGeneratorConfig() != null && draft.isParametricMode()) {
                lastConstraintResult = TowerParametricEditor.recompile(
                    draft,
                    resolveConstraintEnvelope(),
                    previousProfileId);
            }
            syncParametricConfigToSelectedLine(draft);
        });
    }

    public void enableProfile(PoleDesign draft, String profileId) {
        switchProfile(draft, profileId);
    }

    public boolean canBuild() {
        return TowerDesignerParameterController.canBuild(lastConstraintResult);
    }

    public Optional<TowerLineBuildEnvelope> tryResolveLineEnvelope() {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            return Optional.empty();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return Optional.empty();
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(client.world, ctx.coordinates());
        return TowerBuildEnvelopeResolver.tryFromFootprint(line, terrain, ctx.coordinates());
    }

    public TowerBuildEnvelope resolveConstraintEnvelope() {
        return tryResolveLineEnvelope()
            .map(TowerLineBuildEnvelope::constraintEnvelope)
            .orElse(TowerBuildEnvelopeResolver.tryFromClientPlayer().orElse(null));
    }

    public void syncParametricConfigToSelectedLine(PoleDesign draft) {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        String editingId = ctx.state().getPoleDesignerEditingId();
        if (ParametricFootprintSync.syncFromDesign(line, draft, editingId)) {
            ctx.invalidatePreview();
        }
    }

    public boolean previewShowsLastValidStructure() {
        return lastConstraintResult != null && lastConstraintResult.hasErrors();
    }

    public void onConvertToManual(PoleDesign draft) {
        TowerParametricEditor.convertToManual(draft);
        lastConstraintResult = null;
    }

    public boolean restoreParametric(PoleDesign draft) {
        if (!TowerParametricEditor.restoreParametric(draft, resolveConstraintEnvelope())) {
            return false;
        }
        lastConstraintResult = TowerParametricEditor.recompile(draft, resolveConstraintEnvelope());
        syncParametricConfigToSelectedLine(draft);
        return true;
    }

    public boolean isSameProfile(PoleDesign draft, String profileId) {
        if (draft == null || draft.getGeneratorConfig() == null) {
            return false;
        }
        return Objects.equals(draft.getGeneratorConfig().profileId(), profileId);
    }

    public TowerGeneratorConfig generatorConfig(PoleDesign draft) {
        return draft != null ? draft.getGeneratorConfig() : null;
    }

    public boolean isParametric(PoleDesign draft) {
        return draft != null && draft.isParametricMode();
    }

    public boolean isManualLegacy(PoleDesign draft) {
        return draft != null && draft.isManualLegacyMode();
    }

    public TowerGeneratorMode mode(PoleDesign draft) {
        TowerGeneratorConfig config = generatorConfig(draft);
        return config != null ? config.mode() : null;
    }
}
