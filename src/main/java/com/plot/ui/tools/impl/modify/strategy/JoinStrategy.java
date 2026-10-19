package com.plot.ui.tools.impl.modify.strategy;

import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.ExplodeJoinHandler;
import com.plot.utils.PlotI18n;

import java.util.List;

public class JoinStrategy extends SelectionCommandStrategy {

    @Override
    protected String emptySelectionStatusKey() {
        return "status.plot.join.select_first";
    }

    @Override
    protected String noEffectStatusKey() {
        return "status.plot.join.nothing";
    }

    @Override
    protected String historyOperationKey() {
        return "history.plot.op.join";
    }

    @Override
    protected List<Shape> computeResult(List<Shape> selected) {
        return ExplodeJoinHandler.joinShapes(selected);
    }

    @Override
    protected boolean hasEffect(List<Shape> selected, List<Shape> result) {
        return ExplodeJoinHandler.hasJoinEffect(selected, result);
    }

    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.join");
    }

    @Override
    public String getStrategyDescription() {
        return PlotI18n.modeLabel("strategy.plot.desc.join");
    }
}
