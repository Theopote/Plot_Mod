package com.plot.ui.tools.impl.modify.strategy;

import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.ExplodeJoinHandler;
import com.plot.utils.PlotI18n;

import java.util.List;

public class ExplodeStrategy extends SelectionCommandStrategy {

    @Override
    protected String emptySelectionStatusKey() {
        return "status.plot.explode.select_first";
    }

    @Override
    protected String noEffectStatusKey() {
        return "status.plot.explode.nothing";
    }

    @Override
    protected String historyOperationKey() {
        return "history.plot.op.explode";
    }

    @Override
    protected List<Shape> computeResult(List<Shape> selected) {
        return ExplodeJoinHandler.explodeShapes(selected);
    }

    @Override
    protected boolean hasEffect(List<Shape> selected, List<Shape> result) {
        return ExplodeJoinHandler.hasExplodeEffect(selected, result);
    }

    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.explode");
    }

    @Override
    public String getStrategyDescription() {
        return PlotI18n.modeLabel("strategy.plot.desc.explode");
    }
}
