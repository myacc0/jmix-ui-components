package uz.kapitalbank.umida;

import com.vaadin.flow.component.html.Span;
import io.jmix.core.Metadata;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.data.binding.HtmlContainerReadonlyDataBinding;
import io.jmix.flowui.model.DataComponents;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.component.UserTimeZoneHtmlContainerDataBinding;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.enums.dq.DqCheckRunStatus;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An HTML component bound to a property in a descriptor shows the value as a field does: a date and
 * time in the user's time zone, whatever offset the value carries.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class UserTimeZoneHtmlContainerDataBindingTest {

    @Autowired
    HtmlContainerReadonlyDataBinding htmlContainerDataBinding;

    @Autowired
    DataComponents dataComponents;

    @Autowired
    Metadata metadata;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Test
    void theDescriptorsUseTheTimeZoneAwareBinding() {
        assertInstanceOf(UserTimeZoneHtmlContainerDataBinding.class, htmlContainerDataBinding);
    }

    @Test
    void aDateTimeIsShownInTheUserTimeZone() {
        OffsetDateTime now = OffsetDateTime.now(currentAuthentication.getTimeZone().toZoneId());
        // an offset other than the user's, so that showing the value as it is would show another time
        ZoneOffset otherOffset = now.getOffset().equals(ZoneOffset.UTC) ? ZoneOffset.ofHours(5) : ZoneOffset.UTC;
        OffsetDateTime startedAt = OffsetDateTime.of(2026, 3, 5, 23, 30, 15, 0, otherOffset);

        DqCheckRun checkRun = metadata.create(DqCheckRun.class);
        checkRun.setStartedAt(startedAt);
        checkRun.setStatus(DqCheckRunStatus.SUCCESS);
        InstanceContainer<DqCheckRun> container = dataComponents.createInstanceContainer(DqCheckRun.class);
        container.setItem(checkRun);

        Span startedAtValue = new Span();
        Span statusValue = new Span();
        htmlContainerDataBinding.bind(startedAtValue, container, "startedAt");
        htmlContainerDataBinding.bind(statusValue, container, "status");

        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        assertEquals(format.format(startedAt.atZoneSameInstant(currentAuthentication.getTimeZone().toZoneId())),
                startedAtValue.getText());
        assertNotEquals(format.format(startedAt), startedAtValue.getText(), "the offset of the value is not kept");

        OffsetDateTime finishedAt = startedAt.plusHours(1);
        container.getItem().setFinishedAt(finishedAt);
        Span finishedAtValue = new Span();
        htmlContainerDataBinding.bind(finishedAtValue, container, "finishedAt");
        container.getItem().setFinishedAt(finishedAt.plusMinutes(1));
        assertEquals(format.format(finishedAt.plusMinutes(1)
                        .atZoneSameInstant(currentAuthentication.getTimeZone().toZoneId())),
                finishedAtValue.getText(), "a changed value is shown again");

        assertFalse(statusValue.getText().isBlank(), "an enum is shown by its caption");
    }
}
