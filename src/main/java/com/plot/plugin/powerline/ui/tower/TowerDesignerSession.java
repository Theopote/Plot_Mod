package com.plot.plugin.powerline.ui.tower;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelopeResolver;
import com.plot.plugin.powerline.design.parametric.TowerConstraintResult;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorMode;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.ParametricFootprintSync;
import com.plot.plugin.powerline.ui.PowerLineUiContext;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Parametric edit session: recompile, envelope resolution, footprint transaction, and preview sync. */
public final class TowerDesignerSession {
    private final PowerLineUiContext ctx;
    private final ParametricTowerEditState parametricState = new ParametricTowerEditState();
    private TowerConstraintResult lastConstraintResult;
    private TowerGeneratorConfig footprintBaseline;
    private boolean footprintRollbackEnabled;

    public TowerDesignerSession(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public TowerConstraintResult lastConstraintResult() {
        return lastConstraintResult;
    }

    public void beginSession(PoleDesign draft) {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        String editingId = ctx.state().getPoleDesignerEditingId();
        footprintRollbackEnabled = ParametricFootprintSync.targetsEditedLine(line, draft, editingId);
        footprintBaseline = captureFootprintBaseline(line);
        lastConstraintResult = null;
        parametricState.captureFromDesign(draft);
    }

    public void endSession(boolean commit, PoleDesign draft) {
        if (commit) {
            commitFootprintBaseline();
        } else {
            rollbackFootprint(draft);
        }
        footprintRollbackEnabled = false;
        footprintBaseline = null;
        lastConstraintResult = null;
        parametricState.reset();
    }

    public void afterDraftRestored(PoleDesign draft) {
        parametricState.captureFromDesign(draft);
        refreshConstraints(draft);
        syncParametricConfigToSelectedLine(draft);
    }

    public boolean canSaveDraft(PoleDesign draft) {
        if (draft == null) {
            return false;
        }
        if (draft.hasTowerStructure()
                && draft.isParametricMode()
                && !draft.isManualLegacyMode()) {
            return canBuild();
        }
        return true;
    }

    public void refreshConstraints(PoleDesign draft) {
        if (draft == null || !draft.isParametricMode() || !draft.hasTowerStructure()) {
            lastConstraintResult = null;
            return;
        }
        lastConstraintResult = TowerParametricEditor.recompile(draft, resolveConstraintEnvelope());
        parametricState.applyRecompileResult(draft, lastConstraintResult);
    }

    /**
     * Legacy 分层 / 参数化塔体单选切换时同步结构与预览状态。
     * <p>
     * Legacy：仅保留分层体素与 FREE 挂点；参数化配置可保留以便切回，但不会在此处 recompile。
     * 参数化塔体：启用或恢复参数化生成，并从当前参数编译结构。
     */
    public void syncStructureMode(PoleDesign draft, boolean towerStructureMode) {
        if (draft == null) {
            return;
        }
        if (!towerStructureMode) {
            TowerArmAttachmentBinding.releaseBoundAttachmentsForLegacyLayers(draft);
            draft.clearTowerStructure();
            lastConstraintResult = null;
            parametricState.reset();
            clearParametricConfigFromSelectedLine(draft);
            return;
        }
        if (draft.isManualLegacyMode()) {
            restoreParametric(draft);
            return;
        }
        if (!draft.isParametricMode()) {
            enableProfile(draft, TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID);
            return;
        }
        lastConstraintResult = TowerParametricEditor.recompile(draft, resolveConstraintEnvelope());
        parametricState.applyRecompileResult(draft, lastConstraintResult);
        if (!draft.hasTowerStructure()) {
            String profileId = draft.getGeneratorConfig() != null
                ? draft.getGeneratorConfig().profileId()
                : TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID;
            switchProfile(draft, profileId);
        } else {
            TowerArmAttachmentBinding.ensureV2Bindings(draft);
            parametricState.captureFromDesign(draft);
        }
        syncParametricConfigToSelectedLine(draft);
    }

    public void applyParametricChange(PoleDesign draft, UnaryOperator<TowerParameterSet> change) {
        applyParametricChange(draft, resolveConstraintEnvelope(), change);
    }

    public void applyParametricChange(
            PoleDesign draft,
            TowerBuildEnvelope envelope,
            UnaryOperator<TowerParameterSet> change) {
        TowerParameterSet requested = change.apply(parametricState.parametersForEdit(draft));
        parametricState.recordRequested(draft, requested);
        lastConstraintResult = TowerParametricEditor.recompile(draft, envelope);
        parametricState.applyRecompileResult(draft, lastConstraintResult);
        syncParametricConfigToSelectedLine(draft);
    }

    public void switchProfile(PoleDesign draft, String profileId) {
        TowerProfileUiCatalog.find(profileId).ifPresent(option -> {
            lastConstraintResult = TowerParametricEditor.switchProfile(
                draft,
                option.id(),
                option.defaults().get(),
                resolveConstraintEnvelope());
            parametricState.applyRecompileResult(draft, lastConstraintResult);
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
        if (!ParametricFootprintSync.usesParametricTower(draft)) {
            return;
        }
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        String editingId = ctx.state().getPoleDesignerEditingId();
        TowerParameterSet footprintParameters = parametricState.footprintParameters(lastConstraintResult);
        boolean synced = footprintParameters != null
            ? ParametricFootprintSync.syncFromDesign(line, draft, editingId, footprintParameters)
            : ParametricFootprintSync.syncFromDesign(line, draft, editingId);
        if (synced) {
            ctx.invalidatePreview();
        }
    }

    public void clearParametricConfigFromSelectedLine(PoleDesign draft) {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        String editingId = ctx.state().getPoleDesignerEditingId();
        if (ParametricFootprintSync.clearFromDesign(line, draft, editingId)) {
            ctx.invalidatePreview();
        }
    }

    public boolean previewShowsLastValidStructure() {
        return lastConstraintResult != null && lastConstraintResult.hasErrors();
    }

    public void onConvertToManual(PoleDesign draft) {
        TowerParametricEditor.convertToManual(draft);
        lastConstraintResult = null;
        parametricState.reset();
    }

    public boolean restoreParametric(PoleDesign draft) {
        if (!TowerParametricEditor.restoreParametric(draft, resolveConstraintEnvelope())) {
            return false;
        }
        lastConstraintResult = TowerParametricEditor.recompile(draft, resolveConstraintEnvelope());
        parametricState.applyRecompileResult(draft, lastConstraintResult);
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

    TowerParameterSet requestedParameters() {
        return parametricState.requestedParameters();
    }

    TowerParameterSet resolvedParameters() {
        return parametricState.resolvedParameters();
    }

    TowerParameterSet lastValidParameters() {
        return parametricState.lastValidParameters();
    }

    private void rollbackFootprint(PoleDesign draft) {
        if (!footprintRollbackEnabled) {
            return;
        }
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        String editingId = ctx.state().getPoleDesignerEditingId();
        if (ParametricFootprintSync.restoreBaseline(line, draft, editingId, footprintBaseline)) {
            ctx.invalidatePreview();
        }
    }

    public void commitFootprintBaselineAfterSave() {
        commitFootprintBaseline();
    }

    private void commitFootprintBaseline() {
        if (!footprintRollbackEnabled) {
            return;
        }
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        footprintBaseline = captureFootprintBaseline(line);
    }

    private static TowerGeneratorConfig captureFootprintBaseline(PowerLineFootprint line) {
        if (line == null || !line.hasParametricTowerConfig()) {
            return null;
        }
        return line.getParametricTowerConfig().copy();
    }
}
