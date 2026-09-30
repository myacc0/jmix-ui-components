package com.company.demo.datastructure;

import com.company.demo.DemoApplication;
import com.company.demo.component.d3orgchart.D3OrgChart;
import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.entity.dict.DictDataDomainSteward;
import com.company.demo.entity.dict.DictDataProduct;
import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.entity.orgstructure.Position;
import com.company.demo.enums.orgstructure.OrgStructurePositionStatus;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.orgstructure.DataAssetCardView;
import com.company.demo.view.orgstructure.DataDomainStructureView;
import com.company.demo.view.orgstructure.DataProductStructureView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.data.grid.TreeDataGridItems;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opens the data domain and data product structure views, selects an element in the tree
 * and clicks a chart node the way the browser reports it, then checks the card dialog that
 * opens. Every component the controllers bind by id is resolved here, so a broken descriptor,
 * data container or component id fails this test rather than in front of the user.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DataStructureViewsUiTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;

    private final List<Object> cleanup = new ArrayList<>();

    private String suffix;
    private DictDataDomain rootDomain;
    private DictDataDomain childDomain;
    private DictDataProduct rootProduct;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        Department owner = create(Department.class, d -> d.setName("Owner " + suffix));
        JobTitle title = create(JobTitle.class, t -> t.setName("Director " + suffix));
        Employee head = create(Employee.class, e -> {
            e.setFullName("Head " + suffix);
            e.setEmail("head-" + suffix + "@test.com");
        });
        Employee stewardEmployee = create(Employee.class, e -> e.setFullName("Steward " + suffix));
        create(Position.class, p -> {
            p.setDepartment(owner);
            p.setJobTitle(title);
            p.setEmployee(head);
            p.setIshead(1);
            p.setStatus(OrgStructurePositionStatus.FILLED);
        });

        rootDomain = create(DictDataDomain.class, d -> fillDomain(d, "Root domain " + suffix, null, owner));
        childDomain = create(DictDataDomain.class, d -> fillDomain(d, "Child domain " + suffix, rootDomain, owner));
        create(DictDataDomainSteward.class, s -> {
            s.setDomain(childDomain);
            s.setEmployee(stewardEmployee);
            s.setDateBegin(LocalDate.now().minusDays(1));
        });

        rootProduct = create(DictDataProduct.class, p -> {
            p.setCode("code-" + UUID.randomUUID());
            p.setShortName("Root product " + suffix);
            p.setBusinessOwner(owner);
            p.setAssignDate(LocalDate.of(2026, 3, 1));
        });
    }

    @Test
    void domainChartNodeOpensTheCardOfTheDomain() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DataDomainStructureView.class).navigate();
        DataDomainStructureView view = UiTestUtils.getCurrentView();

        TreeDataGrid<DictDataDomain> tree = UiTestUtils.getComponent(view, "domainsTreeDataGrid");
        tree.select(treeItem(tree, rootDomain.getId()));

        clickChartNode(UiTestUtils.getComponent(view, "d3OrgChart"), String.valueOf(childDomain.getId()));

        DataAssetCardView card = openedCard();
        assertEquals("Child domain " + suffix, card.getPageTitle(), "the domain name is the dialog title");
        assertEquals("Head " + suffix, this.<H4>component(card, "headNameLabel").getText());
        assertEquals("Director " + suffix, this.<Span>component(card, "headJobTitleLabel").getText());
        assertEquals("Owner " + suffix, this.<Span>component(card, "headDepartmentLabel").getText());
        assertEquals("head-" + suffix + "@test.com", this.<Span>component(card, "headEmailLabel").getText());
        // the head has no photo, so the initials of the domain name stand in for it
        assertEquals("CD", this.<Span>component(card, "headPhotoPlaceholder").getText());

        assertEquals("Long Child domain " + suffix, this.<Span>component(card, "longNameValue").getText());
        assertEquals("Owner " + suffix, this.<Span>component(card, "businessOwnerValue").getText());
        assertFalse(this.<Span>component(card, "assignDateValue").getText().isBlank());
        assertFalse(this.<Span>component(card, "createdDateValue").getText().isBlank());

        Div stewards = component(card, "stewardsValue");
        assertEquals(List.of("Steward " + suffix),
                stewards.getChildren().map(child -> ((HasText) child).getText()).toList());
    }

    @Test
    void productChartNodeOpensTheCardOfTheProduct() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DataProductStructureView.class).navigate();
        DataProductStructureView view = UiTestUtils.getCurrentView();

        TreeDataGrid<DictDataProduct> tree = UiTestUtils.getComponent(view, "productsTreeDataGrid");
        tree.select(treeItem(tree, rootProduct.getId()));

        clickChartNode(UiTestUtils.getComponent(view, "d3OrgChart"), String.valueOf(rootProduct.getId()));

        DataAssetCardView card = openedCard();
        assertEquals("Root product " + suffix, card.getPageTitle());
        assertEquals("Head " + suffix, this.<H4>component(card, "headNameLabel").getText());
        // no long name, no description and no stewards — every empty value shows a dash
        assertEquals("—", this.<Span>component(card, "longNameValue").getText());
        assertEquals("—", this.<Span>component(card, "descriptionValue").getText());
        Div stewards = component(card, "stewardsValue");
        assertEquals(List.of("—"), stewards.getChildren().map(child -> ((HasText) child).getText()).toList());
    }

    @Test
    void chartNodeClickWithoutSelectionOpensNothing() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DataDomainStructureView.class).navigate();
        DataDomainStructureView view = UiTestUtils.getCurrentView();

        clickChartNode(UiTestUtils.getComponent(view, "d3OrgChart"), String.valueOf(rootDomain.getId()));

        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        assertTrue(findDescendant(UI.getCurrent(), DataAssetCardView.class).isEmpty(),
                "the chart is empty until a tree element is selected");
    }

    /** The browser reports a node click as a DOM event; the server sees exactly this event. */
    private void clickChartNode(D3OrgChart chart, String nodeId) {
        ComponentUtil.fireEvent(chart, new D3OrgChart.NodeClickEvent(chart, true, nodeId));
    }

    private <T extends Component> T component(DataAssetCardView card, String id) {
        return UiTestUtils.getComponent(card, id);
    }

    private DataAssetCardView openedCard() {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return findDescendant(ui, DataAssetCardView.class)
                .orElseGet(() -> fail("DataAssetCardView is not opened"));
    }

    private <E> E treeItem(TreeDataGrid<E> tree, Object id) {
        TreeDataGridItems<E> items = tree.getItems();
        E item = items != null ? items.getItem(id) : null;
        if (item == null) {
            fail("item " + id + " is not shown in the tree");
        }
        return item;
    }

    private <V> Optional<V> findDescendant(Component component, Class<V> componentClass) {
        return component.getChildren()
                .map(child -> componentClass.isInstance(child)
                        ? Optional.of(componentClass.cast(child))
                        : findDescendant(child, componentClass))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private void fillDomain(DictDataDomain domain, String shortName, DictDataDomain parent, Department owner) {
        domain.setCode("code-" + UUID.randomUUID());
        domain.setShortName(shortName);
        domain.setLongName("Long " + shortName);
        domain.setParent(parent);
        domain.setBusinessOwner(owner);
        domain.setAssignDate(LocalDate.of(2026, 1, 15));
    }

    private <T> T create(Class<T> entityClass, java.util.function.Consumer<T> initializer) {
        T entity = dataManager.create(entityClass);
        initializer.accept(entity);
        T saved = dataManager.save(entity);
        cleanup.add(saved);
        return saved;
    }

    @AfterEach
    void tearDown() {
        // reverse creation order; domains, products and stewards are soft-deletable, and a
        // soft-deleted row would keep its foreign key to the department — remove them for real
        List<Object> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        for (Object entity : reversed) {
            dataManager.save(new SaveContext()
                    .removing(entity)
                    .setHint(PersistenceHints.SOFT_DELETION, false));
        }
        cleanup.clear();
    }
}
