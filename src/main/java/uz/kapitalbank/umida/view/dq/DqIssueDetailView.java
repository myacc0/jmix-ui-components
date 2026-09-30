package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-issues/:id", layout = MainView.class)
@ViewController(id = "umida_DqIssue.detail")
@ViewDescriptor(path = "dq-issue-detail-view.xml")
@EditedEntityContainer("dqIssueDc")
public class DqIssueDetailView extends StandardDetailView<DqIssue> {
}