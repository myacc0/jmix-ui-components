package uz.kapitalbank.umida.view.starrockssync;

import uz.kapitalbank.umida.entity.starrockssync.TableSynchronizer;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "table-synchronizers", layout = MainView.class)
@ViewController(id = "umida_TableSynchronizer.list")
@ViewDescriptor(path = "table-synchronizer-list-view.xml")
@LookupComponent("tableSynchronizersDataGrid")
@DialogMode(width = "64em")
public class TableSynchronizerListView extends StandardListView<TableSynchronizer> {
}