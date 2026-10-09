package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.enums.dq.DqCheckRunStatus;
import uz.kapitalbank.umida.enums.dq.DqCheckRunTrigger;
import uz.kapitalbank.umida.service.dq.DqCheckRunService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "triggered by" caption of a check run: the trigger title for a system run, the user linked to
 * the employee who started a manual one.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckRunServiceTests {

    @Autowired
    DqCheckRunService checkRunService;

    @Autowired
    DataManager dataManager;

    @Autowired
    Messages messages;

    @Autowired
    MetadataTools metadataTools;

    /** In creation order; removed in reverse. */
    private final List<Object> created = new ArrayList<>();

    @Test
    void aSystemRunShowsTheTriggerTitle() {
        DqCheckRun run = createRun(DqCheckRunTrigger.SYSTEM, null);

        assertEquals(messages.getMessage(DqCheckRunTrigger.SYSTEM), captionOf(run));
    }

    @Test
    void aManualRunShowsTheUserLinkedToTheEmployee() {
        OrgStructureEmployee employee = createEmployee();
        User user = createUser(employee);
        DqCheckRun run = createRun(DqCheckRunTrigger.MANUAL, employee);

        assertEquals(metadataTools.getInstanceName(user), captionOf(run));
    }

    @Test
    void aManualRunFallsBackToTheEmployeeWithoutALinkedUser() {
        OrgStructureEmployee employee = createEmployee();
        DqCheckRun run = createRun(DqCheckRunTrigger.MANUAL, employee);

        assertEquals(employee.getFullName(), captionOf(run));
    }

    @Test
    void aManualRunWithoutAnEmployeeShowsNothing() {
        DqCheckRun run = createRun(DqCheckRunTrigger.MANUAL, null);

        assertEquals("", captionOf(run));
    }

    @Test
    void theEmployeeOfAUserIsFoundByUsername() {
        OrgStructureEmployee employee = createEmployee();
        User user = createUser(employee);

        assertEquals(employee.getId(),
                checkRunService.findEmployeeOfUser(user.getUsername()).map(OrgStructureEmployee::getId).orElse(null));
        assertTrue(checkRunService.findEmployeeOfUser("no-such-user-" + UUID.randomUUID()).isEmpty());
    }

    /** Reloads the run the way the list view does, so the caption is computed from loaded data. */
    private String captionOf(DqCheckRun run) {
        DqCheckRun loaded = dataManager.load(DqCheckRun.class)
                .id(run.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("triggeredBy", FetchPlan.INSTANCE_NAME))
                .one();
        Map<UUID, String> captions = checkRunService.getTriggeredByCaptions(List.of(loaded));
        return captions.get(run.getId());
    }

    private OrgStructureEmployee createEmployee() {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("DQ run initiator " + UUID.randomUUID().toString().substring(0, 8));
        return track(dataManager.save(employee));
    }

    private User createUser(OrgStructureEmployee employee) {
        User user = dataManager.create(User.class);
        user.setUsername("dq-run-test-" + UUID.randomUUID());
        user.setFirstName("Dq");
        user.setLastName("Initiator");
        user.setEmployee(employee);
        return track(dataManager.save(user));
    }

    private DqCheckRun createRun(DqCheckRunTrigger trigger, OrgStructureEmployee employee) {
        DqCheckRun run = dataManager.create(DqCheckRun.class);
        run.setDataSource("main");
        run.setTriggered(trigger);
        run.setTriggeredBy(employee);
        run.setStartedAt(OffsetDateTime.now());
        run.setStatus(DqCheckRunStatus.SUCCESS);
        run.setRulesTotal(0);
        return track(dataManager.save(run));
    }

    private <T> T track(T saved) {
        created.add(saved);
        return saved;
    }

    @AfterEach
    void tearDown() {
        List<Object> reversed = new ArrayList<>(created);
        Collections.reverse(reversed);
        reversed.forEach(dataManager::remove);
        created.clear();
    }
}
