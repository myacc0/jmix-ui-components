package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.Position;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "positions/:id", layout = MainView.class)
@ViewController(id = "umida_Position.detail")
@ViewDescriptor(path = "position-detail-view.xml")
@EditedEntityContainer("positionDc")
public class PositionDetailView extends StandardDetailView<Position> {
}