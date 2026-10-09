package uz.kapitalbank.umida.view.dq;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.fragment.FragmentUtils;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.enums.dq.DqCheckResultStatus;
import uz.kapitalbank.umida.service.dq.DqViolationsService;
import uz.kapitalbank.umida.service.dq.DqViolationsService.ViolationsPage;
import uz.kapitalbank.umida.utils.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * The rows that violated the rule of a check result, read page by page with the result's
 * {@code violationsQuery}. The columns are whatever that query selects, so the grid is built when the
 * first page arrives. Shown by {@link #setCheckResult(DqCheckRunResult)}.
 */
@FragmentDescriptor("dq-violations-fragment.xml")
public class DqViolationsFragment extends Fragment<VerticalLayout> {

    private static final Logger log = LoggerFactory.getLogger(DqViolationsFragment.class);

    public static final int PAGE_SIZE = 50;

    @Autowired
    private DqViolationsService violationsService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private VerticalLayout violationsGridBox;

    @ViewComponent
    private Span pageStatusLabel;

    @ViewComponent
    private JmixButton firstPageButton;

    @ViewComponent
    private JmixButton previousPageButton;

    @ViewComponent
    private JmixButton nextPageButton;

    @ViewComponent
    private JmixButton lastPageButton;

    private UUID checkResultId;
    private long totalCount;
    private int firstResult;
    private Grid<List<Object>> violationsGrid;

    /**
     * Whether the result has violating rows to show: only a rule that failed has them, and only a
     * rule type that can point at individual rows leaves a query to read them with.
     */
    public static boolean hasViolations(@Nullable DqCheckRunResult result) {
        return result != null
                && result.getStatus() == DqCheckResultStatus.FAILED
                && result.getViolationsQuery() != null
                && !result.getViolationsQuery().isBlank();
    }

    /**
     * Shows the first page of the violating rows of the result; with no result, an empty fragment.
     * The result is only read for its id: the query is loaded from the stored result.
     */
    public void setCheckResult(@Nullable DqCheckRunResult checkResult) {
        checkResultId = checkResult == null ? null : checkResult.getId();
        clearGrid();
        if (checkResultId == null) {
            totalCount = 0;
            firstResult = 0;
            updatePagination(0);
            return;
        }
        try {
            totalCount = violationsService.countViolations(checkResultId);
        } catch (RuntimeException e) {
            showLoadError(e);
            return;
        }
        loadPage(0);
    }

    @Subscribe(id = "firstPageButton", subject = "clickListener")
    public void onFirstPageButtonClick(final ClickEvent<JmixButton> event) {
        loadPage(0);
    }

    @Subscribe(id = "previousPageButton", subject = "clickListener")
    public void onPreviousPageButtonClick(final ClickEvent<JmixButton> event) {
        loadPage(Math.max(0, firstResult - PAGE_SIZE));
    }

    @Subscribe(id = "nextPageButton", subject = "clickListener")
    public void onNextPageButtonClick(final ClickEvent<JmixButton> event) {
        loadPage(firstResult + PAGE_SIZE);
    }

    @Subscribe(id = "lastPageButton", subject = "clickListener")
    public void onLastPageButtonClick(final ClickEvent<JmixButton> event) {
        loadPage(lastPageFirstResult());
    }

    private void loadPage(int pageFirstResult) {
        if (checkResultId == null) {
            return;
        }
        ViolationsPage page;
        try {
            page = violationsService.loadViolations(checkResultId, pageFirstResult, PAGE_SIZE);
        } catch (RuntimeException e) {
            showLoadError(e);
            return;
        }

        if (violationsGrid == null) {
            violationsGrid = createGrid(page.columns());
            violationsGridBox.add(violationsGrid);
        }
        violationsGrid.setItems(page.rows());

        firstResult = pageFirstResult;
        updatePagination(page.rows().size());
    }

    private void clearGrid() {
        if (violationsGrid != null) {
            violationsGridBox.remove(violationsGrid);
            violationsGrid = null;
        }
    }

    private Grid<List<Object>> createGrid(List<String> columns) {
        Grid<List<Object>> grid = new Grid<>();
        // a fragment resolves its components by a scoped id, not by the Vaadin one
        FragmentUtils.setComponentId(grid, "violationsDataGrid");
        grid.setColumnReorderingAllowed(true);
        grid.setSizeFull();

        for (int i = 0; i < columns.size(); i++) {
            int index = i;
            grid.addColumn(row -> StringUtils.asText(row.get(index)))
                    .setHeader(columns.get(i))
                    .setAutoWidth(true)
                    .setResizable(true);
        }
        return grid;
    }

    /**
     * The count is taken when the result is set, while each page is read live, so a page is also
     * allowed to come back shorter or longer than the count promised.
     */
    private void updatePagination(int pageSize) {
        boolean hasPrevious = firstResult > 0;
        boolean hasNext = firstResult + pageSize < totalCount;
        firstPageButton.setEnabled(hasPrevious);
        previousPageButton.setEnabled(hasPrevious);
        nextPageButton.setEnabled(hasNext);
        lastPageButton.setEnabled(hasNext);

        pageStatusLabel.setText(pageSize == 0
                ? messageBundle.getMessage("dqViolationsFragment.noRows")
                : messageBundle.formatMessage("dqViolationsFragment.pageStatus",
                firstResult + 1, firstResult + pageSize, totalCount));
    }

    private int lastPageFirstResult() {
        if (totalCount <= 0) {
            return 0;
        }
        long lastPage = (totalCount - 1) / PAGE_SIZE;
        return Math.toIntExact(lastPage * PAGE_SIZE);
    }

    private void showLoadError(RuntimeException e) {
        log.warn("Violating rows of check result {} cannot be read", checkResultId, e);
        pageStatusLabel.setText(messageBundle.getMessage("dqViolationsFragment.loadFailed"));
        notifications.create(messageBundle.getMessage("dqViolationsFragment.loadFailed"),
                        StringUtils.asText(e.getMessage()))
                .withType(Notifications.Type.ERROR)
                .show();
    }
}
