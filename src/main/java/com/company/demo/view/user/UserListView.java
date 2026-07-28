package com.company.demo.view.user;

import com.company.demo.entity.User;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.InputStreamDownloadHandler;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;

@Route(value = "users", layout = MainView.class)
@ViewController(id = "demo_User.list")
@ViewDescriptor(path = "user-list-view.xml")
@LookupComponent("usersDataGrid")
@DialogMode(width = "64em")
public class UserListView extends StandardListView<User> {
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private FileStorageLocator fileStorageLocator;

    @Supply(to = "usersDataGrid.picture", subject = "renderer")
    private Renderer<User> usersDataGridPictureRenderer() {
        return new ComponentRenderer<>(user -> {
            FileRef fileRef = user.getPicture();
            if (fileRef != null) {
                Image image = uiComponents.create(Image.class);
                image.setWidth("32px");
                image.setHeight("32px");
                InputStreamDownloadHandler handler =
                        DownloadHandler.fromInputStream(e -> {
                            InputStream is = fileStorageLocator.getByName(fileRef.getStorageName()).openStream(fileRef);
                            return new DownloadResponse(is, fileRef.getFileName(), fileRef.getContentType(), -1);
                        });
                image.setSrc(handler);
                image.setClassName("user-picture");
                return image;
            } else {
                return null;
            }
        });
    }

}