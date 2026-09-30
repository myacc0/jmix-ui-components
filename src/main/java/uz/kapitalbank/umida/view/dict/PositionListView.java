package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.Position;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "positions", layout = MainView.class)
@ViewController(id = "umida_Position.list")
@ViewDescriptor(path = "position-list-view.xml")
@LookupComponent("positionsDataGrid")
@DialogMode(width = "64em")
public class PositionListView extends StandardListView<Position> {
}