package uz.kapitalbank.umida.view.dq;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.function.ValueProvider;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.fragment.FragmentUtils;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.ViewComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.dto.dq.DqReconciliation;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.enums.dq.DqCheckResultStatus;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.service.dq.DqCrossSourceReconciler;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a cross-source rule compared in a check: the configured pairs of columns side by side, with the
 * value each data source returned and whether they match. Read from the reconciliation stored on the
 * check result, so it shows the data as the check saw it. Shown by {@link #setCheckResult}.
 */
@FragmentDescriptor("dq-reconciliation-fragment.xml")
public class DqReconciliationFragment extends Fragment<VerticalLayout> {

    /** Shown for a value the query returned as NULL, to tell it from an empty cell. */
    private static final String NULL_TEXT = "NULL";

    @Autowired
    private DqCrossSourceReconciler crossSourceReconciler;

    @Autowired
    private DqBadges dqBadges;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private VerticalLayout reconciliationGridBox;

    @ViewComponent
    private Span summaryLabel;

    private Grid<DqReconciliation.Pair> reconciliationGrid;

    /** Whether the result keeps a reconciliation to show: only a checked cross-source rule does. */
    public static boolean hasReconciliation(@Nullable DqCheckRunResult result) {
        return result != null
                && result.getReconciliation() != null
                && !result.getReconciliation().isBlank();
    }

    /** Shows the reconciliation of the result; with none, an empty grid. */
    public void setCheckResult(@Nullable DqCheckRunResult checkResult) {
        DqReconciliation reconciliation = checkResult == null
                ? null
                : crossSourceReconciler.fromJson(checkResult.getReconciliation());

        if (reconciliationGrid == null) {
            reconciliationGrid = createGrid();
            reconciliationGridBox.add(reconciliationGrid);
        }

        if (reconciliation == null) {
            reconciliationGrid.setItems(List.of());
            summaryLabel.setText(messageBundle.getMessage("dqReconciliationFragment.noData"));
            return;
        }

        reconciliationGrid.setItems(reconciliation.getPairs());
        summaryLabel.setText(messageBundle.formatMessage("dqReconciliationFragment.summary",
                dataSource(reconciliation.getPrimary()), dataSource(reconciliation.getSecondary()),
                reconciliation.mismatches().size(), reconciliation.getPairs().size()));
    }

    private Grid<DqReconciliation.Pair> createGrid() {
        Grid<DqReconciliation.Pair> grid = new Grid<>();
        // a fragment resolves its components by a scoped id, not by the Vaadin one
        FragmentUtils.setComponentId(grid, "reconciliationDataGrid");
        grid.setColumnReorderingAllowed(true);
        grid.setSizeFull();

        addColumn(grid, "dqReconciliationFragment.primaryAlias", DqReconciliation.Pair::getPrimaryAlias);
        addColumn(grid, "dqReconciliationFragment.primaryValue", pair -> number(pair.getPrimaryValue()));
        addColumn(grid, "dqReconciliationFragment.secondaryAlias", DqReconciliation.Pair::getSecondaryAlias);
        addColumn(grid, "dqReconciliationFragment.secondaryValue", pair -> number(pair.getSecondaryValue()));
        addColumn(grid, "dqReconciliationFragment.difference", pair -> {
            BigDecimal difference = pair.difference();
            return difference == null ? "" : difference.toPlainString();
        });
        grid.addColumn(new ComponentRenderer<>(this::matchBadge))
                .setHeader(messageBundle.getMessage("dqReconciliationFragment.result"))
                .setAutoWidth(true)
                .setResizable(true);
        return grid;
    }

    private void addColumn(Grid<DqReconciliation.Pair> grid, String headerKey,
                           ValueProvider<DqReconciliation.Pair, String> value) {
        grid.addColumn(value)
                .setHeader(messageBundle.getMessage(headerKey))
                .setAutoWidth(true)
                .setResizable(true);
    }

    private Span matchBadge(DqReconciliation.Pair pair) {
        return pair.isMatched()
                ? dqBadges.badge(DqBadges.RESULT_STATUS, DqCheckResultStatus.PASSED,
                messageBundle.getMessage("dqReconciliationFragment.matched"))
                : dqBadges.badge(DqBadges.RESULT_STATUS, DqCheckResultStatus.FAILED,
                messageBundle.getMessage("dqReconciliationFragment.mismatched"));
    }

    private static String number(@Nullable BigDecimal value) {
        return value == null ? NULL_TEXT : value.toPlainString();
    }

    private static String dataSource(@Nullable DqReconciliation.Source source) {
        return source == null || source.getDataSource() == null ? "" : source.getDataSource();
    }
}
